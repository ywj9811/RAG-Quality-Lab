package com.ragqualitylab.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByDeletedAtIsNullOrderByUploadedAtDesc();

    List<Document> findAllByOrderByUploadedAtDesc();

    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);

    List<Document> findByDocumentKeyOrderByVersionDesc(UUID documentKey);

    /** 같은 내용의 삭제되지 않은 문서(실패 건 제외)가 있는지. */
    @Query("select count(d) > 0 from Document d where d.contentSha256 = :sha and d.deletedAt is null and d.status <> 'FAILED'")
    boolean existsActiveWithSameContent(String sha);

    @Query("select coalesce(max(d.version), 0) from Document d where d.documentKey = :documentKey")
    int findMaxVersion(UUID documentKey);

    /** 처리 시작은 한 번만: UPLOADED 상태일 때만 PROCESSING으로 바꾼다. */
    @Modifying
    @Query("update Document d set d.status = 'PROCESSING' where d.id = :id and d.status = 'UPLOADED' and d.deletedAt is null")
    int claimForProcessing(UUID id);

    /** 재시작 복구: 중단된 처리 중·대기 문서를 실패로 표시한다. */
    @Modifying
    @Query("update Document d set d.status = 'FAILED', d.failureCode = 'INTERRUPTED', d.processedAt = :now "
        + "where d.status in ('UPLOADED', 'PROCESSING') and d.deletedAt is null")
    int failInterrupted(Instant now);
}
