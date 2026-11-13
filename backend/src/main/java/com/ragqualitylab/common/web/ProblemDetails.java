package com.ragqualitylab.common.web;

import org.springframework.http.ProblemDetail;

/** RFC 9457 Problem Details + code 확장 필드 생성. */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        return problem;
    }
}
