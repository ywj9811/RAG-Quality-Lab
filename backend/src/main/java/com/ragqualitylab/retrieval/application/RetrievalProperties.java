package com.ragqualitylab.retrieval.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** topK: 기본 검색 개수(기본 5, 03_architecture 5절). */
@ConfigurationProperties("app.retrieval")
public record RetrievalProperties(Integer topK) {

    public RetrievalProperties {
        if (topK == null) {
            topK = 5;
        }
        if (topK < 1 || topK > 50) {
            throw new IllegalArgumentException("app.retrieval.top-k는 1~50이어야 합니다.");
        }
    }
}
