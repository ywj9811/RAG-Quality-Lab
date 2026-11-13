# ADR-0002: Ollama는 Mac 네이티브로 실행

- 상태: 제안됨 (2026-11-12)
- 관련: 명세 2.3, 12절 8번, CLAUDE.md 5절

## 배경
- 오너 환경: macOS 14.6.1, Apple M1 Pro, 32GB. Ollama 미설치.
- Ollama 공식 FAQ: macOS Docker Desktop은 GPU 패스스루가 없어 **컨테이너에서 GPU 가속 불가**.
- Ollama macOS 요구사항: macOS 14 이상, Apple Silicon은 CPU+GPU 지원.

## 결정 (제안)
- 공식 dmg로 `/Applications`에 설치(오너 승인 후, 오너 또는 Claude Code가 수행).
- 바인딩은 **기본값 `127.0.0.1:11434` 유지**(LAN 노출 방지). `OLLAMA_HOST=0.0.0.0` 설정하지 않음.
- 개발 모드: 호스트에서 실행하는 API가 `http://localhost:11434` 사용.
- 데모 모드(앱 컨테이너): `http://host.docker.internal:11434`. **Phase 1 검증(2026-11-12): 127.0.0.1 바인딩 상태로 접근 가능**(Docker Desktop 29.4.3, Ollama 0.40.13).
- 모델 자동 pull 끔(`spring.ai.ollama.init.pull-model-strategy=never`), 수동 `ollama pull qwen3-embedding:0.6b`.

## 대안
| 대안 | 장점 | 단점 |
|---|---|---|
| Docker 컨테이너 Ollama | Compose로 일괄 관리 | GPU 불가 → 임베딩 속도 저하, VM 메모리(8GB) 압박 |
| 컨테이너 접근 불가 시: API를 항상 호스트에서 실행 | 단순 | 데모 모드에서 Compose 일괄 기동 불가 |
| 컨테이너 접근 불가 시: `OLLAMA_HOST`를 Docker 브리지 대상 주소로 제한 바인딩 | 컨테이너 접근 | 설정 복잡, LAN 노출 위험 검토 필요 → 별도 승인 |

## 결과
- 전역 앱 설치 1건, 모델 저장소 `~/.ollama` 약 0.6GB 사용.
- Compose 기동만으로는 임베딩이 동작하지 않으므로 README와 헬스체크에 Ollama 상태를 표시.

## 출처 (확인일 2026-11-12)
- https://docs.ollama.com/macos
- https://docs.ollama.com/faq
