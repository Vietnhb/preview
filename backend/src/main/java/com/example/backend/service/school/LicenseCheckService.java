package com.example.backend.service.school;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.example.backend.dto.school.LicenseStatusResponse;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.exception.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LicenseCheckService {
    @Value("${physlive.license.warning-days}")
    private int warningDays;

    public boolean isLicenseActive(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getActive()) || user.getRole() == null) return false;
        String role = user.getRole().getName();
        RoleName roleName = RoleName.from(role).orElse(null);
        if (roleName == null) return false;
        if (roleName.isPlatformRole()) return user.getSchool() == null;
        return roleName.isSchoolRole() && user.getSchool() != null
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
