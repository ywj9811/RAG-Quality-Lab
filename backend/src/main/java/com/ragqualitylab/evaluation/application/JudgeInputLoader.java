package com.ragqualitylab.evaluation.application;

import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 평가 대상 답변과 생성 당시 근거(retrieval_records 스냅샷)를 읽어 Judge 입력을 만든다 (EVAL-02). */
@Component
public class JudgeInputLoader {

    /** 직전 대화로 넣는 질문·답변 쌍 수 */
    static final int HISTORY_PAIRS = 3;

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public JudgeInputLoader(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 평가할 수 있는 답변인지 확인한다: ASSISTANT 메시지이고 ERROR가 아니어야 한다. */
    public void requireEvaluable(UUID messageId) {
        List<String[]> rows = jdbc.query("SELECT role, answer_type FROM chat_messages WHERE id = ?",
            (rs, i) -> new String[] {rs.getString("role"), rs.getString("answer_type")}, messageId);
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "답변을 찾을 수 없습니다.");
        }
        if (!"ASSISTANT".equals(rows.getFirst()[0]) || "ERROR".equals(rows.getFirst()[1])) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "생성에 성공한 AI 답변만 평가할 수 있습니다.");
        }
    }

    public JudgeInput load(UUID answerMessageId) {
        record Msg(UUID sessionId, int seq, String role, String content, String answerType) {
        }
        Msg answer = jdbc.queryForObject("SELECT session_id, seq, role, content, answer_type FROM chat_messages WHERE id = ?",
            (rs, i) -> new Msg(rs.getObject("session_id", UUID.class), rs.getInt("seq"), rs.getString("role"),
                rs.getString("content"), rs.getString("answer_type")), answerMessageId);
        List<Msg> earlier = jdbc.query("""
            SELECT session_id, seq, role, content, answer_type FROM chat_messages
            WHERE session_id = ? AND seq < ? ORDER BY seq
            """, (rs, i) -> new Msg(rs.getObject("session_id", UUID.class), rs.getInt("seq"), rs.getString("role"),
            rs.getString("content"), rs.getString("answer_type")), answer.sessionId(), answer.seq());
        if (earlier.isEmpty() || !"USER".equals(earlier.getLast().role())) {
            throw new IllegalStateException("답변 앞에 질문이 없습니다: " + answerMessageId);
        }
        String question = earlier.getLast().content();

        List<JudgeInput.Turn> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < earlier.size() - 1; i++) {
            Msg q = earlier.get(i);
            Msg a = earlier.get(i + 1);
            if ("USER".equals(q.role()) && "ASSISTANT".equals(a.role()) && !"ERROR".equals(a.answerType())) {
                pairs.add(new JudgeInput.Turn(q.content(), a.content()));
                i++;
            }
        }
        List<JudgeInput.Turn> history = pairs.subList(Math.max(0, pairs.size() - HISTORY_PAIRS), pairs.size());

        List<JudgeInput.Source> sources = jdbc.query("""
            SELECT rank, document_filename_snapshot, source_locator::text AS locator, text_snapshot
            FROM retrieval_records WHERE answer_message_id = ? ORDER BY rank
            """, (rs, i) -> {
            JsonNode locator = json.readTree(rs.getString("locator"));
            String heading = locator.path("heading").asString("");
            String label = rs.getString("document_filename_snapshot") + (heading.isEmpty() ? "" : " › " + heading);
            return new JudgeInput.Source(rs.getInt("rank"), label, rs.getString("text_snapshot"));
        }, answerMessageId);
        return new JudgeInput(question, List.copyOf(history), answer.content(), sources);
    }
}
