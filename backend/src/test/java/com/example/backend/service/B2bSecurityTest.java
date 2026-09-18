package com.example.backend.service;

import com.example.backend.controller.AssignmentController;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AssignmentController.class, properties = {"debug=false", "spring.config.import=", "logging.level.root=WARN"})
@Import({SecurityConfig.class, JwtFilter.class})
class B2bSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AssignmentService assignments;
    @MockBean JwtUtil jwt;
    @MockBean UserRepository users;
    @MockBean LicenseCheckService licenses;

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
}
