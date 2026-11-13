package com.ragqualitylab.retrieval.infrastructure;

import com.ragqualitylab.document.domain.SourceLocator;
import com.ragqualitylab.llm.embedding.VectorLiteral;
import com.ragqualitylab.retrieval.application.RetrievedChunk;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 벡터 검색 (ADR-0005). 삭제되었거나 READY가 아닌 문서의 청크는 제외한다 (DOC-03, DOC-05).
 * HNSW는 인덱스 탐색 후 필터를 적용하므로, 필터 때문에 결과가 Top-K보다 적어지지 않도록
 * pgvector 0.8의 반복 스캔(hnsw.iterative_scan)을 트랜잭션 범위에서 켠다.
 */
@Repository
public class ChunkSearchRepository {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public ChunkSearchRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<RetrievedChunk> search(float[] queryVector, int topK) {
        jdbc.execute("SET LOCAL hnsw.iterative_scan = relaxed_order");
        AtomicInteger rank = new AtomicInteger();
        // relaxed_order는 순서가 약간 어긋날 수 있어 바깥에서 다시 정렬한다
        return jdbc.query("""
            SELECT * FROM (
                SELECT c.id, c.document_id, d.original_filename, d.version, c.chunk_index, c.content,
                       c.source_locator::text AS locator, c.embedding <=> ?::vector AS distance
                FROM document_chunks c
                JOIN documents d ON d.id = c.document_id
                WHERE c.deleted_at IS NULL AND d.deleted_at IS NULL AND d.status = 'READY'
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
            ) t ORDER BY distance
            """,
            (rs, i) -> new RetrievedChunk(
                rank.incrementAndGet(),
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("original_filename"),
                rs.getInt("version"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                json.readValue(rs.getString("locator"), SourceLocator.class),
                1.0 - rs.getDouble("distance")),
            VectorLiteral.of(queryVector), VectorLiteral.of(queryVector), topK);
    }
}
