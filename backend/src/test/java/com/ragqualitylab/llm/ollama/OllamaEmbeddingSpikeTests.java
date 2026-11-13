package com.ragqualitylab.llm.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragqualitylab.TestcontainersConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 로컬 Ollama + qwen3-embedding:0.6b 실제 임베딩 확인 (ADR-0003).
 * 실행: ./gradlew ollamaTest  (Ollama 실행 및 모델 pull 필요)
 */
@Tag("ollama")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OllamaEmbeddingSpikeTests {

    @Autowired
    EmbeddingModel embeddingModel;

    @Test
    void koreanTextProduces1024DimensionVector() {
        float[] vector = embeddingModel.embed("@Transactional의 기본 전파 속성은 REQUIRED입니다.");

        assertThat(vector).hasSize(1024);
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        // ADR-0003: 정규화 여부를 확인해 기록한다 (코사인 거리 사용 시 정규화되어 있으면 내적과 동일)
        System.out.printf("embedding L2 norm = %.6f%n", Math.sqrt(norm));
        assertThat(Math.sqrt(norm)).isGreaterThan(0);
    }

    @Test
    void relatedKoreanSentencesAreCloserThanUnrelated() {
        float[] q = embeddingModel.embed("트랜잭션 전파 방식의 기본값은 무엇인가요?");
        float[] related = embeddingModel.embed("@Transactional의 기본 전파 속성은 REQUIRED이며 기존 트랜잭션에 참여합니다.");
        float[] unrelated = embeddingModel.embed("Redis는 인메모리 키-값 저장소로 캐시에 자주 쓰입니다.");

        assertThat(cosine(q, related)).isGreaterThan(cosine(q, unrelated));
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
