package com.ragqualitylab.llm.judge;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Judge 모델 설정 (ADR-0004). 생성 모델과 설정·프롬프트·인터페이스를 분리한다 (EVAL-01).
 * model(기본 gemini-3.6-flash), maxOutputTokens(2048), temperature(0), timeout(60초), thinkingLevel(LOW),
 * allowedModels: 무료 티어 확인된 모델만. 무료 RPD가 20이라 재시도는 하지 않는다(실패 시 관리자가 재평가)
 */
@ConfigurationProperties("app.llm.judge")
public record JudgeProperties(String model, Integer maxOutputTokens, Double temperature, Duration timeout,
                              String thinkingLevel, List<String> allowedModels) {

    public JudgeProperties {
        if (model == null) {
            model = "gemini-3.6-flash";
        }
        if (maxOutputTokens == null) {
            maxOutputTokens = 2048;
        }
        if (temperature == null) {
            temperature = 0.0;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(60);
        }
        if (thinkingLevel == null) {
            thinkingLevel = "LOW";
        }
        if (allowedModels == null || allowedModels.isEmpty()) {
            allowedModels = List.of("gemini-3.6-flash", "gemini-3.8-flash");
        }
        if (!allowedModels.contains(model)) {
            throw new IllegalArgumentException(
                "app.llm.judge.model=" + model + "은 허용 목록(무료 티어 확인 모델)에 없습니다: " + allowedModels);
        }
    }
}
