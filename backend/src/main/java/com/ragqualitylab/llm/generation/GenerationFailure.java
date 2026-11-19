package com.ragqualitylab.llm.generation;

/** 외부 생성 모델 호출 실패. kind로 사용자 안내와 사용량 기록 상태를 구분한다. */
public class GenerationFailure extends RuntimeException {

    public enum Kind { RATE_LIMITED, TIMEOUT, UNAVAILABLE }

    private final Kind kind;

    public GenerationFailure(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
