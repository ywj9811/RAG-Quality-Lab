package com.ragqualitylab.llm.usage;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 LLM 호출 예산 (ADR-0007, docs/phase0/01 1.2절). 무료 한도 보호용이며 초과 시 fail closed.
 * dailyGenerationCalls / dailyJudgeCalls: 하루 호출 상한(기본 300 / 12, RPD 500 / 20의 60%)
 * generationPerMinute / judgePerMinute: 전역 분당 상한(기본 10 / 4, 모델 RPM보다 낮게)
 * resetZone: 하루 경계 시간대. Gemini RPD는 태평양 시간 자정에 초기화된다
 */
@ConfigurationProperties("app.quota")
public record LlmQuotaProperties(Integer dailyGenerationCalls, Integer dailyJudgeCalls,
                                 Integer generationPerMinute, Integer judgePerMinute, ZoneId resetZone) {

    public LlmQuotaProperties {
        if (dailyGenerationCalls == null) {
            dailyGenerationCalls = 300;
        }
        if (dailyJudgeCalls == null) {
            dailyJudgeCalls = 12;
        }
        if (generationPerMinute == null) {
            generationPerMinute = 10;
        }
        if (judgePerMinute == null) {
            judgePerMinute = 4;
        }
        if (resetZone == null) {
            resetZone = ZoneId.of("America/Los_Angeles");
        }
    }

    public int dailyLimit(OperationType type) {
        return switch (type) {
            case GENERATE -> dailyGenerationCalls;
            case JUDGE -> dailyJudgeCalls;
            default -> throw new IllegalArgumentException("No external quota for " + type);
        };
    }

    public int perMinute(OperationType type) {
        return switch (type) {
            case GENERATE -> generationPerMinute;
            case JUDGE -> judgePerMinute;
            default -> throw new IllegalArgumentException("No external quota for " + type);
        };
    }
}
