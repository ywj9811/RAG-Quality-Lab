package com.ragqualitylab.chat.application;

import com.ragqualitylab.document.domain.SourceLocator;
import com.ragqualitylab.retrieval.application.RetrievedChunk;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** 답변 당시 검색 결과 스냅샷 (CHAT-06, 평가 재현성 EVAL-02). 원본 청크가 지워져도 텍스트는 남는다. */
@Repository
public class RetrievalRecordRepository {

    static final int SNIPPET_CHARS = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    public RetrievalRecordRepository(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void saveAll(UUID answerMessageId, List<RetrievedChunk> chunks, Set<Integer> citedRanks) {
        List<MapSqlParameterSource> rows = new ArrayList<>();
        for (RetrievedChunk c : chunks) {
            rows.add(new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("answer", answerMessageId)
                .addValue("doc", c.documentId())
                .addValue("chunk", c.chunkId())
                .addValue("rank", c.rank())
                .addValue("score", c.score())
                .addValue("cited", citedRanks.contains(c.rank()))
                .addValue("text", c.content())
                .addValue("locator", json.writeValueAsString(c.locator()))
                .addValue("filename", c.filename())
                .addValue("version", c.documentVersion()));
        }
        jdbc.batchUpdate("""
            INSERT INTO retrieval_records (id, answer_message_id, document_id, chunk_id, rank, score, cited,
                text_snapshot, source_locator, document_filename_snapshot, document_version_snapshot)
            VALUES (:id, :answer, :doc, :chunk, :rank, :score, :cited, :text, CAST(:locator AS jsonb), :filename, :version)
            """, rows.toArray(MapSqlParameterSource[]::new));
    }

    /** 답변별 인용 목록(인용된 근거만, 번호 순). */
    public Map<UUID, List<Citation>> citationsFor(Collection<UUID> answerMessageIds) {
        Map<UUID, List<Citation>> result = new HashMap<>();
        if (answerMessageIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
            SELECT answer_message_id, rank, document_id, document_filename_snapshot, source_locator::text AS locator,
                   text_snapshot
            FROM retrieval_records WHERE cited AND answer_message_id IN (:ids) ORDER BY answer_message_id, rank
            """, new MapSqlParameterSource("ids", answerMessageIds), rs -> {
            result.computeIfAbsent(rs.getObject("answer_message_id", UUID.class), k -> new ArrayList<>())
                .add(new Citation(rs.getInt("rank"), rs.getObject("document_id", UUID.class),
                    rs.getString("document_filename_snapshot"),
                    json.readValue(rs.getString("locator"), SourceLocator.class),
                    snippet(rs.getString("text_snapshot"))));
        });
        return result;
    }

    /** 청크 첫 줄("문서 제목 > 소제목")을 빼고 앞부분만 보여준다. */
    static String snippet(String chunkText) {
        int newline = chunkText.indexOf('\n');
        String body = newline >= 0 ? chunkText.substring(newline + 1) : chunkText;
        body = body.strip();
        return body.length() <= SNIPPET_CHARS ? body : body.substring(0, SNIPPET_CHARS) + "…";
    }
}
