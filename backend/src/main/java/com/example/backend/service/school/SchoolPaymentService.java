package com.example.backend.service.school;

import com.example.backend.dto.school.SchoolPaymentRecoveryRequest;
import com.example.backend.dto.school.SchoolRegistrationRequest;
import com.example.backend.entity.account.User;
import com.example.backend.entity.school.LicensePlan;
import com.example.backend.entity.school.School;
import com.example.backend.entity.school.SchoolPayment;
import com.example.backend.repository.account.RoleRepository;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.school.LicensePlanRepository;
import com.example.backend.repository.school.SchoolPaymentRepository;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.service.account.CurrentUserService;

import com.example.backend.entity.enums.RoleName;
import com.example.backend.exception.ApiException;
import com.example.backend.config.properties.VnpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URI;
import java.net.http.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;

@Service @RequiredArgsConstructor
public class SchoolPaymentService {
    private final LicensePlanRepository plans;
    private final SchoolPaymentRepository payments;
    private final SchoolRepository schools;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final CurrentUserService currentUser;
    private final LicenseCheckService licenseCheck;
    private final jakarta.persistence.EntityManager entityManager;
    private final VnpayProperties vnpay;
    private final HttpClient http;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public record Checkout(UUID paymentId, String paymentUrl) { }
    public record Notification(UUID id, String schoolName, String planCode, Long amountVnd, Instant paidAt, String status) { }
    public record Quote(String planCode, String purpose, long amountVnd, LocalDate licenseStart, LocalDate licenseEnd) { }
    public record PaymentRow(UUID id, String planCode, String purpose, Long amountVnd, String status, Instant createdAt, Instant paidAt) { }
    public record AdminPaymentRow(UUID id, String schoolName, String managerEmail, String planCode, String purpose, Long amountVnd, String status, Instant createdAt, Instant paidAt) { }
    public record RevenueSummary(long paidTransactions, long pendingTransactions, long reviewTransactions, long grossPaidVnd) { }
    public record Billing(String planCode, String nextPlanCode, LocalDate licenseStart, LocalDate licenseEnd,
        Integer studentQuota, long studentsUsed, Integer monthlyTokenQuota, long tokensUsed, List<PaymentRow> payments) { }

    @Transactional
    public Checkout checkout(SchoolRegistrationRequest request, String ip) {
        requireConfigured();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu không được vượt quá 72 byte UTF-8.");
        var plan = plans.findById(request.planCode()).filter(LicensePlan::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Gói đăng ký không còn khả dụng."));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String code = request.schoolCode().trim().toUpperCase(Locale.ROOT);
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            var user = existing.get();
            if (Boolean.FALSE.equals(user.getActive()) && user.getSchool() != null && code.equals(user.getSchool().getCode())
                && passwords.matches(request.password(), user.getPassword())) return recover(new SchoolPaymentRecoveryRequest(email, request.password()), ip);
            throw new ApiException(HttpStatus.CONFLICT, "Email đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác.");
        }
        if (schools.existsByCode(code) || schools.findByName(request.schoolName().trim()).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "Trường đã đăng ký. Vui lòng liên hệ người quản lý trường.");
        School school = new School();
        school.setCode(code); school.setName(request.schoolName().trim()); school.setAddress(request.address().trim());
        school.setContactEmail(email); school.setPhoneNumber(request.phoneNumber().trim()); school.setActive(false);
        schools.save(school);
        User manager = new User(); manager.setEmail(email); manager.setFullName(request.fullName().trim());
        manager.setPassword(passwords.encode(request.password())); manager.setSchool(school); manager.setActive(false);
        manager.setRole(roles.findByName(RoleName.SCHOOL_MANAGER.name()).orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Role quản lý trường chưa được cấu hình.")));
        users.save(manager);
        SchoolPayment payment = new SchoolPayment(); payment.setSchool(school); payment.setManager(manager);
        payment.setPlanCode(plan.getCode()); payment.setAmountVnd(plan.getAnnualPriceVnd()); payment.setMonthlyTokenQuota(plan.getMonthlyTokenQuota());
        payment.setStudentQuota(plan.getStudentQuota());
        payment.setAnnualPriceVnd(plan.getAnnualPriceVnd());
        payments.save(payment);
        return new Checkout(payment.getId(), buildUrl(payment, ip));
    }

    private void requireConfigured() {
        if (vnpay.tmnCode().isBlank() || vnpay.hashSecret().isBlank()
                || vnpay.paymentUrl().isBlank() || vnpay.returnUrl().isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            "VNPAY Sandbox chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
    }

    String buildUrl(SchoolPayment payment, String ip) {
        var now = payment.getCreatedAt().atZone(vnpay.zoneId());
        var format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0"); params.put("vnp_Command", "pay"); params.put("vnp_TmnCode", vnpay.tmnCode());
        params.put("vnp_Amount", Long.toString(Math.multiplyExact(payment.getAmountVnd(), 100)));
        params.put("vnp_CurrCode", "VND"); params.put("vnp_Locale", "vn"); params.put("vnp_OrderType", "other");
        params.put("vnp_TxnRef", payment.getId().toString()); params.put("vnp_OrderInfo", "PhysLive " + payment.getPlanCode());
        params.put("vnp_ReturnUrl", vnpay.returnUrl()); params.put("vnp_IpAddr", ip);
        params.put("vnp_CreateDate", now.format(format)); params.put("vnp_ExpireDate", now.plusMinutes(15).format(format));
        String query = canonical(params);
        return vnpay.paymentUrl() + "?" + query + "&vnp_SecureHash=" + sign(query);
    }

    static String canonical(Map<String, String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
            .filter(e -> e.getKey().startsWith("vnp_") && !e.getKey().equals("vnp_SecureHash") && !e.getKey().equals("vnp_SecureHashType") && e.getValue() != null && !e.getValue().isEmpty())
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
            .collect(Collectors.joining("&"));
    }

    String sign(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(vnpay.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("Cannot sign VNPAY request", ex); }
    }

    @Transactional
    public Map<String, String> ipn(Map<String, String> fields) {
        if (vnpay.hashSecret().isBlank() || !MessageDigest.isEqual(sign(canonical(fields)).getBytes(StandardCharsets.US_ASCII),
            fields.getOrDefault("vnp_SecureHash", "").toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) return reply("97", "Invalid signature");
        if (!vnpay.tmnCode().equals(fields.get("vnp_TmnCode"))) return reply("97", "Invalid merchant");
        UUID id;
        try { id = UUID.fromString(fields.getOrDefault("vnp_TxnRef", "")); } catch (IllegalArgumentException ex) { return reply("01", "Order not found"); }
        var reference = payments.findById(id);
        if (reference.isEmpty()) return reply("01", "Order not found");
        lockedSchool(reference.get().getSchool().getId());
        var found = payments.findLockedById(id);
        if (found.isEmpty()) return reply("01", "Order not found");
        var payment = found.get();
        if (!Long.toString(payment.getAmountVnd() * 100).equals(fields.get("vnp_Amount"))) return reply("04", "Invalid amount");
        boolean success = "00".equals(fields.get("vnp_ResponseCode")) && "00".equals(fields.get("vnp_TransactionStatus"));
        if (!"PENDING".equals(payment.getStatus()) && !("FAILED".equals(payment.getStatus()) && success)) return reply("02", "Order already confirmed");
        if ("00".equals(fields.get("vnp_ResponseCode")) && "00".equals(fields.get("vnp_TransactionStatus"))) {
            activate(payment, fields.get("vnp_TransactionNo"));
        } else if ("02".equals(fields.get("vnp_TransactionStatus"))) payment.setStatus("FAILED");
        return reply("00", "Confirm success");
    }

    private static Map<String, String> reply(String code, String message) { return Map.of("RspCode", code, "Message", message); }

    private void activate(SchoolPayment payment, String transactionNo) {
        var school = lockedSchool(payment.getSchool().getId());
        if (("REGISTRATION".equals(payment.getPurpose()) && school.getPlanCode() != null)
            || (!"REGISTRATION".equals(payment.getPurpose()) && !Objects.equals(school.getPlanCode(), payment.getPreviousPlanCode()))
            || (payment.getStudentQuota() != null && users.countActiveStudents(school.getId()) > payment.getStudentQuota())) {
            payment.setStatus("REQUIRES_REVIEW"); payment.setPaidAt(Instant.now()); payment.setProviderTransactionNo(transactionNo); return;
        }
        LocalDate start = payment.getLicenseStart() == null ? LocalDate.now(vnpay.zoneId()) : payment.getLicenseStart();
        LocalDate end = payment.getLicenseEnd() == null ? start.plusYears(1).minusDays(1) : payment.getLicenseEnd();
        if ("REGISTRATION".equals(payment.getPurpose())) {
            school.setActive(true); payment.getManager().setActive(true);
        }
        school.setLicenseStart(start); school.setLicenseEnd(end);
        school.setMonthlyTokenQuota(payment.getMonthlyTokenQuota()); school.setStudentQuota(payment.getStudentQuota());
        school.setPlanCode(payment.getPlanCode()); school.setAnnualPriceVnd(payment.getAnnualPriceVnd() == null ? payment.getAmountVnd() : payment.getAnnualPriceVnd());
        school.setNextPlanCode(null);
        payment.setStatus("PAID"); payment.setPaidAt(Instant.now()); payment.setProviderTransactionNo(transactionNo);
    }

    @Transactional
    public Checkout recover(SchoolPaymentRecoveryRequest credentials, String ip) {
        requireConfigured();
        var user = users.findByEmail(credentials.email().trim().toLowerCase(Locale.ROOT))
            .filter(u -> passwords.matches(credentials.password(), u.getPassword()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."));
        if (user.getSchool() == null || user.getRole() == null || !RoleName.SCHOOL_MANAGER.matches(user.getRole().getName()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản không có đăng ký trường cần thanh toán.");
        lockedSchool(user.getSchool().getId());
        var latest = payments.findFirstByManagerIdOrderByCreatedAtDesc(user.getId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đăng ký cần thanh toán."));
        var payment = payments.findLockedById(latest.getId()).orElseThrow();
        if ("PAID".equals(payment.getStatus())) return new Checkout(payment.getId(), null);
        if ("PENDING".equals(payment.getStatus()) && !payment.getCreatedAt().plusSeconds(900).isAfter(Instant.now())) reconcile(payment);
        if ("PAID".equals(payment.getStatus())) return new Checkout(payment.getId(), null);
        if ("REQUIRES_REVIEW".equals(payment.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "Giao dịch cần admin đối soát. Vui lòng liên hệ hỗ trợ.");
        if (!"REGISTRATION".equals(payment.getPurpose()) && "FAILED".equals(payment.getStatus())) return new Checkout(payment.getId(), null);
        if ("PENDING".equals(payment.getStatus())) {
            if (!payment.getCreatedAt().plusSeconds(900).isAfter(Instant.now()))
                throw new ApiException(HttpStatus.CONFLICT, "VNPAY đang xử lý giao dịch. Vui lòng kiểm tra lại trước khi thanh toán lần nữa.");
            return new Checkout(payment.getId(), buildUrl(payment, ip));
        }
        var retry = new SchoolPayment(); retry.setSchool(payment.getSchool()); retry.setManager(payment.getManager());
        retry.setPlanCode(payment.getPlanCode()); retry.setAmountVnd(payment.getAmountVnd()); retry.setAnnualPriceVnd(payment.getAnnualPriceVnd());
        retry.setMonthlyTokenQuota(payment.getMonthlyTokenQuota()); retry.setStudentQuota(payment.getStudentQuota());
        retry.setPurpose(payment.getPurpose()); retry.setLicenseStart(payment.getLicenseStart()); retry.setLicenseEnd(payment.getLicenseEnd());
        retry.setPreviousPlanCode(payment.getPreviousPlanCode());
        payments.save(retry); return new Checkout(retry.getId(), buildUrl(retry, ip));
    }

    Map<String, String> queryProvider(SchoolPayment payment) {
        Map<String,String> fields = new LinkedHashMap<>();
        fields.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", "")); fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "querydr"); fields.put("vnp_TmnCode", vnpay.tmnCode()); fields.put("vnp_TxnRef", payment.getId().toString());
        fields.put("vnp_TransactionDate", payment.getCreatedAt().atZone(vnpay.zoneId()).format(DATE_FORMAT));
        fields.put("vnp_CreateDate", ZonedDateTime.now(vnpay.zoneId()).format(DATE_FORMAT)); fields.put("vnp_IpAddr", vnpay.serverIp());
        fields.put("vnp_OrderInfo", "Query PhysLive payment"); fields.put("vnp_SecureHash", sign(String.join("|", fields.values())));
        try {
            var mapper = new ObjectMapper();
            var request = HttpRequest.newBuilder(URI.create(vnpay.queryUrl())).timeout(vnpay.requestTimeout())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(fields))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new java.io.IOException("Query failed");
            return mapper.readValue(response.body(), new TypeReference<Map<String,String>>() { });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Truy vấn VNPAY bị gián đoạn.");
        } catch (java.io.IOException ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Không thể xác minh giao dịch với VNPAY. Vui lòng thử lại."); }
    }

    void reconcile(SchoolPayment payment) {
        var fields = queryProvider(payment);
        String input = java.util.stream.Stream.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount")
            .map(key -> Objects.toString(fields.get(key), "")).collect(Collectors.joining("|"));
        if (!MessageDigest.isEqual(sign(input).getBytes(StandardCharsets.US_ASCII), fields.getOrDefault("vnp_SecureHash", "").toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))
            || !vnpay.tmnCode().equals(fields.get("vnp_TmnCode")) || !payment.getId().toString().equals(fields.get("vnp_TxnRef")))
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Phản hồi VNPAY không hợp lệ.");
        if ("91".equals(fields.get("vnp_ResponseCode"))) { payment.setStatus("FAILED"); return; }
        if (!"00".equals(fields.get("vnp_ResponseCode")) || !Long.toString(payment.getAmountVnd() * 100).equals(fields.get("vnp_Amount")))
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VNPAY chưa xác minh được giao dịch.");
        if ("00".equals(fields.get("vnp_TransactionStatus")) && "01".equals(fields.get("vnp_TransactionType"))) activate(payment, fields.get("vnp_TransactionNo"));
        else if ("02".equals(fields.get("vnp_TransactionStatus"))) payment.setStatus("FAILED");
    }

    @Scheduled(fixedDelayString = "${VNPAY_RECONCILE_DELAY_MS}")
    @Transactional
    public void reconcileExpiredPayments() {
        if (vnpay.tmnCode().isBlank() || vnpay.hashSecret().isBlank()) return;
        Instant cutoff = Instant.now().minusSeconds(900);
        for (SchoolPayment payment : payments.findByStatusAndCreatedAtBefore("PENDING", cutoff)) {
            try { reconcile(payments.findLockedById(payment.getId()).orElse(payment)); }
            catch (RuntimeException ex) { payment.setStatus("REQUIRES_REVIEW"); payments.save(payment); }
        }
    }

    @Transactional
    public String reconcilePayment(UUID id) {
        SchoolPayment payment = payments.findLockedById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch."));
        if ("PENDING".equals(payment.getStatus())) reconcile(payment);
        return payment.getStatus();
    }

    @Transactional(readOnly = true)
    public List<AdminPaymentRow> adminPayments() {
        return payments.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).stream()
                .map(payment -> new AdminPaymentRow(payment.getId(), payment.getSchool().getName(), payment.getManager().getEmail(), payment.getPlanCode(), payment.getPurpose(), payment.getAmountVnd(), payment.getStatus(), payment.getCreatedAt(), payment.getPaidAt())).toList();
    }

    @Transactional(readOnly = true)
    public RevenueSummary revenueSummary() {
        var totals = payments.summarizeRevenue();
        return new RevenueSummary(totals.getPaidTransactions(), totals.getPendingTransactions(),
                totals.getReviewTransactions(), totals.getGrossPaidVnd());
    }

    @Transactional(readOnly=true)
    public String status(UUID id) {
        var payment = payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch."));
        return "PENDING".equals(payment.getStatus()) && !payment.getCreatedAt().plusSeconds(900).isAfter(Instant.now()) ? "EXPIRED" : payment.getStatus();
    }

    private User manager() {
        var user = currentUser.requireCurrentUser();
        if (user.getSchool() == null || user.getRole() == null
                || !RoleName.SCHOOL_MANAGER.matches(user.getRole().getName()) || !user.getSchool().isActive())
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ quản lý trường đang hoạt động được quản lý gói.");
        return user;
    }

    private School lockedSchool(UUID id) {
        var school = schools.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found"));
        entityManager.refresh(school, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return school;
    }

    @Transactional(readOnly=true)
    public Billing billing() { return billing(manager().getSchool()); }

    private Billing billing(School school) {
        long used = LocalDate.now(vnpay.zoneId()).withDayOfMonth(1).equals(school.getTokenUsageMonth()) && school.getUsedTokens() != null ? school.getUsedTokens() : 0;
        return new Billing(school.getPlanCode(), school.getNextPlanCode(), school.getLicenseStart(), school.getLicenseEnd(),
            school.getStudentQuota(), users.countActiveStudents(school.getId()), school.getMonthlyTokenQuota(), used,
            payments.findBySchoolIdOrderByCreatedAtDesc(school.getId()).stream().map(p -> new PaymentRow(p.getId(), p.getPlanCode(), p.getPurpose(), p.getAmountVnd(),
                "PENDING".equals(p.getStatus()) && p.getCreatedAt().plusSeconds(900).isBefore(Instant.now()) ? "EXPIRED" : p.getStatus(), p.getCreatedAt(), p.getPaidAt())).toList());
    }

    private LicensePlan availablePlan(String code) {
        return plans.findById(code).filter(LicensePlan::isActive).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Gói không còn khả dụng."));
    }

    @Transactional(readOnly=true)
    public Quote quote(String code) { return quote(manager().getSchool(), availablePlan(code)); }

    private Quote quote(School school, LicensePlan plan) {
        LocalDate today = LocalDate.now(vnpay.zoneId());
        if (plan.getStudentQuota() < users.countActiveStudents(school.getId()))
            throw new ApiException(HttpStatus.CONFLICT, "Số học sinh đang hoạt động vượt quota gói này. Vui lòng giảm số tài khoản trước khi đổi gói.");
        if (school.getLicenseEnd() == null || school.getLicenseEnd().isBefore(today))
            return new Quote(plan.getCode(), "RENEWAL", plan.getAnnualPriceVnd(), today, today.plusYears(1).minusDays(1));
        if (school.getLicenseStart() == null || school.getLicenseStart().isAfter(today) || school.getAnnualPriceVnd() == null || school.getPlanCode() == null)
            throw new ApiException(HttpStatus.CONFLICT, "Gói hiện tại cần admin xác nhận thông tin trước khi đổi gói.");
        if (school.getPlanCode().equals(plan.getCode()))
            throw new ApiException(HttpStatus.CONFLICT, "Trường đang dùng gói này. Bạn có thể chọn gói kỳ tiếp theo hoặc gia hạn khi hết hạn.");
        long difference = plan.getAnnualPriceVnd() - school.getAnnualPriceVnd();
        if (difference <= 0) throw new ApiException(HttpStatus.CONFLICT, "Hạ gói chỉ áp dụng ở kỳ tiếp theo. Vui lòng chọn gói cho kỳ sau.");
        long remaining = ChronoUnit.DAYS.between(today, school.getLicenseEnd()) + 1;
        long duration = ChronoUnit.DAYS.between(school.getLicenseStart(), school.getLicenseEnd()) + 1;
        long amount = BigDecimal.valueOf(difference).multiply(BigDecimal.valueOf(remaining))
            .divide(BigDecimal.valueOf(duration), 0, RoundingMode.CEILING).longValueExact();
        return new Quote(plan.getCode(), "UPGRADE", amount, school.getLicenseStart(), school.getLicenseEnd());
    }

    @Transactional
    public Checkout purchase(String code, Long expectedAmount, String ip) {
        requireConfigured(); var manager = manager();
        var school = lockedSchool(manager.getSchool().getId());
        var pending = payments.findBySchoolIdOrderByCreatedAtDesc(school.getId()).stream().filter(p -> "PENDING".equals(p.getStatus())).findFirst();
        if (pending.isPresent()) {
            var payment = payments.findLockedById(pending.get().getId()).orElseThrow();
            if (!payment.getCreatedAt().plusSeconds(900).isAfter(Instant.now())) reconcile(payment);
            if (!"PENDING".equals(payment.getStatus())) return new Checkout(payment.getId(), null);
            if (!payment.getPlanCode().equals(code) || !payment.getCreatedAt().plusSeconds(900).isAfter(Instant.now()))
                throw new ApiException(HttpStatus.CONFLICT, "Còn giao dịch đang xử lý. Vui lòng kiểm tra giao dịch đó trước.");
            if (!Objects.equals(expectedAmount, payment.getAmountVnd())) throw new ApiException(HttpStatus.CONFLICT, "Có giao dịch cũ với số tiền khác. Vui lòng kiểm tra lịch sử thanh toán.");
            return new Checkout(payment.getId(), buildUrl(payment, ip));
        }
        var plan = availablePlan(code); var quote = quote(school, plan);
        if (!Objects.equals(expectedAmount, quote.amountVnd())) throw new ApiException(HttpStatus.CONFLICT, "Báo giá đã thay đổi. Vui lòng lấy lại báo giá trước khi thanh toán.");
        var payment = new SchoolPayment(); payment.setSchool(school); payment.setManager(manager);
        payment.setPlanCode(code); payment.setPreviousPlanCode(school.getPlanCode()); payment.setPurpose(quote.purpose());
        payment.setAmountVnd(quote.amountVnd()); payment.setAnnualPriceVnd(plan.getAnnualPriceVnd());
        payment.setStudentQuota(plan.getStudentQuota()); payment.setMonthlyTokenQuota(plan.getMonthlyTokenQuota());
        payment.setLicenseStart(quote.licenseStart()); payment.setLicenseEnd(quote.licenseEnd());
        payments.save(payment); return new Checkout(payment.getId(), buildUrl(payment, ip));
    }

    @Transactional
    public Billing nextPlan(String code) {
        var manager = manager();
        licenseCheck.requireWriteAccess(manager);
        var school = lockedSchool(manager.getSchool().getId());
        availablePlan(code); school.setNextPlanCode(code.equals(school.getPlanCode()) ? null : code);
        return billing(school);
    }

    @Transactional(readOnly=true)
    public List<Notification> notifications() {
        return payments.findTop20ByStatusInOrderByPaidAtDesc(List.of("PAID", "REQUIRES_REVIEW")).stream()
            .map(p -> new Notification(p.getId(), p.getSchool().getName(), p.getPlanCode(), p.getAmountVnd(), p.getPaidAt(), p.getStatus())).toList();
    }
}
