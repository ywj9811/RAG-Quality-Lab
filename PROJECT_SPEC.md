# RAG Quality Lab — 프로젝트 기획·설계·개발 인수 명세서

- 문서 버전: v0.3 (2026-11-12) — v0.2 대비 변경: 오너 확정 결정(2.3절) 반영, 언어·Git·로컬 환경 보호 규칙 추가, 모호한 문장 정리
- 상태: 개발 착수 전 검토안. 아래 '승인 게이트' 전까지 실제 기능 구현 금지.
- 개발 실행: Claude Code
- 최종 의사결정 및 승인: 프로젝트 오너(사용자)
- 주요 원칙: **로컬 우선, 유료 전환 금지, RAG와 평가 분리, 검증 가능성 우선**

## 1. 프로젝트 개요

### 1.1 목적
관리자가 등록한 문서를 근거로 답하는 RAG 챗봇을 만들고, 관리자가 개별 응답을 선택하여 독립된 LLM-as-a-Judge 평가를 수동 실행할 수 있는 웹 플랫폼을 만든다. 평가 결과에는 항목별 점수·근거·개선 제안 및 평가 모델/프롬프트 버전이 포함된다. 챗봇 품질 점수와 실제 정답률은 동일한 지표로 취급하지 않는다.

### 1.2 사용자
- **방문자:** 회원가입 없이 1회성 채팅 세션 이용. 현재 세션 내 후속 질문 가능. 세션 종료 후 사용자 재접근 불가.
- **관리자:** 인증 후 문서 등록/삭제, 대화 기록 조회, 개별 AI 응답 평가, 결과/통계 조회.
- **운영자:** MVP에서는 관리자 단일 역할로 통합 가능.

### 1.3 핵심 사용자 흐름
1. 관리자가 문서를 올린다 → 텍스트 추출 → 청크 분할 → 로컬 임베딩 → pgvector 인덱싱.
2. 방문자가 질문한다 → 검색 → 근거 청크 + 대화 맥락으로 생성 LLM 호출 → 인용 표시 → 질문/답변/당시 검색 청크 스냅샷 저장.
3. 관리자가 답변을 선택한다 → 평가 요청 생성 → 별도 Judge LLM 호출 → 구조화 결과 검증 및 이력 저장 → 대시보드에 반영.

## 2. 비용 정책 — 최우선 제약

- **목표:** 개인 로컬 개발·데모에서 외부 API 과금 **0원**. 하드웨어, 전력 및 향후 서버 호스팅 비용은 별개.
- 무료 티어는 계정·지역·모델·시점별로 달라질 수 있다. 실제 모델 ID, 사용 가능 여부, RPM/RPD/TPM, 약관 및 요금은 **개발 시작 시 공식 페이지에서 재검증**한다.
- 유료 계정/유료 프로젝트/결제 수단 연결, 유료 API 자동 대체, 유료 클라우드 자원 생성은 **오너의 명시적 사전 승인 없이는 금지**.
- 무료 한도 도달 시 **fail closed**: 자동 과금/유료 폴백 없이 제한 오류 표시. 요청 제한, 일별 사용 예산, 최대 출력 토큰, 타임아웃, 재시도 상한을 구성 가능하게 한다.
- 요청 수·입출력 토큰·오류·모델별 호출 내역을 추적한다. 비용은 참고용 추정치이며 0원 보장으로 표시하지 않는다.
- 외부 API 무료 티어로 보낸 콘텐츠가 제공업체의 제품 개선에 이용될 수 있다. 따라서 MVP는 **공개·비민감 문서와 비식별 테스트 질문만 사용**, 개인정보·사내 기밀 입력 금지를 사용자에게 고지한다.
- **완전 로컬 대안:** 생성/평가 모두 Ollama 모델로 실행할 수 있게 LLM 어댑터 인터페이스를 설계하되, 초기 MVP에서 로컬 모델 품질/리소스 요구가 높으면 후속 단계로 둔다.

### 2.1 기본 기술 선택(잠정)
| 역할 | 1차 선택 | 비고 |
|---|---|---|
| 사용자/관리자 웹 | React + TypeScript | 하나의 앱에서 역할별 라우트 분리 가능 |
| 서버 | Java 21 + Spring Boot | 모듈형 모놀리스 |
| LLM 연결 | Spring AI | 버전 및 공급업체 호환성 사전 검증 |
| 답변 생성 | Gemini 2.5 Flash-Lite 무료 티어 **후보** | 실제 사용 가능성과 무료 조건 재확인 |
| 응답 평가 | Gemini 2.5 Flash 무료 티어 **후보** | 생성 모델과 설정·프롬프트 분리 |
| 임베딩 | Ollama + Qwen3 Embedding 0.6B **후보** | 설치·리소스·차원·Spring AI 연동 검증 |
| 벡터 저장 | PostgreSQL + pgvector (Docker 컨테이너, 로컬) | 별도 관리형 벡터 DB 미사용. 관계형 데이터와 같은 DB 사용 |
| 문서 저장 | 로컬 볼륨 | DB에는 메타데이터 저장 |
| 인증 | Spring Security | 관리자 계정: 초기 1개 |
| 구동 | Docker Compose | 앱, PostgreSQL(+pgvector). Ollama는 Mac 네이티브 설치를 우선 검토(Docker 컨테이너는 일반적으로 Mac GPU 가속을 쓰기 어려움), 컨테이너는 대안 |
| 테스트 | JUnit 5, Testcontainers, 프론트 테스트 | 외부 API 호출은 테스트 이중화 |

> 모델 이름은 **설정값**이어야 하며 코드에 고정하지 않는다. 후보 모델이 무료 조건을 만족하지 않으면 무료로 쓸 수 있는 대체 모델 또는 로컬 모델을 비교해 오너에게 결정 요청한다.

### 2.2 공식 참고 링크
- Gemini API 가격·무료 티어: https://ai.google.dev/gemini-api/docs/pricing
- Gemini API 사용량 제한: https://ai.google.dev/gemini-api/docs/rate-limits
- Spring AI 문서: https://docs.spring.io/spring-ai/reference/
- Spring 공식 가이드: https://spring.io/guides
- Ollama 모델 라이브러리: https://ollama.com/library
- pgvector: https://github.com/pgvector/pgvector

### 2.3 오너 확정 결정 (v0.3)
| 항목 | 결정 |
|---|---|
| DB | PostgreSQL + pgvector를 Docker Compose 컨테이너로 실행한다. 오너의 기존 MySQL/Redis 컨테이너는 이 프로젝트와 무관하며 건드리지 않는다. |
| Redis | MVP에서 사용하지 않는다. `08_caching.md`는 지식베이스 주제일 뿐이다. |
| 언어 | 한국어를 적극 권장한다. 문서, UI, 챗봇 답변, 골든 질문, 평가 사유를 한국어로 작성한다. |
| Git | 작업 브랜치 + PR 방식이다. Claude Code는 PR 생성까지 하고 머지는 오너만 한다. |
| Ollama | Mac 네이티브 설치를 우선 검토한다. 앱 컨테이너는 `host.docker.internal`로 접근하는 방안을 검토한다. 최종 결정은 Phase 0에서 승인받는다. |
| 로컬 환경 | 오너의 기존 Docker 리소스를 수정·삭제하지 않는다. 호스트 포트는 기본 포트와 겹치지 않게 정한다. |
| 위임 범위 | 위에 없는 사항은 Claude Code가 추천안과 근거를 제시하고, 큰 변경은 `CLAUDE.md` 1절에 따라 승인을 받는다. |

## 3. 초기 RAG 지식베이스 계획

### 3.1 범위
주제는 **Java / Spring Boot 기술 Q&A**로 시작한다. 그러나 검색/문서/평가 기능은 주제에 종속되지 않게 구현한다. MVP는 하나의 지식베이스를 사용한다.

### 3.2 초기 데이터
다음 8개 **원문 직접 작성 Markdown** 문서(한국어, 기술 용어는 영문 병기 가능)를 `knowledge/base/` 아래에 준비한다. 코드를 포함한 외부 원문을 통째로 복사하지 말고 공식 문서를 참고하여 독자적으로 설명한다. 각 문서에는 제목, 버전, 작성일, 출처 URL, 핵심 개념, 예시, 답변 가능 범위, 답변 불가 범위를 작성한다.

| 파일 | 주제 | 공식 참고 자료 예시 |
|---|---|---|
| 01_rest_api.md | REST API와 HTTP 기본 | https://spring.io/guides/gs/rest-service/ |
| 02_spring_mvc.md | Spring MVC / Controller | https://docs.spring.io/spring-framework/reference/web/webmvc.html |
| 03_spring_data_jpa.md | Entity / Repository / JPA | https://spring.io/guides/gs/accessing-data-jpa/ |
| 04_transaction.md | 트랜잭션, 전파, 롤백 | https://docs.spring.io/spring-framework/reference/data-access/transaction.html |
| 05_spring_security.md | 인증과 인가 | https://spring.io/guides/gs/securing-web/ |
| 06_exception_handling.md | API 예외 처리 | https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html |
| 07_database_index.md | 관계형 DB 인덱스 | https://www.postgresql.org/docs/current/indexes.html |
| 08_caching.md | 캐시와 Redis 개념 | https://redis.io/docs/latest/develop/ |

**라이선스 주의:** 공식 자료는 참고·출처 표시에 사용한다. 원문/코드 재배포 여부는 각 자료의 실제 라이선스를 확인한다. 명확하지 않은 경우 원문 복제 대신 독립 저술 및 짧은 출처 링크를 사용한다.

### 3.3 검증 데이터셋
- 최초 **40개 골든 질문(문서당 5개)**을 `evaluation/golden_questions.jsonl`에 작성한다.
- 필드: `id`, `document_id`, `question`, `reference_answer`, `supporting_sections`, `expected_behavior`, `difficulty`, `tags`.
- 정상 질문, 표현만 바꾼 질문, 복수 청크가 필요한 질문, 범위 밖 질문, 근거 부족 질문을 포함한다.
- `reference_answer`는 **사람이 검토할 초안**이며, 모델 산출 결과를 검증 없이 정답으로 확정하지 않는다.
- 골든 질문은 **회귀 테스트/품질 검증용**으로 사용한다. 일반 사용자 모든 질문마다 정답 비교를 강제하지 않는다.
- Judge에게는 `reference_answer`를 제공하지 않는다(`EVAL-02` 입력에 포함하지 않음). `reference_answer`는 사람 검토와 회귀 비교용으로만 쓰고, 평가 프롬프트와 분리해서 보관한다.
- 질문과 `reference_answer`는 한국어로 작성한다.

## 4. MVP 기능 요구사항

### 4.1 문서 관리
- `DOC-01` 관리자는 PDF, TXT, MD 파일을 업로드한다. MIME/확장자/용량 검증 및 파일명 정규화 필수.
- `DOC-02` 처리 상태 `UPLOADED → PROCESSING → READY` 또는 `FAILED`를 관리한다.
- `DOC-03` 파싱 → 청크 → 임베딩 → 저장은 재시도 가능하도록 설계한다. 검색 가능 문서에 한해 답변에 활용한다.
- `DOC-04` 청크는 문서 ID, 문서 버전, 인덱스/원문 위치, 텍스트, 벡터 차원·모델 정보를 가진다.
- `DOC-05` 삭제한 문서는 **향후 검색에서 즉시 제외**한다. 이전 평가 재현을 위한 당시 검색 청크 스냅샷은 보존 정책에 따른다.
- `DOC-06` 스캔 PDF OCR은 MVP에서 제외한다. 텍스트 추출 불가를 명확히 안내한다.

### 4.2 채팅
- `CHAT-01` 익명 방문자가 새 세션을 시작한다. 세션 ID와 **별도 비밀 접근 토큰**을 발급한다.
- `CHAT-02` 토큰을 안전한 HttpOnly/SameSite 쿠키 등으로 관리하고, 세션 ID만으로 타인 기록을 조회하거나 메시지를 보낼 수 없게 한다.
- `CHAT-03` 세션 내 후속 질문 및 대화 맥락 유지(토큰 상한/맥락 절단 정책 필수).
- `CHAT-04` 검색된 문서와 관련 청크를 근거로 생성 LLM 호출; 답변에 근거 문서 및 위치 표시.
- `CHAT-05` 검색 근거가 부족하거나 질문이 범위를 벗어나면 임의로 지어내지 않고 답변 불가/불확실을 안내한다.
- `CHAT-06` 질문/응답, 생성 모델과 프롬프트 버전, 검색 청크 스냅샷, 소요시간, 토큰 정보를 저장한다.
- `CHAT-07` 종료 후 방문자 재접근 불가, 관리자만 권한 검증을 거쳐 조회 가능. 세션 자동 만료와 데이터 보관 기간은 설정값으로 둔다. "세션 종료"의 정의(명시적 종료, 자동 만료, 브라우저 이탈)와 종료 후 토큰 무효화 시점은 Phase 0에서 확정한다.
- `CHAT-08` 익명 서비스 남용 방지를 위해 요청 빈도 제한, 질문 길이 제한, API 호출 상한 적용.

### 4.3 관리자
- `ADM-01` 관리자 로그인/로그아웃, 비밀번호 안전한 해시 저장 및 세션/토큰 관리.
- `ADM-02` 문서 업로드/목록/상태/삭제.
- `ADM-03` 대화 세션 목록(페이징, 날짜·상태 필터) 및 질문/답변 상세.
- `ADM-04` 특정 **AI 답변 한 건**에 대해 수동 평가 요청.
- `ADM-05` 평가 결과(총점, 항목별 점수, 각 근거, 개선 제안, 상태, 모델/프롬프트 버전) 조회.
- `ADM-06` 동일 답변 재평가 가능, 이전 결과 덮어쓰기 금지.
- `ADM-07` 대시보드: 총 세션/질문/평가/실패 수, 평가 완료 답변의 평균 점수, 항목별 평균, 낮은 점수 목록.

### 4.4 LLM-as-a-Judge
- `EVAL-01` 답변 생성과 별도 서비스/설정/프롬프트. 일반 채팅 응답 경로에서 자동 평가하지 않는다.
- `EVAL-02` 입력: 사용자 질문, 필요한 이전 맥락, 실제 답변, **생성 당시의** 검색 근거, 평가 루브릭.
- `EVAL-03` 항목: 근거 충실성 35, 질문 관련성 25, 완전성 20, 인용 정확성 20 (초기 가중치, 버전 관리).
- `EVAL-04` JSON 구조화 출력 및 필드·점수 범위 검증. 항목별 점수를 합산하여 총점 계산; 모델 총점과 불일치 시 교정 또는 검증 오류 처리.
- `EVAL-05` 상태 `PENDING/RUNNING/COMPLETED/FAILED`; 타임아웃/오류/재시도 한도 및 중복 요청 정책 정의.
- `EVAL-06` 평가 결과는 품질 *추정치*이며 실제 사실 정확도/정답률로 표현하지 않는다. 근거 문서 자체가 틀린 경우는 별개 한계로 표시한다.
- `EVAL-07` 평가 모델의 판단을 절대적인 진실로 취급하지 않고, 골든 데이터셋과 사람 검토로 일치도를 검사할 수 있는 확장 지점을 둔다.

## 5. 초기 평가 루브릭

| 항목 | 만점 | 판정 질문 |
|---|---:|---|
| Faithfulness | 35 | 답변의 구체적 주장이 제공된 근거에서 뒷받침되는가? |
| Relevance | 25 | 사용자의 실제 질문에 직접 답하는가? |
| Completeness | 20 | 문서에서 답할 수 있는 요구사항을 빠뜨리지 않았는가? |
| Citation accuracy | 20 | 답변에 표시한 인용이 정확한 위치에서 주장에 대응하는가? |
| 합계 | 100 | 각 항목 합산 |

**규칙:** 항목별 0~만점의 정수 점수, 짧은 평가 사유 및 관련 근거를 반환한다. 관련 문서 자체가 없거나 인용이 없을 때의 처리 기준을 프롬프트에 명시한다. 점수 경계 예시 3~5개를 작성하고 평가 프롬프트는 Git으로 버전 관리한다.

### 평가 JSON 예시(설명용)
```json
{
  "rubricVersion": "v1",
  "scores": {
    "faithfulness": {"score": 30, "reason": "근거에 부합하나 일부 단정적 표현이 있음"},
    "relevance": {"score": 23, "reason": "질문의 핵심을 다룸"},
    "completeness": {"score": 15, "reason": "일부 조건이 누락됨"},
    "citationAccuracy": {"score": 15, "reason": "일부 인용이 주장과 정확히 대응하지 않음"}
  },
  "issues": ["설명 누락"],
  "suggestions": ["관련 조건 및 인용 추가"]
}
```
**서버에서 총점 83을 계산**한다. 예시는 실제 채점 결과가 아니다.

## 6. 시스템 설계

```text
[Visitor React UI]       [Admin React UI]
        |                       |
        +-----------+-----------+
                    |
             [Spring Boot API]
                    |
       +------------+---------------+----------------+
       |                            |                |
 [Chat / RAG Module]         [Document Module]  [Evaluation Module]
       |                            |                |
 [Retriever → Generation LLM]  [Parser → Chunk →   [Judge LLM API]
       |                       Ollama Embed]          |
       +------------+---------------+----------------+
                    |
          [PostgreSQL + pgvector]
                    |
          [Local document volume]
```

- 초기 배포는 단일 백엔드/프론트엔드 + DB + 임베딩 서버로 구성한다.
- 외부 API 통신은 `GenerationModelPort`/`JudgeModelPort`처럼 추상화한다. 두 역할이 동일 제공업체를 사용하더라도 설정과 인터페이스를 분리한다.
- 청크 분할 크기·중복률·Top-K·유사도 임계값·임베딩 모델·프롬프트는 변경 가능한 설정값으로 둔다.
- 검색 품질 측정이 가능하도록 retrieval query, 결과 순위, score, 청크 스냅샷을 저장한다.
- 문서에 삽입된 악성 지시문은 데이터로 취급하고, 관리자 지시·시스템 프롬프트를 덮어쓰지 못하도록 방어한다.

## 7. 데이터 모델(초안)

| 엔티티 | 최소 필드/관계 |
|---|---|
| `admin_users` | id, username, password_hash, created_at |
| `documents` | id, filename, content_type, version, processing_status, uploaded_at, deleted_at |
| `document_chunks` | id, document_id, chunk_index, text, source_locator, embedding(vector), embedding_model |
| `chat_sessions` | id, status, created_at, expires_at, closed_at |
| `chat_messages` | id, session_id, role, content, created_at, model_id, prompt_version, latency_ms |
| `retrieval_records` | id, answer_message_id, document_id, chunk_id, rank, score, text_snapshot, source_locator |
| `evaluations` | id, answer_message_id, status, rubric_version, judge_model_id, prompt_version, total_score, created_at, completed_at, error_code |
| `evaluation_scores` | id, evaluation_id, criterion, score, reason |
| `llm_usage_records` | id, operation_type, model_id, input_tokens, output_tokens, latency_ms, status, created_at |

**주의:** 위는 논리 구조. 구현 전 키·제약조건·삭제 정책·인덱스·동시성·개인정보 최소화 검토가 필요하다. 동일 문서의 버전이 변경되어도 평가 재현성에 필요한 근거는 유지한다.

## 8. API 초안

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/chat/sessions` | 신규 익명 세션 |
| POST | `/api/chat/sessions/{id}/messages` | 질문 및 답변 |
| POST | `/api/chat/sessions/{id}/close` | 세션 종료 |
| POST | `/api/admin/auth/login` | 관리자 로그인 |
| POST | `/api/admin/auth/logout` | 관리자 로그아웃 |
| POST | `/api/admin/documents` | 문서 업로드 |
| GET | `/api/admin/documents` | 문서 목록 |
| DELETE | `/api/admin/documents/{id}` | 검색 대상 제외/삭제 |
| GET | `/api/admin/chats` | 세션 목록 |
| GET | `/api/admin/chats/{id}` | 세션 상세 |
| POST | `/api/admin/evaluations` | 특정 답변 평가 요청 |
| GET | `/api/admin/evaluations/{id}` | 평가 상태/결과 |
| GET | `/api/admin/messages/{id}/evaluations` | 평가 이력 |
| GET | `/api/admin/dashboard` | 요약 통계 |

API 응답 스키마, 페이지네이션, 인증 쿠키, 비동기 평가 반환 방식(예: 202 + 평가 ID)은 상세 설계 때 확정한다.

## 9. 비기능 요구사항

- **보안:** 관리자 인증/인가, 로그인 시도 제한, 익명 세션 소유권 확인, 파일 검증, 업로드 용량 제한, 프롬프트 인젝션 방어, HTTPS 배포, 비밀키 환경변수 관리, 민감정보 로그 금지.
- **가용성:** 외부 API 오류와 무료 한도 초과를 구분; 실패 시 사용자에게 이해 가능한 상태 표시; 무한 재시도 금지.
- **재현성:** 생성/평가 모델명, 프롬프트/루브릭 버전, 당시 검색 청크와 파라미터 저장.
- **개인정보:** 사용자에게 기록 저장 사실 고지; 최대 보관 기간·삭제·익명 세션 정책을 개발 전 확정. **보관 기간은 현재 미확정**.
- **성능 측정:** 검색 시간, 생성 시간, 전체 응답 시간, 평가 시간, 토큰 수 및 실패율 기록. 목표 수치는 테스트 환경 확인 후 확정.
- **테스트:** 단위 테스트, DB 통합 테스트(Testcontainers), 보안 부정 테스트, 실패/한도 초과/타임아웃 테스트, 40개 골든 질문으로 회귀 테스트.
- **언어:** 사용자 화면, 챗봇 답변, 평가 사유는 한국어를 기본으로 한다. 임베딩·생성 모델은 한국어 성능을 비교 기준에 포함한다.
- **로컬 환경 안전:** 오너의 기존 Docker 컨테이너·볼륨·네트워크를 건드리지 않는다. Compose 프로젝트명과 호스트 포트를 분리하고 문서화한다.

## 10. 단계별 개발 계획과 승인 게이트

| 단계 | 범위 | 검수 산출물 |
|---|---|---|
| 0 — 사전조사/설계 | 공식 무료 정책, 모델/차원/버전 호환성, API/ERD/화면 설계 | 비용 검증표, ADR, ERD, API 계약, 와이어프레임, 리스크 (`docs/phase0/`, `docs/adr/`에 파일로 제출) |
| 1 — 프로젝트 기초 | 프론트/백/DB/Compose, 보안 기본값, CI 초안 | 실행 가이드, 헬스체크, 테스트 결과 |
| 2 — 지식베이스 | 8개 Markdown 초안과 40개 골든 질문, 문서 업로드/청크/임베딩 | 데이터셋·출처/라이선스 메모, 인덱싱 검수 |
| 3 — 채팅/RAG | 세션, 질문/검색/생성, 근거 표시, 로그 | 성공·근거부족·오류 시나리오 테스트 |
| 4 — 관리자 | 인증, 문서/대화 조회 | 권한 테스트 및 화면 검수 |
| 5 — 평가 | 수동 평가, JSON 검증, 이력, 실패 처리 | 루브릭/프롬프트 버전, 평가 결과 샘플 |
| 6 — 대시보드/마감 | 통계, 로그, 보안 검토, 회귀테스트 | 결과 보고서, 미해결 이슈, 사용설명 |

**승인 게이트:** 각 단계의 설계/검수 내용을 오너가 승인하기 전에는 다음 단계의 본 구현에 착수하지 않는다. 특히 **단계 0에서는 코드 구현 없이 조사·설계 문서만** 제출한다.

## 11. MVP 완료 기준

- [ ] 로컬 Compose로 전체 기능 실행 가능
- [ ] 관리자 문서 등록 → READY → 검색 가능
- [ ] 방문자 질의 → 문서 근거를 포함한 응답 확인
- [ ] 근거 부족 시 문서 밖 내용을 무리하게 생성하지 않음
- [ ] 세션 종료 후 방문자 조회/추가 요청 거절
- [ ] 타 사용자가 세션 ID만으로 대화 열람 불가능
- [ ] 관리자 문서·대화 조회 가능
- [ ] 관리자의 개별 응답 평가로 점수/사유 생성
- [ ] 평가 모델 정보, 프롬프트 버전, 근거 저장
- [ ] 재평가 시 과거 평가 이력 보존
- [ ] 대시보드에서 **평가 완료 건의 평균 품질점수** 확인
- [ ] 무료 한도 초과/외부 장애 시 과금 없는 안전한 실패
- [ ] 핵심 자동화 테스트 통과 및 알려진 한계 문서화

## 12. 미결정 사항(승인 전 제안)

1. 운영/데모 환경의 RAM·GPU·OS, Ollama 실행 가능 여부 (Claude Code가 읽기 전용 명령으로 직접 확인해 보고한다).
2. 실제 Gemini 무료 티어 계정·쿼터·약관 확인 및 모델 확정.
3. 문서/대화 보관 기간, 세션 만료 시간, 문서 최대 크기.
4. 골든 데이터셋의 인간 검토 방식과 평가 기준 고정.
5. 파일 저장소 위치, 외부 공개 여부, HTTPS 배포 여부.
6. 동시 사용량 및 응답 시간 목표의 측정 기준.
7. 생성 모델과 Judge 모델이 같은 계열(Gemini)일 때의 평가 편향 완화 방안.
8. Ollama 실행 방식(Mac 네이티브 vs 컨테이너)과 호스트 포트 배정.

**불명확한 사항은 독단적으로 확정하지 말고, 추천 기본값·대안·영향을 제시하여 오너에게 승인을 요청한다.**
