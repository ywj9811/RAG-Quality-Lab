---
id: doc-05
title: Spring Security 인증과 인가
version: 1
written_at: 2026-11-13
applies_to: Spring Security 7.x, Spring Boot 4.x (서블릿 기반)
sources:
  - https://spring.io/guides/gs/securing-web/ (확인일 2026-11-13, 참고만 함)
  - https://docs.spring.io/spring-security/reference/ (확인일 2026-11-13, 참고만 함)
---

# Spring Security 인증과 인가

## 핵심 개념

- **인증(Authentication)**: "당신은 누구인가"를 확인하는 과정이다. 아이디와 비밀번호, 토큰, 인증서 등으로 사용자를 식별한다.
- **인가(Authorization)**: "당신은 이것을 해도 되는가"를 판단하는 과정이다. 인증된 사용자의 역할(role)이나 권한(authority)을 보고 접근을 허용하거나 거부한다.

인증이 먼저이고 인가가 나중이다. 인증에 실패하면 보통 401, 인증은 됐지만 권한이 없으면 403으로 응답한다.

Spring Security는 서블릿 애플리케이션에서 이 두 가지를 **필터(Filter)**로 처리한다. Spring Boot에서는 `spring-boot-starter-security`를 추가하면 기본적으로 모든 요청에 인증을 요구하는 보안 설정이 켜진다.

## 필터 체인 구조

Spring Security는 서블릿 필터 하나(`DelegatingFilterProxy`)를 통해 Spring 빈으로 등록된 **SecurityFilterChain**에 요청을 넘긴다. 체인 안에는 역할이 나뉜 여러 보안 필터가 정해진 순서로 놓인다. 대표적인 필터의 역할은 다음과 같다.

- 보안 컨텍스트 불러오기: 이전 요청에서 인증된 정보를 세션 등에서 꺼내 현재 요청에 연결한다.
- CSRF 검증: 상태를 바꾸는 요청에 CSRF 토큰이 있는지 확인한다.
- 인증 처리: 폼 로그인, HTTP Basic, 토큰 인증 등 설정한 방식으로 사용자를 인증한다.
- 예외 변환: 인증·인가 예외를 401·403 응답이나 로그인 페이지 이동으로 바꾼다.
- 인가 처리: 요청 경로와 사용자의 권한을 비교해 최종 허용 여부를 결정한다.

설정은 `SecurityFilterChain` 빈을 만들어 `HttpSecurity`로 구성한다. Spring Security 7에서는 람다 방식의 설정 문법을 사용한다.

## 인증 정보의 저장: SecurityContext

인증에 성공하면 사용자 정보를 담은 `Authentication` 객체가 **SecurityContext**에 저장되고, 이것은 `SecurityContextHolder`를 통해 현재 요청을 처리하는 스레드에서 꺼내 쓸 수 있다.

- `Authentication`에는 주체(principal, 보통 사용자 이름이나 사용자 객체), 자격 증명, 권한 목록이 들어 있다.
- 세션 기반 인증에서는 SecurityContext가 HTTP 세션에 저장되어 다음 요청에서 다시 불러온다.
- 컨트롤러에서는 메서드 파라미터로 `Principal`이나 `Authentication`을 받거나 `@AuthenticationPrincipal`을 사용해 현재 사용자를 얻는다.

## 사용자 조회와 비밀번호 인코딩

아이디·비밀번호 인증에서는 보통 다음 구성 요소가 협력한다.

- **UserDetailsService**: 사용자 이름으로 사용자 정보(`UserDetails`)를 조회한다. DB에서 읽도록 직접 구현하는 경우가 많다.
- **PasswordEncoder**: 비밀번호를 해시로 바꾸고, 입력값과 저장된 해시가 맞는지 비교한다.

비밀번호는 절대 평문으로 저장하지 않는다. 일방향이고 일부러 느리게 설계된 해시 함수(bcrypt, scrypt, Argon2, PBKDF2)를 사용한다. `PasswordEncoderFactories.createDelegatingPasswordEncoder()`는 기본으로 bcrypt를 쓰고, 저장값 앞에 `{bcrypt}`처럼 알고리즘 표시를 붙여 나중에 알고리즘을 바꿔도 기존 해시를 검증할 수 있게 한다. 같은 비밀번호도 매번 다른 솔트(salt)가 붙으므로 해시값이 매번 달라지는 것이 정상이다.

## 폼 로그인과 세션

전통적인 웹 애플리케이션은 **폼 로그인 + 서버 세션** 방식을 많이 쓴다.

1. 사용자가 로그인 폼에 아이디와 비밀번호를 보낸다.
2. 인증에 성공하면 서버는 세션을 만들고, 세션 ID를 쿠키(톰캣 기본 이름 `JSESSIONID`)로 내려준다.
3. 이후 요청은 쿠키의 세션 ID로 SecurityContext를 다시 찾는다.

세션 방식에서 주의할 보안 사항은 다음과 같다.

- **세션 고정(session fixation) 방지**: 로그인 성공 시 세션 ID를 새로 바꿔서, 공격자가 미리 심어 둔 세션 ID를 쓰지 못하게 한다. Spring Security의 기본 로그인 처리는 이를 자동으로 수행한다.
- **쿠키 속성**: `HttpOnly`(자바스크립트에서 읽기 금지), `Secure`(HTTPS에서만 전송), `SameSite`(다른 사이트에서 시작된 요청에 쿠키를 보낼지 제한)를 설정한다.
- **로그아웃**: 세션을 무효화하고 SecurityContext를 비운다.

토큰 방식(예: JWT)은 서버가 세션을 저장하지 않는 대신, 토큰을 즉시 무효화하기 어렵다는 단점이 있다. 어느 방식이 맞는지는 서비스 구조에 따라 다르다.

## CSRF 보호

CSRF(Cross-Site Request Forgery)는 사용자가 로그인된 상태에서 악성 사이트가 사용자의 브라우저를 이용해 원치 않는 요청을 보내게 만드는 공격이다. 브라우저가 쿠키를 자동으로 붙여 보내기 때문에 생긴다.

- Spring Security는 기본적으로 CSRF 보호를 켜고, POST·PUT·PATCH·DELETE 같은 상태 변경 요청에 **CSRF 토큰**을 요구한다. GET처럼 안전한 메서드는 검사하지 않는다.
- 서버 렌더링 폼에서는 숨은 필드로 토큰을 넣는다.
- 단일 페이지 애플리케이션(SPA)에서는 서버가 토큰을 쿠키(기본 이름 `XSRF-TOKEN`)로 내려주고, 프런트엔드가 그 값을 요청 헤더(`X-XSRF-TOKEN`)에 담아 보내는 방식을 많이 쓴다. Spring Security 6.4 이후에는 `csrf(csrf -> csrf.spa())`로 이 구성을 간단히 켤 수 있다.
- 쿠키를 전혀 쓰지 않고 매 요청에 토큰을 헤더로 직접 보내는 순수 토큰 API라면 CSRF 위험이 낮아 비활성화하는 경우도 있다. 쿠키로 인증하는 한 CSRF 보호는 끄지 않는다.

## URL 기반 인가와 메서드 보안

**URL 기반 인가**: `authorizeHttpRequests`에서 경로별 규칙을 정한다. 규칙은 **위에서부터 순서대로** 검사되므로 구체적인 규칙을 먼저, 넓은 규칙을 나중에 둔다.

- `permitAll()`: 누구나 허용
- `authenticated()`: 인증된 사용자만
- `hasRole("ADMIN")`: `ROLE_ADMIN` 권한을 가진 사용자만. `hasRole`은 `ROLE_` 접두어를 자동으로 붙이고, `hasAuthority`는 이름 그대로 비교한다.
- `denyAll()`: 모두 거부

**메서드 보안**: 설정 클래스에 `@EnableMethodSecurity`를 붙이면 서비스 메서드에 `@PreAuthorize("hasRole('ADMIN')")` 같은 조건을 걸 수 있다. URL 규칙만으로 표현하기 어려운 세밀한 권한(예: 본인 소유 데이터만 수정)에 쓴다.

## 예시

이 문서를 위해 작성한 설정 예시다.

```java
@Configuration
class WebSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login"))
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

## 자주 하는 실수

- 401과 403을 구분하지 않는 것.
- 넓은 규칙(`anyRequest()`)을 구체적인 규칙보다 먼저 써서 뒤의 규칙이 무시되는 것.
- `hasRole("ROLE_ADMIN")`처럼 접두어를 중복으로 붙이는 것.
- 쿠키 기반 인증인데 CSRF 보호를 끄는 것.
- 비밀번호를 평문이나 빠른 해시(MD5, SHA-1 단독)로 저장하는 것.

## 답변 가능 범위

- 인증과 인가의 차이, 401과 403의 차이
- SecurityFilterChain과 보안 필터의 역할
- SecurityContext와 현재 사용자 정보 얻는 방법
- UserDetailsService와 PasswordEncoder, bcrypt와 위임 인코더
- 폼 로그인과 세션, 세션 고정 방지, 쿠키 보안 속성
- CSRF의 원리와 SPA에서의 토큰 전달 방식
- URL 기반 인가 규칙의 순서, `hasRole`과 `hasAuthority`, 메서드 보안

## 답변 불가 범위

- OAuth2 인가 서버 구축, OpenID Connect 공급자 구현
- SAML, LDAP, Kerberos 연동 상세
- JWT 서명 알고리즘과 키 관리의 상세
- 리액티브(WebFlux) 보안 설정
