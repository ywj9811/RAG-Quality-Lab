package com.ragqualitylab.llm.generation;

/** 답변 생성 모델 포트 (명세 6절). Judge는 별도 포트(JudgeModelPort, Phase 5)로 분리한다. */
public interface GenerationModelPort {

    GenerationResult generate(GenerationRequest request);

    String provider();

    String modelId();
}
