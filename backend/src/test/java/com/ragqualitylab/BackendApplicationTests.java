package com.ragqualitylab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 1 연동 스파이크(docs/phase0/02 3절 ⚠ 항목):
 * Google GenAI(채팅)와 Ollama(임베딩) 자동설정이 함께 뜨는지, Flyway가 pgvector를 활성화하는지 확인한다.
 * 외부 호출은 하지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

    @Autowired
    ApplicationContext context;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void chatModelIsGeminiOnly() {
        assertThat(context.getBeansOfType(ChatModel.class).values())
            .singleElement().isInstanceOf(GoogleGenAiChatModel.class);
    }

    @Test
    void embeddingModelIsOllamaOnly() {
        assertThat(context.getBeansOfType(EmbeddingModel.class).values())
            .singleElement().isInstanceOf(OllamaEmbeddingModel.class);
    }

    @Test
    void flywayEnablesPgvector() {
        String version = jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
        assertThat(version).isEqualTo("0.8.7");
    }
}
