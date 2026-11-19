package com.ragqualitylab.llm.generation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 생성 모델 설정 (ADR-0004, 03_architecture 5절).
 * model: 모델 ID(allowedModels 안이어야 함), maxOutputTokens(1024), temperature(0.2), timeout(30초),
 * thinkingLevel: MINIMAL·LOW·MEDIUM·HIGH (기본 MINIMAL, 지연·토큰 절약), allowedModels: 무료 티어 확인된 모델만
 */
@ConfigurationProperties("app.llm.generation")
public record GenerationProperties(String model, Integer maxOutputTokens, Double temperature, Duration timeout,
                                   String thinkingLevel, java.util.List<String> allowedModels) {

    public GenerationProperties {
        if (model == null) {
            model = "gemini-3.5-flash-lite";
        }
        if (maxOutputTokens == null) {
            maxOutputTokens = 1024;
        }
        if (temperature == null) {
            temperature = 0.2;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(30);
        }
        if (thinkingLevel == null) {
            thinkingLevel = "MINIMAL";
        }
        if (allowedModels == null || allowedModels.isEmpty()) {
            allowedModels = java.util.List.of("gemini-3.5-flash-lite", "gemini-2.5-flash-lite");
        }
        if (!allowedModels.contains(model)) {
            throw new IllegalArgumentException(
                "app.llm.generation.model=" + model + "은 허용 목록(무료 티어 확인 모델)에 없습니다: " + allowedModels);
        }
    }
}
