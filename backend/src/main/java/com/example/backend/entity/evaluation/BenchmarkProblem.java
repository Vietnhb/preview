package com.example.backend.entity.evaluation;

import com.example.backend.entity.common.AuditedEntity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import jakarta.persistence.Version;

@Entity
@Table(name = "benchmark_problems")
@Getter
@Setter
public class BenchmarkProblem extends AuditedEntity {

    @Column(nullable = false, columnDefinition = "text")
    private String problemText;

    @Column(nullable = false, length = 32)
    private String topic;

    @Column(nullable = false, length = 32)
    private String gradeScope;

    @Column(nullable = false, length = 80)
    private String sourceCategory;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 24)
    private String status = "DRAFT";

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_by_reference", length = 80)
    private String createdByReference;

    @Column(name = "archived_reason", columnDefinition = "text")
    private String archivedReason;

    @OneToMany(mappedBy = "benchmarkProblem", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("createdAt ASC")
    private List<GoldAnnotation> annotations = new ArrayList<>();

    @OneToMany(mappedBy = "benchmarkProblem", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @OrderBy("createdAt ASC")
    private List<Adjudication> adjudications = new ArrayList<>();

    public void addAnnotation(GoldAnnotation annotation) {
        annotations.add(annotation);
        annotation.setBenchmarkProblem(this);
    }

    public void addAdjudication(Adjudication adjudication) {
        adjudications.add(adjudication);
        adjudication.setBenchmarkProblem(this);
    }
}
