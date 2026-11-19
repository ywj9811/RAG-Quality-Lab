package com.ragqualitylab.admin.chat;

import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import com.ragqualitylab.common.web.PageResponse;
import com.ragqualitylab.document.domain.SourceLocator;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 대화 조회 (ADM-03). 화면용 읽기 모델이라 JPA 엔티티 대신 SQL로 필요한 필드만 읽는다.
 * 관리자는 세션 상태(종료·만료)와 무관하게 조회할 수 있다 (CHAT-07).
 */
@Service
@Transactional(readOnly = true)
public class AdminChatQueryService {

    /** 날짜 필터의 기준 시간대(화면 표시와 같은 한국 시간) */
    static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Seoul");

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    public AdminChatQueryService(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public PageResponse<SessionSummary> list(LocalDate from, LocalDate to, String status, int page, int size) {
        if (page < 0 || size < 1 || size > PageResponse.MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page는 0 이상, size는 1~100이어야 합니다.");
        }
        if (status != null && !List.of("ACTIVE", "CLOSED", "EXPIRED").contains(status)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "status 값이 올바르지 않습니다.");
        }
        var params = new MapSqlParameterSource()
            .addValue("from", from == null ? null : Timestamp.from(from.atStartOfDay(DISPLAY_ZONE).toInstant()))
            .addValue("to", to == null ? null : Timestamp.from(to.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant()))
            .addValue("status", status)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
        String where = """
            WHERE (CAST(:from AS timestamptz) IS NULL OR s.created_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR s.created_at < :to)
              AND (CAST(:status AS varchar) IS NULL OR s.status = :status)
            """;
        Long total = jdbc.queryForObject("SELECT count(*) FROM chat_sessions s " + where, params, Long.class);
        List<SessionSummary> items = jdbc.query("""
            SELECT s.id, s.status, s.created_at, s.last_activity_at, s.closed_at,
                   (SELECT count(*) FROM chat_messages m WHERE m.session_id = s.id AND m.role = 'USER') AS questions,
                   (SELECT count(*) FROM chat_messages m WHERE m.session_id = s.id AND m.answer_type = 'ERROR') AS errors,
                   (SELECT left(m.content, 80) FROM chat_messages m WHERE m.session_id = s.id AND m.role = 'USER'
                    ORDER BY m.seq LIMIT 1) AS first_question
            FROM chat_sessions s
            """ + where + " ORDER BY s.created_at DESC LIMIT :limit OFFSET :offset",
            params, (rs, i) -> new SessionSummary(rs.getObject("id", UUID.class), rs.getString("status"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("last_activity_at")),
                instant(rs.getTimestamp("closed_at")), rs.getInt("questions"), rs.getInt("errors"),
                rs.getString("first_question")));
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public SessionDetail detail(UUID sessionId) {
        var params = new MapSqlParameterSource("id", sessionId);
        List<SessionDetail> found = jdbc.query("""
            SELECT id, status, created_at, last_activity_at, expires_at, closed_at FROM chat_sessions WHERE id = :id
            """, params, (rs, i) -> new SessionDetail(rs.getObject("id", UUID.class), rs.getString("status"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("last_activity_at")),
            instant(rs.getTimestamp("expires_at")), instant(rs.getTimestamp("closed_at")), new ArrayList<>()));
        if (found.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "대화를 찾을 수 없습니다.");
        }
        SessionDetail detail = found.getFirst();

        Map<UUID, List<RetrievalSnapshot>> snapshots = new HashMap<>();
        jdbc.query("""
            SELECT r.answer_message_id, r.rank, r.score, r.cited, r.document_id, r.chunk_id,
                   r.document_filename_snapshot, r.document_version_snapshot, r.source_locator::text AS locator,
                   r.text_snapshot
            FROM retrieval_records r JOIN chat_messages m ON m.id = r.answer_message_id
            WHERE m.session_id = :id ORDER BY r.answer_message_id, r.rank
            """, params, rs -> {
            snapshots.computeIfAbsent(rs.getObject("answer_message_id", UUID.class), k -> new ArrayList<>())
                .add(new RetrievalSnapshot(rs.getInt("rank"), rs.getDouble("score"), rs.getBoolean("cited"),
                    rs.getObject("document_id", UUID.class), rs.getObject("chunk_id", UUID.class) != null,
                    rs.getString("document_filename_snapshot"), rs.getInt("document_version_snapshot"),
                    json.readValue(rs.getString("locator"), SourceLocator.class), rs.getString("text_snapshot")));
        });

        jdbc.query("""
            SELECT id, seq, role, content, answer_type, model_id, prompt_version, retrieval_query, input_tokens,
                   output_tokens, retrieval_ms, generation_ms, latency_ms, error_code, created_at
            FROM chat_messages WHERE session_id = :id ORDER BY seq
            """, params, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            detail.messages().add(new MessageDetail(id, rs.getInt("seq"), rs.getString("role"), rs.getString("content"),
                rs.getString("answer_type"), rs.getString("model_id"), rs.getString("prompt_version"),
                rs.getString("retrieval_query"), (Integer) rs.getObject("input_tokens"),
                (Integer) rs.getObject("output_tokens"), (Integer) rs.getObject("retrieval_ms"),
                (Integer) rs.getObject("generation_ms"), (Integer) rs.getObject("latency_ms"),
                rs.getString("error_code"), instant(rs.getTimestamp("created_at")),
                snapshots.getOrDefault(id, List.of())));
        });
        return detail;
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public record SessionSummary(UUID id, String status, Instant createdAt, Instant lastActivityAt, Instant closedAt,
                                 int questionCount, int errorCount, String firstQuestion) {
    }

    public record SessionDetail(UUID id, String status, Instant createdAt, Instant lastActivityAt, Instant expiresAt,
                                Instant closedAt, List<MessageDetail> messages) {
    }

    public record MessageDetail(UUID id, int seq, String role, String content, String answerType, String modelId,
                                String promptVersion, String retrievalQuery, Integer inputTokens, Integer outputTokens,
                                Integer retrievalMs, Integer generationMs, Integer latencyMs, String errorCode,
                                Instant createdAt, List<RetrievalSnapshot> retrieval) {
    }

    /** chunkAvailable: 원본 청크가 아직 남아 있는지(삭제 후 30일 지나면 false). 텍스트는 스냅샷으로 항상 남는다 */
    public record RetrievalSnapshot(int rank, double score, boolean cited, UUID documentId, boolean chunkAvailable,
                                    String filename, int documentVersion, SourceLocator sourceLocator, String text) {
    }
}
