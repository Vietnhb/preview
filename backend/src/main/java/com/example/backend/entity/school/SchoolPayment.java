package com.example.backend.entity.school;

import com.example.backend.entity.account.User;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "school_payments") @Data
public class SchoolPayment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private School school;
    @ManyToOne(optional = false) @JoinColumn(nullable = false)
    private User manager;
    @Column(nullable = false)
    private String planCode;
    @Column(nullable = false)
    private Long amountVnd;
    private Integer monthlyTokenQuota;
    private Integer studentQuota;
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("'REGISTRATION'")
    private String purpose = "REGISTRATION";
    private Long annualPriceVnd;
    private String previousPlanCode;
    private java.time.LocalDate licenseStart;
    private java.time.LocalDate licenseEnd;
    @Column(nullable = false)
    private String status = "PENDING";
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    private Instant paidAt;
    private String providerTransactionNo;
}
