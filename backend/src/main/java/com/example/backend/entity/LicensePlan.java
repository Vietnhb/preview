package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

/** Published subscription terms; editable in PostgreSQL, never supplied by the client. */
@Entity
@Table(name = "license_plans")
@Data
public class LicensePlan {
    @Id @Column(length = 40)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private Long annualPriceVnd;
    @Column(nullable = false)
    private Integer studentQuota;
    private Integer monthlyTokenQuota;
    @Column(nullable = false)
    private boolean active = true;
}
