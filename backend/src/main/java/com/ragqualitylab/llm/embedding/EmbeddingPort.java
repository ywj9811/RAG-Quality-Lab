package com.ragqualitylab.llm.embedding;

import java.util.List;

/**
 * 임베딩 포트 (ADR-0003). 질의와 문서를 구분한다: Qwen3-Embedding은 질의에만 instruction 접두어를 붙이는 비대칭 모델이다.
 */
public interface EmbeddingPort {

    List<float[]> embedDocuments(List<String> texts);

    float[] embedQuery(String query);

    /** 청크에 기록할 임베딩 모델 식별자. */
    String modelId();

    int dimensions();
}
