package com.example.backend.service;

import com.example.backend.controller.AssignmentController;
import com.example.backend.controller.SchoolPaymentController;
import com.example.backend.entity.Role;
import com.example.backend.entity.School;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.JwtFilter;
import com.example.backend.security.JwtUtil;
import com.example.backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AssignmentController.class, SchoolPaymentController.class}, properties = {"debug=false", "spring.config.import=", "logging.level.root=WARN"})
@Import({SecurityConfig.class, JwtFilter.class})
class B2bSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AssignmentService assignments;
    @MockBean JwtUtil jwt;
    @MockBean UserRepository users;
    @MockBean LicenseCheckService licenses;
    @MockBean SchoolPaymentService schoolPayments;

    private User expiredSchoolRole(String roleName, String email, String token) {
        Role role = new Role(); role.setName(roleName);
        School school = new School(); school.setActive(true);
        User user = new User(); user.setActive(true); user.setRole(role); user.setSchool(school);
        when(jwt.extractClaims(token)).thenReturn(io.jsonwebtoken.Jwts.claims().subject(email).build());
        when(users.findByEmail(email)).thenReturn(Optional.of(user));
        when(licenses.canPerformWriteOperations(user)).thenReturn(false);
        return user;
    }

    @Test void unauthenticatedRequestsReturn401() throws Exception {
        mvc.perform(get("/api/assignments/mine/student")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles = "STUDENT")
    void studentCanReadOwnAssignments() throws Exception {
        when(assignments.forStudent()).thenReturn(List.of());
        mvc.perform(get("/api/assignments/mine/student")).andExpect(status().isOk());
    }
    @Test @WithMockUser(roles = "STUDENT")
    void studentCanSubmitPredictions() throws Exception {
        mvc.perform(post("/api/assignments/" + UUID.randomUUID() + "/predictions")
                .contentType("application/json").content("{\"predictions\":{\"answer\":1}}"))
                .andExpect(status().isOk());
        verify(assignments).submit(any(), any());
    }
    @Test @WithMockUser(roles = "CONTENT_REVIEWER")
    void reviewerCannotAccessTeachingAssignments() throws Exception {
        mvc.perform(get("/api/assignments/mine/teacher")).andExpect(status().isForbidden());
        verifyNoInteractions(assignments);
    }

    @Test void jwtAuthenticatedReviewerGets403ForTeachingAssignments() throws Exception {
        String token = "reviewer-token";
        Role role = new Role(); role.setName("CONTENT_REVIEWER");
        User reviewer = new User(); reviewer.setActive(true); reviewer.setRole(role);
        when(jwt.extractClaims(token)).thenReturn(io.jsonwebtoken.Jwts.claims()
                .subject("reviewer@example.com").build());
        when(users.findByEmail("reviewer@example.com")).thenReturn(Optional.of(reviewer));

        mvc.perform(get("/api/assignments/mine/teacher")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(assignments);
    }
    @Test @WithMockUser(roles = "TEACHER")
    void teacherCannotSubmitAsStudent() throws Exception {
        mvc.perform(post("/api/assignments/" + UUID.randomUUID() + "/predictions")
                .contentType("application/json").content("{\"predictions\":{\"answer\":1}}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(assignments);
    }
    @Test void expiredLicenseAllowsReadsButDeniesWritesWithExistingJwt() throws Exception {
        Role role = new Role(); role.setName("STUDENT");
        User student = new User(); student.setRole(role); student.setSchool(new School());
        when(jwt.extractClaims("test-token")).thenReturn(io.jsonwebtoken.Jwts.claims().subject("student@example.com").build());
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(student));
        when(licenses.canPerformWriteOperations(student)).thenReturn(false);
        mvc.perform(get("/api/assignments/mine/student").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/assignments/" + UUID.randomUUID() + "/predictions")
                .header("Authorization", "Bearer test-token").contentType("application/json")
                .content("{\"predictions\":{\"answer\":1}}"))
                .andExpect(status().isForbidden());
        verify(assignments, never()).submit(any(), any());
    }

    @Test void expiredSchoolManagerCanQuoteAndStartRenewalCheckout() throws Exception {
        String token = "expired-manager-token";
        expiredSchoolRole("SCHOOL_MANAGER", "manager@example.com", token);
        when(schoolPayments.quote("PRO")).thenReturn(new SchoolPaymentService.Quote(
                "PRO", "RENEWAL", 50000L, LocalDate.now(), LocalDate.now().plusYears(1).minusDays(1)));
        when(schoolPayments.purchase("PRO", 50000L, "127.0.0.1"))
                .thenReturn(new SchoolPaymentService.Checkout(UUID.randomUUID(), "https://sandbox.example/checkout"));

        mvc.perform(post("/api/school/billing/quote").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"planCode\":\"PRO\",\"expectedAmountVnd\":50000}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/school/billing/checkout").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"planCode\":\"PRO\",\"expectedAmountVnd\":50000}"))
                .andExpect(status().isOk());
        verify(schoolPayments).quote("PRO");
        verify(schoolPayments).purchase("PRO", 50000L, "127.0.0.1");
    }

    @Test void expiredSchoolManagerCannotScheduleNextPlan() throws Exception {
        String token = "expired-manager-token";
        expiredSchoolRole("SCHOOL_MANAGER", "manager@example.com", token);

        mvc.perform(put("/api/school/billing/next-plan").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"planCode\":\"PRO\",\"expectedAmountVnd\":50000}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(schoolPayments);
    }

    @Test void expiredTeacherCannotUseManagerRenewalException() throws Exception {
        String token = "expired-teacher-token";
        expiredSchoolRole("TEACHER", "teacher@example.com", token);

        mvc.perform(post("/api/school/billing/quote").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"planCode\":\"PRO\",\"expectedAmountVnd\":50000}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(schoolPayments);
    }
}
