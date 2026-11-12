# 08. RAG 지식베이스(8개 문서)와 골든 질문(40개) 계획

- 실제 문서·질문 작성은 **Phase 2**에서 한다. 이 문서는 제작·검증 계획이다.
- 모든 문서·질문·참고 답안은 한국어로 작성(기술 용어 영문 병기 허용).

## 1. 문서 작성 원칙
1. **독자 저술:** 공식 문서를 읽고 이해한 내용을 자체 문장·자체 예제로 작성. 원문 문단·코드 복사 금지. 짧은 용어 정의 인용도 피하고 출처 링크로 대체.
2. **고정 템플릿** (각 문서 공통):
   ```markdown
   ---
   id: doc-04
   title: 트랜잭션, 전파, 롤백
   version: 1
   written_at: 2026-MM-DD
   applies_to: Spring Framework 7.x / Spring Boot 4.x
   sources:
     - https://docs.spring.io/spring-framework/reference/data-access/transaction.html (확인일 YYYY-MM-DD)
   ---
   # 트랜잭션, 전파, 롤백
   ## 핵심 개념
   ## 상세 설명 (소제목 3~6개)
   ## 예시 (자체 작성 코드, 20줄 이내)
   ## 자주 하는 실수
   ## 답변 가능 범위
   ## 답변 불가 범위
   ```
3. **분량:** 문서당 약 4,000~7,000자. 청크 800자 기준 6~10개 청크가 되도록 하여 "복수 청크 필요 질문"을 만들 수 있게 한다.
4. **검색 친화성:** 소제목(`##`)을 의미 단위로 나누어 `source_locator.heading`이 인용에 쓰이게 한다. 한 소제목에 한 주제.
5. **경계 명시:** "답변 불가 범위"는 범위 밖 질문을 설계하는 근거로 쓴다 (예: 04 문서 — "분산 트랜잭션(XA), 특정 DB 엔진의 격리 수준 구현 세부는 다루지 않음").
6. **버전 기준 명시:** `applies_to`에 기준 버전. 버전에 따라 달라지는 내용은 피하거나 명시.
7. **인젝션 테스트 문서는 별도:** 악성 지시문이 든 테스트 문서는 `knowledge/test-fixtures/`에 두고 기본 지식베이스에 포함하지 않는다.

## 2. 문서별 계획

| 파일 | 주요 소제목(안) | 답변 불가 범위(예) | 참고 출처 |
|---|---|---|---|
| 01_rest_api.md | 리소스와 URI, HTTP 메서드 의미·멱등성, 상태 코드 선택, 요청/응답 표현(JSON), 버전 관리 방식 | GraphQL, gRPC, HTTP/3 내부 | spring.io/guides/gs/rest-service, RFC 9110 |
| 02_spring_mvc.md | DispatcherServlet 흐름, `@Controller`/`@RestController`, 요청 매핑, 파라미터 바인딩, 검증(`@Valid`), 응답 변환 | WebFlux, 뷰 템플릿 엔진 상세 | Spring Framework Web MVC 문서 |
| 03_spring_data_jpa.md | Entity 매핑, 연관관계, Repository 메서드 쿼리, 지연 로딩과 N+1, 영속성 컨텍스트 | 특정 벤더 Hibernate 튜닝, QueryDSL | spring.io/guides/gs/accessing-data-jpa |
| 04_transaction.md | `@Transactional`, 전파(REQUIRED/REQUIRES_NEW 등), 롤백 규칙(체크/언체크 예외), 프록시와 self-invocation, 읽기 전용 | XA/분산 트랜잭션 | Spring Framework 트랜잭션 문서 |
| 05_spring_security.md | 인증 vs 인가, 필터 체인, 폼 로그인/세션, 비밀번호 인코딩, CSRF, 메서드 보안 | OAuth2 서버 구축, SAML | spring.io/guides/gs/securing-web |
| 06_exception_handling.md | `@ExceptionHandler`, `@ControllerAdvice`, Problem Details(RFC 9457), 검증 오류 응답, 로깅 원칙 | 프론트엔드 오류 처리 | Spring MVC `@ExceptionHandler` 문서 |
| 07_database_index.md | B-tree 원리, 복합 인덱스 순서, 선택도, 커버링 인덱스, 인덱스가 안 쓰이는 경우, `EXPLAIN` 읽기 | 특정 DB 내부 저장 구조, 샤딩 | PostgreSQL Indexes 문서 |
| 08_caching.md | 캐시 개념·적중률, 캐시 전략(Cache-aside 등), TTL·무효화, Spring Cache 추상화, Redis 자료구조 개요 | Redis 클러스터 운영, 영속화 튜닝 | Redis 공식 문서, Spring Cache 문서 |

> 08_caching.md는 지식베이스 주제일 뿐이며, 이 프로젝트가 Redis를 사용한다는 뜻이 아니다 (명세 2.3).

## 3. 출처·라이선스 검토 방안
1. 문서별 `sources`에 URL과 확인일 기록.
2. Phase 2 착수 시 각 출처의 라이선스/저작권 고지를 확인해 `knowledge/SOURCES.md`에 표로 기록 (출처, 라이선스 표기, 확인일, 사용 방식="참고 후 독자 저술").
3. 라이선스가 불명확하거나 재배포 제한이 있으면 **링크만 남기고 내용은 독자 저술** (명세 3.2 원칙). 이번 계획 자체가 원문 복제를 하지 않으므로 라이선스 리스크는 낮다.
4. 자체 검사: 작성 후 공식 원문과 문장 단위 유사도를 수동 점검(동일 문장 연속 2개 이상 금지).
5. LLM 보조 작성 시: 공개 공식 문서 주제만 다루고, 산출물은 오너 검토 전 DRAFT로 표시.

## 4. 골든 질문 40개

### 4.1 구성 (문서당 5개)

| 유형 | 문서당 | 합계 | `expected_behavior` | 설명 |
|---|---|---|---|---|
| 정상(normal) | 2 | 16 | `ANSWER` | 단일 청크에서 답 가능 |
| 표현 변경(paraphrase) | 1 | 8 | `ANSWER` | 정상 질문 중 하나를 다른 어휘·구어체로 (한국어 동의어·영문 용어 혼용) |
| 복수 청크(multi_chunk) | 1 | 8 | `ANSWER` | 2개 이상 소제목 결합 필요 |
| 범위 밖 / 근거 부족 | 1 | 8 | `REFUSE` 또는 `PARTIAL` | 홀수 문서: 문서의 "답변 불가 범위" 주제(범위 밖), 짝수 문서: 주제는 맞지만 문서에 없는 세부(근거 부족) |

### 4.2 JSONL 스키마

```json
{"id":"gq-04-03","document_id":"doc-04","question":"같은 클래스 안에서 @Transactional 메서드를 호출하면 트랜잭션이 적용되나요?","reference_answer":"기본 프록시 방식에서는 적용되지 않는다. … ","supporting_sections":["doc-04#프록시와 self-invocation"],"expected_behavior":"ANSWER","difficulty":"medium","tags":["paraphrase","proxy"],"review_status":"DRAFT","reviewed_at":null}
```
- 명세 필드 + 추가 제안 필드 `review_status`(DRAFT/APPROVED), `reviewed_at`.
- `document_id`는 "관련 문서" 의미, 범위 밖 질문은 경계를 정의한 문서 ID 또는 `null`.
- `supporting_sections`는 `doc-id#소제목`. 범위 밖이면 `[]`.
- `difficulty`: `easy|medium|hard`. 목표 비율 약 4:4:2.

### 4.3 제작 절차
1. 문서 확정(오너 승인) 후 질문 작성 — 문서가 바뀌면 질문도 재검토.
2. 각 질문마다 `supporting_sections`를 먼저 정하고, 그 소제목 내용만으로 `reference_answer` 작성.
3. **Judge에게 `reference_answer` 비제공** (명세 3.3). 평가 프롬프트·코드와 파일 분리.
4. 자동 검증 스크립트(Phase 2): JSON 스키마, ID 중복, 문서당 5개, 유형 분포, `supporting_sections`가 실제 소제목에 존재하는지.

### 4.4 사람 검토 (12절 4번)
- 검토자: 오너 1인. 체크 항목: ① 질문이 자연스러운 한국어인가 ② 참고 답안이 문서 근거만으로 도출되는가 ③ 기대 동작이 맞는가 ④ 문서와 표현이 지나치게 같아 검색이 쉬워지지 않았는가(paraphrase 유형).
- 승인 시 `review_status=APPROVED`. **APPROVED 항목만 회귀 지표에 사용.**
- 검토 결과는 PR 리뷰로 남긴다.

### 4.5 활용 계획 (회귀 테스트)
| 단계 | 내용 | 외부 호출 |
|---|---|---|
| 검색 회귀 (Phase 2~) | `ANSWER` 질문 32개에 대해 Recall@5, MRR, `REFUSE` 질문의 최고 점수 분포 → 임계값 보정 | **0회** (로컬 임베딩만) |
| 생성 회귀 (Phase 6, 수동) | 40개 질문 답변 생성, `expected_behavior` 일치율(거절해야 할 때 거절했는가) | 생성 40회 |
| Judge 일치도 (Phase 6, 수동·선택) | 오너가 10~12개 답변에 수동 점수 → Judge 점수와 비교 (RK-01) | 평가 10~40회 |

- 잠정 합격 기준(Phase 2 측정 후 확정): 검색 Recall@5 ≥ 0.8, 거절 기대 질문 정확 거절률 ≥ 0.75.
