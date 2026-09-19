package com.example.backend.service;

import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.exception.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchoolPaymentServiceTest {
    private SchoolPaymentRepository payments;
    private SchoolPaymentService service;
    private SchoolPayment payment;
    private SchoolRepository schools;
    private LicensePlanRepository plans;
    private UserRepository users;
    private CurrentUserService current;
    private PasswordEncoder encoder;

    @BeforeEach void setup() {
        payments = mock(SchoolPaymentRepository.class);
        schools = mock(SchoolRepository.class);
        plans = mock(LicensePlanRepository.class); users = mock(UserRepository.class);
        current = mock(CurrentUserService.class); encoder = mock(PasswordEncoder.class);
        service = new SchoolPaymentService(plans, payments, schools, users, mock(RoleRepository.class), encoder, current, new LicenseCheckService(schools), mock(jakarta.persistence.EntityManager.class));
        ReflectionTestUtils.setField(service, "tmnCode", "TESTCODE");
        ReflectionTestUtils.setField(service, "hashSecret", "test-signing-secret");
        payment = new SchoolPayment(); payment.setId(UUID.randomUUID()); payment.setAmountVnd(30000000L);
        payment.setMonthlyTokenQuota(100000); payment.setSchool(new School()); payment.getSchool().setActive(false);
        payment.setManager(new User()); payment.getManager().setActive(false);
        payment.getSchool().setId(UUID.randomUUID());
        when(schools.findByIdForUpdate(payment.getSchool().getId())).thenReturn(Optional.of(payment.getSchool()));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(payments.findLockedById(payment.getId())).thenReturn(Optional.of(payment));
        payment.setAnnualPriceVnd(30000000L); payment.setStudentQuota(500);
        Role role = new Role(); role.setName("SCHOOL_MANAGER"); payment.getManager().setId(1);
        payment.getManager().setSchool(payment.getSchool()); payment.getManager().setRole(role);
        when(current.requireCurrentUser()).thenReturn(payment.getManager());
    }
    private Map<String, String> callback(String response, String transactionStatus, long amount) {
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_TmnCode", "TESTCODE"); fields.put("vnp_TxnRef", payment.getId().toString());
        fields.put("vnp_Amount", Long.toString(amount)); fields.put("vnp_ResponseCode", response);
        fields.put("vnp_TransactionStatus", transactionStatus); fields.put("vnp_TransactionNo", "1234");
        fields.put("vnp_SecureHash", service.sign(SchoolPaymentService.canonical(fields)));
        return fields;
    }
    @Test void successfulVerifiedIpnActivatesSchoolAndManager() {
        assertEquals("00", service.ipn(callback("00", "00", 3000000000L)).get("RspCode"));
        assertEquals("PAID", payment.getStatus()); assertTrue(payment.getSchool().isActive()); assertTrue(payment.getManager().getActive());
        assertEquals(100000, payment.getSchool().getMonthlyTokenQuota());
        assertEquals(payment.getSchool().getLicenseStart().plusYears(1).minusDays(1), payment.getSchool().getLicenseEnd());
        assertNotNull(payment.getPaidAt());
    }
    @Test void alteredSignedDataCannotActivateSchool() {
        var fields = callback("00", "00", 3000000000L); fields.put("vnp_Amount", "1");
        assertEquals("97", service.ipn(fields).get("RspCode"));
        assertFalse(payment.getSchool().isActive()); verifyNoInteractions(payments);
    }
    @Test void validSignatureWithWrongAmountIsRejected() {
        assertEquals("04", service.ipn(callback("00", "00", 100L)).get("RspCode"));
        assertEquals("PENDING", payment.getStatus()); assertFalse(payment.getManager().getActive());
    }
    @Test void duplicatedIpnDoesNotExtendLicense() {
        var fields = callback("00", "00", 3000000000L); service.ipn(fields);
        LocalDate end = payment.getSchool().getLicenseEnd(); Instant paid = payment.getPaidAt();
        assertEquals("02", service.ipn(fields).get("RspCode"));
        assertEquals(end, payment.getSchool().getLicenseEnd()); assertEquals(paid, payment.getPaidAt());
    }
    @Test void failedTransactionCannotActivateAccount() {
        assertEquals("00", service.ipn(callback("24", "02", 3000000000L)).get("RspCode"));
        assertEquals("FAILED", payment.getStatus()); assertFalse(payment.getSchool().isActive()); assertNull(payment.getPaidAt());
    }
    @Test void unconfiguredGatewayRejectsBeforeSavingAccount() {
        ReflectionTestUtils.setField(service, "hashSecret", "");
        assertThrows(ApiException.class, () -> service.checkout(null, "127.0.0.1")); verifyNoInteractions(payments);
    }
    @Test void signatureMatchesPublishedHmacSha512Vector() {
        ReflectionTestUtils.setField(service, "hashSecret", "key");
        assertEquals("b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a", service.sign("The quick brown fox jumps over the lazy dog"));
        assertEquals("vnp_Amount=100&vnp_OrderInfo=School+plan", SchoolPaymentService.canonical(Map.of("vnp_OrderInfo", "School plan", "vnp_Amount", "100", "vnp_SecureHash", "ignored", "other", "ignored")));
    }

    @Test void expiredSchoolManagerCannotScheduleNextPlan() {
        payment.getSchool().setActive(true);
        payment.getSchool().setLicenseStart(LocalDate.now().minusYears(1));
        payment.getSchool().setLicenseEnd(LocalDate.now().minusDays(1));
        payment.getManager().setActive(true);

        assertThrows(ApiException.class, () -> service.nextPlan("PRO"));
        verify(schools, never()).findByIdForUpdate(any());
    }
}
