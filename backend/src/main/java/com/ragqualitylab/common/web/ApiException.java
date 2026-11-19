package com.ragqualitylab.common.web;

/** 애플리케이션이 의도적으로 반환하는 오류. 메시지(detail)는 사용자에게 보여도 안전한 한국어 문장이어야 한다. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Long retryAfterSeconds;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, null);
    }

    public ApiException(ErrorCode code, String detail, Long retryAfterSeconds) {
        super(detail);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode code() {
        return code;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
