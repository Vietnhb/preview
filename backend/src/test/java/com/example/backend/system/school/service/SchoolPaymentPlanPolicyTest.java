package com.example.backend.system.school.service;

import com.example.backend.config.VnpayProperties;
import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.Role;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.repository.RoleRepository;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.account.TestPermissions;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.school.dto.SchoolPaymentContracts.Checkout;
import com.example.backend.system.school.dto.SchoolPaymentContracts;
import com.example.backend.system.school.model.entity.LicensePlan;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.school.model.entity.SchoolPayment;
import com.example.backend.system.school.repository.LicensePlanRepository;
import com.example.backend.system.school.repository.SchoolPaymentRepository;
import com.example.backend.system.school.repository.SchoolRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchoolPaymentPlanPolicyTest {
    private final LicensePlanRepository plans = mock(LicensePlanRepository.class);
    private final SchoolPaymentRepository payments = mock(SchoolPaymentRepository.class);
    private final SchoolRepository schools = mock(SchoolRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final VnpayProperties vnpay = new VnpayProperties("TEST", "test-only-secret",
            "https://payments.example.test/pay", "https://app.example.test/return", "https://payments.example.test/query",
            "127.0.0.1", ZoneId.of("Asia/Bangkok"), Duration.ofSeconds(1), Duration.ofSeconds(1));
    private final SchoolPaymentService service = new SchoolPaymentService(plans, payments, schools, users,
            roles, mock(PasswordEncoder.class), currentUser, TestPermissions.access(), mock(LicenseCheckService.class),
            mock(EntityManager.class), vnpay, mock(HttpClient.class), mock(ApplicationEventPublisher.class));
    private final LocalDate today = LocalDate.now(vnpay.zoneId());
    private School school;
    private User manager;
    private LicensePlan small;
    private LicensePlan large;

    @BeforeEach
    void setup() {
        small = plan("CUSTOM_SMALL", 1_000_000L, 100, 10_000);
        large = plan("CUSTOM_LARGE", 3_000_000L, 500, 100_000);
        school = new School();
        school.setId(UUID.randomUUID());
        school.setName("Test School");
        school.setLicenseStart(today.minusMonths(3));
        school.setLicenseEnd(today.plusMonths(9));
        school.setPlanCode(small.getCode());
        school.setAnnualPriceVnd(small.getAnnualPriceVnd());
        school.setStudentQuota(small.getStudentQuota());
        school.setMonthlyTokenQuota(small.getMonthlyTokenQuota());
        school.setUsedTokens(2_000L);
        school.setTokenUsageMonth(today.withDayOfMonth(1));
        Role role = new Role();
        role.setName("SCHOOL");
        manager = new User();
        manager.setId(4);
        manager.setRole(role);
        manager.setSchool(school);
        when(currentUser.requireCurrentUser()).thenReturn(manager);
        when(schools.findByIdForUpdate(school.getId())).thenReturn(Optional.of(school));
        when(users.countActiveStudents(school.getId())).thenReturn(50L);
        when(plans.findById(small.getCode())).thenReturn(Optional.of(small));
        when(plans.findById(large.getCode())).thenReturn(Optional.of(large));
        when(plans.findByActiveTrueOrderByAnnualPriceVndAsc()).thenReturn(List.of(small, large));
        when(payments.findBySchoolIdOrderByCreatedAtDesc(school.getId())).thenReturn(List.of());
        when(payments.save(any(SchoolPayment.class))).thenAnswer(invocation -> {
            SchoolPayment payment = invocation.getArgument(0);
            if (payment.getId() == null) payment.setId(UUID.randomUUID());
            return payment;
        });
    }

    @Test
    void activeUpgradeQuotesProratedDifferenceAndImmediatelyReplacesPaidEntitlement() {
        LocalDate oldStart = school.getLicenseStart();
        LocalDate oldEnd = school.getLicenseEnd();
        school.setNextPlanCode("LEGACY_QUEUE");
        var quote = service.quote(large.getCode());
        long remaining = ChronoUnit.DAYS.between(today, oldEnd) + 1;
        long duration = ChronoUnit.DAYS.between(oldEnd.minusYears(1), oldEnd);
        long expected = BigDecimal.valueOf(2_000_000L).multiply(BigDecimal.valueOf(remaining))
                .divide(BigDecimal.valueOf(duration), 0, RoundingMode.CEILING).longValueExact();
        assertEquals("UPGRADE", quote.purpose());
        assertEquals(expected, quote.amountVnd());
        assertEquals(oldStart, quote.licenseStart());
        assertEquals(oldEnd, quote.licenseEnd());
        SchoolPayment payment = checkout(quote);
        assertEquals(small.getCode(), school.getPlanCode(), "Checkout alone must not activate entitlements");

        assertEquals("00", service.ipn(ipn(payment, payment.getAmountVnd())).get("RspCode"));
        assertEquals("PAID", payment.getStatus());
        assertEquals(large.getCode(), school.getPlanCode());
        assertEquals(500, school.getStudentQuota());
        assertEquals(100_000, school.getMonthlyTokenQuota());
        assertEquals(3_000_000L, school.getAnnualPriceVnd());
        assertEquals(oldStart, school.getLicenseStart());
        assertEquals(oldEnd, school.getLicenseEnd());
        assertNull(school.getNextPlanCode());
        assertEquals(2_000L, school.getUsedTokens(), "Upgrading must not reset monthly usage");
        assertEquals("02", service.ipn(ipn(payment, payment.getAmountVnd())).get("RspCode"));
        assertEquals(oldEnd, school.getLicenseEnd(), "Duplicate callbacks must not extend the license");
    }

    @Test
    void activeLargePlanCannotDowngradeOrQueueSmallerPlan() {
        applyPlan(large);
        ApiException quoteError = assertThrows(ApiException.class, () -> service.quote(small.getCode()));
        assertEquals(HttpStatus.CONFLICT, quoteError.getStatus());
        assertThrows(ApiException.class, () -> service.purchase(small.getCode(), 1_000_000L, "127.0.0.1"));
        assertNull(school.getNextPlanCode());
        assertEquals(large.getCode(), school.getPlanCode());
        verify(payments, never()).save(any());
    }

    @Test
    void moreExpensivePlanWithLowerCapacityIsNotAnUpgrade() {
        LicensePlan incomparable = plan("CUSTOM_EXPENSIVE", 4_000_000L, 80, 50_000);
        when(plans.findById(incomparable.getCode())).thenReturn(Optional.of(incomparable));
        assertThrows(ApiException.class, () -> service.quote(incomparable.getCode()));
        incomparable.setStudentQuota(500);
        incomparable.setMonthlyTokenQuota(5_000);
        assertThrows(ApiException.class, () -> service.quote(incomparable.getCode()));
        school.setMonthlyTokenQuota(null);
        assertThrows(ApiException.class, () -> service.quote(large.getCode()), "Unlimited tokens cannot be reduced to a finite quota");
    }

    @Test
    void activeSamePlanRenewalExtendsOneYearAndIsIdempotent() {
        LocalDate oldStart = school.getLicenseStart();
        LocalDate oldEnd = school.getLicenseEnd();
        var quote = service.quote(small.getCode());
        assertEquals("RENEWAL", quote.purpose());
        assertEquals(1_000_000L, quote.amountVnd());
        assertEquals(oldStart, quote.licenseStart());
        assertEquals(oldEnd.plusYears(1), quote.licenseEnd());
        SchoolPayment payment = checkout(quote);
        service.ipn(ipn(payment, payment.getAmountVnd()));
        assertEquals("PAID", payment.getStatus());
        assertEquals(small.getCode(), school.getPlanCode());
        assertEquals(oldStart, school.getLicenseStart());
        assertEquals(oldEnd.plusYears(1), school.getLicenseEnd());
        service.ipn(ipn(payment, payment.getAmountVnd()));
        assertEquals(oldEnd.plusYears(1), school.getLicenseEnd());
    }

    @Test
    void upgradeAfterEarlyRenewalPricesAllRemainingDaysAtAnnualRate() {
        school.setLicenseStart(today.minusYears(2));
        school.setLicenseEnd(today.plusYears(1).plusMonths(9));
        var quote = service.quote(large.getCode());
        long remaining = ChronoUnit.DAYS.between(today, school.getLicenseEnd()) + 1;
        long annualDays = ChronoUnit.DAYS.between(school.getLicenseEnd().minusYears(1), school.getLicenseEnd());
        long expected = BigDecimal.valueOf(2_000_000L).multiply(BigDecimal.valueOf(remaining))
                .divide(BigDecimal.valueOf(annualDays), 0, RoundingMode.CEILING).longValueExact();
        assertEquals(expected, quote.amountVnd());
        assertTrue(quote.amountVnd() > 2_000_000L, "More than a year remaining needs more than one year's price difference");
    }

    @Test
    void expiredSchoolMayChooseSmallerPublishedPlanIfStudentCountFits() {
        applyPlan(large);
        school.setLicenseEnd(today.minusDays(1));
        var quote = service.quote(small.getCode());
        assertEquals("RENEWAL", quote.purpose());
        assertEquals(today, quote.licenseStart());
        assertEquals(today.plusYears(1).minusDays(1), quote.licenseEnd());
        SchoolPayment payment = checkout(quote);
        service.ipn(ipn(payment, payment.getAmountVnd()));
        assertEquals("PAID", payment.getStatus());
        assertEquals(small.getCode(), school.getPlanCode());
        assertEquals(today, school.getLicenseStart());
        assertEquals(100, school.getStudentQuota());
    }

    @Test
    void expiredRenewalStillEnforcesCurrentStudentCount() {
        school.setLicenseEnd(today.minusDays(1));
        when(users.countActiveStudents(school.getId())).thenReturn(150L);
        assertThrows(ApiException.class, () -> service.quote(small.getCode()));
        assertDoesNotThrow(() -> service.quote(large.getCode()));
    }

    @Test
    void staleQuotedAmountCannotCreateCheckout() {
        var quote = service.quote(large.getCode());
        assertThrows(ApiException.class, () -> service.purchase(quote.planCode(), quote.amountVnd() - 1, "127.0.0.1"));
        verify(payments, never()).save(any());
    }

    @Test
    void pendingCheckoutWithSameAmountIsReusedAndDoesNotCreateAnotherOrder() {
        SchoolPayment payment = checkout(service.quote(large.getCode()));
        clearInvocations(payments);
        when(payments.findBySchoolIdOrderByCreatedAtDesc(school.getId())).thenReturn(List.of(payment));
        var reused = service.purchase(payment.getPlanCode(), payment.getAmountVnd(), "127.0.0.1");
        assertEquals(payment.getId(), reused.paymentId());
        verify(payments, never()).save(any());
        assertThrows(ApiException.class, () -> service.purchase(payment.getPlanCode(), payment.getAmountVnd() + 1, "127.0.0.1"));
    }

    @Test
    void signedCallbackWithWrongAmountCannotActivate() {
        SchoolPayment payment = checkout(service.quote(large.getCode()));
        assertEquals("04", service.ipn(ipn(payment, payment.getAmountVnd() + 1)).get("RspCode"));
        assertEquals("PENDING", payment.getStatus());
        assertEquals(small.getCode(), school.getPlanCode());
    }

    @Test
    void callbackAfterLicensePeriodChangedRequiresReview() {
        SchoolPayment payment = checkout(service.quote(large.getCode()));
        school.setLicenseEnd(school.getLicenseEnd().plusDays(30));
        LocalDate changedEnd = school.getLicenseEnd();
        service.ipn(ipn(payment, payment.getAmountVnd()));
        assertEquals("REQUIRES_REVIEW", payment.getStatus());
        assertEquals(changedEnd, school.getLicenseEnd());
        assertEquals(small.getCode(), school.getPlanCode());
    }

    @Test
    void callbackAfterConcurrentSamePlanRenewalCannotOverwriteNewPeriod() {
        SchoolPayment payment = checkout(service.quote(small.getCode()));
        school.setLicenseEnd(school.getLicenseEnd().plusYears(1));
        LocalDate changedEnd = school.getLicenseEnd();
        service.ipn(ipn(payment, payment.getAmountVnd()));
        assertEquals("REQUIRES_REVIEW", payment.getStatus());
        assertEquals(changedEnd, school.getLicenseEnd());
    }

    @Test
    void billingChoicesAreServerDrivenAndNeverAdvertiseQueuedDowngrades() {
        applyPlan(large);
        school.setNextPlanCode(small.getCode());
        var billing = service.billing();
        assertNull(billing.nextPlanCode());
        var smaller = billing.planChoices().stream().filter(choice -> small.getCode().equals(choice.planCode())).findFirst().orElseThrow();
        var current = billing.planChoices().stream().filter(choice -> large.getCode().equals(choice.planCode())).findFirst().orElseThrow();
        assertFalse(smaller.allowed());
        assertNotNull(smaller.reason());
        assertTrue(current.allowed());
        assertEquals("RENEWAL", current.purpose());
    }

    @Test
    void paidRegistrationRequiresFirstPasswordChangeAndNormalizesSchoolCapabilities() {
        SchoolPayment registration = new SchoolPayment();
        registration.setId(UUID.randomUUID());
        registration.setRegistrationEmail("school@example.test");
        registration.setRegistrationSchoolCode("TEST-SCHOOL");
        registration.setRegistrationSchoolName("New Test School");
        registration.setRegistrationManagerName("School Manager");
        registration.setRegistrationPasswordHash("test-password-hash");
        registration.setPlanCode(small.getCode());
        registration.setAmountVnd(small.getAnnualPriceVnd());
        registration.setAnnualPriceVnd(small.getAnnualPriceVnd());
        registration.setStudentQuota(small.getStudentQuota());
        registration.setMonthlyTokenQuota(small.getMonthlyTokenQuota());
        Role schoolRole = new Role();
        schoolRole.setName("SCHOOL");
        when(roles.findByName("SCHOOL")).thenReturn(Optional.of(schoolRole));
        when(payments.findById(registration.getId())).thenReturn(Optional.of(registration));
        when(payments.findLockedById(registration.getId())).thenReturn(Optional.of(registration));
        when(schools.save(any(School.class))).thenAnswer(invocation -> {
            School created = invocation.getArgument(0);
            created.setId(UUID.randomUUID());
            return created;
        });

        assertEquals("00", service.ipn(ipn(registration, registration.getAmountVnd())).get("RspCode"));
        var captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User created = captor.getValue();
        assertTrue(created.isMustChangePassword());
        assertTrue(created.getPermissions().isEmpty());
        assertEquals("SCHOOL", created.getRole().getName());
        assertEquals("PAID", registration.getStatus());
    }

    private SchoolPayment checkout(SchoolPaymentContracts.Quote quote) {
        service.purchase(quote.planCode(), quote.amountVnd(), "127.0.0.1");
        var captor = ArgumentCaptor.forClass(SchoolPayment.class);
        verify(payments).save(captor.capture());
        var payment = captor.getValue();
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.findLockedById(payment.getId())).thenReturn(Optional.of(payment));
        return payment;
    }

    private Map<String, String> ipn(SchoolPayment payment, long amount) {
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_TmnCode", vnpay.tmnCode());
        fields.put("vnp_TxnRef", SchoolPaymentService.providerTxnRef(payment.getId()));
        fields.put("vnp_Amount", String.valueOf(amount * 100));
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_TransactionNo", "test-transaction");
        fields.put("vnp_SecureHash", service.sign(SchoolPaymentService.canonicalIpn(fields)));
        return fields;
    }

    private void applyPlan(LicensePlan plan) {
        school.setPlanCode(plan.getCode());
        school.setAnnualPriceVnd(plan.getAnnualPriceVnd());
        school.setStudentQuota(plan.getStudentQuota());
        school.setMonthlyTokenQuota(plan.getMonthlyTokenQuota());
    }

    private LicensePlan plan(String code, long price, int students, Integer tokens) {
        LicensePlan plan = new LicensePlan();
        plan.setCode(code);
        plan.setName(code);
        plan.setAnnualPriceVnd(price);
        plan.setStudentQuota(students);
        plan.setMonthlyTokenQuota(tokens);
        return plan;
    }
}
