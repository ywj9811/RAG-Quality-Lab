package com.ragqualitylab.llm.generation;

import java.util.List;

/** 생성 요청. systemPrompt는 지시, userPrompt는 데이터 블록(근거·이전 대화·질문)을 담는다. */
public record GenerationRequest(String systemPrompt, String userPrompt) {

    public record Turn(String question, String answer) {
    }

    public record Source(int index, String label, String text) {
    }

    /** 구성 요소. 프롬프트 조립은 PromptBuilder가 한다. */
    public record Parts(List<Source> sources, List<Turn> history, String question) {
    }
}
