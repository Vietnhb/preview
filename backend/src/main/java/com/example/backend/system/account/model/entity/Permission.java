package com.example.backend.system.account.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catalog of grantable permissions. Each permission is available to accounts of one role. */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    @Column(nullable = false, length = 120)
    private String label;

    public Permission(String code) {
        this.code = code;
    }
}
