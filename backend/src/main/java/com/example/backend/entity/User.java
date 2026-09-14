package com.example.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String password;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    // Nullable keeps existing rows compatible during the first schema upgrade.
    @Column(nullable = true)
    private Boolean active = true;

    @Column(name = "institution_id", length = 120)
    private String institutionId;

    @Column(name = "last_login")
    private Instant lastLogin;
}
