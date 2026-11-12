# 05. API 계약 (초안)

## 0. 공통 규칙
- Base path `/api`, JSON(UTF-8), 필드명 camelCase, 시각은 ISO-8601(UTC, `Z`).
- 오류 응답은 RFC 9457 Problem Details 형식 + `code` 확장 필드:
  ```json
  {"type":"about:blank","title":"요청 한도를 초과했습니다","status":429,"code":"RATE_LIMITED","detail":"잠시 후 다시 시도해 주세요.","retryAfterSeconds":30}
  ```
- 페이지네이션: `?page=0&size=20&sort=createdAt,desc` → `{ "items": [...], "page": 0, "size": 20, "totalElements": 123 }`. `size` 최대 100.
- 인증
  - 방문자: 쿠키 `rql_chat`(HttpOnly, SameSite=Strict, Path=/api/chat). 토큰이 URL·본문·로그에 나타나지 않음.
  - 관리자: Spring Security 서버 세션 쿠키 `rql_admin`(HttpOnly, SameSite=Strict) + **CSRF 토큰**(쿠키→헤더 `X-XSRF-TOKEN`). 유휴 30분 만료.
- 방문자 API도 상태 변경 요청은 SameSite=Strict + `Origin` 헤더 검사로 CSRF 방어.

### 공통 오류 코드
| HTTP | code | 상황 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 길이·형식 위반 |
| 401 | `UNAUTHENTICATED` | 관리자 미로그인 |
| 403 | `FORBIDDEN` / `CSRF_INVALID` | |
| 404 | `NOT_FOUND` | 리소스 없음 **또는 세션 토큰 불일치**(존재 여부 비공개) |
| 409 | `CONFLICT` / `EVALUATION_IN_PROGRESS` / `MESSAGE_IN_PROGRESS` | |
| 410 | `SESSION_CLOSED` / `SESSION_EXPIRED` | |
| 413 | `FILE_TOO_LARGE` | |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | |
| 423 | `ACCOUNT_LOCKED` | 로그인 시도 초과 |
| 429 | `RATE_LIMITED` | 앱 요청 제한 |
| 503 | `DAILY_QUOTA_EXCEEDED` / `UPSTREAM_RATE_LIMITED` / `UPSTREAM_UNAVAILABLE` / `EMBEDDING_UNAVAILABLE` | 무료 한도·외부 장애 (fail closed) |
| 504 | `UPSTREAM_TIMEOUT` | |

## 1. 방문자 채팅

### POST `/api/chat/sessions` — 세션 생성 (CHAT-01)
- 요청 본문 없음. 응답 `201`, `Set-Cookie: rql_chat=<token>`
```json
{"sessionId":"0193…","status":"ACTIVE","expiresAt":"2026-11-13T11:00:00Z","idleTimeoutSeconds":1800,"notice":"입력 내용은 저장되며 외부 AI 서비스로 전송됩니다. 개인정보를 입력하지 마세요."}
```

### GET `/api/chat/sessions/{id}` — 현재 세션 대화 조회
- 쿠키 토큰 필수. 새로고침 복원용. `200 {sessionId,status,messages:[…]}`; 종료/만료 `410`.

### POST `/api/chat/sessions/{id}/messages` — 질문 (CHAT-03~06, 08)
요청:
```json
{"content":"@Transactional의 기본 전파 속성은 무엇인가요?"}
```
응답 `200`:
```json
{
  "questionMessageId":"…","answerMessageId":"…",
  "answer":"기본 전파 속성은 REQUIRED입니다 … [1]",
  "answerType":"ANSWERED",
  "citations":[{"index":1,"documentId":"…","filename":"04_transaction.md","sourceLocator":{"heading":"전파 속성","charStart":1200,"charEnd":1980},"snippet":"…"}],
  "latencyMs":2310
}
```
- `answerType=INSUFFICIENT_EVIDENCE`이면 `citations=[]`, 안내 문구 반환 (LLM 미호출 가능).
- 답변 생성 실패 시 질문은 저장하고 `503/504` 반환(답변 메시지는 `answerType=ERROR`로 기록).

### POST `/api/chat/sessions/{id}/close` — 종료 (CHAT-07)
- `204`, `Set-Cookie: rql_chat=; Max-Age=0`. 이미 종료면 `410`.

## 2. 관리자 인증 (ADM-01)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/admin/auth/csrf` | — | `204` + XSRF 쿠키 |
| POST | `/api/admin/auth/login` | `{username,password}` | `200 {username}` / `401` / `423` |
| POST | `/api/admin/auth/logout` | — | `204` |
| GET | `/api/admin/auth/me` | — | `200 {username}` / `401` |

- 로그인 5회 실패 시 15분 잠금(설정값). 실패 응답은 계정 존재 여부를 노출하지 않음.
- 초기 관리자 계정은 기동 시 환경변수(`ADMIN_INITIAL_USERNAME`, `ADMIN_INITIAL_PASSWORD`)로 **계정이 없을 때만** 생성, 비밀번호 로그 출력 금지. 해시는 Spring Security 기본 위임 인코더(bcrypt).

## 3. 관리자 문서 (DOC-01~06, ADM-02)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/admin/documents` | `multipart/form-data` `file`, 선택 `documentKey`(새 버전 업로드). `202 {documentId,status:"UPLOADED",version}` |
| GET | `/api/admin/documents` | 목록 `?status=&includeDeleted=false` |
| GET | `/api/admin/documents/{id}` | 상세(청크 수, 실패 사유, 버전 이력) |
| POST | `/api/admin/documents/{id}/reprocess` | `FAILED` 문서 재처리. 그 외 상태면 `409` |
| DELETE | `/api/admin/documents/{id}` | `204`. 즉시 검색 제외(soft delete) |

- 검증: 확장자 `.pdf/.txt/.md` **그리고** 내용 시그니처(PDF `%PDF-`, 텍스트는 UTF-8 디코딩 가능 여부), 10MB 이하. 저장 파일명은 UUID, 원본명은 정규화(경로 문자 제거, NFC, 길이 제한) 후 메타데이터로만 저장.
- 같은 `content_sha256`의 READY 문서가 있으면 `409 DUPLICATE_DOCUMENT`.

## 4. 관리자 대화 (ADM-03)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/admin/chats` | `?from=&to=&status=&page=&size=` → 세션 요약(메시지 수, 마지막 활동, 평가 수) |
| GET | `/api/admin/chats/{sessionId}` | 메시지 전체 + 답변별 검색 스냅샷 + 최신 평가 요약 |

## 5. 관리자 평가 (ADM-04~06, EVAL-01~05)

### POST `/api/admin/evaluations`
```json
{"messageId":"…"}
```
- `202 {evaluationId,status:"PENDING"}`, `Location: /api/admin/evaluations/{id}`
- 대상이 ASSISTANT 메시지가 아니거나 `answerType=ERROR`면 `400`. 진행 중 평가 존재 시 `409 EVALUATION_IN_PROGRESS` + `existingEvaluationId`.

### GET `/api/admin/evaluations/{id}`
```json
{
  "evaluationId":"…","messageId":"…","status":"COMPLETED",
  "rubricVersion":"v1","judgeModelId":"gemini-3.6-flash","promptVersion":"judge-v1",
  "totalScore":83,
  "scores":[{"criterion":"faithfulness","score":30,"maxScore":35,"reason":"…"}],
  "issues":["…"],"suggestions":["…"],
  "disclaimer":"이 점수는 LLM이 추정한 품질 지표이며 실제 정답률이 아닙니다.",
  "createdAt":"…","completedAt":"…","errorCode":null
}
```
- 클라이언트는 `PENDING/RUNNING` 동안 2~3초 간격 폴링(최대 2분).

### GET `/api/admin/messages/{id}/evaluations`
- 해당 답변의 평가 이력(최신순). 덮어쓰기 없음(ADM-06).

## 6. 대시보드 (ADM-07)

### GET `/api/admin/dashboard?from=&to=`
```json
{
  "totals":{"sessions":120,"questions":340,"evaluations":45,"failedEvaluations":3,"failedAnswers":5},
  "quality":{"rubricVersion":"v1","evaluatedAnswers":42,"averageTotal":78.4,
    "criteriaAverage":{"faithfulness":29.1,"relevance":21.0,"completeness":14.2,"citationAccuracy":14.1}},
  "lowScores":[{"messageId":"…","evaluationId":"…","totalScore":41,"question":"…"}],
  "usage":{"today":{"generationCalls":37,"judgeCalls":6,"generationBudget":200,"judgeBudget":50}}
}
```
- 평균은 **답변별 최신 COMPLETED 평가 1건, 루브릭 버전별** 계산.

## 7. 헬스체크
- `GET /actuator/health` (DB, Ollama 연결 상태 포함; 외부 Gemini는 호출하지 않음 — 무료 한도 보호).
