package com.ragqualitylab.common.web;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드. 목록과 의미는 docs/phase0/05_api_contract.md "공통 오류 코드"를 따른다.
 * Phase 1에서는 공통 경로에 필요한 코드만 둔다.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "로그인에 실패했습니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "요청 검증에 실패했습니다"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "계정이 잠겼습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다"),
    CONFLICT(HttpStatus.CONFLICT, "현재 상태에서 처리할 수 없습니다"),
    DUPLICATE_DOCUMENT(HttpStatus.CONFLICT, "중복된 문서입니다"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일이 너무 큽니다"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식입니다"),
    MESSAGE_IN_PROGRESS(HttpStatus.CONFLICT, "이전 질문을 처리하는 중입니다"),
    SESSION_CLOSED(HttpStatus.GONE, "종료된 대화입니다"),
    SESSION_EXPIRED(HttpStatus.GONE, "만료된 대화입니다"),
    PERSONAL_INFO_DETECTED(HttpStatus.BAD_REQUEST, "개인정보로 보이는 내용이 있습니다"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청 한도를 초과했습니다"),
    DAILY_QUOTA_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "오늘 사용 한도를 모두 사용했습니다"),
    UPSTREAM_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "외부 AI 서비스 한도에 도달했습니다"),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "외부 AI 서비스에 연결할 수 없습니다"),
    EMBEDDING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "임베딩 서버에 연결할 수 없습니다"),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "외부 AI 서비스 응답이 늦습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
