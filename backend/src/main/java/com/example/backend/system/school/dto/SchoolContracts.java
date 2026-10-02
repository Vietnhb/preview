package com.example.backend.system.school.dto;

import com.example.backend.system.school.model.entity.School;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Platform school-management inputs and detached school details. */
public final class SchoolContracts {
    private SchoolContracts() { }

    public record Save(@NotBlank @Size(max = 80) String code,
                       @NotBlank @Size(max = 200) String name,
                       @Size(max = 300) String address, boolean active,
                       LocalDate licenseStart, LocalDate licenseEnd,
                       @PositiveOrZero Integer monthlyTokenQuota) { }

    public record Response(UUID id, String name, String code, String shortName, String address,
                           String provinceCity, String phoneNumber, String contactEmail,
                           LocalDate licenseStart, LocalDate licenseEnd, boolean active,
                           Integer monthlyTokenQuota, Integer studentQuota, String planCode,
                           Long annualPriceVnd, String nextPlanCode, Long usedTokens,
                           LocalDate tokenUsageMonth, Instant createdAt, Instant updatedAt,
                           boolean licenseActive) {
        public static Response from(School school) {
            return new Response(school.getId(), school.getName(), school.getCode(), school.getShortName(),
                    school.getAddress(), school.getProvinceCity(), school.getPhoneNumber(), school.getContactEmail(),
                    school.getLicenseStart(), school.getLicenseEnd(), school.isActive(), school.getMonthlyTokenQuota(),
                    school.getStudentQuota(), school.getPlanCode(), school.getAnnualPriceVnd(), school.getNextPlanCode(),
                    school.getUsedTokens(), school.getTokenUsageMonth(), school.getCreatedAt(), school.getUpdatedAt(),
                    school.isLicenseActive());
        }
    }
}
