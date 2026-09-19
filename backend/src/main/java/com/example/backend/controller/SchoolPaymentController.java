package com.example.backend.controller;

import com.example.backend.service.SchoolPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class SchoolPaymentController {
    private final SchoolPaymentService payments;

    @PostMapping("/api/auth/school-checkout")
    public SchoolPaymentService.Checkout checkout(@Valid @RequestBody SchoolPaymentService.Registration request,
            HttpServletRequest http) {
        return payments.checkout(request, http.getRemoteAddr());
    }

    @GetMapping("/api/auth/payments/vnpay/ipn")
    public Map<String, String> ipn(@RequestParam Map<String, String> fields) {
        return payments.ipn(fields);
    }

    @PostMapping("/api/auth/school-checkout/recover")
    public SchoolPaymentService.Checkout recover(@Valid @RequestBody SchoolPaymentService.Recovery credentials, HttpServletRequest http) {
        return payments.recover(credentials, http.getRemoteAddr());
    }

    @PreAuthorize("hasRole('SCHOOL_MANAGER')") @GetMapping("/api/school/billing")
    public SchoolPaymentService.Billing billing() { return payments.billing(); }
    @PreAuthorize("hasRole('SCHOOL_MANAGER')") @PostMapping("/api/school/billing/quote")
    public SchoolPaymentService.Quote quote(@Valid @RequestBody SchoolPaymentService.PlanChoice request) { return payments.quote(request.planCode()); }
    @PreAuthorize("hasRole('SCHOOL_MANAGER')") @PostMapping("/api/school/billing/checkout")
    public SchoolPaymentService.Checkout purchase(@Valid @RequestBody SchoolPaymentService.PlanChoice request, HttpServletRequest http) { return payments.purchase(request.planCode(), request.expectedAmountVnd(), http.getRemoteAddr()); }
    @PreAuthorize("hasRole('SCHOOL_MANAGER')") @PutMapping("/api/school/billing/next-plan")
    public SchoolPaymentService.Billing nextPlan(@Valid @RequestBody SchoolPaymentService.PlanChoice request) { return payments.nextPlan(request.planCode()); }

    @GetMapping("/api/auth/payments/{id}")
    public Map<String, String> status(@PathVariable UUID id) {
        return Map.of("status", payments.status(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/payment-notifications")
    public List<SchoolPaymentService.Notification> notifications() {
        return payments.notifications();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/payments")
    public List<SchoolPaymentService.AdminPaymentRow> adminPayments() { return payments.adminPayments(); }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/payment-report")
    public SchoolPaymentService.RevenueSummary paymentReport() { return payments.revenueSummary(); }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/admin/payments/{id}/reconcile")
    public Map<String, String> reconcile(@PathVariable UUID id) { return Map.of("status", payments.reconcilePayment(id)); }
}
