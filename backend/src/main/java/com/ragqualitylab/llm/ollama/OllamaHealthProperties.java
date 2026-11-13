package com.ragqualitylab.llm.ollama;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Ollama 헬스체크 설정. timeout: 응답 대기 상한(기본 2초). */
@ConfigurationProperties("app.ollama")
public record OllamaHealthProperties(Duration healthTimeout) {

    public OllamaHealthProperties {
        if (healthTimeout == null) {
            healthTimeout = Duration.ofSeconds(2);
        }
    }
}
