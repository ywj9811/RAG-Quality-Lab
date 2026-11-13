package com.ragqualitylab.document.application;

import com.ragqualitylab.admin.domain.AdminUser;
import com.ragqualitylab.admin.domain.AdminUserRepository;
import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import com.ragqualitylab.document.domain.Document;
import com.ragqualitylab.document.domain.DocumentRepository;
import com.ragqualitylab.document.domain.DocumentStatus;
import com.ragqualitylab.document.infrastructure.ChunkJdbcRepository;
import com.ragqualitylab.document.infrastructure.LocalFileStorage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 문서 관리 유스케이스 (DOC-01~05, ADM-02). 실제 파싱·임베딩은 {@link DocumentProcessor}가 비동기로 한다. */
@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final ChunkJdbcRepository chunks;
    private final AdminUserRepository admins;
    private final UploadValidator validator;
    private final LocalFileStorage storage;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, ChunkJdbcRepository chunks, AdminUserRepository admins,
                           UploadValidator validator, LocalFileStorage storage, ApplicationEventPublisher events,
                           Clock clock) {
        this.documents = documents;
        this.chunks = chunks;
        this.admins = admins;
        this.validator = validator;
        this.storage = storage;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 업로드를 접수한다. documentKey가 있으면 그 문서의 새 버전으로 올린다.
     * 새 버전이 READY가 되면 이전 버전은 검색에서 빠진다.
     */
    @Transactional
    public Document upload(String originalFilename, byte[] content, UUID documentKey, String adminUsername) {
        UploadValidator.ValidatedUpload valid = validator.validate(originalFilename, content);
        String sha256 = sha256(content);
        if (documents.existsActiveWithSameContent(sha256)) {
            throw new ApiException(ErrorCode.DUPLICATE_DOCUMENT, "같은 내용의 문서가 이미 등록되어 있습니다.");
        }
        UUID key = documentKey;
        int version = 1;
        if (key != null) {
            int maxVersion = documents.findMaxVersion(key);
            if (maxVersion == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND, "새 버전을 올릴 기존 문서를 찾을 수 없습니다.");
            }
            version = maxVersion + 1;
        } else {
            key = UUID.randomUUID();
        }
        UUID uploadedBy = admins.findByUsername(adminUsername).map(AdminUser::getId).orElse(null);
        String storedPath = storage.store(content);
        Document doc = documents.save(new Document(key, version, valid.filename(), storedPath,
            valid.kind().contentType(), content.length, sha256, uploadedBy, clock.instant()));
        events.publishEvent(new DocumentUploadedEvent(doc.getId()));
        return doc;
    }

    /** 실패한 문서만 다시 처리한다. */
    @Transactional
    public Document reprocess(UUID id) {
        Document doc = findActive(id);
        if (doc.getStatus() != DocumentStatus.FAILED) {
            throw new ApiException(ErrorCode.CONFLICT, "실패한 문서만 다시 처리할 수 있습니다.");
        }
        doc.markQueued();
        events.publishEvent(new DocumentUploadedEvent(doc.getId()));
        return doc;
    }

    /** DOC-05: 삭제 즉시 검색에서 제외한다(같은 트랜잭션에서 문서·청크 soft delete). */
    @Transactional
    public void delete(UUID id) {
        Document doc = findActive(id);
        Instant now = clock.instant();
        doc.markDeleted(now);
        chunks.softDeleteByDocument(doc.getId(), now);
    }

    @Transactional(readOnly = true)
    public List<Document> list(DocumentStatus status, boolean includeDeleted) {
        List<Document> all = includeDeleted
            ? documents.findAllByOrderByUploadedAtDesc()
            : documents.findByDeletedAtIsNullOrderByUploadedAtDesc();
        return status == null ? all : all.stream().filter(d -> d.getStatus() == status).toList();
    }

    @Transactional(readOnly = true)
    public DocumentDetail detail(UUID id) {
        Document doc = documents.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."));
        return new DocumentDetail(doc, documents.findByDocumentKeyOrderByVersionDesc(doc.getDocumentKey()));
    }

    private Document findActive(UUID id) {
        return documents.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "문서를 찾을 수 없습니다."));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record DocumentDetail(Document document, List<Document> versions) {
    }
}
