# 오너 사후 검수 안내

Claude Code가 직접 머지한 작업을 오너가 나중에 확인하기 위한 안내다. 설계 의도는 `docs/phase0/`와 `docs/adr/`에 있고, 이 문서는 **실제로 구현된 구조**와 **어디를 보면 되는지**만 적는다. 기능이 추가될 때마다 갱신한다.

- 최종 갱신: 2026-11-19 (Phase 3 완료)

## 1. 개발 방식

| 항목 | 방식 | 확인 위치 |
|---|---|---|
| 작업 단위 | 기능 단위 브랜치 → PR → CI 통과 → 머지 | GitHub PR 목록, 아래 4절 |
| 커밋 | Conventional Commits 제목(영어) + 한국어 본문(변경 이유) | `git log` |
| 스키마 | Flyway 마이그레이션만으로 변경, Hibernate는 `ddl-auto: validate`로 검증만 | `backend/src/main/resources/db/migration/` |
| 테스트 | 기본 `test`(단위 + Testcontainers 실제 PostgreSQL), `ollamaTest`(로컬 임베딩), `externalTest`(Gemini, 수동) | `backend/build.gradle.kts`, README "테스트" |
| 외부 호출 대체 | 테스트에서는 결정적 가짜 임베딩(`FakeEmbeddingPort`) 사용 | `backend/src/test/.../support/` |
| 결정 기록 | 설계를 바꾼 결정은 ADR에 "Phase N 결과" 절로 추가 | `docs/adr/` |

## 2. 도메인 구조 (백엔드)

패키지는 **기능 모듈 우선**, 모듈 안은 `web → application → domain ← infrastructure` 계층이다 (`docs/phase0/03_architecture.md` 2절).

```text
com.ragqualitylab
├─ common/         보안 설정, 오류 코드·Problem Details 응답
├─ admin/          관리자 계정·로그인·잠금·초기 계정 생성
├─ document/       업로드 검증, 텍스트 추출, 청크 분할, 비동기 처리, 문서 API
├─ retrieval/      질의 임베딩 + 벡터 검색
├─ chat/           익명 세션·토큰, 질문→검색→생성→저장, 프롬프트 조립, 인용 검증
└─ llm/            외부 모델 포트와 어댑터
   ├─ embedding/   EmbeddingPort ← OllamaEmbeddingAdapter
   ├─ generation/  GenerationModelPort ← GeminiGenerationAdapter (Judge 포트는 Phase 5에서 별도)
   └─ usage/       LlmBudget: 분당·일일 호출 예산, llm_usage_records 기록
```

| 모듈 | 핵심 클래스 | 검수 포인트 |
|---|---|---|
| admin | `AdminLoginService`, `AdminUser` | 잠금 규칙, 계정 존재 여부 비노출, 세션 ID 교체 |
| document | `DocumentService`(업로드·삭제·재처리), `DocumentProcessor`(추출→청크→임베딩→저장) | 임베딩은 트랜잭션 밖, 청크 저장과 READY 전환은 한 트랜잭션. 삭제 즉시 검색 제외 |
| document | `Chunker`, `UploadValidator` | 제목 경계 분할, 확장자 위장·크기·파일명 처리 |
| retrieval | `ChunkSearchRepository` | Spring AI VectorStore 대신 자체 SQL (ADR-0005) |
| llm | `EmbeddingPort`, `GenerationModelPort` | 모델을 바꿔도 다른 모듈은 포트만 의존 |
| chat | `ChatService.ask` | 질문 저장(세션 행 잠금)과 답변 저장은 짧은 트랜잭션, 외부 호출은 트랜잭션 밖. 실패해도 ERROR 답변을 남김 |
| chat | `ChatSessionService` | 토큰 원문 미저장(해시), 토큰 불일치·없는 세션 모두 404, 종료·만료 410 |
| chat | `PromptBuilder`, `CitationParser` | 근거·질문을 태그 블록에 넣고 태그 흉내를 이스케이프, 범위 밖 인용 번호 제거 |
| llm | `LlmBudget` | 예약 행을 먼저 넣고 advisory lock으로 직렬화해 동시 요청에도 일일 상한을 넘지 않음 |

## 3. 데이터 구조

| 테이블 | 마이그레이션 | 설계 문서와 다른 점 |
|---|---|---|
| `admin_users` | V2 | 없음 |
| `documents`, `document_chunks` | V3 | `content_sha256`을 `varchar(64)` + 길이 CHECK로 둠(ERD는 char64). 청크 FK는 `ON DELETE CASCADE`(물리 삭제 배치용) |

| `chat_sessions`, `chat_messages`, `retrieval_records`, `llm_usage_records` | V4 | `llm_usage_records.status`에 `RESERVED`(호출 전 예약) 추가 |

평가 테이블은 Phase 5에서 추가한다 (`docs/phase0/04_erd.md`).

## 4. PR 기록

| PR | 단계 | 내용 | 머지 |
|---|---|---|---|
| #1 | 0 | 설계 문서·ADR | 오너 |
| #2 | 0 | 명세 v0.4, 위임 규칙 | 오너 |
| #3 | 1 | 프로젝트 골격, Compose, CI | 오너 |
| #4 | 2 | 관리자 인증 | 오너 |
| #5 | 2 | 지식베이스 문서 8개 | Claude Code |
| #6 | 2 | 문서 업로드·임베딩·검색, 골든 질문 40개 | 오너 |
| #7 | — | 자가 머지 규칙 확대, 이 검수 안내 추가 | 오너 |
| #8 | 3 준비 | Gemini 무료 한도·일일 예산 기록 | Claude Code |
| #9 | — | 검수 안내 PR 기록 갱신 (CI 완료 전에 머지한 규칙 위반 1건, 사후 CI 통과 확인) | Claude Code |
| #10 | 3 | 익명 세션, RAG 답변 생성, 호출 예산 | Claude Code |
| #11 | 3 | 방문자 채팅 화면 | Claude Code |

## 5. 검수 시 추천 순서
1. 이 문서 2·3절로 구조 파악
2. 관심 있는 PR 본문의 "설계 선택"과 "알려진 리스크"
3. 해당 모듈의 통합 테스트(동작 명세 역할): 예) `DocumentIngestionIntegrationTests`, `AdminAuthIntegrationTests`
4. 데모 모드 실행 후 화면 확인 (README "실행")
