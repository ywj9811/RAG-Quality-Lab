package com.ragqualitylab.llm.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragqualitylab.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 실제 Gemini 무료 티어 호출 1회 (수동 전용, 무료 한도 1회 소모).
 * 실행: RQL_ENV_FILE을 지정해 .env의 키를 읽게 한다.
 *   cd backend && RQL_ENV_FILE=../.env ./gradlew externalTest
 */
@Tag("external")
@SpringBootTest(properties = "spring.ai.google.genai.api-key=${GEMINI_API_KEY}")
@Import(TestcontainersConfiguration.class)
class GeminiGenerationExternalTests {

    @Autowired
    GeminiGenerationAdapter adapter;

    @Test
    void generatesKoreanAnswerWithUsage() {
        GenerationResult result = adapter.generate(new GenerationRequest(
            "한국어로 한 문장만 답한다.", "<question>HTTP 404는 무슨 뜻인가요?</question>"));
        assertThat(result.text()).isNotBlank();
        assertThat(result.inputTokens()).isPositive();
        System.out.printf("model=%s in=%s out=%s text=%s%n", result.modelId(), result.inputTokens(),
            result.outputTokens(), result.text());
    }
}
