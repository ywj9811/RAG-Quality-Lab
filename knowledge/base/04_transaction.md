---
id: doc-04
title: 트랜잭션, 전파, 롤백
version: 1
written_at: 2026-11-13
applies_to: Spring Framework 7.x, Spring Boot 4.x
sources:
  - https://docs.spring.io/spring-framework/reference/data-access/transaction.html (확인일 2026-11-13, 참고만 함)
---

# 트랜잭션, 전파, 롤백

## 핵심 개념

트랜잭션은 여러 데이터 변경을 하나의 작업 단위로 묶어서 **모두 반영하거나 모두 취소**하게 만드는 장치다. 데이터베이스 트랜잭션의 성질은 흔히 ACID로 요약한다.

- **원자성(Atomicity)**: 전부 성공하거나 전부 실패한다.
- **일관성(Consistency)**: 트랜잭션 전후로 데이터 규칙(제약조건)이 지켜진다.
- **격리성(Isolation)**: 동시에 실행되는 트랜잭션이 서로의 중간 상태를 어느 정도까지 볼 수 있는지 정한다.
- **지속성(Durability)**: 커밋된 결과는 장애가 나도 남는다.

Spring은 JDBC, JPA 등 서로 다른 데이터 접근 기술의 트랜잭션을 `PlatformTransactionManager`라는 공통 추상화로 다룬다. 개발자는 대부분 `@Transactional` 애너테이션으로 선언적으로 사용한다.

## @Transactional의 동작 방식

`@Transactional`을 메서드나 클래스에 붙이면, Spring은 해당 빈을 감싸는 **프록시(proxy)**를 만든다. 외부에서 메서드를 호출하면 프록시가 먼저 받아서 다음 순서로 처리한다.

1. 트랜잭션을 시작하거나 기존 트랜잭션에 참여한다(전파 규칙에 따름).
2. 실제 메서드를 실행한다.
3. 정상 종료면 커밋하고, 롤백 대상 예외가 나오면 롤백한다.

주요 속성은 다음과 같다.

| 속성 | 의미 | 기본값 |
|---|---|---|
| `propagation` | 전파 방식 | `REQUIRED` |
| `isolation` | 격리 수준 | `DEFAULT`(DB 기본값을 따름) |
| `readOnly` | 읽기 전용 힌트 | `false` |
| `timeout` | 제한 시간(초) | 제한 없음(기본 트랜잭션 매니저 설정) |
| `rollbackFor` / `noRollbackFor` | 롤백 규칙 조정 | 아래 롤백 규칙 참고 |

일반적으로 트랜잭션 경계는 **서비스 계층 메서드**에 둔다. 하나의 업무 단위가 여러 리포지토리 호출을 묶어야 하기 때문이다.

## 전파 속성

전파(propagation)는 이미 트랜잭션이 진행 중일 때 새 트랜잭션 메서드를 호출하면 어떻게 할지를 정한다.

| 전파 속성 | 기존 트랜잭션이 있을 때 | 없을 때 |
|---|---|---|
| `REQUIRED` (기본값) | 기존 트랜잭션에 참여 | 새로 시작 |
| `REQUIRES_NEW` | 기존 것을 잠시 보류하고 **별도 트랜잭션** 시작 | 새로 시작 |
| `SUPPORTS` | 참여 | 트랜잭션 없이 실행 |
| `MANDATORY` | 참여 | 예외 발생 |
| `NOT_SUPPORTED` | 기존 것을 보류하고 트랜잭션 없이 실행 | 트랜잭션 없이 실행 |
| `NEVER` | 예외 발생 | 트랜잭션 없이 실행 |
| `NESTED` | 기존 트랜잭션 안에 저장점(savepoint)을 만들어 부분 롤백 가능 | 새로 시작 |

**REQUIRED에서 주의할 점**: 안쪽 메서드와 바깥 메서드가 하나의 물리 트랜잭션을 공유한다. 안쪽 메서드에서 롤백 대상 예외가 발생하면 트랜잭션 전체가 "롤백 전용(rollback-only)"으로 표시된다. 바깥 메서드가 그 예외를 잡아서 정상 종료하려 해도, 커밋 시점에 `UnexpectedRollbackException`이 발생하며 전체가 롤백된다.

**REQUIRES_NEW의 용도**: 바깥 작업의 성공 여부와 관계없이 반드시 남겨야 하는 기록(예: 감사 로그)을 저장할 때 쓴다. 안쪽 트랜잭션은 독립적으로 커밋되거나 롤백된다. 단, 새 트랜잭션은 DB 커넥션을 하나 더 사용하므로 남용하면 커넥션 풀이 고갈될 수 있다.

**NESTED**: 저장점을 지원하는 트랜잭션 매니저(주로 JDBC 기반 `DataSourceTransactionManager`)에서 쓸 수 있다. JPA 트랜잭션 매니저에서는 지원이 제한된다.

## 롤백 규칙

Spring의 기본 롤백 규칙은 다음과 같다.

- **언체크 예외(`RuntimeException`과 그 하위 클래스)와 `Error`**: 롤백한다.
- **체크 예외(`Exception`의 하위 중 `RuntimeException`이 아닌 것, 예: `IOException`)**: 롤백하지 **않고** 커밋한다.

체크 예외에서도 롤백하려면 `@Transactional(rollbackFor = IOException.class)`처럼 지정한다. 반대로 특정 언체크 예외에서 롤백하지 않으려면 `noRollbackFor`를 쓴다.

예외를 메서드 안에서 잡아서 삼켜 버리면 프록시는 예외를 보지 못하므로 커밋한다. 이 경우 롤백이 필요하면 예외를 다시 던지거나, `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`로 직접 롤백 표시를 해야 한다.

## 프록시와 self-invocation 문제

`@Transactional`은 프록시를 통해 동작하므로, **같은 클래스 안에서 자기 메서드를 직접 호출(self-invocation)**하면 프록시를 거치지 않는다. 그 결과 호출된 메서드의 `@Transactional` 설정이 적용되지 않는다.

```java
@Service
class ReportService {
    public void runAll() {
        saveAudit();              // this.saveAudit() → 프록시를 거치지 않음
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAudit() { /* REQUIRES_NEW가 적용되지 않는다 */ }
}
```

해결 방법은 다음과 같다.

- 트랜잭션 설정이 다른 메서드를 **별도 빈(클래스)**으로 분리하고 주입받아 호출한다. 가장 흔하고 명확한 방법이다.
- `TransactionTemplate`으로 코드에서 직접 트랜잭션 범위를 지정한다.

같은 이유로, 기본 프록시 방식에서는 `private` 메서드에 붙인 `@Transactional`도 적용되지 않는다. 프록시가 외부에서 호출할 수 있는 메서드만 가로챌 수 있기 때문이다.

## 읽기 전용 트랜잭션

`@Transactional(readOnly = true)`는 조회 전용 작업이라는 힌트다.

- JPA(Hibernate)에서는 플러시를 하지 않도록 설정되어, 변경 감지 비용을 줄이고 실수로 데이터가 바뀌는 것을 막는 데 도움이 된다.
- JDBC 드라이버나 DB에 따라 읽기 전용 커넥션 최적화가 적용되거나, 복제본(replica)으로 라우팅하는 기준으로 쓰이기도 한다.
- 읽기 전용이라고 해서 모든 DB가 쓰기를 강제로 막는 것은 아니다. 이는 힌트이며 실제 효과는 기술 스택에 따라 다르다.

## 격리 수준 개요

격리 수준은 동시성 문제를 얼마나 막을지 정한다. 낮을수록 동시 처리량이 좋고, 높을수록 일관성이 강하다.

| 격리 수준 | 막는 현상 |
|---|---|
| READ UNCOMMITTED | 거의 없음 (다른 트랜잭션의 미커밋 데이터를 읽을 수 있음) |
| READ COMMITTED | 더티 리드 |
| REPEATABLE READ | 더티 리드, 반복 불가능한 읽기 |
| SERIALIZABLE | 위 현상과 팬텀 리드까지 |

`Isolation.DEFAULT`는 DB의 기본값을 그대로 쓴다는 뜻이다. 기본값은 DB마다 다르다(예: PostgreSQL은 READ COMMITTED).

## 자주 하는 실수

- 같은 클래스 안의 메서드를 호출하면서 `@Transactional` 설정이 적용되기를 기대하는 것.
- `private` 메서드에 `@Transactional`을 붙이는 것.
- 체크 예외가 발생했는데 자동으로 롤백될 것이라고 생각하는 것.
- REQUIRED로 묶인 안쪽 메서드의 예외를 바깥에서 잡고 정상 커밋을 기대하는 것.
- 트랜잭션 안에서 외부 API 호출 등 오래 걸리는 작업을 해서 DB 커넥션과 잠금을 오래 잡는 것.

## 답변 가능 범위

- ACID와 Spring 트랜잭션 추상화의 개요
- `@Transactional`의 프록시 동작 방식과 주요 속성
- 전파 속성별 동작(REQUIRED, REQUIRES_NEW, NESTED 등)과 rollback-only 표시
- 기본 롤백 규칙(언체크 예외와 체크 예외의 차이)과 `rollbackFor`
- self-invocation 문제와 해결 방법, private 메서드 제약
- 읽기 전용 트랜잭션의 의미, 격리 수준 개요

## 답변 불가 범위

- XA·JTA 기반 분산 트랜잭션, 2단계 커밋
- 사가(Saga) 패턴 등 마이크로서비스 간 트랜잭션 설계
- 특정 DB 엔진의 격리 수준 내부 구현(MVCC 세부 동작)
- 리액티브 트랜잭션(`TransactionalOperator`)
