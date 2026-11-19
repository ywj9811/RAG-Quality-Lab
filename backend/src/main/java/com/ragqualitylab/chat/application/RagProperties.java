package com.ragqualitylab.chat.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 답변 설정 (CHAT-03·05, 03_architecture 5절).
 * contextMessages: 생성에 넣는 직전 대화 메시지 수(6 = 질문·답변 3쌍)
 * maxContextChars: 직전 대화 최대 글자 수(3000자 ≈ 2000토큰)
 * minScore: 이보다 낮은 최고 유사도면 LLM을 부르지 않고 근거 부족 안내.
 *   Phase 2 측정상 임계값만으로는 범위 밖 질문을 가릴 수 없어(ADR-0003) 정상 질문 오거절을 막도록 낮게 둔다(0.35)
 * blockPersonalInfo: 개인정보 형태가 보이면 외부 LLM 전송 전에 거절(기본 true)
 */
@ConfigurationProperties("app.rag")
public record RagProperties(Integer contextMessages, Integer maxContextChars, Double minScore,
                            Boolean blockPersonalInfo) {

    public RagProperties {
        if (contextMessages == null) {
            contextMessages = 6;
        }
        if (maxContextChars == null) {
            maxContextChars = 3000;
        }
        if (minScore == null) {
            minScore = 0.35;
        }
        if (blockPersonalInfo == null) {
            blockPersonalInfo = true;
        }
    }
}
