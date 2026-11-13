---
id: doc-01
title: REST API와 HTTP 기본
version: 1
written_at: 2026-11-13
applies_to: HTTP/1.1 의미론(RFC 9110), Spring Boot 4.x
sources:
  - https://spring.io/guides/gs/rest-service/ (확인일 2026-11-13, 참고만 함)
  - https://www.rfc-editor.org/rfc/rfc9110 (확인일 2026-11-13, 참고만 함)
---

# REST API와 HTTP 기본

## 핵심 개념

REST(Representational State Transfer)는 웹 API를 설계하는 하나의 스타일이다. 서버가 관리하는 대상을 **리소스(resource)**로 보고, 각 리소스에 고유한 주소(URI)를 붙인 뒤, HTTP가 이미 정의해 둔 메서드와 상태 코드로 리소스를 다룬다. REST는 표준 규격이 아니라 설계 원칙의 묶음이므로 "REST답다"는 판단에는 정도의 차이가 있다.

REST 스타일 API의 대표적인 특징은 다음과 같다.

- **무상태(stateless)**: 각 요청은 처리에 필요한 정보를 모두 담는다. 서버는 이전 요청을 기억해야만 다음 요청을 이해할 수 있는 구조를 피한다. 인증 정보도 매 요청에 쿠키나 헤더로 함께 보낸다.
- **균일한 인터페이스**: 리소스마다 별도 동사를 만들지 않고, 정해진 HTTP 메서드(GET, POST, PUT, PATCH, DELETE 등)를 같은 의미로 사용한다.
- **표현(representation)**: 클라이언트가 주고받는 것은 리소스 자체가 아니라 리소스의 표현이다. 웹 API에서는 대부분 JSON을 사용한다.

## 리소스와 URI 설계

URI는 "무엇"을 가리키고, 메서드는 "어떻게"를 나타낸다. 그래서 URI에는 동사보다 명사를 쓰는 것이 일반적이다.

| 좋지 않은 예 | 권장 예 | 이유 |
|---|---|---|
| `POST /createOrder` | `POST /orders` | 생성이라는 동작은 메서드가 표현한다 |
| `GET /getOrder?id=7` | `GET /orders/7` | 개별 리소스는 경로로 식별한다 |
| `POST /orders/7/delete` | `DELETE /orders/7` | 삭제 의미가 메서드에 있다 |

설계 시 자주 쓰는 규칙은 다음과 같다.

- 컬렉션은 복수형 명사(`/orders`), 개별 항목은 식별자(`/orders/7`)로 표현한다.
- 소속 관계는 경로 계층으로 표현할 수 있다(`/orders/7/items`). 다만 계층을 3단계 이상 깊게 만들면 사용하기 어려워진다.
- 필터, 정렬, 페이지 같은 조회 조건은 쿼리 문자열로 전달한다(`/orders?status=PAID&page=0&size=20`).
- 동작이 리소스로 자연스럽게 표현되지 않을 때(예: 주문 취소)는 상태 변경으로 모델링하거나(`PATCH /orders/7` 본문에 상태 전달), 하위 리소스로 표현하기도 한다(`POST /orders/7/cancellation`). 어느 쪽이든 팀 안에서 일관되게 쓰는 것이 중요하다.

## HTTP 메서드의 의미와 멱등성

HTTP 메서드는 **안전성(safe)**과 **멱등성(idempotent)**이라는 두 성질로 구분할 수 있다.

- **안전한 메서드**: 서버 상태를 바꾸지 않는 것을 전제로 한다. GET, HEAD, OPTIONS가 해당한다.
- **멱등한 메서드**: 같은 요청을 여러 번 보내도 서버에 남는 결과가 한 번 보낸 것과 같다. GET, PUT, DELETE가 해당한다. 응답 코드는 달라질 수 있다. 예를 들어 같은 리소스를 두 번 DELETE하면 두 번째는 404가 될 수 있지만, "리소스가 없다"는 서버 상태는 같다.

| 메서드 | 주된 용도 | 안전 | 멱등 |
|---|---|---|---|
| GET | 조회 | 예 | 예 |
| POST | 생성, 처리 요청 | 아니오 | 아니오 |
| PUT | 전체 교체(없으면 생성할 수도 있음) | 아니오 | 예 |
| PATCH | 부분 변경 | 아니오 | 보장되지 않음 |
| DELETE | 삭제 | 아니오 | 예 |

멱등성이 중요한 이유는 **재시도** 때문이다. 네트워크 오류로 응답을 받지 못했을 때, 멱등한 요청은 다시 보내도 안전하다. 반면 POST를 무심코 재시도하면 주문이 두 번 생성될 수 있다. 이를 막기 위해 클라이언트가 고유한 멱등 키를 헤더로 보내고 서버가 중복을 걸러내는 방식을 쓰기도 한다.

## 상태 코드 선택

상태 코드는 응답 본문을 읽지 않아도 결과의 성격을 알 수 있게 해 준다. 첫 자리 숫자로 분류된다.

- **2xx 성공**: `200 OK`(일반 성공), `201 Created`(생성 성공. 보통 `Location` 헤더에 새 리소스 주소를 담는다), `202 Accepted`(접수했지만 처리는 나중에 끝남), `204 No Content`(성공했고 본문 없음).
- **4xx 클라이언트 오류**: `400 Bad Request`(형식·값 오류), `401 Unauthorized`(인증 필요 또는 인증 실패), `403 Forbidden`(인증은 됐지만 권한 없음), `404 Not Found`(리소스 없음), `409 Conflict`(현재 상태와 충돌. 예: 중복 생성), `422 Unprocessable Content`(형식은 맞지만 의미상 처리 불가), `429 Too Many Requests`(요청 빈도 초과).
- **5xx 서버 오류**: `500 Internal Server Error`(예상하지 못한 서버 오류), `503 Service Unavailable`(일시적으로 처리 불가), `504 Gateway Timeout`(뒤쪽 서버 응답 시간 초과).

자주 헷갈리는 구분은 401과 403이다. 401은 "누구인지 모르겠다"이고, 403은 "누구인지는 알지만 허용되지 않는다"이다. 또한 모든 오류를 200으로 응답하고 본문에 `success: false`를 넣는 방식은 HTTP의 의미를 버리는 것이라 캐시, 모니터링, 재시도 정책이 오작동하기 쉽다.

## 요청과 응답 표현

- 요청 본문과 응답 본문의 형식은 `Content-Type` 헤더로 밝힌다. JSON이면 `application/json`이다.
- 클라이언트는 원하는 응답 형식을 `Accept` 헤더로 알릴 수 있다.
- 필드 이름 규칙(예: camelCase)과 날짜 형식(예: ISO-8601, `2026-11-13T09:00:00Z`)을 API 전체에서 통일한다.
- 목록 응답에는 페이지 정보(현재 페이지, 크기, 전체 개수 등)를 함께 담아 클라이언트가 다음 요청을 계산할 수 있게 한다.
- 오류 응답 본문도 형식을 통일한다. 표준 형식으로 RFC 9457 Problem Details가 있다(06 문서 참고).

## 버전 관리

API는 한 번 공개하면 기존 클라이언트를 깨뜨리지 않고 바꾸기 어렵다. 그래서 하위 호환이 깨지는 변경에 대비한 버전 전략을 미리 정한다.

- **경로 버전**: `/v1/orders`. 눈에 잘 띄고 라우팅이 단순하다.
- **헤더 버전**: 사용자 정의 헤더나 `Accept`의 미디어 타입으로 버전을 전달한다. URI는 깔끔하지만 디버깅이 다소 어렵다.

필드를 추가하는 것은 보통 하위 호환 변경이다. 필드를 제거하거나 의미를 바꾸는 것은 호환이 깨지는 변경이다.

## 예시

아래는 이 문서를 위해 작성한 Spring MVC 예시다. 주문 하나를 조회하고, 새 주문을 만들면 201과 `Location` 헤더를 돌려준다.

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) { this.orders = orders; }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable long id) {
        return orders.find(id);               // 없으면 서비스에서 404 예외
    }

    @PostMapping
    ResponseEntity<OrderResponse> create(@RequestBody CreateOrderRequest body) {
        OrderResponse created = orders.create(body);
        URI location = URI.create("/orders/" + created.id());
        return ResponseEntity.created(location).body(created);   // 201 Created
    }
}
```

## 자주 하는 실수

- URI에 동사를 넣어 메서드의 의미와 중복시키는 것.
- 조회용 GET 요청이 서버 상태를 바꾸게 만드는 것. 크롤러나 미리 불러오기 기능이 의도치 않게 데이터를 바꿀 수 있다.
- 모든 오류를 400 또는 500 하나로 뭉뚱그리는 것.
- 생성 성공에 200만 쓰고 새 리소스 위치를 알려주지 않는 것.
- POST 요청을 클라이언트가 자동으로 재시도하게 두는 것.

## 답변 가능 범위

- REST의 기본 원칙(리소스, 무상태, 균일한 인터페이스)
- URI 설계 관례, HTTP 메서드의 의미, 안전성과 멱등성
- 주요 상태 코드의 의미와 선택 기준(특히 401과 403의 차이)
- JSON 표현, 페이지 정보, API 버전 관리 방식의 장단점
- Spring MVC로 201과 Location 헤더를 반환하는 방법

## 답변 불가 범위

- GraphQL, gRPC, WebSocket 같은 다른 API 방식의 상세
- HTTP/2, HTTP/3의 전송 계층 동작과 성능 튜닝
- HATEOAS 구현 라이브러리 사용법
- 특정 API 게이트웨이 제품의 설정 방법
