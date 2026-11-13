---
id: doc-06
title: API 예외 처리
version: 1
written_at: 2026-11-13
applies_to: Spring Framework 7.x, Spring Boot 4.x (Web MVC)
sources:
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html (확인일 2026-11-13, 참고만 함)
  - https://www.rfc-editor.org/rfc/rfc9457 (확인일 2026-11-13, 참고만 함)
---

# API 예외 처리

## 핵심 개념

API 예외 처리의 목표는 두 가지다.

1. **클라이언트가 이해할 수 있는 일관된 오류 응답**을 돌려준다. 상태 코드, 오류 종류, 사람이 읽을 메시지를 항상 같은 형식으로 준다.
2. **내부 정보를 노출하지 않는다.** 스택 트레이스, SQL, 서버 경로, 라이브러리 버전 같은 정보는 공격자에게 단서가 되므로 응답에 넣지 않고 서버 로그에만 남긴다.

Spring MVC에서는 컨트롤러 처리 중 발생한 예외를 `HandlerExceptionResolver`가 받아 응답으로 바꾼다. 개발자는 주로 `@ExceptionHandler`와 `@ControllerAdvice`로 이 과정을 제어한다.

## @ExceptionHandler

컨트롤러 안에 `@ExceptionHandler(처리할예외.class)`를 붙인 메서드를 만들면, 그 컨트롤러에서 해당 예외가 발생했을 때 이 메서드가 응답을 만든다.

- 처리할 예외 타입은 애너테이션 속성이나 메서드 파라미터 타입으로 지정한다.
- 반환값은 일반 핸들러와 같은 방식으로 처리된다. `ResponseEntity`, 응답 객체, `ProblemDetail` 등을 반환할 수 있다.
- 여러 핸들러가 후보일 때는 **예외 타입이 더 구체적인** 핸들러가 선택된다. 예를 들어 `IllegalArgumentException` 핸들러와 `RuntimeException` 핸들러가 모두 있으면, `IllegalArgumentException`에는 앞의 핸들러가 쓰인다.
- 컨트롤러 안에 정의한 핸들러는 그 컨트롤러에만 적용된다.

## @ControllerAdvice와 전역 처리

여러 컨트롤러에 공통으로 적용하려면 `@ControllerAdvice` 클래스에 `@ExceptionHandler` 메서드를 모은다. JSON API라면 `@ResponseBody`가 합쳐진 `@RestControllerAdvice`를 쓴다.

- 적용 범위는 `basePackages`, `assignableTypes`, `annotations` 속성으로 좁힐 수 있다.
- 컨트롤러 내부의 `@ExceptionHandler`가 전역 핸들러보다 먼저 적용된다.
- 여러 advice 클래스가 있으면 `@Order`로 우선순위를 정할 수 있다.
- 주의할 점: `@ControllerAdvice`는 **DispatcherServlet 안에서 발생한 예외**만 처리한다. 서블릿 필터(예: Spring Security 필터)에서 발생한 인증·인가 오류는 여기까지 오지 않는다. 이런 오류는 Spring Security의 `AuthenticationEntryPoint`(401)와 `AccessDeniedHandler`(403)에서 별도로 응답 형식을 맞춘다.

## Problem Details (RFC 9457)

오류 응답 본문의 표준 형식으로 **RFC 9457 Problem Details**가 있다(이전 RFC 7807을 대체). 미디어 타입은 `application/problem+json`이고, 다음 필드를 정의한다.

| 필드 | 의미 |
|---|---|
| `type` | 오류 유형을 식별하는 URI. 별도 문서가 없으면 `about:blank` |
| `title` | 오류 유형의 짧은 요약 |
| `status` | HTTP 상태 코드 |
| `detail` | 이번 발생에 대한 사람이 읽을 설명 |
| `instance` | 이번 발생을 식별하는 URI(보통 요청 경로) |

표준 필드 외에 **확장 필드**를 추가할 수 있다. 예를 들어 클라이언트가 분기 처리에 쓸 수 있도록 `"code": "ORDER_NOT_FOUND"` 같은 안정적인 오류 코드를 넣거나, 검증 오류 목록을 넣는다.

Spring Framework는 `ProblemDetail` 클래스와 `ErrorResponse` 인터페이스로 이 형식을 지원한다.

- 핸들러에서 `ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다.")`를 만들어 반환하면 Problem Details 형식으로 응답한다.
- `setProperty("code", "...")`로 확장 필드를 추가한다.
- Spring Boot에서 `spring.mvc.problemdetails.enabled=true`를 설정하면 Spring MVC가 기본으로 던지는 예외(형식 오류, 지원하지 않는 메서드 등)도 Problem Details로 응답한다.
- `ResponseEntityExceptionHandler`를 상속한 advice 클래스를 만들면 Spring MVC 내장 예외들의 응답을 한 곳에서 다듬을 수 있다.

## 검증 오류 응답

`@Valid` 검증 실패는 예외 종류에 따라 처리 위치가 다르다.

- `@RequestBody` 객체 검증 실패: `MethodArgumentNotValidException`
- 메서드 파라미터 단위 검증 실패(Spring 6.1 이후의 내장 메서드 검증): `HandlerMethodValidationException`

검증 오류 응답에는 어떤 필드가 왜 틀렸는지 알려주되, 내부 클래스 이름이나 원본 예외 메시지를 그대로 노출하지 않는다. 예를 들어 `"errors": [{"field": "title", "message": "제목은 필수입니다."}]`처럼 정리해서 내려준다.

## 업무 예외 설계

업무 규칙 위반(재고 부족, 중복 가입 등)은 의미가 드러나는 예외로 표현하는 것이 좋다.

- 모든 업무 오류를 `RuntimeException`이나 `IllegalStateException` 하나로 던지면, 응답 단계에서 상태 코드와 메시지를 구분하기 어렵다.
- 오류 코드를 열거형으로 정의하고(코드, HTTP 상태, 기본 제목), 공통 업무 예외가 이 코드를 갖게 하면 전역 핸들러 하나로 일관된 응답을 만들 수 있다.
- 예외 메시지를 그대로 사용자에게 보여줄 계획이라면, 메시지에 내부 식별자나 민감 정보가 섞이지 않도록 작성 규칙을 정한다.
- 예상하지 못한 예외를 잡는 마지막 핸들러(`Exception.class`)는 500과 일반적인 안내 문구를 반환하고, 원인은 서버 로그에 남긴다.

## 로깅 원칙

- 클라이언트 잘못(4xx)은 보통 정보(INFO)나 경고(WARN) 수준으로, 서버 잘못(5xx)은 오류(ERROR) 수준과 스택 트레이스로 남긴다.
- 비밀번호, 토큰, 개인정보가 담긴 요청 본문은 로그에 남기지 않는다.
- 응답과 로그를 연결할 수 있도록 요청 ID(추적 ID)를 로그와 응답 헤더에 함께 남기는 방법을 많이 쓴다.
- 같은 예외를 여러 계층에서 반복해 로그로 남기면 분석이 어려워진다. 한 곳(보통 최종 처리 지점)에서 남긴다.

## 예시

이 문서를 위해 작성한 예시다.

```java
public enum ErrorCode {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다"),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다");
    final HttpStatus status; final String title;
    ErrorCode(HttpStatus status, String title) { this.status = status; this.title = title; }
}

public class BusinessException extends RuntimeException {
    final ErrorCode code;
    public BusinessException(ErrorCode code, String detail) { super(detail); this.code = code; }
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ProblemDetail handle(BusinessException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(e.code.status, e.getMessage());
        p.setTitle(e.code.title);
        p.setProperty("code", e.code.name());
        return p;
    }
}
```

## 자주 하는 실수

- 스택 트레이스나 예외 원문 메시지를 응답에 그대로 담는 것(Spring Boot의 `server.error.include-stacktrace`를 켜 두는 것 포함).
- Spring Security 필터에서 난 401·403이 `@ControllerAdvice`로 처리될 것이라고 기대하는 것.
- 모든 예외를 500으로 응답하는 것.
- 같은 오류인데 엔드포인트마다 응답 형식이 다른 것.

## 답변 가능 범위

- `@ExceptionHandler`의 동작과 핸들러 선택 규칙
- `@ControllerAdvice`·`@RestControllerAdvice`의 전역 처리와 적용 범위, 우선순위
- 필터 단계 예외가 advice로 오지 않는 이유와 대처
- RFC 9457 Problem Details의 필드와 확장 필드, Spring의 `ProblemDetail` 사용법
- 검증 오류 응답 구성, 업무 예외와 오류 코드 설계, 로깅 원칙

## 답변 불가 범위

- 프런트엔드에서 오류를 표시하는 UI 구현
- 분산 추적 시스템(OpenTelemetry 등)의 설정 방법
- WebFlux의 예외 처리(`WebExceptionHandler`)
- 특정 로깅 라이브러리의 상세 설정
