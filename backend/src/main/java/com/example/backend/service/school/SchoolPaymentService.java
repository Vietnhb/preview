package com.example.backend.service.school;

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
import jakarta.validation.constraints.*;
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

    public record Registration(@NotBlank String planCode, @NotBlank @Size(max=200) String schoolName,
        @NotBlank @Size(max=80) @Pattern(regexp="[A-Za-z0-9_-]+") String schoolCode,
        @NotBlank @Size(max=300) String address, @NotBlank @Size(max=200) String fullName,
        @NotBlank @Email @Size(max=100) String email, @NotBlank @Size(max=20) String phoneNumber,
        @NotBlank @Size(min=8,max=72) String password) { }
    public record Checkout(UUID paymentId, String paymentUrl) { }
    public record Notification(UUID id, String schoolName, String planCode, Long amountVnd, Instant paidAt, String status) { }
    public record Recovery(@NotBlank @Email String email, @NotBlank String password) { }
    public record PlanChoice(@NotBlank String planCode, @Positive Long expectedAmountVnd) { }
    public record Quote(String planCode, String purpose, long amountVnd, LocalDate licenseStart, LocalDate licenseEnd) { }
    public record PaymentRow(UUID id, String planCode, String purpose, Long amountVnd, String status, Instant createdAt, Instant paidAt) { }
    public record AdminPaymentRow(UUID id, String schoolName, String managerEmail, String planCode, String purpose, Long amountVnd, String status, Instant createdAt, Instant paidAt) { }
    public record RevenueSummary(long paidTransactions, long pendingTransactions, long reviewTransactions, long grossPaidVnd) { }
    public record Billing(String planCode, String nextPlanCode, LocalDate licenseStart, LocalDate licenseEnd,
        Integer studentQuota, long studentsUsed, Integer monthlyTokenQuota, long tokensUsed, List<PaymentRow> payments) { }

    @Transactional
    public Checkout checkout(Registration request, String ip) {
        requireConfigured();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "MÃƒÂ¡Ã‚ÂºÃ‚Â­t khÃƒÂ¡Ã‚ÂºÃ‚Â©u khÃƒÆ’Ã‚Â´ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c vÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£t quÃƒÆ’Ã‚Â¡ 72 byte UTF-8.");
        var plan = plans.findById(request.planCode()).filter(LicensePlan::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "GÃƒÆ’Ã‚Â³i Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng kÃƒÆ’Ã‚Â½ khÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â²n khÃƒÂ¡Ã‚ÂºÃ‚Â£ dÃƒÂ¡Ã‚Â»Ã‚Â¥ng."));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String code = request.schoolCode().trim().toUpperCase(Locale.ROOT);
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            var user = existing.get();
            if (Boolean.FALSE.equals(user.getActive()) && user.getSchool() != null && code.equals(user.getSchool().getCode())
                && passwords.matches(request.password(), user.getPassword())) return recover(new Recovery(email, request.password()), ip);
            throw new ApiException(HttpStatus.CONFLICT, "Email Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c sÃƒÂ¡Ã‚Â»Ã‚Â­ dÃƒÂ¡Ã‚Â»Ã‚Â¥ng. Vui lÃƒÆ’Ã‚Â²ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng nhÃƒÂ¡Ã‚ÂºÃ‚Â­p hoÃƒÂ¡Ã‚ÂºÃ‚Â·c dÃƒÆ’Ã‚Â¹ng email khÃƒÆ’Ã‚Â¡c.");
        }
        if (schools.existsByCode(code) || schools.findByName(request.schoolName().trim()).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "TrÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng kÃƒÆ’Ã‚Â½. Vui lÃƒÆ’Ã‚Â²ng liÃƒÆ’Ã‚Âªn hÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡ ngÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âi quÃƒÂ¡Ã‚ÂºÃ‚Â£n lÃƒÆ’Ã‚Â½ trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng.");
        School school = new School();
        school.setCode(code); school.setName(request.schoolName().trim()); school.setAddress(request.address().trim());
        school.setContactEmail(email); school.setPhoneNumber(request.phoneNumber().trim()); school.setActive(false);
        schools.save(school);
        User manager = new User(); manager.setEmail(email); manager.setFullName(request.fullName().trim());
        manager.setPassword(passwords.encode(request.password())); manager.setSchool(school); manager.setActive(false);
        manager.setRole(roles.findByName(RoleName.SCHOOL_MANAGER.name()).orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Role quÃƒÂ¡Ã‚ÂºÃ‚Â£n lÃƒÆ’Ã‚Â½ trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng chÃƒâ€ Ã‚Â°a Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c cÃƒÂ¡Ã‚ÂºÃ‚Â¥u hÃƒÆ’Ã‚Â¬nh.")));
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
            "VNPAY Sandbox chÃƒâ€ Ã‚Â°a Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c cÃƒÂ¡Ã‚ÂºÃ‚Â¥u hÃƒÆ’Ã‚Â¬nh. Vui lÃƒÆ’Ã‚Â²ng liÃƒÆ’Ã‚Âªn hÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡ quÃƒÂ¡Ã‚ÂºÃ‚Â£n trÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ viÃƒÆ’Ã‚Âªn.");
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
    public Checkout recover(Recovery credentials, String ip) {
        requireConfigured();
        var user = users.findByEmail(credentials.email().trim().toLowerCase(Locale.ROOT))
            .filter(u -> passwords.matches(credentials.password(), u.getPassword()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email hoÃƒÂ¡Ã‚ÂºÃ‚Â·c mÃƒÂ¡Ã‚ÂºÃ‚Â­t khÃƒÂ¡Ã‚ÂºÃ‚Â©u khÃƒÆ’Ã‚Â´ng Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Âºng."));
        if (user.getSchool() == null || user.getRole() == null || !RoleName.SCHOOL_MANAGER.matches(user.getRole().getName()))
            throw new ApiException(HttpStatus.FORBIDDEN, "TÃƒÆ’Ã‚Â i khoÃƒÂ¡Ã‚ÂºÃ‚Â£n khÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â³ Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng kÃƒÆ’Ã‚Â½ trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng cÃƒÂ¡Ã‚ÂºÃ‚Â§n thanh toÃƒÆ’Ã‚Â¡n.");
        lockedSchool(user.getSchool().getId());
        var latest = payments.findFirstByManagerIdOrderByCreatedAtDesc(user.getId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng kÃƒÆ’Ã‚Â½ cÃƒÂ¡Ã‚ÂºÃ‚Â§n thanh toÃƒÆ’Ã‚Â¡n."));
        var payment = payments.findLockedById(latest.getId()).orElseThrow();
        if ("PAID".equals(payment.getStatus())) return new Checkout(payment.getId(), null);
        if ("PENDING".equals(payment.getStatus()) && !payment.getCreatedAt().plusSeconds(900).isAfter(Instant.now())) reconcile(payment);
        if ("PAID".equals(payment.getStatus())) return new Checkout(payment.getId(), null);
        if ("REQUIRES_REVIEW".equals(payment.getStatus())) throw new ApiException(HttpStatus.CONFLICT, "Giao dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch cÃƒÂ¡Ã‚ÂºÃ‚Â§n admin Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœi soÃƒÆ’Ã‚Â¡t. Vui lÃƒÆ’Ã‚Â²ng liÃƒÆ’Ã‚Âªn hÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡ hÃƒÂ¡Ã‚Â»Ã¢â‚¬â€ trÃƒÂ¡Ã‚Â»Ã‚Â£.");
        if (!"REGISTRATION".equals(payment.getPurpose()) && "FAILED".equals(payment.getStatus())) return new Checkout(payment.getId(), null);
        if ("PENDING".equals(payment.getStatus())) {
            if (!payment.getCreatedAt().plusSeconds(900).isAfter(Instant.now()))
                throw new ApiException(HttpStatus.CONFLICT, "VNPAY Ãƒâ€žÃ¢â‚¬Ëœang xÃƒÂ¡Ã‚Â»Ã‚Â­ lÃƒÆ’Ã‚Â½ giao dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch. Vui lÃƒÆ’Ã‚Â²ng kiÃƒÂ¡Ã‚Â»Ã†â€™m tra lÃƒÂ¡Ã‚ÂºÃ‚Â¡i trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc khi thanh toÃƒÆ’Ã‚Â¡n lÃƒÂ¡Ã‚ÂºÃ‚Â§n nÃƒÂ¡Ã‚Â»Ã‚Â¯a.");
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
            Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Truy vÃƒÂ¡Ã‚ÂºÃ‚Â¥n VNPAY bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ giÃƒÆ’Ã‚Â¡n Ãƒâ€žÃ¢â‚¬ËœoÃƒÂ¡Ã‚ÂºÃ‚Â¡n.");
        } catch (java.io.IOException ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ xÃƒÆ’Ã‚Â¡c minh giao dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi VNPAY. Vui lÃƒÆ’Ã‚Â²ng thÃƒÂ¡Ã‚Â»Ã‚Â­ lÃƒÂ¡Ã‚ÂºÃ‚Â¡i."); }
    }

    void reconcile(SchoolPayment payment) {
        var fields = queryProvider(payment);
        String input = java.util.stream.Stream.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode", "vnp_PromotionAmount")
            .map(key -> Objects.toString(fields.get(key), "")).collect(Collectors.joining("|"));
        if (!MessageDigest.isEqual(sign(input).getBytes(StandardCharsets.US_ASCII), fields.getOrDefault("vnp_SecureHash", "").toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))
            || !vnpay.tmnCode().equals(fields.get("vnp_TmnCode")) || !payment.getId().toString().equals(fields.get("vnp_TxnRef")))
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PhÃƒÂ¡Ã‚ÂºÃ‚Â£n hÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“i VNPAY khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        if ("91".equals(fields.get("vnp_ResponseCode"))) { payment.setStatus("FAILED"); return; }
        if (!"00".equals(fields.get("vnp_ResponseCode")) || !Long.toString(payment.getAmountVnd() * 100).equals(fields.get("vnp_Amount")))
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VNPAY chÃƒâ€ Ã‚Â°a xÃƒÆ’Ã‚Â¡c minh Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c giao dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch.");
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
        SchoolPayment payment = payments.findLockedById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y giao dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch."));
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
        var rows = payments.findAll();
        return new RevenueSummary(rows.stream().filter(p -> "PAID".equals(p.getStatus())).count(), rows.stream().filter(p -> "PENDING".equals(p.getStatus())).count(), rows.stream().filter(p -> "REQUIRES_REVIEW".equals(p.getStatus())).count(), rows.stream().filter(p -> "PAID".equals(p.getStatus())).mapToLong(SchoolPayment::getAmountVnd).sum());
    }

    @Transactional(readOnly=true)
    public String status(UUID id) {
        var payment = payments.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y giao dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch."));
        return "PENDING".equals(payment.getStatus()) && !payment.getCreatedAt().plusSeconds(900).isAfter(Instant.now()) ? "EXPIRED" : payment.getStatus();
    }

    private User manager() {
        var user = currentUser.requireCurrentUser();
        if (user.getSchool() == null || user.getRole() == null
                || !RoleName.SCHOOL_MANAGER.matches(user.getRole().getName()) || !user.getSchool().isActive())
            throw new ApiException(HttpStatus.FORBIDDEN, "ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° quÃƒÂ¡Ã‚ÂºÃ‚Â£n lÃƒÆ’Ã‚Â½ trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng Ãƒâ€žÃ¢â‚¬Ëœang hoÃƒÂ¡Ã‚ÂºÃ‚Â¡t Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c quÃƒÂ¡Ã‚ÂºÃ‚Â£n lÃƒÆ’Ã‚Â½ gÃƒÆ’Ã‚Â³i.");
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
        return plans.findById(code).filter(LicensePlan::isActive).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "GÃƒÆ’Ã‚Â³i khÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â²n khÃƒÂ¡Ã‚ÂºÃ‚Â£ dÃƒÂ¡Ã‚Â»Ã‚Â¥ng."));
    }

    @Transactional(readOnly=true)
    public Quote quote(String code) { return quote(manager().getSchool(), availablePlan(code)); }

    private Quote quote(School school, LicensePlan plan) {
        LocalDate today = LocalDate.now(vnpay.zoneId());
        if (plan.getStudentQuota() < users.countActiveStudents(school.getId()))
            throw new ApiException(HttpStatus.CONFLICT, "SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ hÃƒÂ¡Ã‚Â»Ã‚Âc sinh Ãƒâ€žÃ¢â‚¬Ëœang hoÃƒÂ¡Ã‚ÂºÃ‚Â¡t Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢ng vÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£t quota gÃƒÆ’Ã‚Â³i nÃƒÆ’Ã‚Â y. Vui lÃƒÆ’Ã‚Â²ng giÃƒÂ¡Ã‚ÂºÃ‚Â£m sÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ tÃƒÆ’Ã‚Â i khoÃƒÂ¡Ã‚ÂºÃ‚Â£n trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc khi Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢i gÃƒÆ’Ã‚Â³i.");
        if (school.getLicenseEnd() == null || school.getLicenseEnd().isBefore(today))
            return new Quote(plan.getCode(), "RENEWAL", plan.getAnnualPriceVnd(), today, today.plusYears(1).minusDays(1));
        if (school.getLicenseStart() == null || school.getLicenseStart().isAfter(today) || school.getAnnualPriceVnd() == null || school.getPlanCode() == null)
            throw new ApiException(HttpStatus.CONFLICT, "GÃƒÆ’Ã‚Â³i hiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i cÃƒÂ¡Ã‚ÂºÃ‚Â§n admin xÃƒÆ’Ã‚Â¡c nhÃƒÂ¡Ã‚ÂºÃ‚Â­n thÃƒÆ’Ã‚Â´ng tin trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc khi Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢i gÃƒÆ’Ã‚Â³i.");
        if (school.getPlanCode().equals(plan.getCode()))
            throw new ApiException(HttpStatus.CONFLICT, "TrÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âng Ãƒâ€žÃ¢â‚¬Ëœang dÃƒÆ’Ã‚Â¹ng gÃƒÆ’Ã‚Â³i nÃƒÆ’Ã‚Â y. BÃƒÂ¡Ã‚ÂºÃ‚Â¡n cÃƒÆ’Ã‚Â³ thÃƒÂ¡Ã‚Â»Ã†â€™ chÃƒÂ¡Ã‚Â»Ã‚Ân gÃƒÆ’Ã‚Â³i kÃƒÂ¡Ã‚Â»Ã‚Â³ tiÃƒÂ¡Ã‚ÂºÃ‚Â¿p theo hoÃƒÂ¡Ã‚ÂºÃ‚Â·c gia hÃƒÂ¡Ã‚ÂºÃ‚Â¡n khi hÃƒÂ¡Ã‚ÂºÃ‚Â¿t hÃƒÂ¡Ã‚ÂºÃ‚Â¡n.");
        long difference = plan.getAnnualPriceVnd() - school.getAnnualPriceVnd();
        if (difference <= 0) throw new ApiException(HttpStatus.CONFLICT, "HÃƒÂ¡Ã‚ÂºÃ‚Â¡ gÃƒÆ’Ã‚Â³i chÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° ÃƒÆ’Ã‚Â¡p dÃƒÂ¡Ã‚Â»Ã‚Â¥ng ÃƒÂ¡Ã‚Â»Ã…Â¸ kÃƒÂ¡Ã‚Â»Ã‚Â³ tiÃƒÂ¡Ã‚ÂºÃ‚Â¿p theo. Vui lÃƒÆ’Ã‚Â²ng chÃƒÂ¡Ã‚Â»Ã‚Ân gÃƒÆ’Ã‚Â³i cho kÃƒÂ¡Ã‚Â»Ã‚Â³ sau.");
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
                throw new ApiException(HttpStatus.CONFLICT, "CÃƒÆ’Ã‚Â²n giao dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch Ãƒâ€žÃ¢â‚¬Ëœang xÃƒÂ¡Ã‚Â»Ã‚Â­ lÃƒÆ’Ã‚Â½. Vui lÃƒÆ’Ã‚Â²ng kiÃƒÂ¡Ã‚Â»Ã†â€™m tra giao dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â³ trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc.");
            if (!Objects.equals(expectedAmount, payment.getAmountVnd())) throw new ApiException(HttpStatus.CONFLICT, "CÃƒÆ’Ã‚Â³ giao dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch cÃƒâ€¦Ã‚Â© vÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi sÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ tiÃƒÂ¡Ã‚Â»Ã‚Ân khÃƒÆ’Ã‚Â¡c. Vui lÃƒÆ’Ã‚Â²ng kiÃƒÂ¡Ã‚Â»Ã†â€™m tra lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch sÃƒÂ¡Ã‚Â»Ã‚Â­ thanh toÃƒÆ’Ã‚Â¡n.");
            return new Checkout(payment.getId(), buildUrl(payment, ip));
        }
        var plan = availablePlan(code); var quote = quote(school, plan);
        if (!Objects.equals(expectedAmount, quote.amountVnd())) throw new ApiException(HttpStatus.CONFLICT, "BÃƒÆ’Ã‚Â¡o giÃƒÆ’Ã‚Â¡ Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ thay Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢i. Vui lÃƒÆ’Ã‚Â²ng lÃƒÂ¡Ã‚ÂºÃ‚Â¥y lÃƒÂ¡Ã‚ÂºÃ‚Â¡i bÃƒÆ’Ã‚Â¡o giÃƒÆ’Ã‚Â¡ trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc khi thanh toÃƒÆ’Ã‚Â¡n.");
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
