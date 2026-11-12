package com.ragqualitylab.llm.ollama;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 호스트 네이티브 Ollama(ADR-0002) 연결 상태. /api/version만 호출하며 모델 추론은 하지 않는다.
 * 외부 Gemini API는 무료 한도 보호를 위해 헬스체크에서 호출하지 않는다.
 */
@Component("ollama")
public class OllamaHealthIndicator implements HealthIndicator {

    private final RestClient client;

    public OllamaHealthIndicator(@Value("${spring.ai.ollama.base-url}") String baseUrl,
                                 OllamaHealthProperties properties) {
        var httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(properties.healthTimeout())
            .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.healthTimeout());
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public Health health() {
        try {
            Map<?, ?> body = client.get().uri("/api/version").retrieve().body(Map.class);
            Object version = body == null ? null : body.get("version");
            return Health.up().withDetail("version", String.valueOf(version)).build();
        } catch (RuntimeException e) {
            return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
