# 09. 승인 요청 체크리스트

우선순위: **P0** = Phase 1 착수 전 반드시 필요, **P1** = Phase 1 중 필요, **P2** = 해당 Phase 전까지.
각 항목에 ☑(승인) / ✎(수정 후 승인) / ✗(반려)로 답해 주세요.

## P0 — Phase 1 착수 전 필수

| # | 승인 항목 | 추천안 | 대안 | 근거 문서 |
|---|---|---|---|---|
| 1 | **기술 스택·버전** | Java 21 + Spring Boot 4.1.x + Spring AI 2.0.2 + Gradle Wrapper 9.x + PostgreSQL 17 + pgvector 0.8.7 + React/TS/Vite | Boot 3.5 + Spring AI 1.1.8 | ADR-0001, 02 |
| 2 | **LLM 모델 교체** (명세의 `gemini-2.5-flash`는 deprecated) | 생성 `gemini-3.5-flash-lite`, Judge `gemini-3.6-flash` (설정값) | 생성 `gemini-2.5-flash-lite` / Judge `gemini-3.8-flash` | ADR-0004, 01 |
| 3 | **오너 직접 확인 요청:** AI Studio rate-limit 페이지에서 위 모델들의 무료 RPM/RPD 수치 | 수치를 알려주시면 일일 예산 = RPD의 50~70% | 잠정값(생성 200/일, 평가 50/일)으로 시작 | 01 §1.2 |
| 4 | **Ollama Mac 네이티브 설치** (공식 dmg, `/Applications`) + `qwen3-embedding:0.6b` pull(639MB) — 전역 설치 승인 | 네이티브, `127.0.0.1:11434` 유지 | Docker 컨테이너(CPU 전용, 느림) | ADR-0002, ADR-0003 |
| 5 | **임베딩 1024차원 고정**, instruction 접두어 적용 | 1024 | MRL로 768/512 축소 | ADR-0003 |
| 6 | **자체 `document_chunks` 테이블** (Spring AI VectorStore 미사용) | 자체 테이블 + JdbcTemplate | Spring AI PgVectorStore | ADR-0005 |
| 7 | **호스트 포트·Compose 격리**: DB 15432, API 18080, Web 15173, Ollama 11434(네이티브 기본), 모두 127.0.0.1 바인딩, 프로젝트명 `rag-quality-lab` | 제안대로 | 다른 번호 지정 | ADR-0008 |
| 8 | **단계 순서 변경**: 관리자 인증을 Phase 2 앞부분으로 이동 | 이동 | 업로드 API 없이 CLI 인덱싱 | 00 R2 |
| 9 | **arm64 JDK 21 설치** (현재 JBR 21이 x86_64/Rosetta) | Gradle toolchain 자동 프로비저닝(프로젝트 범위, `~/.gradle`에 저장) | 직접 설치(Temurin arm64) / 현 상태 유지 | 00 §1, RK-14 |
| 10 | **gh CLI 설치·인증** (현재 미설치 → 이번 PR은 로컬 커밋만) | 오너가 `brew install gh` + `gh auth login` 직접 수행 | 오너가 수동으로 push/PR | — |

## P1 — Phase 1~3 중 필요

| # | 승인 항목 | 추천안 | 대안 | 근거 |
|---|---|---|---|---|
| 11 | CHAT-07 세션 종료 정의 | 명시 종료 즉시 무효 / 유휴 30분·절대 24시간 만료 / 브라우저 이탈은 종료 아님 | 유휴 1시간 | 00 §4, ADR-0006 |
| 12 | 요청 제한 방식 (Redis 없음) | 인메모리 토큰 버킷 + DB 일일 예산(생성/평가 분리), 의존성 추가 없음 | Bucket4j 추가 | ADR-0007 |
| 13 | 보관 기간·파일 크기 | 대화 90일, 삭제 문서 파일·벡터 30일, 업로드 10MB | 30일 / 5MB | 00 R15, 04 §2 |
| 14 | 외부 공개 여부 | **로컬 전용**(HTTPS 없음). 공개는 별도 단계 | 공개 데모 | 00 R16, RK-16 |
| 15 | Node 버전 | Node 24 LTS (`.nvmrc`), 전역 전환은 오너 판단 | 23.6 유지 | 02 |
| 16 | 데이터 모델 보완 (토큰 해시, 토큰 수, 검색 파라미터, 평가 스냅샷 등) | 04 ERD 채택 | 명세 7절 원안 | 04 |
| 17 | API 계약 (오류 코드, 202+폴링, 409 중복 정책, 404 토큰 불일치) | 05 채택 | — | 05 |
| 18 | 명세 v0.4 갱신 — 승인 결과를 `PROJECT_SPEC.md`에 반영하는 PR을 Claude Code가 작성 | 별도 PR | 오너 직접 수정 | 00 §7 |

## P2 — 해당 Phase 전까지

| # | 승인 항목 | 추천안 | 시점 |
|---|---|---|---|
| 19 | 루브릭 "적절한 거부" 채점 규칙 | 00 R8 | Phase 5 |
| 20 | 대시보드 평균 기준 | 답변별 최신 COMPLETED 1건, 루브릭 버전별 | Phase 6 |
| 21 | 골든셋 구성·검토 절차 (문서당 2/1/1/1, 오너 1인 검토, APPROVED만 사용) | 08 | Phase 2 |
| 22 | 회귀 테스트 정의 (검색 회귀 자동·외부 0회, 생성/Judge 회귀 수동) | 08 §4.5 | Phase 2/6 |
| 23 | Judge 편향 완화 — 로컬 Ollama 보조 Judge를 Phase 5 선택 과제로 포함할지 | 포함(선택) | Phase 5 |
| 24 | 와이어프레임 (06) | 채택 | Phase 3/4 |

---

## 이번 Phase 0에서 하지 않은 것
- 기능 코드, 빌드 파일, Compose 파일, DB 마이그레이션 작성
- 컨테이너·볼륨 생성, 소프트웨어 설치, 외부 API 호출, 계정 로그인
- `PROJECT_SPEC.md`·`CLAUDE.md` 수정
