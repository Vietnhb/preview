package com.example.backend.service.school;

import com.example.backend.config.properties.VnpayProperties;
import com.example.backend.dto.school.SchoolRegistrationRequest;
import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.school.LicensePlan;
import com.example.backend.entity.school.School;
import com.example.backend.entity.school.SchoolPayment;
import com.example.backend.repository.account.RoleRepository;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.school.LicensePlanRepository;
import com.example.backend.repository.school.SchoolPaymentRepository;
import com.example.backend.repository.school.SchoolRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchoolPaymentRegistrationTest {
    @Test
    void createsSchoolAndManagerOnlyAfterVerifiedSuccessfulIpn() {
        LicensePlanRepository plans = mock(LicensePlanRepository.class);
        SchoolPaymentRepository payments = mock(SchoolPaymentRepository.class);
        SchoolRepository schools = mock(SchoolRepository.class);
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        VnpayProperties properties = new VnpayProperties("merchant", "secret", "https://pay.test",
            "https://app.test/result", "https://query.test", "127.0.0.1", ZoneId.of("Asia/Ho_Chi_Minh"),
            Duration.ofSeconds(2), Duration.ofSeconds(2));
        SchoolPaymentService service = new SchoolPaymentService(plans, payments, schools, users, roles, encoder,
            null, null, null, properties, mock(HttpClient.class), mock(com.example.backend.service.realtime.RealtimeEventService.class));

        LicensePlan plan = new LicensePlan();
        plan.setCode("PRO"); plan.setActive(true); plan.setAnnualPriceVnd(1_000_000L);
        plan.setStudentQuota(500); plan.setMonthlyTokenQuota(10_000);
        when(plans.findById("PRO")).thenReturn(Optional.of(plan));
        when(users.findByEmail("manager@example.com")).thenReturn(Optional.empty());
        when(schools.findByName("Test School")).thenReturn(Optional.empty());
        when(payments.findFirstByRegistrationEmailIgnoreCaseOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
        when(encoder.encode("password123")).thenReturn("encoded-password");
        UUID paymentId = UUID.randomUUID();
        when(payments.save(any())).thenAnswer(invocation -> {
            SchoolPayment payment = invocation.getArgument(0);
            payment.setId(paymentId);
            return payment;
        });

        var checkout = service.checkout(new SchoolRegistrationRequest("PRO", "Test School", "TEST-SCHOOL",
            "Hanoi", "Manager", "manager@example.com", "0900000000", "password123"), "127.0.0.1");

        assertEquals(paymentId, checkout.paymentId());
        verify(schools, never()).save(any());
        verify(users, never()).save(any());
        ArgumentCaptor<SchoolPayment> paymentCaptor = ArgumentCaptor.forClass(SchoolPayment.class);
        verify(payments).save(paymentCaptor.capture());
        SchoolPayment pending = paymentCaptor.getValue();
        assertNull(pending.getSchool());
        assertNull(pending.getManager());
        assertEquals("encoded-password", pending.getRegistrationPasswordHash());

        when(payments.findById(paymentId)).thenReturn(Optional.of(pending));
        when(payments.findLockedById(paymentId)).thenReturn(Optional.of(pending));
        Role managerRole = new Role(); managerRole.setName("SCHOOL_MANAGER");
        when(roles.findByName("SCHOOL_MANAGER")).thenReturn(Optional.of(managerRole));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_TmnCode", "merchant");
        fields.put("vnp_TxnRef", SchoolPaymentService.providerTxnRef(paymentId));
        fields.put("vnp_Amount", "100000000");
        fields.put("vnp_ResponseCode", "24");
        fields.put("vnp_TransactionStatus", "02");
        fields.put("vnp_TransactionNo", "123456");
        fields.put("vnp_SecureHash", service.sign(SchoolPaymentService.canonicalIpn(fields)));

        assertEquals("00", service.ipn(fields).get("RspCode"));
        assertEquals("FAILED", pending.getStatus());
        verify(schools, never()).save(any());
        verify(users, never()).save(any());

        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_SecureHash", service.sign(SchoolPaymentService.canonicalIpn(fields)));

        assertEquals("00", service.ipn(fields).get("RspCode"));
        verify(schools).save(any(School.class));
        verify(users).save(any(User.class));
        assertEquals("PAID", pending.getStatus());
        assertNotNull(pending.getSchool());
        assertNotNull(pending.getManager());
        assertEquals("manager@example.com", pending.getManager().getEmail());
        assertEquals("encoded-password", pending.getManager().getPassword());
        assertNull(pending.getRegistrationPasswordHash());
    }
}
