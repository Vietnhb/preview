package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "schools")
@Getter @Setter
public class School extends AuditedEntity {
    @Column(nullable = false, unique = true, length = 80)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 300)
    private String address;
    @Column(nullable = false)
    private boolean active = true;
}
