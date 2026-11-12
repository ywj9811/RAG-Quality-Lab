# 01. 비용·가용성 검증표

- 확인일: **2026-11-12** (모든 출처 공통)
- 방법: 공식 문서 웹 조회만 수행. **계정 로그인·API 키 발급·API 호출은 하지 않았다.**
- 주의: 무료 조건은 계정·지역·시점에 따라 바뀐다. Phase 1 착수 직전과 모델 변경 시 재확인한다.

## 1. Gemini API 무료 티어

### 1.1 모델별 무료 여부 (Standard 요금표 기준)

| 모델 ID | 상태 | 무료 티어 | 무료 티어 데이터 제품 개선 사용 | 비고 |
|---|---|---|---|---|
| `gemini-2.5-flash` | **Deprecated**, AI Studio 선택 불가 | 있음 | 예 | **2026-12-01부터 `gemini-3.5-flash-lite`로 자동 라우팅** → 명세의 Judge 후보로 부적합 |
| `gemini-2.5-flash-lite` | 지원 중단 예정 없음("until further notice") | 있음 | 예 | 명세의 생성 후보. 계속 사용 가능 |
| `gemini-2.5-pro` | Deprecated | 있음 | 예 | 2026-12-01부터 `gemini-3.8-flash`로 라우팅 |
| `gemini-3.1-flash-lite` | Stable | 있음 | 예 | |
| `gemini-3.5-flash-lite` | Stable (2026-07 갱신) | 있음 | 예 | 입력 1,048,576 / 출력 65,536 토큰, Structured outputs·Thinking 지원 |
| `gemini-3.6-flash` | Stable (2026-07 갱신), "이전 세대지만 프로덕션 사용 가능" | 있음 | 예 | 입력 1,048,576 / 출력 65,536 토큰, Structured outputs·Thinking 지원 |
| `gemini-3.8-flash` | "New"(안정 라벨 없음) | 있음 | 예 | 최신 Flash |
| `gemini-3.1-pro-preview`, `gemini-4-*` | — | **없음** | — | 사용 금지 (유료 전용) |
| `gemini-embedding-001`, `gemini-embedding-2` | — | 있음 | 예 | 로컬 임베딩 사용 방침이므로 미사용 |

- 참고: 2027-01-01부터 일부 3.x 계열 **유료 단가 인상** 공지가 있다. 무료 티어만 쓰면 직접 영향은 없으나, 유료 전환 금지 원칙을 재확인하는 근거다.
- 출처: https://aistudio.google.com/docs/pricing (구 `ai.google.dev/gemini-api/docs/pricing`에서 301 이동), https://aistudio.google.com/docs/models , https://aistudio.google.com/docs/models/gemini-3.5-flash-lite , https://aistudio.google.com/docs/models/gemini-3.6-flash

### 1.2 사용량 제한(Rate limit)

| 항목 | 확인 내용 |
|---|---|
| 모델별 무료 RPM/TPM/RPD | **공식 문서 페이지에 수치 미공개.** "AI Studio에서 확인"하도록 안내 (https://aistudio.google.com/rate-limit, 로그인 필요) |
| 적용 단위 | **API 키가 아니라 프로젝트 단위** |
| RPD 초기화 | **태평양 시간 자정** (한국 시간 오후 4시 또는 5시, 서머타임에 따라 다름) |
| 출처 | https://aistudio.google.com/docs/rate-limits |

> **오너 확인 필요:** 계정 로그인은 금지되어 있어 Claude Code가 수치를 확인할 수 없다. 오너가 AI Studio rate-limit 페이지에서 후보 모델 2~3개의 무료 RPM/RPD를 확인해 알려주면, 앱의 일일 예산 기본값을 **확인된 RPD의 50~70%**로 설정한다 (평가·골든셋 실행 여유분 확보).

### 1.3 약관·데이터 사용 조건

| 항목 | 내용 | 프로젝트 대응 |
|---|---|---|
| 무료(Unpaid) 서비스 데이터 | 프롬프트·응답 등이 Google 제품·ML 기술 개선에 사용될 수 있음. **사람 검토자가 입력/출력을 읽고 주석할 수 있음**(계정과 분리 후) | 공개·비민감 문서만 사용, UI에 고지 |
| 민감정보 | "민감·기밀·개인 정보를 무료 서비스에 제출하지 말 것" | 질문 입력창 경고 문구, 로그 마스킹 |
| 연령 | 18세 이상만 사용. 18세 미만 대상/접근 가능성이 높은 앱 제공 금지 | 로컬 데모 전용. 공개 시 이용 안내 필요 |
| 지역 | 한국은 이용 가능 지역에 포함. **EEA·스위스·영국 사용자에게 제공하는 앱은 유료 서비스만 허용** | 공개 배포 금지(MVP 로컬 전용) |
| 약관 시행일 | 2026-03-23 | |
| 출처 | https://aistudio.google.com/docs/terms , https://aistudio.google.com/docs/available-regions |

### 1.4 비용 0원 유지 장치

- **결제 수단이 연결되지 않은 프로젝트의 API 키만 사용**(오너가 발급). 결제 연결 시 같은 키가 유료로 전환될 수 있으므로 README에 경고.
- 앱 측: 일일 호출 예산(DB 집계) 초과 시 fail closed, 429 응답 시 재시도 최대 1회 + 지수 백오프, 최대 출력 토큰 상한, 타임아웃.
- 유료 전용 모델 ID를 설정하지 못하도록 **허용 모델 목록(allowlist)** 검증을 기동 시 수행.

## 2. 로컬 임베딩: Ollama + Qwen3-Embedding-0.6B

| 항목 | 값 | 출처 |
|---|---|---|
| Ollama 태그 | `qwen3-embedding:0.6b` (639 MB, 컨텍스트 32K) / `4b` 2.5 GB / `8b` 4.7 GB | https://ollama.com/library/qwen3-embedding |
| 임베딩 차원 | 0.6B: **최대 1024**, MRL로 32~1024 사용자 지정 | https://huggingface.co/Qwen/Qwen3-Embedding-0.6B |
| 최대 시퀀스 | 32K 토큰 | 위 HF 페이지 |
| 질의 instruction | 질의에 `Instruct: {작업 설명}\nQuery:{질의}` 형식 권장, 문서에는 불필요. 다국어 환경에서도 **instruction은 영어로 작성 권장** | 위 HF 페이지 |
| 다국어 | 100개 이상 언어 + 프로그래밍 언어 | 위 HF 페이지 |
| MTEB Multilingual 평균 | 0.6B **64.33** / 4B 69.45 / 8B 70.58 | 위 HF 페이지 |
| 라이선스 | Apache-2.0 | 위 HF 페이지 |
| Ollama `/api/embed` | `dimensions` 파라미터 지원, `truncate` 기본 true. 정규화 여부는 문서에 미기재 → **앱에서 L2 정규화 확인** | https://docs.ollama.com/api/embed |
| 비용 | 0원 (로컬 실행, 데이터 외부 전송 없음) | https://docs.ollama.com/faq |

**한국어 검색 성능 판단:** 공식 자료에는 한국어 단독 점수가 없다. 다국어 평균 기준 0.6B는 8B보다 약 6점 낮다. 따라서 **Phase 2에서 골든 질문 40개로 Recall@5/MRR을 직접 측정**하고, 기준 미달(예: Recall@5 < 0.8) 시 `4b`로 전환을 제안한다. 4B도 1024차원 초과 가능성이 있으면 MRL로 1024에 맞춰 스키마 변경을 피한다. (4B의 기본 차원은 Ollama 페이지에 미기재 — 전환 시 재확인)

## 3. Ollama 실행 방식

| 항목 | 내용 | 출처 |
|---|---|---|
| macOS 요구사항 | macOS 14 Sonoma 이상, Apple M 시리즈는 CPU+GPU 지원 | https://docs.ollama.com/macos |
| 설치 | `ollama.dmg` → Applications 드래그, CLI 링크 생성 제안 | 위와 동일 |
| 모델 저장 | `~/.ollama` | 위와 동일 |
| 기본 바인딩 | `127.0.0.1:11434` | https://docs.ollama.com/faq |
| Docker GPU | **macOS Docker Desktop은 GPU 패스스루가 없어 GPU 가속 불가** | https://docs.ollama.com/faq |

→ 오너 환경(macOS 14.6.1, M1 Pro)은 네이티브 요구사항을 충족한다. 상세는 ADR-0002.

## 4. Spring AI 연동 가용성

| 항목 | 확인 내용 | 출처 |
|---|---|---|
| 현재 GA | **2.0.2** (2026-10-23). 프리뷰 2.1.0-RC2 | https://github.com/spring-projects/spring-ai/releases |
| Boot 호환 | "Spring AI 2.0.x는 Spring Boot 4.0.x, 4.1.x 지원" | https://docs.spring.io/spring-ai/reference/getting-started.html |
| Gemini 연동 | `spring-ai-starter-model-google-genai`. `spring.ai.google.genai.api-key` 설정 시 **Gemini Developer API(키 방식)** 사용, Vertex 불필요 | https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html |
| 구조화 출력 | Google GenAI는 provider-native structured output 지원 목록에 포함. 기본 꺼짐, `entity(..., spec -> spec.useProviderStructuredOutput())`로 활성화 | https://docs.spring.io/spring-ai/reference/api/structured-output/native.html |
| Ollama 임베딩 | `spring-ai-starter-model-ollama`, `spring.ai.ollama.base-url`(기본 localhost:11434), `spring.ai.ollama.embedding.model`, 자동 pull 기본 `never` | https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html |
| 차원 옵션 | Spring AI Ollama 임베딩 문서에 `dimensions` 옵션 언급 없음 → **기본 1024 사용**(차원 축소 불필요) | 위와 동일 |
| 공급자 혼합 | 채팅=Google GenAI, 임베딩=Ollama 조합은 문서에 명시적 언급 없음. `spring.ai.model.chat` / `spring.ai.model.embedding` 개별 선택 속성이 존재 → **Phase 1 스파이크에서 기동 검증** | 위 문서들 |

## 5. 종합 판정

| 구성 | 가용성 | 비용 | 판정 |
|---|---|---|---|
| 생성: Gemini 무료 티어 | 가능 (3.5-flash-lite / 2.5-flash-lite) | 0원(무료 한도 내) | ✅ 조건부 (오너의 RPD 확인 필요) |
| 평가: Gemini 무료 티어 | 가능 (3.6-flash), **2.5-flash 불가** | 0원(무료 한도 내) | ✅ 조건부 |
| 임베딩: Ollama Qwen3 0.6B | 가능 | 0원 | ✅ (설치 승인 필요) |
| Spring AI 2.0.2 연동 | 가능 | — | ✅ (공급자 혼합은 Phase 1 스파이크로 검증) |
| pgvector | 가능 (1024 ≤ HNSW 2000 한도) | 0원 | ✅ |
