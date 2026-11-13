package com.ragqualitylab.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException e) {
        return ProblemDetails.of(e.code(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        return ProblemDetails.of(ErrorCode.VALIDATION_FAILED, "입력 값을 확인해 주세요.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNotFound(NoResourceFoundException e) {
        return ProblemDetails.of(ErrorCode.NOT_FOUND, "요청한 경로가 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        // 요청 본문 등 민감할 수 있는 값은 로그에 남기지 않는다 (예외 종류와 메시지만)
        log.error("Unhandled exception: {}", e.getClass().getName(), e);
        return ProblemDetails.of(ErrorCode.INTERNAL_ERROR, "잠시 후 다시 시도해 주세요.");
    }
}
