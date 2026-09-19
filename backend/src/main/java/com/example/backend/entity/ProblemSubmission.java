package com.example.backend.entity;
import com.example.backend.enums.SourceMode;
import com.example.backend.enums.SubmissionStatus;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "problem_submissions")
@Getter
@Setter
public class ProblemSubmission extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SourceMode sourceMode;

    @Column(columnDefinition = "text")
    private String originalText;

    @Column(columnDefinition = "text")
    private String editableText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubmissionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_specification_id")
    private Specification currentSpecification;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<SourceAsset> sourceAssets = new ArrayList<>();

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<ExtractionRun> extractionRuns = new ArrayList<>();

    public void addSourceAsset(SourceAsset asset) {
        sourceAssets.add(asset);
        asset.setSubmission(this);
    }

    public void addExtractionRun(ExtractionRun extractionRun) {
        extractionRuns.add(extractionRun);
        extractionRun.setSubmission(this);
    }
}
