# ADR-0004: 생성 모델과 Judge 모델 선정

- 상태: 제안됨 (2026-11-12)
- 관련: 명세 2.1, EVAL-01, 12절 2·7번, RK-01, RK-05

## 배경
- 명세 후보: 생성 `gemini-2.5-flash-lite`, Judge `gemini-2.5-flash`.
- 공식 문서 확인 결과 `gemini-2.5-flash`는 **deprecated**, 2026-12-01부터 `gemini-3.5-flash-lite`로 자동 라우팅. `gemini-2.5-flash-lite`는 계속 제공.
- 무료 티어 있음 + Stable: `gemini-3.1-flash-lite`, `gemini-3.5-flash-lite`, `gemini-3.6-flash`. `gemini-3.8-flash`는 "New".
- 모두 무료 티어 데이터는 제품 개선에 사용됨.

## 결정 (제안)
| 역할 | 모델 | 이유 |
|---|---|---|
| 생성 | `gemini-3.5-flash-lite` | Stable, 저지연·경량, 구조화 출력·thinking 지원 |
| Judge | `gemini-3.6-flash` | Stable, 생성보다 상위 등급(판단 품질), **생성과 다른 모델** |

- 모델 ID는 `app.llm.generation.model` / `app.llm.judge.model` 설정값. 기동 시 허용 목록 검증(무료 티어 확인된 모델만).
- 응답 메타데이터의 실제 모델 버전을 기록(자동 라우팅 감지).
- Judge는 provider-native 구조화 출력(JSON 스키마) 사용 + 서버 측 재검증.
- Thinking 수준은 설정값(생성: 최소, Judge: 낮음). Spring AI 옵션 노출 여부는 Phase 1 확인.

## 대안
- 생성 `gemini-2.5-flash-lite`: 중단 예정 없다고 명시되어 있으나 구세대.
- Judge `gemini-3.8-flash`: 최신이지만 Stable 라벨 없음.
- 로컬 Ollama Judge(다른 계열): 편향 완화에 유리, 품질·속도 검증 필요 → Phase 5 선택 과제.

## 결과
- 무료 RPM/RPD 수치는 오너가 AI Studio에서 확인해야 확정 가능.
- 같은 Gemini 계열이므로 평가 편향이 남는다 → RK-01 대응책으로 완화·문서화.

## 출처 (확인일 2026-11-12)
- https://aistudio.google.com/docs/pricing
- https://aistudio.google.com/docs/models
- https://aistudio.google.com/docs/models/gemini-3.5-flash-lite
- https://aistudio.google.com/docs/models/gemini-3.6-flash
- https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html
- https://docs.spring.io/spring-ai/reference/api/structured-output/native.html
