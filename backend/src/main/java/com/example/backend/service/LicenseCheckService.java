package com.example.backend.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.example.backend.constants.RoleConstants;
import com.example.backend.dto.LicenseStatusResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LicenseCheckService {
    private final SchoolRepository schoolRepository;
    @Value("${physlive.license.warning-days:30}")
    private int warningDays = 30;

    public boolean isLicenseActive(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getActive()) || user.getRole() == null) return false;
        String role = user.getRole().getName();
        if (RoleConstants.isPlatformRole(role)) return user.getSchool() == null;
        return RoleConstants.isSchoolRole(role) && user.getSchool() != null
                && user.getSchool().isActive() && user.getSchool().isLicenseActive();
    }

    public boolean isInGraceMode(User user) {
        if (user == null || user.getSchool() == null || !Boolean.TRUE.equals(user.getActive())) return false;
        var school = user.getSchool();
        return school.isActive() && school.getLicenseEnd() != null
                && school.getLicenseEnd().isBefore(LocalDate.now());
    }

    public boolean shouldShowRenewalBanner(User user) {
        return user != null && user.getSchool() != null && user.getSchool().getLicenseEnd() != null
                && ChronoUnit.DAYS.between(LocalDate.now(), user.getSchool().getLicenseEnd()) <= warningDays;
    }

    public long getDaysUntilExpiry(UUID schoolId) {
        var school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "School not found"));
        return school.getLicenseEnd() == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), school.getLicenseEnd());
    }

    public boolean canPerformWriteOperations(User user) {
        return isLicenseActive(user);
    }

    public void requireWriteAccess(User user) {
        if (!canPerformWriteOperations(user))
            throw new ApiException(HttpStatus.FORBIDDEN, "An active school license is required for this operation");
    }

    public LicenseStatusResponse status(User user) {
        var end = user.getSchool() == null ? null : user.getSchool().getLicenseEnd();
        return new LicenseStatusResponse(isLicenseActive(user), isInGraceMode(user),
                shouldShowRenewalBanner(user), end == null ? 0 : ChronoUnit.DAYS.between(LocalDate.now(), end),
                canPerformWriteOperations(user), end == null ? null : end.toString());
    }
}
