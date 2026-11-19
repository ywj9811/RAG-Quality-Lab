package com.ragqualitylab.admin.dashboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ragqualitylab.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DashboardIntegrationTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    UUID answerA;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM chat_sessions");
        jdbc.update("DELETE FROM llm_usage_records");
        UUID s = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_sessions (id, access_token_hash, status, created_at, last_activity_at, expires_at) "
            + "VALUES (?, ?, 'ACTIVE', now(), now(), now() + interval '1 day')", s, "c".repeat(64));
        msg(s, 1, "USER", "질문 A", null);
        answerA = msg(s, 2, "ASSISTANT", "답 A", "ANSWERED");
        msg(s, 3, "USER", "질문 B", null);
        UUID answerB = msg(s, 4, "ASSISTANT", "답 B", "INSUFFICIENT_EVIDENCE");
        msg(s, 5, "USER", "질문 C", null);
        msg(s, 6, "ASSISTANT", "오류", "ERROR");
        // 답변 A: 옛 평가 40점 → 최신 80점(최신만 평균에 반영), 답변 B: 60점, 실패 1건
        eval(answerA, 40, "now() - interval '2 hours'", new int[] {10, 10, 10, 10});
        eval(answerA, 80, "now() - interval '1 hour'", new int[] {30, 20, 15, 15});
        eval(answerB, 60, "now()", new int[] {20, 20, 10, 10});
        jdbc.update("INSERT INTO evaluations (id, answer_message_id, status, rubric_version, judge_model_id, prompt_version, "
            + "error_code, created_at, completed_at) VALUES (?, ?, 'FAILED', 'v1', 'j', 'p', 'UPSTREAM_TIMEOUT', now(), now())",
            UUID.randomUUID(), answerA);
        jdbc.update("INSERT INTO llm_usage_records (id, operation_type, provider, model_id, status, created_at) "
            + "VALUES (?, 'GENERATE', 'g', 'm', 'SUCCESS', now()), (?, 'GENERATE', 'g', 'm', 'QUOTA_BLOCKED', now())",
            UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void summarizesTotalsQualityLowScoresAndUsage() throws Exception {
        mvc.perform(get("/api/admin/dashboard").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totals.sessions").value(1))
            .andExpect(jsonPath("$.totals.questions").value(3))
            .andExpect(jsonPath("$.totals.failedAnswers").value(1))
            .andExpect(jsonPath("$.totals.insufficientEvidence").value(1))
            .andExpect(jsonPath("$.totals.evaluations").value(4))
            .andExpect(jsonPath("$.totals.failedEvaluations").value(1))
            .andExpect(jsonPath("$.quality.evaluatedAnswers").value(2))
            .andExpect(jsonPath("$.quality.averageTotal").value(70.0))
            .andExpect(jsonPath("$.quality.criteriaAverage.faithfulness").value(25.0))
            .andExpect(jsonPath("$.quality.criteriaMax.faithfulness").value(35))
            .andExpect(jsonPath("$.lowScores[0].totalScore").value(60))
            .andExpect(jsonPath("$.lowScores[0].question").value("질문 B"))
            .andExpect(jsonPath("$.lowScores.length()").value(2))
            .andExpect(jsonPath("$.usage.generationCalls").value(1))
            .andExpect(jsonPath("$.usage.generationBudget").value(300))
            .andExpect(jsonPath("$.usage.judgeBudget").value(12));
    }

    @Test
    void validatesRangeAndRequiresAdmin() throws Exception {
        mvc.perform(get("/api/admin/dashboard?from=2026-11-20&to=2026-11-01").with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/dashboard?from=2020-01-01&to=2020-01-02").with(user("admin").roles("ADMIN")))
            .andExpect(jsonPath("$.totals.sessions").value(0))
            .andExpect(jsonPath("$.quality.averageTotal").doesNotExist());
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    private UUID msg(UUID session, int seq, String role, String content, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_messages (id, session_id, seq, role, content, answer_type, created_at) VALUES (?, ?, ?, ?, ?, ?, now())",
            id, session, seq, role, content, type);
        return id;
    }

    private void eval(UUID answer, int total, String at, int[] scores) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO evaluations (id, answer_message_id, status, rubric_version, judge_model_id, prompt_version, "
            + "total_score, created_at, completed_at) VALUES (?, ?, 'COMPLETED', 'v1', 'j', 'p', ?, " + at + ", " + at + ")",
            id, answer, total);
        String[] keys = {"faithfulness", "relevance", "completeness", "citationAccuracy"};
        int[] max = {35, 25, 20, 20};
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO evaluation_scores (id, evaluation_id, criterion, score, max_score, reason) VALUES (?, ?, ?, ?, ?, 'r')",
                UUID.randomUUID(), id, keys[i], scores[i], max[i]);
        }
    }
}
