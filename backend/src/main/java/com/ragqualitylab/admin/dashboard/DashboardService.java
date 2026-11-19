package com.ragqualitylab.admin.dashboard;

import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import com.ragqualitylab.evaluation.application.Rubric;
import com.ragqualitylab.llm.usage.LlmBudget;
import com.ragqualitylab.llm.usage.LlmQuotaProperties;
import com.ragqualitylab.llm.usage.OperationType;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 대시보드 (ADM-07). 기간은 한국 시간 날짜, 기본 최근 7일.
 * 품질 평균은 답변별 최신 COMPLETED 평가 1건만, 현재 루브릭 버전 기준으로 계산한다 (승인 P2 #20).
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final int DEFAULT_DAYS = 7;
    static final int LOW_SCORE_LIMIT = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final Rubric rubric;
    private final LlmBudget budget;
    private final LlmQuotaProperties quota;
    private final Clock clock;

    public DashboardService(NamedParameterJdbcTemplate jdbc, Rubric rubric, LlmBudget budget, LlmQuotaProperties quota,
                            Clock clock) {
        this.jdbc = jdbc;
        this.rubric = rubric;
        this.budget = budget;
        this.quota = quota;
        this.clock = clock;
    }

    public Dashboard summary(LocalDate from, LocalDate to) {
        LocalDate today = clock.instant().atZone(ZONE).toLocalDate();
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1) : from;
        if (start.isAfter(end)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "시작일이 종료일보다 늦습니다.");
        }
        var p = new MapSqlParameterSource()
            .addValue("from", Timestamp.from(start.atStartOfDay(ZONE).toInstant()))
            .addValue("to", Timestamp.from(end.plusDays(1).atStartOfDay(ZONE).toInstant()))
            .addValue("rubric", rubric.version());

        Totals totals = jdbc.queryForObject("""
            SELECT
              (SELECT count(*) FROM chat_sessions WHERE created_at >= :from AND created_at < :to) AS sessions,
              (SELECT count(*) FROM chat_messages WHERE role = 'USER' AND created_at >= :from AND created_at < :to) AS questions,
              (SELECT count(*) FROM chat_messages WHERE answer_type = 'ERROR' AND created_at >= :from AND created_at < :to) AS failed_answers,
              (SELECT count(*) FROM chat_messages WHERE answer_type = 'INSUFFICIENT_EVIDENCE' AND created_at >= :from AND created_at < :to) AS insufficient,
              (SELECT count(*) FROM evaluations WHERE created_at >= :from AND created_at < :to) AS evaluations,
              (SELECT count(*) FROM evaluations WHERE status = 'FAILED' AND created_at >= :from AND created_at < :to) AS failed_evaluations
            """, p, (rs, i) -> new Totals(rs.getInt("sessions"), rs.getInt("questions"), rs.getInt("failed_answers"),
            rs.getInt("insufficient"), rs.getInt("evaluations"), rs.getInt("failed_evaluations")));

        // 기간 안에 완료된 평가 중 답변별 최신 1건
        String latest = """
            WITH latest AS (
              SELECT DISTINCT ON (e.answer_message_id) e.id, e.answer_message_id, e.total_score, e.completed_at
              FROM evaluations e
              WHERE e.status = 'COMPLETED' AND e.rubric_version = :rubric
                AND e.completed_at >= :from AND e.completed_at < :to
              ORDER BY e.answer_message_id, e.completed_at DESC
            )
            """;
        Map<String, Object> agg = jdbc.queryForMap(latest + "SELECT count(*) AS n, avg(total_score) AS avg FROM latest", p);
        Map<String, Double> criteria = new LinkedHashMap<>();
        rubric.keys().forEach(k -> criteria.put(k, null));
        jdbc.query(latest + """
            SELECT s.criterion, avg(s.score) AS avg FROM latest l JOIN evaluation_scores s ON s.evaluation_id = l.id
            GROUP BY s.criterion
            """, p, rs -> {
            criteria.put(rs.getString("criterion"), round(rs.getDouble("avg")));
        });
        List<LowScore> lowScores = jdbc.query(latest + """
            SELECT l.id AS evaluation_id, l.answer_message_id, l.total_score, m.session_id,
                   (SELECT left(q.content, 80) FROM chat_messages q WHERE q.session_id = m.session_id AND q.seq = m.seq - 1) AS question
            FROM latest l JOIN chat_messages m ON m.id = l.answer_message_id
            ORDER BY l.total_score ASC, l.completed_at DESC LIMIT
            """ + LOW_SCORE_LIMIT, p, (rs, i) -> new LowScore(rs.getObject("evaluation_id", UUID.class),
            rs.getObject("answer_message_id", UUID.class), rs.getObject("session_id", UUID.class),
            rs.getInt("total_score"), rs.getString("question")));
        Number n = (Number) agg.get("n");
        Number avg = (Number) agg.get("avg");
        Quality quality = new Quality(rubric.version(), n == null ? 0 : n.intValue(),
            avg == null ? null : round(avg.doubleValue()), criteria, maxScores());

        Usage usage = new Usage(budget.usedToday(OperationType.GENERATE), quota.dailyGenerationCalls(),
            budget.usedToday(OperationType.JUDGE), quota.dailyJudgeCalls());
        return new Dashboard(start, end, totals, quality, lowScores, usage);
    }

    private Map<String, Integer> maxScores() {
        Map<String, Integer> m = new LinkedHashMap<>();
        rubric.criteria().forEach(c -> m.put(c.key(), c.maxScore()));
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    public record Dashboard(LocalDate from, LocalDate to, Totals totals, Quality quality, List<LowScore> lowScores,
                            Usage usage) {
    }

    public record Totals(int sessions, int questions, int failedAnswers, int insufficientEvidence, int evaluations,
                         int failedEvaluations) {
    }

    public record Quality(String rubricVersion, int evaluatedAnswers, Double averageTotal,
                          Map<String, Double> criteriaAverage, Map<String, Integer> criteriaMax) {
    }

    public record LowScore(UUID evaluationId, UUID messageId, UUID sessionId, int totalScore, String question) {
    }

    /** 오늘(태평양 시간 자정 기준) 외부 호출 사용량과 예산 */
    public record Usage(int generationCalls, int generationBudget, int judgeCalls, int judgeBudget) {
    }
}
