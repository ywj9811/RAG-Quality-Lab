package com.ragqualitylab.common.web;

/** 애플리케이션이 의도적으로 반환하는 오류. 메시지(detail)는 사용자에게 보여도 안전한 한국어 문장이어야 한다. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
