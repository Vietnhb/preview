package com.example.backend.system.school.dto;

import com.example.backend.system.school.model.entity.LicensePlan;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Requests and responses for school operations. */
public final class SchoolPaymentContracts {
    private SchoolPaymentContracts() { }

    public record LicensePlanResponse(String code, String name, String description, Long annualPriceVnd,
                                      Integer studentQuota, Integer monthlyTokenQuota, boolean active) {
        public static LicensePlanResponse from(LicensePlan plan) {
            return new LicensePlanResponse(plan.getCode(), plan.getName(), plan.getDescription(), plan.getAnnualPriceVnd(),
                    plan.getStudentQuota(), plan.getMonthlyTokenQuota(), plan.isActive());
        }
    }

    public record SchoolRegistrationRequest(
            @NotBlank String planCode,
            @NotBlank @Size(max = 200) String schoolName,
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String schoolCode,
            @NotBlank @Size(max = 300) String address,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(max = 20) String phoneNumber,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record SchoolPaymentRecoveryRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record SchoolPaymentPlanChoiceRequest(
            @NotBlank String planCode,
            @Positive Long expectedAmountVnd) {
    }

    public record Checkout(UUID paymentId, String paymentUrl) { }
    public record Notification(UUID id, String schoolName, String planCode, Long amountVnd, Instant paidAt, String status) { }
    public record Quote(String planCode, String purpose, long amountVnd, LocalDate licenseStart, LocalDate licenseEnd) { }
    public record PaymentRow(UUID id, String planCode, String purpose, Long amountVnd, String status, Instant createdAt, Instant paidAt) { }
    public record AdminPaymentRow(UUID id, String schoolName, String managerEmail, String planCode, String purpose, Long amountVnd, String status, Instant createdAt, Instant paidAt) { }
    public record RevenueSummary(long paidTransactions, long pendingTransactions, long reviewTransactions, long grossPaidVnd) { }
    public record PlanChoice(String planCode, boolean allowed, String purpose, String reason) { }
    public record Billing(String planCode, String nextPlanCode, LocalDate licenseStart, LocalDate licenseEnd,
        Integer studentQuota, long studentsUsed, Integer monthlyTokenQuota, long tokensUsed, List<PaymentRow> payments,
        List<PlanChoice> planChoices) { }
}
