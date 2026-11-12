# 02. 버전 호환성 매트릭스

- 확인일: 2026-11-12
- 원칙: 공식 문서로 확인한 값만 "확인"으로 표기. 미확인 값은 Phase 1 착수 시 **배포 후 2주 이상 지난 안정판**으로 고정하고 이 문서를 갱신한다.

## 1. 추천 조합 (후보 A — 추천)

| 구성요소 | 추천 버전 | 근거 | 상태 |
|---|---|---|---|
| Java | **21 (LTS, arm64 빌드)** | Boot 4.1.2는 Java 17~27 지원. 명세 요구 21 | 확인 |
| Spring Boot | **4.1.x** (현재 4.1.2) | 현재 안정판. Spring Framework 7.0.10+ | 확인 |
| Spring AI | **2.0.2** (BOM `org.springframework.ai:spring-ai-bom:2.0.2`) | 2.0.x는 Boot 4.0.x/4.1.x 지원 | 확인 |
| 빌드 | **Gradle Wrapper 9.x** (Kotlin DSL) | Boot 4.1은 Gradle 8.14+ 또는 9.x 필요. 전역 Gradle 8.12.1은 미달 → Wrapper로 해결 | 확인 |
| PostgreSQL | **17** | pgvector는 PG 13+ 지원. 17은 성숙한 안정판 | 확인 |
| pgvector | **0.8.7** (이미지 `pgvector/pgvector:0.8.7-pg17-bookworm` 형식으로 고정) | 설치 문서 기준 최신. `vector` 인덱스 한도 2000차원 | 확인 |
| DB 마이그레이션 | Flyway (Boot BOM 관리 버전) + `flyway-database-postgresql` | 스키마 Git 버전 관리 | Phase 1 확인 |
| Testcontainers | Boot BOM 관리 버전, `pgvector/pgvector` 이미지 사용 | DB 통합 테스트 | Phase 1 확인 |
| Ollama | 설치 시점 최신 안정판 (macOS 14+) | 오너 macOS 14.6.1 충족 | 확인 |
| 임베딩 모델 | `qwen3-embedding:0.6b`, 1024차원 | 01 문서 참조 | 확인 |
| 생성 모델 | `gemini-3.5-flash-lite` (설정값) | Stable, 무료 티어 | 확인 |
| Judge 모델 | `gemini-3.6-flash` (설정값) | Stable, 무료 티어 | 확인 |
| Node.js | **24 LTS** 권장 (현재 23.6.0) | 23은 비 LTS | 승인 필요 |
| 프론트엔드 | React + TypeScript + Vite, React Router, 테스트 Vitest + Testing Library | 단일 앱 역할별 라우트 | Phase 1에 버전 고정 |
| Docker | Desktop 29.4.3 / Compose v5.1.4 (현 설치본) | Compose 파일 `name: rag-quality-lab` | 확인 |

## 2. 대안 조합 (후보 B — 보수적)

| 구성요소 | 버전 | 장점 | 단점 |
|---|---|---|---|
| Spring Boot | 3.5.x | 자료·예제가 많음 | Spring AI 2.0 사용 불가 |
| Spring AI | 1.1.8 (2026-06) | Boot 3.x 계열 호환 | 구버전 라인, 최신 Gemini 3.x 모델 지원·구조화 출력 개선 반영이 늦을 수 있음. 지원 기간이 더 짧을 가능성 |

→ **후보 A 추천.** 신규 프로젝트이므로 레거시 호환 부담이 없고, Spring AI 최신 라인과 공식 지원 조합을 따른다.

## 3. 상호 호환성 확인표

| 조합 | 결과 | 비고 |
|---|---|---|
| Spring AI 2.0.2 ↔ Boot 4.1.x | ✅ | 공식 명시 |
| Boot 4.1.x ↔ Java 21 | ✅ | 17~27 |
| Boot 4.1.x ↔ Gradle 9.x | ✅ | 전역 8.12.1은 ❌ → Wrapper |
| Spring AI Google GenAI ↔ Gemini Developer API 키 | ✅ | `api-key` 설정 시 |
| Spring AI Google GenAI ↔ 네이티브 구조화 출력 | ✅ | 기본 꺼짐, 호출 단위 활성화 |
| Spring AI Ollama ↔ `qwen3-embedding:0.6b` | ✅ (Phase 1 검증 2026-11-12) | Ollama 0.40.13에서 1024차원, L2 노름 1.0(정규화됨). 한국어 관련 문장이 무관 문장보다 가까움 (`OllamaEmbeddingSpikeTests`) |
| Google GenAI(채팅) + Ollama(임베딩) 동시 자동설정 | ✅ (Phase 1 검증) | `spring.ai.model.chat=google-genai`, `spring.ai.model.embedding=ollama`로 ChatModel·EmbeddingModel이 각각 1개만 생성됨. **단, Gemini 키가 비어 있으면 기동 실패** → 키가 없을 때 `RQL_CHAT_PROVIDER=none` |
| 1024차원 ↔ pgvector HNSW | ✅ | 한도 2000 |
| Docker 앱 컨테이너 → 호스트 Ollama | ✅ (Phase 1 검증) | Ollama를 127.0.0.1에만 바인딩한 상태에서 API 컨테이너가 `host.docker.internal:11434`로 접근 가능(Docker Desktop 29.4.3). `OLLAMA_HOST` 변경 불필요 |
| JDK x86_64(Rosetta) ↔ arm64 Docker/Testcontainers | ✅ 해소 | Gradle toolchain(vendor ADOPTIUM)과 daemon JVM 지정으로 Temurin 21.0.13 arm64 자동 사용 |

## 4. Phase 1에서 확인한 추가 사항 (2026-11-12)
| 항목 | 내용 | 대응 |
|---|---|---|
| Node 23 | Vitest 4, jsdom 29·30이 Node 23을 지원 범위에서 제외 | Node 24 LTS(24.22.2) 기준, `engine-strict`로 차단 |
| npm 10.9.2 (Homebrew Node 23 동봉) | 의존성 해석 버그(`Cannot read properties of null (reading 'edgesOut')`)로 설치 실패 | Node 24 동봉 npm 11 사용 |
| 고정 버전 | Gradle 9.8.2(배포본 SHA-256 고정), React 19.3.1, Vite 8.3.5, React Router 8.5.1, TypeScript 7.0.2, Vitest 4.1.12, nginx 1.30.6, Node 24.22.2 이미지 | 배포 후 2주 이상 지난 안정판 |

## 5. 버전 고정 정책
- Gradle: Spring Boot 플러그인 + Spring AI BOM으로 관리, 개별 버전 직접 지정 최소화.
- Docker 이미지: `latest` 금지, 버전 태그 고정.
- 프론트엔드: `package-lock.json` 커밋, 정확한 버전 고정.
- 버전 변경은 ADR 갱신과 함께 PR로 제출.
