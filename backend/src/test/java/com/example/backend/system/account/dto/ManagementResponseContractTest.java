package com.example.backend.system.account.dto;

import com.example.backend.security.JwtUtil;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.account.service.AuthService;
import com.example.backend.system.curriculum.repository.TopicRepository;
import com.example.backend.system.curriculum.service.CurriculumService;
import com.example.backend.system.operations.service.AdminOperationsService;
import com.example.backend.system.school.model.entity.LicensePlan;
import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.school.repository.LicensePlanRepository;
import com.example.backend.system.school.repository.SchoolRepository;
import com.example.backend.system.school.service.LicenseCheckService;
import com.example.backend.system.simulation.repository.SimulationRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagementResponseContractTest {
    private final SchoolRepository schools = mock(SchoolRepository.class);
    private final LicensePlanRepository plans = mock(LicensePlanRepository.class);
    private final SimulationRunRepository runs = mock(SimulationRunRepository.class);
    private final AdminOperationsService service = new AdminOperationsService(schools, runs,
            mock(CurriculumService.class), plans, mock(TopicRepository.class));
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void schoolDetailsKeepTheExistingJsonFieldsAndDoNotExposeMutableEntities() {
        School school = new School();
        school.setId(UUID.randomUUID()); school.setName("School"); school.setCode("SCHOOL");
        school.setLicenseStart(LocalDate.now().minusDays(1)); school.setLicenseEnd(LocalDate.now().plusDays(1));
        school.setPlanCode("PRO"); school.setStudentQuota(200); school.setAnnualPriceVnd(5000000L);
        when(schools.findAll()).thenReturn(List.of(school));

        var response = service.schools().getFirst();

        assertEquals(json.valueToTree(school), json.valueToTree(response));
        assertTrue(response.licenseActive());
        school.setName("Later change");
        assertEquals("School", response.name());
    }

    @Test
    void publicPlansReturnOnlyPublishedPlanDtosWithTheExistingJsonShape() {
        LicensePlan plan = new LicensePlan();
        plan.setCode("PRO"); plan.setName("Pro"); plan.setDescription("Published terms");
        plan.setAnnualPriceVnd(5000000L); plan.setStudentQuota(200); plan.setMonthlyTokenQuota(100000);
        when(plans.findByActiveTrueOrderByAnnualPriceVndAsc()).thenReturn(List.of(plan));
        var auth = new AuthService(mock(UserRepository.class), mock(JwtUtil.class),
                mock(PasswordEncoder.class), plans, mock(LicenseCheckService.class));

        var response = auth.plans().getFirst();

        assertEquals(json.valueToTree(plan), json.valueToTree(response));
        plan.setName("Changed terms");
        assertEquals("Pro", response.name());
        verify(plans).findByActiveTrueOrderByAnnualPriceVndAsc();
    }

    @Test
    void validationMetricsPreserveZeroAndFractionalFailureRates() {
        when(runs.count()).thenReturn(0L, 10L);
        when(runs.countByValidationPassedFalse()).thenReturn(0L, 3L);
        assertEquals(0.0, service.validationMetrics().failureRate());
        var metrics = service.validationMetrics();
        assertEquals(10, metrics.total()); assertEquals(3, metrics.failed());
        assertEquals(0.3, metrics.failureRate());
    }
}
