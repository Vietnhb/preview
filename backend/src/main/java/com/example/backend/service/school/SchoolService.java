package com.example.backend.service.school;

import com.example.backend.service.account.CurrentUserService;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.entity.school.School;
import com.example.backend.entity.audit.TokenUsageAudit;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.repository.audit.TokenUsageAuditRepository;

/**
 * Service for managing schools (B2B customers).
 * Simple, straightforward implementation without over-engineering.
 */
@Service
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final CurrentUserService currentUserService;
    private final LicenseCheckService licenseCheckService;
    private final TokenUsageAuditRepository tokenAudits;

    @org.springframework.beans.factory.annotation.Autowired
    public SchoolService(SchoolRepository schoolRepository, CurrentUserService currentUserService,
            LicenseCheckService licenseCheckService, TokenUsageAuditRepository tokenAudits) {
        this.schoolRepository = schoolRepository;
        this.currentUserService = currentUserService;
        this.licenseCheckService = licenseCheckService;
        this.tokenAudits = tokenAudits;
    }

    public SchoolService(SchoolRepository schoolRepository, CurrentUserService currentUserService,
            LicenseCheckService licenseCheckService) {
        this(schoolRepository, currentUserService, licenseCheckService, null);
    }

    /**
     * Commit actual provider usage even if downstream specification validation
     * fails.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public com.fasterxml.jackson.databind.JsonNode meterAiCall(
            java.util.function.Supplier<com.fasterxml.jackson.databind.JsonNode> providerCall) {
        var actor = currentUserService.requireCurrentUser();
        licenseCheckService.requireWriteAccess(actor);
        if (actor.getSchool() == null)
            return providerCall.get();
        if (actor.getRole() == null || !RoleName.TEACHER.matches(actor.getRole().getName()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only teachers can use school AI tokens");
        School school = schoolRepository.findByIdForUpdate(actor.getSchool().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found"));
        if (!school.isActive() || !school.isLicenseActive())
            throw new ApiException(HttpStatus.FORBIDDEN, "School license does not allow AI generation");
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        if (!month.equals(school.getTokenUsageMonth())) {
            school.setTokenUsageMonth(month);
            school.setUsedTokens(0L);
        }
        if (!school.hasQuotaRemaining())
            throw new ApiException(HttpStatus.FORBIDDEN, "Monthly AI token quota exceeded");
        var response = providerCall.get();
        var tokens = response == null ? null : response.path("usage").path("total_tokens");
        if (tokens == null || !tokens.isIntegralNumber() || !tokens.canConvertToLong() || tokens.longValue() < 0)
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI provider did not report valid token usage");
        school.setUsedTokens(
                Math.addExact(school.getUsedTokens() == null ? 0L : school.getUsedTokens(), tokens.longValue()));
        schoolRepository.save(school);
        if (tokenAudits != null) {
            TokenUsageAudit audit = new TokenUsageAudit();
            audit.setSchool(school);
            audit.setUser(actor);
            audit.setTokens(tokens.longValue());
            audit.setOperation("AI_GENERATION");
            audit.setUsageMonth(month);
            tokenAudits.save(audit);
        }
        return response;
    }
}
