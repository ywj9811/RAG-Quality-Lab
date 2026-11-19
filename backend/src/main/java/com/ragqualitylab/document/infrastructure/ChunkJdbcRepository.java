package com.ragqualitylab.document.infrastructure;

import com.ragqualitylab.document.domain.ChunkDraft;
import com.ragqualitylab.llm.embedding.VectorLiteral;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** document_chunks 쓰기 (ADR-0005: Spring AI VectorStore 대신 자체 테이블 + SQL). */
@Repository
public class ChunkJdbcRepository {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public ChunkJdbcRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insertAll(UUID documentId, List<ChunkDraft> chunks, List<float[]> vectors,
                          String embeddingModel, int embeddingDim, Instant now) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("chunks and vectors size mismatch");
        }
        List<Object[]> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDraft c = chunks.get(i);
            rows.add(new Object[] {
                UUID.randomUUID(), documentId, c.index(), c.content(), json.writeValueAsString(c.locator()),
                VectorLiteral.of(vectors.get(i)), embeddingModel, embeddingDim, Timestamp.from(now)
            });
        }
        jdbc.batchUpdate("""
            INSERT INTO document_chunks
                (id, document_id, chunk_index, content, source_locator, embedding, embedding_model, embedding_dim, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::vector, ?, ?, ?)
            """, rows);
    }

    public int softDeleteByDocument(UUID documentId, Instant now) {
        return jdbc.update("UPDATE document_chunks SET deleted_at = ? WHERE document_id = ? AND deleted_at IS NULL",
            Timestamp.from(now), documentId);
    }
}
