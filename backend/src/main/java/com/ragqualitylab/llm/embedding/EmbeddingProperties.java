package com.ragqualitylab.llm.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * dimensions: 벡터 차원(DB 컬럼 vector(1024)과 같아야 함)
 * queryInstruction: 질의 임베딩 앞에 붙이는 영어 instruction (Qwen3-Embedding 권장 형식)
 * batchSize: 문서 임베딩 한 번에 보내는 청크 수
 */
@ConfigurationProperties("app.embedding")
public record EmbeddingProperties(Integer dimensions, String queryInstruction, Integer batchSize) {

    public EmbeddingProperties {
        if (dimensions == null) {
            dimensions = 1024;
        }
        if (queryInstruction == null) {
            queryInstruction = "";
        }
        if (batchSize == null) {
            batchSize = 16;
        }
    }
}
