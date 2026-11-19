# RAG Quality Lab

관리자가 등록한 문서를 근거로 답하는 RAG 챗봇과, 개별 답변을 LLM-as-a-Judge로 평가하는 웹 플랫폼입니다.
요구사항은 [`PROJECT_SPEC.md`](PROJECT_SPEC.md), 설계와 결정 근거는 [`docs/phase0/`](docs/phase0/)와 [`docs/adr/`](docs/adr/)에 있습니다.

> **개인정보·회사 기밀을 넣지 마세요.** 생성·평가는 Gemini 무료 티어를 쓰며, 무료 티어 입력·출력은 Google 제품 개선에 사용되고 사람 검토자가 볼 수 있습니다.

## 구성

| 구성요소 | 버전 | 실행 위치 |
|---|---|---|
| 백엔드 | Java 21 · Spring Boot 4.1.2 · Spring AI 2.0.2 | 호스트(개발) 또는 컨테이너(데모) |
| 프론트엔드 | React 19 · Vite 8 · Node 24 LTS | 호스트(개발) 또는 컨테이너(데모) |
| DB | PostgreSQL 17 + pgvector 0.8.7 | 컨테이너 |
| 임베딩 | Ollama + `qwen3-embedding:0.6b` | **호스트 네이티브** (Mac GPU 사용, ADR-0002) |
| 생성 LLM | Gemini `gemini-3.5-flash-lite` (무료 티어) | 외부 API |

## 포트 (모두 127.0.0.1에만 열림, ADR-0008)

| 서비스 | 호스트 포트 | 변경 변수 |
|---|---|---|
| PostgreSQL | 15432 | `RQL_DB_PORT` |
| API | 18080 | `RQL_API_PORT` |
| 웹(데모) / Vite 개발 서버 | 15173 | `RQL_WEB_PORT` |
| Ollama (네이티브 기본값) | 11434 | — |

Compose 프로젝트명은 `rag-quality-lab`입니다. 이 프로젝트의 컨테이너·볼륨은 모두 `rag-quality-lab` 접두어가 붙습니다.

## 처음 준비

1. **환경 파일**: `cp .env.example .env` 후 `RQL_DB_PASSWORD`를 임의 문자열로 바꿉니다. `.env`는 커밋되지 않습니다.
2. **Ollama** (macOS 14 이상): GitHub 릴리스 또는 ollama.com의 `Ollama.app`을 `/Applications`에 설치하고 실행합니다.
   ```bash
   # CLI 링크를 만들지 않았다면 앱 내부 경로를 사용
   /Applications/Ollama.app/Contents/Resources/ollama pull qwen3-embedding:0.6b
   ```
   바인딩은 기본값(`127.0.0.1:11434`)을 유지합니다. `OLLAMA_HOST=0.0.0.0`은 설정하지 마세요(LAN 노출).
3. **Gemini 키**(선택): 결제가 연결되지 않은 Google 프로젝트의 키를 `.env`의 `GEMINI_API_KEY`에 넣고 `RQL_CHAT_PROVIDER=google-genai`로 바꿉니다. 키가 없으면 `none`으로 두세요. Spring AI는 키가 비어 있으면 기동에 실패합니다.
4. **JDK**: 별도 설치가 필요 없습니다. Gradle toolchain이 Apple Silicon용 Temurin 21을 `~/.gradle/jdks`에 자동으로 받습니다. Gradle 실행용 JVM(Java 17 이상)만 있으면 됩니다.
5. **IDE**: 저장소 루트를 그대로 열면 됩니다. 루트 `settings.gradle.kts`가 `backend`를 포함(composite build)하므로 IntelliJ가 Gradle 프로젝트로 자동 인식합니다. 이미 열어 둔 상태라면 Gradle 창에서 새로고침(Reload)하세요.
6. **Node 24 LTS**: `frontend/.nvmrc` 기준. Node 23 등 지원 종료 버전은 의존성(Vitest, jsdom)이 지원하지 않아 `npm install`이 실패하도록 막아 두었습니다.

## 관리자 계정

- 관리자 계정이 하나도 없을 때, 기동 시 `.env`의 `ADMIN_INITIAL_USERNAME`·`ADMIN_INITIAL_PASSWORD`(12자 이상)로 초기 관리자 1명을 만듭니다. 계정이 생긴 뒤에는 이 값을 바꿔도 반영되지 않습니다.
- 로그인 화면: `http://127.0.0.1:15173/admin/login`
- 5회 연속 실패 시 15분간 잠깁니다(`app.admin.*` 설정). 세션은 30분 동안 활동이 없으면 만료됩니다.

## 실행

### 개발 모드 (DB만 컨테이너)
```bash
docker compose up -d                       # db
cd backend && ./gradlew bootRun            # http://127.0.0.1:18080
cd frontend && npm install && npm run dev  # http://127.0.0.1:15173
```

### 데모 모드 (전체 컨테이너)
```bash
docker compose --profile app up -d --build # http://127.0.0.1:15173
```
API 컨테이너는 `host.docker.internal:11434`로 호스트의 Ollama에 접근합니다. 개발 모드와 데모 모드는 15173·18080 포트를 함께 쓰므로 동시에 띄우지 않습니다.

### 상태 확인
```bash
curl -s http://127.0.0.1:18080/actuator/health
```
`db`, `ollama` 구성요소 상태가 표시됩니다(상세 정보는 비공개). Gemini는 무료 한도 보호를 위해 헬스체크에서 호출하지 않습니다.

### 종료·정리 (이 프로젝트 리소스만)
```bash
docker compose --profile app down          # 컨테이너 중지·삭제 (데이터 볼륨 유지)
docker compose --profile app down -v       # 이 프로젝트 볼륨까지 삭제 (DB 데이터 소실)
```
`docker system prune`, `docker volume prune` 같은 전역 명령은 다른 프로젝트 리소스까지 지우므로 쓰지 않습니다.

## 테스트

| 명령 | 내용 | 필요 조건 |
|---|---|---|
| `cd backend && ./gradlew test` | 단위 + 통합(Testcontainers pgvector) | Docker |
| `cd backend && ./gradlew ollamaTest` | 실제 Ollama 임베딩 확인 + 골든셋 검색 회귀(Recall@5·MRR, 결과 `backend/build/reports/retrieval-regression.json`) | Ollama 실행 + 모델 pull |
| `cd backend && ./gradlew externalTest` | Gemini 실제 호출 (수동 전용, 무료 한도 소모) | `GEMINI_API_KEY` |
| `cd frontend && npm test` | 프론트 단위 테스트 | Node 24 |
| `cd frontend && npm run typecheck && npm run build` | 타입 검사, 빌드 | Node 24 |

CI(GitHub Actions)는 `test`와 프론트 검사만 실행하며 비밀값을 쓰지 않습니다.

## 설정

주요 값은 `backend/src/main/resources/application.yml`과 `.env.example`에 있습니다.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `RQL_API_BIND` | `127.0.0.1` | API 바인딩 주소 (컨테이너에서는 `0.0.0.0`) |
| `RQL_CHAT_PROVIDER` | `google-genai` (`.env.example`·Compose는 `none`) | 채팅 모델 공급자. 키가 없으면 `none` |
| `RQL_GENERATION_MODEL` | `gemini-3.5-flash-lite` | 생성 모델 ID (ADR-0004) |
| `RQL_EMBEDDING_MODEL` | `qwen3-embedding:0.6b` | 임베딩 모델 (ADR-0003) |
| `RQL_EMBEDDING_QUERY_INSTRUCTION` | (빈 값) | 질의 임베딩 앞에 붙이는 instruction. Phase 2 측정 결과 빈 값이 더 좋음 (ADR-0003) |
| `RQL_UPLOAD_DIR` | `../data/uploads` (컨테이너: `/data/uploads`) | 업로드 원본 저장 위치 |
| `app.document.*` | 10MB, 청크 800자/겹침 120자, 처리 스레드 1 | 업로드·청크 설정 |
| `app.retrieval.top-k` | `5` | 기본 검색 개수 |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | 호스트 실행 시 Ollama 주소 |
| `app.ollama.health-timeout` | `2s` | Ollama 헬스체크 대기 상한 |

Ollama 모델 자동 다운로드는 꺼져 있습니다(`pull-model-strategy: never`).
