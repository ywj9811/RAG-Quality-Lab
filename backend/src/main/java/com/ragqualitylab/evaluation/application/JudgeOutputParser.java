package com.ragqualitylab.evaluation.application;

import com.ragqualitylab.evaluation.domain.EvaluationScoreRepository.CriterionScore;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Judge 응답 검증 (EVAL-04). 모든 루브릭 항목이 있고, 점수가 0~만점 정수이며, 사유가 있어야 한다.
 * 총점은 모델 값을 쓰지 않고 서버에서 항목 점수를 더해 계산한다.
 */
public final class JudgeOutputParser {

    static final int MAX_REASON_CHARS = 1000;
    static final int MAX_LIST_ITEMS = 10;

    private JudgeOutputParser() {
    }

    public static Parsed parse(String raw, Rubric rubric, JsonMapper json) {
        JsonNode root;
        try {
            root = json.readTree(stripFences(raw));
        } catch (RuntimeException e) {
            throw new InvalidJudgeOutput("JSON이 아닙니다");
        }
        JsonNode scores = root.path("scores");
        if (!scores.isObject()) {
            throw new InvalidJudgeOutput("scores가 없습니다");
        }
        List<CriterionScore> result = new ArrayList<>();
        int total = 0;
        for (Rubric.Criterion c : rubric.criteria()) {
            JsonNode node = scores.path(c.key());
            JsonNode score = node.path("score");
            if (!score.isIntegralNumber()) {
                throw new InvalidJudgeOutput(c.key() + " 점수가 정수가 아닙니다");
            }
            int value = score.asInt();
            if (value < 0 || value > c.maxScore()) {
                throw new InvalidJudgeOutput(c.key() + " 점수가 범위를 벗어났습니다: " + value);
            }
            String reason = node.path("reason").asString("").strip();
            if (reason.isEmpty()) {
                throw new InvalidJudgeOutput(c.key() + " 사유가 없습니다");
            }
            result.add(new CriterionScore(c.key(), value, c.maxScore(), truncate(reason, MAX_REASON_CHARS)));
            total += value;
        }
        return new Parsed(result, total, strings(root.path("issues")), strings(root.path("suggestions")));
    }

    static String stripFences(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                s = s.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return s;
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode n : node) {
                String v = n.asString("").strip();
                if (!v.isEmpty() && out.size() < MAX_LIST_ITEMS) {
                    out.add(truncate(v, MAX_REASON_CHARS));
                }
            }
        }
        return out;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record Parsed(List<CriterionScore> scores, int total, List<String> issues, List<String> suggestions) {
    }

    public static class InvalidJudgeOutput extends RuntimeException {
        public InvalidJudgeOutput(String message) {
            super(message);
        }
    }
}
