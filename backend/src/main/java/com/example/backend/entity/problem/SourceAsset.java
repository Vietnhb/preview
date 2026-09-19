package com.example.backend.entity.problem;

import com.example.backend.entity.common.AuditedEntity;
import com.example.backend.entity.enums.AssetType;
import com.example.backend.entity.enums.OcrStatus;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "source_assets")
@Getter
@Setter
public class SourceAsset extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private ProblemSubmission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetType assetType;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long contentLength;

    @Column(nullable = false, length = 64)
    private String checksum;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(columnDefinition = "text")
    private String ocrText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OcrStatus ocrStatus;

    @Column(columnDefinition = "text")
    private String ocrError;
}
