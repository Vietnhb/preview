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
    @ManyToOne @JoinColumn
    private School school;
    @ManyToOne @JoinColumn
    private User manager;
    // Registration details stay here until VNPAY confirms the payment.
    private String registrationSchoolName;
    private String registrationSchoolCode;
    @Column(length = 300)
    private String registrationAddress;
    private String registrationManagerName;
    private String registrationEmail;
    @Column(length = 20)
    private String registrationPhoneNumber;
    private String registrationPasswordHash;
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
