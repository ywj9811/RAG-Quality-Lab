package com.ragqualitylab.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "document_key", nullable = false, updatable = false)
    private UUID documentKey;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    @Column(name = "stored_path", nullable = false, updatable = false)
    private String storedPath;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "uploaded_by", updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Document() {
    }

    public Document(UUID documentKey, int version, String originalFilename, String storedPath, String contentType,
                    long sizeBytes, String contentSha256, UUID uploadedBy, Instant uploadedAt) {
        this.documentKey = documentKey;
        this.version = version;
        this.originalFilename = originalFilename;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.contentSha256 = contentSha256;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.status = DocumentStatus.UPLOADED;
    }

    /** 재처리 대기열에 다시 넣는다. */
    public void markQueued() {
        this.status = DocumentStatus.UPLOADED;
        this.failureCode = null;
        this.processedAt = null;
    }

    public void markReady(int chunkCount, Instant now) {
        this.status = DocumentStatus.READY;
        this.chunkCount = chunkCount;
        this.failureCode = null;
        this.processedAt = now;
    }

    public void markFailed(String failureCode, Instant now) {
        this.status = DocumentStatus.FAILED;
        this.failureCode = failureCode;
        this.processedAt = now;
    }

    public void markDeleted(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() { return id; }
    public UUID getDocumentKey() { return documentKey; }
    public int getVersion() { return version; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoredPath() { return storedPath; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getContentSha256() { return contentSha256; }
    public DocumentStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public int getChunkCount() { return chunkCount; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
