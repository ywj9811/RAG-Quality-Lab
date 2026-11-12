# ADR-0008: 호스트 포트 배정과 Docker Compose 격리

- 상태: 제안됨 (2026-11-12)
- 관련: 명세 2.3, 9절(로컬 환경 안전), CLAUDE.md 5절, RK-13

## 배경
- 오너 Mac에는 무관한 컨테이너(`mysql:latest` 2개, `redis:latest` 1개, 모두 Exited)와 네이티브 프로세스(redis 6379, mongod 27017, AirPlay 5000/7000, epmd 4369)가 있다.

## 결정 (제안)
| 서비스 | 호스트 바인딩 | 컨테이너 포트 |
|---|---|---|
| PostgreSQL+pgvector (`db`) | `127.0.0.1:15432` | 5432 |
| Spring Boot API (`api`, 데모 모드) | `127.0.0.1:18080` | 8080 |
| Web (`web`, 데모 모드) / Vite dev 서버 | `127.0.0.1:15173` | 80 / — |
| Ollama (네이티브) | `127.0.0.1:11434` (기본값) | — |

- `compose.yaml` 최상위 `name: rag-quality-lab` → 볼륨 `rag-quality-lab_pgdata`, `rag-quality-lab_uploads`, 네트워크 `rag-quality-lab_default`.
- 작업 명령은 항상 `docker compose -p rag-quality-lab …` 범위로 한정. `prune`, `rm -f`, 볼륨 일괄 삭제 금지.
- 포트는 `.env.example`로 변경 가능(`RQL_DB_PORT` 등), README에 표 기재.
- 이미지 태그 고정(`latest` 금지).

## 결과
- 모든 포트가 기본 포트와 겹치지 않고 LAN에 노출되지 않음.
