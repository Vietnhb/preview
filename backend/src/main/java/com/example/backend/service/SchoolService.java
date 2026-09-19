package com.example.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.entity.School;
import com.example.backend.entity.TokenUsageAudit;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.SchoolRepository;
import com.example.backend.repository.TokenUsageAuditRepository;


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
        this.schoolRepository = schoolRepository; this.currentUserService = currentUserService;
        this.licenseCheckService = licenseCheckService; this.tokenAudits = tokenAudits;
    }

    public SchoolService(SchoolRepository schoolRepository, CurrentUserService currentUserService,
                         LicenseCheckService licenseCheckService) {
        this(schoolRepository, currentUserService, licenseCheckService, null);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public List<School> getAllSchools() {
        return schoolRepository.findAll();
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public School getSchoolById(UUID schoolId) {
        return schoolRepository.findById(schoolId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found"));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public School createSchool(School school) {
        if (schoolRepository.findByName(school.getName()).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "School name already exists");
        }
        return schoolRepository.save(school);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public School updateSchool(UUID schoolId, School updates) {
        School school = getSchoolById(schoolId);
        
        if (updates.getName() != null) school.setName(updates.getName());
        if (updates.getShortName() != null) school.setShortName(updates.getShortName());
        if (updates.getAddress() != null) school.setAddress(updates.getAddress());
        if (updates.getProvinceCity() != null) school.setProvinceCity(updates.getProvinceCity());
        if (updates.getPhoneNumber() != null) school.setPhoneNumber(updates.getPhoneNumber());
        if (updates.getContactEmail() != null) school.setContactEmail(updates.getContactEmail());
        if (updates.getLicenseStart() != null) school.setLicenseStart(updates.getLicenseStart());
        if (updates.getLicenseEnd() != null) school.setLicenseEnd(updates.getLicenseEnd());
        if (updates.getMonthlyTokenQuota() != null) school.setMonthlyTokenQuota(updates.getMonthlyTokenQuota());
        
        return schoolRepository.save(school);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void setSchoolActive(UUID schoolId, boolean active) {
        School school = getSchoolById(schoolId);
        school.setActive(active);
        schoolRepository.save(school);
    }
    
    /** Commit actual provider usage even if downstream specification validation fails. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public com.fasterxml.jackson.databind.JsonNode meterAiCall(
            java.util.function.Supplier<com.fasterxml.jackson.databind.JsonNode> providerCall) {
        var actor = currentUserService.requireCurrentUser();
        licenseCheckService.requireWriteAccess(actor);
        if (actor.getSchool() == null) return providerCall.get();
        if (actor.getRole() == null || !"TEACHER".equals(actor.getRole().getName()))
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
        school.setUsedTokens(Math.addExact(school.getUsedTokens() == null ? 0L : school.getUsedTokens(), tokens.longValue()));
        schoolRepository.save(school);
        if (tokenAudits != null) {
            TokenUsageAudit audit = new TokenUsageAudit(); audit.setSchool(school); audit.setUser(actor);
            audit.setTokens(tokens.longValue()); audit.setOperation("AI_GENERATION"); audit.setUsageMonth(month); tokenAudits.save(audit);
        }
        return response;
    }

    public boolean canGenerateSimulation(UUID schoolId) {
        School school = getSchoolById(schoolId);
        return school.isActive() && school.isLicenseActive() && school.hasQuotaRemaining();
    }
}
