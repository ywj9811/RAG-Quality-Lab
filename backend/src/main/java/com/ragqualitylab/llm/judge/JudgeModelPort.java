package com.ragqualitylab.llm.judge;

/** Judge 모델 포트 (EVAL-01). 생성 모델 포트와 분리한다. 응답은 JSON 문자열이며 검증은 호출자가 한다. */
public interface JudgeModelPort {

    JudgeResponse judge(String systemPrompt, String userPrompt);

    String provider();

    String modelId();

    record JudgeResponse(String text, String modelId, Integer inputTokens, Integer outputTokens) {
    }
}
