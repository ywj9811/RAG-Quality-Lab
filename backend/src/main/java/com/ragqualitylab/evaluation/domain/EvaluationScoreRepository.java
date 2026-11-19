package com.ragqualitylab.evaluation.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 항목별 점수 (evaluation_scores). 루브릭 순서대로 저장·조회한다. */
@Repository
public class EvaluationScoreRepository {

    private final JdbcTemplate jdbc;

    public EvaluationScoreRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(UUID evaluationId, List<CriterionScore> scores) {
        jdbc.batchUpdate("INSERT INTO evaluation_scores (id, evaluation_id, criterion, score, max_score, reason) "
            + "VALUES (?, ?, ?, ?, ?, ?)", scores.stream().map(s -> new Object[] {
                UUID.randomUUID(), evaluationId, s.criterion(), s.score(), s.maxScore(), s.reason()}).toList());
    }

    public List<CriterionScore> find(UUID evaluationId) {
        return jdbc.query("SELECT criterion, score, max_score, reason FROM evaluation_scores WHERE evaluation_id = ?",
            (rs, i) -> new CriterionScore(rs.getString("criterion"), rs.getInt("score"), rs.getInt("max_score"),
                rs.getString("reason")), evaluationId);
    }

    public record CriterionScore(String criterion, int score, int maxScore, String reason) {
    }

    /** 키 순서 정렬용 */
    public static List<CriterionScore> ordered(List<CriterionScore> scores, List<String> keys) {
        Map<String, Integer> order = new java.util.HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            order.put(keys.get(i), i);
        }
        return scores.stream().sorted(java.util.Comparator.comparingInt(s -> order.getOrDefault(s.criterion(), 99))).toList();
    }
}
