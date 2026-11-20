# 오너 사후 검수 안내

Claude Code가 직접 머지한 작업을 오너가 나중에 확인하기 위한 안내다. 설계 의도는 `docs/phase0/`와 `docs/adr/`에 있고, 이 문서는 **실제로 구현된 구조**와 **어디를 보면 되는지**만 적는다. 기능이 추가될 때마다 갱신한다.

- 최종 갱신: 2026-11-19 (Phase 6, MVP 마감). 완료 기준·측정·보안 점검은 [`docs/report/mvp-report.md`](report/mvp-report.md)

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
├─ common/         보안 설정, 오류 코드·Problem Details 응답, 요청 제한기, 보관 기간 정리(retention)
├─ admin/          관리자 계정·로그인·잠금·초기 계정 생성, 대화 조회(admin/chat), 대시보드(admin/dashboard)
├─ document/       업로드 검증, 텍스트 추출, 청크 분할, 비동기 처리, 문서 API
├─ retrieval/      질의 임베딩 + 벡터 검색
├─ chat/           익명 세션·토큰, 질문→검색→생성→저장, 프롬프트 조립, 인용 검증
├─ evaluation/     수동 평가 요청·실행·이력, 루브릭, Judge 응답 검증
└─ llm/            외부 모델 포트와 어댑터
   ├─ embedding/   EmbeddingPort ← OllamaEmbeddingAdapter
   ├─ generation/  GenerationModelPort ← GeminiGenerationAdapter
   ├─ judge/       JudgeModelPort ← GeminiJudgeAdapter (생성과 별도 포트·설정·프롬프트)
   ├─ common/      호출 시간 제한·실패 분류, 프롬프트 블록 이스케이프
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
| admin | `AdminChatQueryService` | 화면용 읽기 모델은 엔티티 대신 SQL로 필요한 필드만 조회. 관리자는 종료·만료 세션도 조회 |
| chat | `ChatSessionService` | 토큰 원문 미저장(해시), 토큰 불일치·없는 세션 모두 404, 종료·만료 410 |
| chat | `PromptBuilder`, `CitationParser` | 근거·질문을 태그 블록에 넣고 태그 흉내를 이스케이프, 범위 밖 인용 번호 제거 |
| evaluation | `EvaluationService`, `EvaluationRunner` | 진행 중 중복은 부분 유니크 인덱스로 막고 409에 기존 ID. Judge 호출은 트랜잭션 밖, 재시도 없음 |
| evaluation | `JudgeOutputParser`, `Rubric` | 항목·정수·범위·사유 검증, 총점은 서버 합산(모델 총점 무시). 루브릭은 `rubric/v1.json` |
| llm | `LlmBudget` | 예약 행을 먼저 넣고 advisory lock으로 직렬화해 동시 요청에도 일일 상한을 넘지 않음 |

## 3. 데이터 구조

| 테이블 | 마이그레이션 | 설계 문서와 다른 점 |
|---|---|---|
| `admin_users` | V2 | 없음 |
| `documents`, `document_chunks` | V3 | `content_sha256`을 `varchar(64)` + 길이 CHECK로 둠(ERD는 char64). 청크 FK는 `ON DELETE CASCADE`(물리 삭제 배치용) |

| `chat_sessions`, `chat_messages`, `retrieval_records`, `llm_usage_records` | V4 | `llm_usage_records.status`에 `RESERVED`(호출 전 예약) 추가 |

| `evaluations`, `evaluation_scores` | V5 | 진행 중 평가 부분 유니크 인덱스 추가 |

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
| #12 | 4 | 관리자 대화 목록·상세 | Claude Code |
| #13 | 5 | LLM-as-a-Judge 수동 평가 | Claude Code |
| #14 | 5 | Judge 모델 환경변수 연결, 실제 호출 기록 | Claude Code |
| #15 | 6 | 대시보드, 보관 기간 정리, 생성 회귀, MVP 보고서 | Claude Code |
| #16 | 6 | 실제 Judge 채점 확인 기록 | Claude Code |

## 5. 실제 외부 호출 기록 (개발 확인용)

| 일시 | 대상 | 결과 |
|---|---|---|
| 2026-11-19 | 생성 `gemini-3.5-flash-lite` 2회 | 정상. 범위 안 질문 ANSWERED(2.8초), 범위 밖 INSUFFICIENT_EVIDENCE(1.5초) |
| 2026-11-19 | Judge `gemini-3.6-flash` 3회 | 실패: 503 "high demand" 2회, 60초 시간 초과 1회. 앱은 FAILED로 기록, 유료 전환 없음 |
| 2026-11-19 | Judge `gemini-3.8-flash` 1회 (환경변수로 임시 지정) | 60초 시간 초과 |
| 2026-11-19 | **진단용** Judge를 `gemini-3.5-flash-lite`로 임시 지정 1회 | 정상 완료(2.6초, 100점). 파이프라인(입력 구성·JSON 검증·저장·화면) 확인용이며 생성과 같은 모델이라 실제 평가로 보지 않는다. DB의 해당 평가 행은 `judge_model_id=gemini-3.5-flash-lite`로 구분된다 |

| 2026-11-20 | Judge `gemini-3.6-flash` 1회 | **정상 완료**. 항목별 점수·사유 저장 확인 |

Judge 기본 모델은 `gemini-3.6-flash`로 유지한다. 11-19 실패는 Google 쪽 일시적 과부하였다.

## 6. 검수 시 추천 순서
1. 이 문서 2·3절로 구조 파악
2. 관심 있는 PR 본문의 "설계 선택"과 "알려진 리스크"
3. 해당 모듈의 통합 테스트(동작 명세 역할): 예) `DocumentIngestionIntegrationTests`, `AdminAuthIntegrationTests`
4. 데모 모드 실행 후 화면 확인 (README "실행")
