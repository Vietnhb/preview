package com.example.backend.system.curriculum.model.entity;

import com.example.backend.base.crud.model.entity.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "grade_levels")
@Getter
@Setter
public class GradeLevel extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private ContentModule module;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "level", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Lesson> lessons = new ArrayList<>();

}
