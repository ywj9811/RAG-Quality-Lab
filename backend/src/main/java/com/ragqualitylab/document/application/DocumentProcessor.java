package com.ragqualitylab.document.application;

import com.ragqualitylab.document.domain.ChunkDraft;
import com.ragqualitylab.document.domain.Document;
import com.ragqualitylab.document.domain.DocumentRepository;
import com.ragqualitylab.document.domain.DocumentStatus;
import com.ragqualitylab.document.infrastructure.ChunkJdbcRepository;
import com.ragqualitylab.document.infrastructure.LocalFileStorage;
import com.ragqualitylab.llm.embedding.EmbeddingPort;
import com.ragqualitylab.llm.embedding.EmbeddingUnavailableException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 파싱 → 청크 → 임베딩 → 저장 (DOC-02, DOC-03).
 * 임베딩(느린 외부 호출)은 DB 트랜잭션 밖에서 하고, 청크 저장과 READY 전환은 한 트랜잭션으로 묶는다.
 * 실패하면 FAILED + failure_code로 남기고, 관리자가 재처리할 수 있다.
 */
@Component
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final DocumentRepository documents;
    private final ChunkJdbcRepository chunks;
    private final LocalFileStorage storage;
    private final TextExtractor extractor;
    private final Chunker chunker;
    private final EmbeddingPort embedding;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DocumentProcessor(DocumentRepository documents, ChunkJdbcRepository chunks, LocalFileStorage storage,
                             TextExtractor extractor, Chunker chunker, EmbeddingPort embedding,
                             TransactionTemplate tx, Clock clock) {
        this.documents = documents;
        this.chunks = chunks;
        this.storage = storage;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embedding = embedding;
        this.tx = tx;
        this.clock = clock;
    }

    @Async("ingestionExecutor")
    @TransactionalEventListener
    public void onUploaded(DocumentUploadedEvent event) {
        process(event.documentId());
    }

    public void process(UUID documentId) {
        Document doc = tx.execute(status -> documents.claimForProcessing(documentId) == 1
            ? documents.findById(documentId).orElse(null)
            : null);
        if (doc == null) {
            return; // 이미 처리 중이거나 삭제됨
        }
        try {
            byte[] content = readFile(doc);
            ContentKind kind = ContentKind.fromContentType(doc.getContentType());
            ExtractedText text = extractor.extract(kind, content);
            List<ChunkDraft> drafts = chunker.chunk(text, kind, stripExtension(doc.getOriginalFilename()));
            if (drafts.isEmpty()) {
                throw new ProcessingFailure(ProcessingFailure.NO_TEXT, "청크가 없습니다", null);
            }
            List<float[]> vectors = embed(drafts);
            tx.executeWithoutResult(status -> saveReady(documentId, drafts, vectors));
        } catch (ProcessingFailure e) {
            log.warn("문서 처리 실패: id={} code={}", documentId, e.code());
            markFailed(documentId, e.code());
        } catch (RuntimeException e) {
            log.error("문서 처리 중 예상하지 못한 오류: id={}", documentId, e);
            markFailed(documentId, ProcessingFailure.INTERNAL_ERROR);
        }
    }

    private void saveReady(UUID documentId, List<ChunkDraft> drafts, List<float[]> vectors) {
        Document doc = documents.findById(documentId).orElseThrow();
        if (doc.isDeleted() || doc.getStatus() != DocumentStatus.PROCESSING) {
            return; // 처리 중에 삭제됨
        }
        Instant now = clock.instant();
        chunks.insertAll(documentId, drafts, vectors, embedding.modelId(), embedding.dimensions(), now);
        doc.markReady(drafts.size(), now);
        // 같은 문서의 이전 버전은 새 버전이 준비된 시점에 검색에서 뺀다
        for (Document other : documents.findByDocumentKeyOrderByVersionDesc(doc.getDocumentKey())) {
            if (!other.getId().equals(documentId) && !other.isDeleted()) {
                other.markDeleted(now);
                chunks.softDeleteByDocument(other.getId(), now);
            }
        }
    }

    private void markFailed(UUID documentId, String code) {
        tx.executeWithoutResult(status -> documents.findById(documentId)
            .filter(d -> d.getStatus() == DocumentStatus.PROCESSING)
            .ifPresent(d -> d.markFailed(code, clock.instant())));
    }

    private byte[] readFile(Document doc) {
        try {
            return storage.read(doc.getStoredPath());
        } catch (IOException e) {
            throw new ProcessingFailure(ProcessingFailure.FILE_MISSING, "원본 파일을 읽을 수 없습니다", e);
        }
    }

    private List<float[]> embed(List<ChunkDraft> drafts) {
        try {
            return embedding.embedDocuments(drafts.stream().map(ChunkDraft::content).toList());
        } catch (EmbeddingUnavailableException e) {
            throw new ProcessingFailure(ProcessingFailure.EMBEDDING_UNAVAILABLE, "임베딩 서버 호출 실패", e);
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
