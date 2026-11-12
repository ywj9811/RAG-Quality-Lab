# 00. 요구사항 검토 및 수정 제안서

- 대상: `PROJECT_SPEC.md` v0.3, `CLAUDE.md`
- 작성일: 2026-11-12 / 작성: Claude Code (Phase 0)
- 상태: **제안 — 오너 승인 대기**

---

## 1. 오너 환경 확인 결과 (명세 12절 1번)

읽기 전용 명령(`sw_vers`, `sysctl`, `java -version`, `/usr/libexec/java_home -V`, `node -v`, `docker version`, `docker info`, `docker ps -a`, `lsof -iTCP -sTCP:LISTEN`, `gradle --version`, `which ollama/gh`)으로 확인했다. 요약만 기록한다.

| 항목 | 결과 | 판단 |
|---|---|---|
| OS | macOS 14.6.1 (Sonoma), arm64 | Ollama macOS 최소 요구(14 Sonoma 이상) 충족 |
| CPU | Apple M1 Pro, 8코어(성능 6 + 효율 2) | 임베딩 0.6B는 충분. 로컬 8B 생성 모델도 실행 가능 수준 |
| RAM | 32 GB | 충분. Docker VM에는 약 8 GB가 할당되어 있음 |
| GPU | M1 Pro 통합 GPU (Metal) | 네이티브 Ollama에서만 GPU 가속 가능 |
| 디스크 | 여유 약 223 GB | 충분 |
| Java | 기본 JDK가 **JBR 21 (x86_64)** — Apple Silicon에서 Rosetta로 동작. 그 외 17/11/8 (모두 x86_64), Homebrew OpenJDK 23 (arm64, Gradle 런처용) | ⚠ **arm64용 JDK 21 설치를 권장**(성능, 네이티브 라이브러리 호환). 전역 설치이므로 승인 필요 |
| Gradle | 전역 8.12.1 | Spring Boot 4.1은 Gradle 8.14+ 필요 → **프로젝트에서 Gradle Wrapper 사용**(전역 변경 불필요) |
| Maven | 미설치 | Gradle 사용 시 불필요 |
| Node / npm | v23.6.0 / 10.9.2 | Node 23은 홀수(비 LTS) 버전이며 지원 종료. **Node 24 LTS 권장**(전역 변경이므로 승인 필요, 대안: 23으로 진행하고 `.nvmrc`만 명시) |
| Docker | Docker Desktop 29.4.3, Compose v5.1.4, VM: 8 CPU / 약 7.6 GiB | 충분 |
| 기존 컨테이너 | `mysql:latest` 2개, `redis:latest` 1개 — 모두 **Exited** 상태 | 무관 리소스. 건드리지 않음 |
| Ollama | **미설치** | 설치 승인 필요 (ADR-0002) |
| gh CLI | **미설치** | push/PR 불가 → 로컬 커밋만 수행 |
| 사용 중 호스트 포트 | 6379(네이티브 redis-server), 27017(mongod), 5000/7000(macOS AirPlay), 4369(epmd) 등 | 5432/8080/3306/11434는 현재 비어 있음. 그래도 기본 포트 회피 원칙 적용 (ADR-0008) |

> 참고: 6379의 Redis는 Docker가 아닌 **호스트 네이티브 프로세스**다. 이 프로젝트는 Redis를 쓰지 않으므로 영향 없다.

---

## 2. 가장 중요한 발견 (요약)

1. **명세의 Judge 후보 `gemini-2.5-flash`는 이미 지원 중단(deprecated) 상태**이며, 2026-12-01부터 모든 요청이 `gemini-3.5-flash-lite`로 자동 라우팅된다. 그대로 두면 생성과 평가 모델이 사실상 같아진다. → 모델 후보 교체 필요 (ADR-0004, `01` 문서).
2. **Spring AI 현재 GA 2.0.2는 Spring Boot 4.0.x/4.1.x 전용**이다. 스택은 Boot 4.1.x 기준으로 잡아야 한다 (ADR-0001).
3. **Gemini 무료 티어 모델별 RPM/RPD 수치는 공식 문서 페이지에 더 이상 공개되지 않고** AI Studio(로그인 필요)에서만 확인할 수 있다. 계정 로그인은 금지 사항이므로 **오너가 직접 확인**해야 한다.
4. 무료 티어 입력·출력은 **제품 개선에 사용되고 사람 검토자가 볼 수 있다**(약관 명시). "공개·비민감 데이터만" 원칙은 필수다.
5. Qwen3-Embedding-0.6B는 **1024차원**(MRL로 32~1024 축소 가능)이라 pgvector HNSW 한도(2000) 안에 들어간다. 다만 **질의에는 instruction 접두어가 필요**한 비대칭 모델이라 앱에서 질의/문서 임베딩을 구분해야 한다.

---

## 3. 모순·누락 항목과 수정 제안

표기: **[모순]** 명세 내부 충돌, **[누락]** 정의 없음, **[모호]** 해석 여지.

| # | 구분 | 관련 ID | 문제 | 제안 (추천안) | 대안 |
|---|---|---|---|---|---|
| R1 | 모순 | 2.1, EVAL-01 | Judge 후보 `gemini-2.5-flash` deprecated, 2026-12-01 이후 생성 후보 계열(`3.5-flash-lite`)로 라우팅 | 생성 `gemini-3.5-flash-lite`, Judge `gemini-3.6-flash` (둘 다 Stable, 무료 티어 있음) | 생성 `gemini-2.5-flash-lite`(중단 예정 없음) / Judge `gemini-3.8-flash`(New, 안정 라벨 없음) |
| R2 | 모순 | 10절 단계 | Phase 2(문서 업로드)가 Phase 4(관리자 인증)보다 먼저라서 **인증 없는 업로드 API**가 생김 | **관리자 로그인(ADM-01)을 Phase 2 앞부분으로 이동**. Phase 4는 대화 조회 화면 중심 | Phase 2는 업로드 API 없이 시작 시 `knowledge/base/` 자동 인덱싱(CLI)만 하고 API는 Phase 4 |
| R3 | 누락 | CHAT-07 | "세션 종료" 정의 없음 | 4절 정의 채택 (명시 종료=즉시 무효, 유휴 30분/절대 24시간 만료, 브라우저 이탈은 종료로 보지 않음) | 브라우저 `pagehide`에서 `sendBeacon`으로 종료 → 새로고침 시 세션 유실 문제로 비추천 |
| R4 | 누락 | 7절, CHAT-06 | `chat_messages`에 토큰 수 필드 없음, 검색 파라미터(Top-K, 임계값, 검색 질의문) 저장 위치 없음 | `chat_messages`에 `input_tokens/output_tokens`, `retrieval_query`, `retrieval_params(jsonb)` 추가. `llm_usage_records`에 `message_id/evaluation_id` 참조 | 별도 `retrieval_runs` 테이블 (MVP엔 과함) |
| R5 | 누락 | CHAT-01/02 | `chat_sessions`에 토큰 저장 필드 없음 | `access_token_hash`(SHA-256), `last_activity_at`, `client_ip_hash` 추가. 원문 토큰은 저장하지 않음 | — |
| R6 | 모호 | DOC-04/05, 7절 | 문서 "버전"의 의미(재업로드 시 동작)가 없음. 삭제가 soft/hard인지 불명확 | 재업로드 = 새 `documents` 행(버전+1, 같은 `document_key`), 이전 버전은 `deleted_at` 설정. 청크는 **soft delete + 검색 쿼리에서 즉시 제외**, 임베딩 벡터는 보존 기간 후 물리 삭제 | 버전 개념 제거(삭제 후 새로 업로드) — 단순하지만 재현성 추적 약화 |
| R7 | 모순 | 6절, DOC-04 | Spring AI `PgVectorStore`의 기본 테이블(`vector_store`: id/content/metadata json/embedding)은 명세의 `document_chunks`(FK, 버전, 모델명, soft delete)와 맞지 않음 | **자체 `document_chunks` 테이블 + JdbcTemplate 네이티브 SQL로 검색**. Spring AI는 `EmbeddingModel`/`ChatModel`만 사용 (ADR-0005) | Spring AI VectorStore + metadata 필터 — FK·삭제 정합성·스냅샷 관리가 어려움 |
| R8 | 누락 | EVAL-02/03, 5절 | 근거가 없어서 **정당하게 답변을 거부한 경우**의 채점 기준 없음 | 루브릭 v1에 "적절한 거부" 규칙 명시: 근거 없음 + 거부 → Faithfulness/Relevance 만점, Completeness 만점, Citation은 "인용 불필요" 만점. 근거가 있는데 거부 → Completeness·Relevance 감점 | 거부 응답은 평가 대상에서 제외 (데이터 왜곡) |
| R9 | 모호 | ADM-07 | 재평가가 있을 때 평균 점수 산정 기준 없음 | **답변별 최신 COMPLETED 평가 1건만** 평균에 반영. 루브릭 버전별로 분리 표시 | 전체 평가 이력 평균 (재평가가 많은 답변에 가중) |
| R10 | 누락 | EVAL-05 | 중복 요청 정책 미정 | 같은 답변에 `PENDING/RUNNING`이 있으면 **409 + 기존 평가 ID 반환**. COMPLETED/FAILED 후에는 재평가 허용(새 행) | 멱등 키 헤더 |
| R11 | 모순 | 3.3 | "문서당 5개"인데 "범위 밖 질문"은 특정 문서에 속하지 않음 | `document_id`를 **'관련 문서(경계)'**로 재정의하고 `nullable` 허용. `expected_behavior`에 `ANSWER/PARTIAL/REFUSE` 열거값 | 범위 밖 질문을 별도 `oos_*` 묶음으로 분리 (총 40 유지) |
| R12 | 누락 | 9절 테스트 | "40개 골든 질문 회귀 테스트" 실행 방식·무료 한도 소모 미정 | Phase 6에 **수동 실행 CLI/관리자 배치**로 정의. 1회 실행 = 생성 40회(+선택 평가 40회). 기본은 **검색 단계만 자동 회귀**(외부 호출 0), 생성·평가는 수동 | 매 CI마다 실행 — 무료 한도 소모로 비추천 |
| R13 | 누락 | 7절 | `evaluations`에 Judge 원본 응답·입력 스냅샷 없음 → 재현·디버깅 불가 | `raw_response`(크기 제한), `input_snapshot(jsonb)`, `attempt_count` 추가. `evaluation_scores`에 `max_score` 추가 | — |
| R14 | 누락 | ADM-01 | 로그인 시도 제한 저장소 미정 | `admin_users.failed_attempts`, `locked_until` 컬럼 | 메모리 기반(재시작 시 초기화) |
| R15 | 누락 | 9절, 12절 3번 | 보관 기간·파일 최대 크기 미정 | 기본값: 대화 **90일**, 업로드 원본 파일 = 문서 삭제 후 **30일**, 파일 최대 **10 MB**, 세션 유휴 30분/절대 24시간. 전부 설정값 | 대화 30일 (평가 데이터가 빨리 사라짐) |
| R16 | 모호 | 1.2, 9절 | 외부 공개 배포 여부 미정. 약관상 EEA/스위스/영국 사용자 대상 앱은 유료만 허용, 18세 미만 대상 금지 | **MVP는 로컬 전용(localhost 바인딩)**. 공개 배포는 별도 승인 단계로 분리 | 공개 데모 — 지역 제한, 남용, HTTPS, 약관 검토 필요 |
| R17 | 누락 | CHAT-08 | Redis 없이 요청 제한 구현 방법 미정 | 인메모리 토큰 버킷(IP·세션 단위) + **DB 기반 일일 전역 호출 예산**(fail closed) (ADR-0007) | Bucket4j 라이브러리 추가 |
| R18 | 누락 | CHAT-03 | 맥락 절단 정책 수치 없음 | 최근 **6턴(질문+답변 3쌍)**, 대화 맥락 최대 2,000 토큰(문자 수 추정), 질문 최대 1,000자 | 요약 기반 메모리 — 추가 LLM 호출 발생, MVP 제외 |
| R19 | 누락 | 2.1 | Gemini 3.x는 기본적으로 "thinking"을 수행하여 지연·토큰이 증가 | 생성은 thinking 최소/끄기, Judge는 낮은 수준으로 설정값화. Phase 1에서 Spring AI 옵션 노출 여부 확인 | — |
| R20 | 누락 | 3.3, EVAL-07 | 사람 검토 방식 미정(12절 4번) | 오너 1인 검토 + `review_status`(DRAFT/APPROVED) 필드, 승인된 항목만 회귀에 사용 (08 문서) | — |

---

## 4. CHAT-07 "세션 종료" 정의 (확정 제안)

| 종료 유형 | 트리거 | 상태 전이 | 토큰 무효화 시점 |
|---|---|---|---|
| 명시적 종료 | 방문자가 "대화 종료" 클릭 → `POST /api/chat/sessions/{id}/close` | `ACTIVE → CLOSED`, `closed_at` 기록 | **즉시**. 응답에서 쿠키도 삭제(`Max-Age=0`) |
| 유휴 만료 | 마지막 활동(`last_activity_at`) 후 30분(설정값 `chat.session.idle-timeout`) | `ACTIVE → EXPIRED` | **다음 요청 시 판정(lazy)** 하여 즉시 거절 + 5분 주기 스케줄러가 일괄 상태 갱신 |
| 절대 만료 | 생성 후 24시간(설정값 `chat.session.max-lifetime`) | `ACTIVE → EXPIRED` | 위와 동일 |
| 브라우저 이탈/탭 닫기 | 감지하지 않음 | 상태 변화 없음 | 쿠키는 **세션 쿠키**(Max-Age 없음)라 브라우저 종료 시 사라짐. 서버 측은 유휴 만료로 정리 |
| 새 세션 시작 | 같은 브라우저에서 "새 대화" | 이전 세션 `CLOSED` | 새 토큰 쿠키로 덮어쓰기 + 이전 세션 즉시 종료 |

- 종료/만료 세션에 대한 방문자 요청은 **조회·추가 모두 거절**(`410 Gone`, 코드 `SESSION_CLOSED`/`SESSION_EXPIRED`). 토큰 불일치는 존재 여부를 숨기기 위해 `404`.
- 관리자는 상태와 무관하게 조회 가능 (보관 기간 내).
- 토큰: 256비트 난수(Base64URL), DB에는 **SHA-256 해시만** 저장. 쿠키 `HttpOnly; SameSite=Strict; Path=/api/chat; Secure`(HTTPS 환경). 로컬 HTTP 개발에서는 `Secure` 생략을 설정값으로 허용.

---

## 5. MVP 과대 범위 검토

| 항목 | 판단 | 제안 |
|---|---|---|
| PDF 업로드 | 유지 가능 | Spring AI PDF 리더(PDFBox 기반) 사용. 페이지 번호를 `source_locator`로. 스캔 PDF는 "텍스트 없음" FAILED |
| 비동기 평가 | 유지 | 큐/브로커 없이 **DB 상태 + `@Async` 단일 실행기**(동시 1~2). 재시작 시 RUNNING → FAILED(`INTERRUPTED`) 복구 |
| 스트리밍 응답(SSE) | 명세에 없음 | **MVP 제외**. 동기 응답 + 로딩 표시 |
| 대시보드 | 축소 | 수치 카드 + 항목별 평균 + 낮은 점수 목록(표). 차트 라이브러리는 Phase 6에서 필요 시 결정 |
| 완전 로컬 생성/평가(Ollama) | 인터페이스만 | 포트(인터페이스) 설계에 포함, 구현은 **선택 과제**로 후순위. 단 Judge 편향 실험용으로 가치가 커서 Phase 5 선택 항목으로 제안 |
| 다중 지식베이스, 하이브리드 검색(BM25), 리랭커 | 명세 범위 밖 | 제외. 한국어 키워드 검색 보완은 후속 과제로 기록 |
| CI | Phase 1 "초안" | GitHub Actions에서 **외부 호출 없는 테스트만**(단위 + Testcontainers). 비밀값 불필요 |
| 보관 기간 정리 스케줄러 | 유지 | 일 1회 단순 배치 |

**결론:** 기능 범위 자체는 1인 MVP로 수행 가능하다. 다만 R2(단계 순서), R7(벡터 테이블 설계), R12(회귀 테스트 정의)를 승인받아야 일정 지연 위험이 줄어든다.

---

## 6. 명세 12절 미결정 사항별 추천

| # | 항목 | 추천 기본값 | 대안 | 영향 |
|---|---|---|---|---|
| 1 | 실행 환경 | 1절 결과대로 M1 Pro 32GB 로컬 | — | 로컬 임베딩·선택적 로컬 LLM 모두 가능 |
| 2 | Gemini 무료 티어·모델 | 생성 `gemini-3.5-flash-lite` / Judge `gemini-3.6-flash` | 2.5-flash-lite / 3.8-flash | 무료 RPM/RPD는 **오너가 AI Studio에서 확인 필요** |
| 3 | 보관 기간·만료·파일 크기 | 대화 90일, 세션 유휴 30분/절대 24시간, 파일 10MB | 30일/1시간/5MB | 설정값이므로 이후 변경 쉬움 |
| 4 | 골든셋 사람 검토 | 오너 1인 검토, DRAFT→APPROVED | 2인 교차 검토(인력 없음) | 08 문서 참조 |
| 5 | 파일 저장소·공개·HTTPS | Docker 이름 있는 볼륨 `rag-quality-lab_uploads`, **로컬 전용**, HTTPS 없음 | 공개 배포 | 공개 시 약관·지역·남용 대책 재검토 |
| 6 | 동시성·응답 목표 | 측정 기준만 정의: p50/p95 전체 응답시간, 검색·생성 분리 측정. 목표치는 Phase 3 측정 후 확정 | 사전 목표(예: p95 10초) | 무료 티어 지연 변동이 커서 사전 목표는 근거 부족 |
| 7 | 동일 계열 Judge 편향 | 다른 모델 등급 사용 + 골든셋 사람 점수와 일치도 비교 + 선택적 로컬 Judge 교차 검증 | 로컬 Judge 전용 | 07 위험 문서 참조 |
| 8 | Ollama 실행 방식·포트 | **Mac 네이티브**, 기본 `127.0.0.1:11434` 유지 | Docker 컨테이너(CPU 전용) | ADR-0002, ADR-0008 |

---

## 7. 명세 변경 제안 (승인 시 `PROJECT_SPEC.md` v0.4에 반영)

1. 2.1절 모델 후보 교체 (R1)
2. 10절 단계표: 관리자 인증을 Phase 2로 이동 (R2)
3. 4.2절 CHAT-07에 4절 정의 추가 (R3)
4. 7절 데이터 모델: `04_erd.md` 기준으로 갱신 (R4~R6, R13, R14)
5. 5절 루브릭: "적절한 거부" 규칙 추가 (R8)
6. 3.3절 골든셋 필드 정의 보완 (R11, R20)
7. 9절: 회귀 테스트 정의 (R12), 보관 기간 기본값 (R15)

> 명세 파일 자체는 오너 소유 문서이므로 이번 단계에서 **수정하지 않았다**.
