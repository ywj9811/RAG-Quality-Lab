---
id: doc-03
title: Entity, Repository, JPA
version: 1
written_at: 2026-11-13
applies_to: Spring Boot 4.x, Spring Data JPA, Jakarta Persistence 3.x, Hibernate 7.x
sources:
  - https://spring.io/guides/gs/accessing-data-jpa/ (확인일 2026-11-13, 참고만 함)
---

# Entity, Repository, JPA

## 핵심 개념

- **JPA(Jakarta Persistence)**: 자바 객체와 관계형 데이터베이스 테이블을 연결하는 표준 명세다. 명세이므로 실제 동작은 구현체가 담당하며, 가장 널리 쓰이는 구현체가 **Hibernate**다.
- **엔티티(Entity)**: 테이블의 행과 대응하는 자바 클래스다.
- **Spring Data JPA**: JPA 위에서 반복적인 저장소 코드를 줄여 주는 Spring 프로젝트다. 인터페이스만 선언하면 기본 CRUD 구현을 실행 시점에 만들어 준다.

세 가지는 층위가 다르다. Spring Data JPA가 JPA를 사용하고, JPA의 실제 처리는 Hibernate가 한다.

## 엔티티 매핑

엔티티 클래스에는 `@Entity`를 붙이고, 기본 키 필드에 `@Id`를 붙인다.

- `@Table(name = "...")`: 테이블 이름이 클래스 이름과 다를 때 지정한다.
- `@Column`: 컬럼 이름, NULL 허용 여부, 길이 등을 지정한다.
- `@GeneratedValue`: 기본 키 생성 방식을 정한다. `IDENTITY`는 DB의 자동 증가 컬럼을, `SEQUENCE`는 DB 시퀀스를 사용한다. UUID를 키로 쓰는 방법도 있다.
- `@Enumerated(EnumType.STRING)`: 열거형을 이름 문자열로 저장한다. 기본값인 `ORDINAL`은 순서 번호를 저장하므로 열거형 순서를 바꾸면 기존 데이터의 의미가 틀어진다.
- JPA는 엔티티를 만들 때 **인자 없는 생성자**가 필요하다. 외부에서 쓰지 않도록 `protected`로 두는 경우가 많다.

운영 환경에서는 Hibernate가 테이블을 자동으로 만들게(`ddl-auto=create`, `update`) 두기보다, Flyway 같은 마이그레이션 도구로 스키마를 관리하고 `ddl-auto=validate`로 엔티티와 스키마가 맞는지만 확인하는 방식이 안전하다.

## 연관관계 매핑

테이블 사이의 외래 키 관계를 객체 참조로 표현한다.

| 애너테이션 | 관계 | 기본 로딩 방식 |
|---|---|---|
| `@ManyToOne` | 다대일 (예: 주문 여러 개 → 회원 한 명) | 즉시(EAGER) |
| `@OneToOne` | 일대일 | 즉시(EAGER) |
| `@OneToMany` | 일대다 (예: 회원 한 명 → 주문 여러 개) | 지연(LAZY) |
| `@ManyToMany` | 다대다 | 지연(LAZY) |

- 외래 키를 가진 쪽이 **연관관계의 주인**이다. 다대일 관계라면 보통 `@ManyToOne` 쪽이 주인이며 `@JoinColumn`으로 외래 키 컬럼을 지정한다.
- 반대쪽 `@OneToMany(mappedBy = "...")`는 읽기 전용 거울과 같다. 이쪽 컬렉션만 바꿔서는 외래 키가 저장되지 않는다.
- `@ManyToOne`의 기본값이 즉시 로딩이라 예상치 못한 조회가 늘어나기 쉽다. 실무에서는 `fetch = FetchType.LAZY`로 바꾸고 필요할 때만 함께 조회하는 방식을 많이 쓴다.
- `@ManyToMany`는 중간 테이블에 추가 컬럼을 둘 수 없어서, 중간 엔티티를 직접 만들어 일대다·다대일 두 관계로 푸는 경우가 많다.

## Repository와 쿼리 메서드

`JpaRepository<엔티티, ID타입>`을 상속한 인터페이스를 선언하면 `save`, `findById`, `findAll`, `deleteById`, `count` 같은 메서드를 바로 쓸 수 있다.

**메서드 이름으로 쿼리 만들기**: 정해진 규칙으로 메서드 이름을 지으면 Spring Data가 쿼리를 만들어 준다.

- `findByEmail(String email)` → 이메일이 같은 행 조회
- `findByStatusAndCreatedAtAfter(Status s, Instant t)` → 두 조건 AND
- `existsByUsername(String username)` → 존재 여부
- `countByStatus(Status s)` → 개수

**직접 쿼리 작성**: 이름이 너무 길어지거나 조인이 필요하면 `@Query`로 JPQL을 작성한다. JPQL은 테이블이 아니라 **엔티티와 필드 이름**을 대상으로 한다. `nativeQuery = true`로 SQL을 직접 쓸 수도 있다. 데이터를 바꾸는 쿼리에는 `@Modifying`을 함께 붙인다.

**페이징**: 메서드 파라미터에 `Pageable`을 받고 `Page<T>`를 반환하면 정렬과 페이지 처리가 함께 된다. `Page`는 전체 개수를 구하는 카운트 쿼리를 추가로 실행한다.

## 영속성 컨텍스트

엔티티 매니저는 트랜잭션 범위 안에서 **영속성 컨텍스트**라는 작업 공간을 관리한다.

- **1차 캐시와 동일성 보장**: 같은 트랜잭션 안에서 같은 ID로 두 번 조회하면 DB를 한 번만 조회하고 같은 객체를 돌려준다.
- **변경 감지(dirty checking)**: 조회한 엔티티의 필드를 바꾸면, 트랜잭션이 끝날 때 바뀐 내용을 찾아 UPDATE를 실행한다. 그래서 수정할 때 `save`를 다시 부르지 않아도 된다.
- **쓰기 지연**: INSERT, UPDATE를 모아 두었다가 플러시(flush) 시점에 DB로 보낸다. 플러시는 보통 트랜잭션 커밋 직전이나 JPQL 실행 전에 일어난다.
- 영속성 컨텍스트가 닫힌 뒤(트랜잭션 밖)에 지연 로딩 필드를 처음 읽으면 `LazyInitializationException`이 발생한다.

Spring Boot는 기본적으로 OSIV(open-session-in-view)를 켜 두어 화면 렌더링까지 영속성 컨텍스트를 유지한다. 편리하지만 DB 커넥션을 오래 잡고 컨트롤러에서 의도치 않은 쿼리가 실행될 수 있어, API 서버에서는 `spring.jpa.open-in-view=false`로 끄는 경우가 많다.

## 지연 로딩과 N+1 문제

N+1 문제는 목록 1번 조회 후, 각 항목의 연관 엔티티를 읽을 때마다 추가 쿼리가 1번씩 실행되어 총 N+1번의 쿼리가 나가는 현상이다. 예를 들어 주문 100건을 조회한 뒤 각 주문의 회원 이름을 출력하면, 회원 조회 쿼리가 최대 100번 더 실행될 수 있다.

해결 방법은 다음과 같다.

- **페치 조인(fetch join)**: JPQL에서 `join fetch`로 연관 엔티티를 한 번에 조회한다. 컬렉션을 페치 조인하면 결과 행이 늘어나고 페이징과 함께 쓰기 어렵다는 제약이 있다.
- **@EntityGraph**: 리포지토리 메서드에 함께 불러올 연관 필드를 선언한다.
- **배치 크기 설정**: Hibernate의 `default_batch_fetch_size` 등을 설정하면 지연 로딩을 IN 쿼리로 묶어 쿼리 수를 줄인다.
- **DTO 조회**: 필요한 필드만 골라 조회하는 쿼리를 따로 작성한다.

즉시 로딩(EAGER)으로 바꾸는 것은 해결책이 아니다. JPQL로 목록을 조회하면 즉시 로딩 필드도 결국 별도 쿼리로 채워져 N+1이 그대로 발생할 수 있다.

## 예시

이 문서를 위해 작성한 예시다.

```java
@Entity
@Table(name = "orders")
class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    protected Order() { }
}

interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("select o from Order o join fetch o.member where o.status = :status")
    List<Order> findWithMemberByStatus(OrderStatus status);
}
```

## 자주 하는 실수

- `EnumType.ORDINAL`(기본값)로 열거형을 저장하는 것.
- `mappedBy` 쪽 컬렉션만 바꾸고 외래 키가 저장되기를 기대하는 것.
- 트랜잭션 밖에서 지연 로딩 필드를 읽는 것.
- N+1을 즉시 로딩으로 해결하려는 것.
- 운영 DB에 `ddl-auto=update`를 쓰는 것.

## 답변 가능 범위

- JPA, Hibernate, Spring Data JPA의 관계
- 엔티티 기본 매핑과 기본 키 생성 방식, 열거형 저장 방식
- 연관관계 종류별 기본 로딩 방식과 연관관계의 주인
- 쿼리 메서드 이름 규칙, `@Query`, 페이징
- 영속성 컨텍스트(1차 캐시, 변경 감지, 쓰기 지연)와 OSIV
- N+1 문제의 원인과 해결 방법

## 답변 불가 범위

- Hibernate 2차 캐시 설정과 벤더별 성능 튜닝
- QueryDSL, jOOQ 등 다른 쿼리 도구의 사용법
- 특정 DB 엔진의 실행 계획 분석(07 문서의 일반 원칙 외)
- 낙관적 락·비관적 락의 상세 구현
