package com.example.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * School entity for B2B model.
 * Each school has a license with AI quota management.
 *
 * Business Rules:
 * - License expires: users can login but read-only access + renewal banner
 * - AI quota: monthly allocation for simulation generation
 * - Only 1 SCHOOL_MANAGER per school (enforced via UNIQUE constraint on users table)
 */
@Entity
@Table(name = "schools")
@Data
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "province_city", length = 100)
    private String provinceCity;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    /**
     * License start date (inclusive).
     */
    @Column(name = "license_start")
    private LocalDate licenseStart;

    /**
     * License end date (inclusive).
     * After this date: read-only mode + show renewal banner.
     */
    @Column(name = "license_end")
    private LocalDate licenseEnd;

    /**
     * School-level active flag (admin-controlled).
     * false = school disabled completely (users cannot login).
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Monthly token allowance; null means unlimited. No implicit trial allowance. */
    @Column(name = "monthly_token_quota")
    private Integer monthlyTokenQuota = 0;

    /** Active student seats; null preserves administrator-managed legacy schools. */
    @Column(name = "student_quota")
    private Integer studentQuota;
    private String planCode;
    private Long annualPriceVnd;
    private String nextPlanCode;

    @Column(name = "used_tokens")
    private Long usedTokens = 0L;

    @Column(name = "token_usage_month")
    private LocalDate tokenUsageMonth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if license is currently valid.
     * @return true if today is within [licenseStart, licenseEnd]
     */
    public boolean isLicenseActive() {
        LocalDate today = LocalDate.now();
        return licenseStart != null && licenseEnd != null && !today.isBefore(licenseStart) && !today.isAfter(licenseEnd);
    }

    /**
     * Check if school has remaining AI quota.
     * @return true if the current month has remaining tokens
     */
    public boolean hasQuotaRemaining() {
        long used = LocalDate.now().withDayOfMonth(1).equals(tokenUsageMonth) && usedTokens != null ? usedTokens : 0;
        return monthlyTokenQuota == null || used < monthlyTokenQuota;
    }
}
