---
id: doc-02
title: Spring MVC와 Controller
version: 1
written_at: 2026-11-13
applies_to: Spring Framework 7.x, Spring Boot 4.x (서블릿 기반 Web MVC)
sources:
  - https://docs.spring.io/spring-framework/reference/web/webmvc.html (확인일 2026-11-13, 참고만 함)
---

# Spring MVC와 Controller

## 핵심 개념

Spring MVC는 서블릿(Servlet) 위에서 동작하는 Spring의 웹 프레임워크다. 들어온 HTTP 요청을 알맞은 자바 메서드(핸들러)에 연결하고, 요청 값을 메서드 파라미터로 바꿔 넣고, 메서드의 반환값을 HTTP 응답으로 바꿔 내보낸다. 개발자는 주로 **컨트롤러(Controller)** 클래스와 그 안의 핸들러 메서드를 작성한다.

Spring Boot에서는 `spring-boot-starter-webmvc`(이전 이름 `spring-boot-starter-web`)를 추가하면 내장 톰캣과 Spring MVC 기본 설정이 함께 구성된다.

## 요청 처리 흐름 (DispatcherServlet)

모든 요청은 먼저 **DispatcherServlet**이라는 하나의 서블릿으로 들어온다. 이를 프런트 컨트롤러(front controller) 패턴이라고 부른다. 처리 순서를 단순화하면 다음과 같다.

1. **서블릿 필터**: DispatcherServlet에 도달하기 전에 필터 체인을 지난다. Spring Security도 이 단계에서 동작한다.
2. **핸들러 매핑(HandlerMapping)**: 요청 경로와 메서드를 보고 어떤 컨트롤러 메서드가 처리할지 찾는다.
3. **인터셉터(HandlerInterceptor)의 preHandle**: 핸들러 실행 전에 공통 처리를 끼워 넣을 수 있다.
4. **핸들러 어댑터(HandlerAdapter)**: 파라미터 값을 준비해 컨트롤러 메서드를 호출한다. 이때 인자 해석기(argument resolver)와 메시지 변환기(HttpMessageConverter)가 쓰인다.
5. **반환값 처리**: `@ResponseBody`가 붙은 경우 반환 객체를 메시지 변환기가 JSON 등으로 직렬화한다. 뷰 이름을 반환한 경우에는 뷰 리졸버가 화면을 렌더링한다.
6. **예외 처리**: 처리 중 예외가 나면 HandlerExceptionResolver가 처리한다. `@ExceptionHandler`가 여기에 연결된다(06 문서 참고).

필터는 서블릿 수준이라 Spring MVC의 핸들러 정보를 모르고, 인터셉터는 어떤 핸들러가 선택됐는지 알 수 있다는 점이 다르다.

## @Controller와 @RestController

- `@Controller`: 기본적으로 반환값을 **뷰 이름**으로 해석한다. 메서드에 `@ResponseBody`를 붙이면 반환값을 응답 본문으로 쓴다.
- `@RestController`: `@Controller`와 `@ResponseBody`를 합친 애너테이션이다. 모든 메서드의 반환값이 응답 본문이 된다. JSON API에는 보통 이것을 쓴다.

컨트롤러는 HTTP와 관련된 일(요청 해석, 응답 구성, 상태 코드 결정)에 집중하고, 업무 규칙은 서비스 계층에 맡기는 것이 일반적이다. 컨트롤러에 트랜잭션이나 복잡한 로직을 넣으면 테스트와 재사용이 어려워진다.

## 요청 매핑

`@RequestMapping`은 경로와 메서드, 헤더 조건 등으로 요청을 연결한다. 메서드별 축약형이 있다.

| 애너테이션 | 대응 HTTP 메서드 |
|---|---|
| `@GetMapping` | GET |
| `@PostMapping` | POST |
| `@PutMapping` | PUT |
| `@PatchMapping` | PATCH |
| `@DeleteMapping` | DELETE |

클래스에 `@RequestMapping("/orders")`를 붙이면 그 안의 메서드 경로 앞에 공통으로 붙는다. 경로 변수는 `/{id}`처럼 중괄호로 표시한다. `consumes`와 `produces` 속성으로 받는 형식과 내보내는 형식을 제한할 수 있다.

## 파라미터 바인딩

요청의 각 부분을 메서드 파라미터로 받는 대표 방법은 다음과 같다.

| 애너테이션 | 가져오는 위치 | 예 |
|---|---|---|
| `@PathVariable` | 경로 변수 | `/orders/{id}`의 `id` |
| `@RequestParam` | 쿼리 문자열, 폼 필드 | `?page=0` |
| `@RequestHeader` | 요청 헤더 | `Accept-Language` |
| `@CookieValue` | 쿠키 | 세션 외 쿠키 값 |
| `@RequestBody` | 요청 본문 전체 | JSON을 객체로 변환 |
| `@ModelAttribute` | 쿼리·폼 값을 객체 필드에 묶음 | 검색 조건 객체 |

- `@RequestParam`은 기본적으로 필수다. 선택값이면 `required = false`나 `defaultValue`를 지정한다.
- `@RequestBody`는 메시지 변환기가 JSON을 자바 객체로 바꾼다. JSON 형식이 깨졌으면 400 응답이 된다.
- 파일 업로드는 `multipart/form-data` 요청에서 `MultipartFile` 타입으로 받는다.
- 자바 `record`를 요청·응답 객체로 쓰면 불변 데이터 구조를 간결하게 표현할 수 있다.

## 입력 검증 (@Valid)

Bean Validation(Jakarta Validation) 애너테이션을 요청 객체 필드에 붙이고, 파라미터에 `@Valid`를 붙이면 컨트롤러 메서드가 실행되기 전에 검증한다.

- 자주 쓰는 제약: `@NotNull`, `@NotBlank`(문자열이 비어 있거나 공백만 있으면 실패), `@Size`, `@Min`, `@Max`, `@Email`, `@Pattern`.
- `@RequestBody` 객체 검증에 실패하면 `MethodArgumentNotValidException`이 발생한다. 별도 처리가 없으면 400 응답이 된다.
- 검증 오류 응답 형식은 `@ExceptionHandler`로 통일할 수 있다.
- 검증은 형식 확인용이다. "이미 존재하는 이메일인가" 같은 업무 규칙은 서비스 계층에서 확인한다.

## 응답 구성

- 객체를 그대로 반환하면 상태 코드는 200이다.
- 상태 코드나 헤더를 직접 정하려면 `ResponseEntity`를 반환한다. 예: `ResponseEntity.created(uri).body(dto)`, `ResponseEntity.noContent().build()`.
- 메서드에 `@ResponseStatus(HttpStatus.CREATED)`를 붙여 고정 상태 코드를 지정할 수도 있다.
- 엔티티 객체를 그대로 응답으로 내보내면 내부 필드가 노출되고, 지연 로딩 프록시 직렬화 문제가 생길 수 있다. 응답 전용 DTO를 두는 것이 안전하다.

## 예시

이 문서를 위해 작성한 예시다. 검색 조건을 쿼리 문자열로 받고, 생성 요청은 검증한다.

```java
@RestController
@RequestMapping("/api/books")
class BookController {

    private final BookService books;

    BookController(BookService books) { this.books = books; }

    @GetMapping
    List<BookResponse> search(@RequestParam(defaultValue = "") String keyword,
                              @RequestParam(defaultValue = "0") int page) {
        return books.search(keyword, page);
    }

    @PostMapping
    ResponseEntity<BookResponse> create(@Valid @RequestBody CreateBookRequest body) {
        BookResponse saved = books.create(body);
        return ResponseEntity.created(URI.create("/api/books/" + saved.id())).body(saved);
    }

    record CreateBookRequest(@NotBlank @Size(max = 100) String title,
                             @NotBlank String author) { }
}
```

## 자주 하는 실수

- `@Controller`에 `@ResponseBody` 없이 객체를 반환해서 뷰를 찾지 못하는 오류가 나는 것.
- `@Valid`를 빠뜨려 검증 애너테이션이 동작하지 않는 것.
- 엔티티를 그대로 요청·응답 객체로 써서 외부 입력이 내부 필드(예: 권한)를 바꿀 수 있게 되는 것.
- 컨트롤러에서 업무 로직과 트랜잭션까지 처리하는 것.

## 답변 가능 범위

- DispatcherServlet 중심의 요청 처리 흐름, 필터와 인터셉터의 차이
- `@Controller`와 `@RestController`의 차이
- 요청 매핑 애너테이션과 파라미터 바인딩 방법
- `@Valid`를 이용한 입력 검증과 검증 실패 시 동작
- `ResponseEntity`로 상태 코드와 헤더 구성하기

## 답변 불가 범위

- Spring WebFlux와 리액티브 프로그래밍
- Thymeleaf 등 뷰 템플릿 엔진의 문법
- 서블릿 컨테이너(톰캣) 튜닝과 스레드 풀 설정
- 서버 전송 이벤트(SSE)와 WebSocket의 상세 구현
