package com.ragqualitylab.common.security;

import com.ragqualitylab.common.web.ErrorCode;
import com.ragqualitylab.common.web.ProblemDetails;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 기본값.
 * - 헬스체크·관리자 로그인/CSRF 발급만 공개, 그 외 관리자 API는 ROLE_ADMIN, 정의되지 않은 경로는 거부
 * - 관리자 인증은 서버 세션(쿠키 rql_admin), 401/403/423은 Problem Details JSON
 * - CSRF는 SPA용 쿠키 방식(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더). 로그인 요청도 CSRF 검증 대상
 * 방문자 채팅 경로의 인증·CSRF 정책은 Phase 3(ADR-0006)에서 추가한다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper,
                                            SecurityContextRepository securityContextRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/admin/auth/login", "/api/admin/auth/csrf").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
            .csrf(csrf -> csrf.spa())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicy.NO_REFERRER)))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    writeProblem(res, jsonMapper, ProblemDetails.of(ErrorCode.UNAUTHENTICATED, "관리자 로그인이 필요합니다.")))
                .accessDeniedHandler((req, res, e) -> writeProblem(res, jsonMapper, e instanceof CsrfException
                    ? ProblemDetails.of(ErrorCode.CSRF_INVALID, "페이지를 새로고침한 뒤 다시 시도해 주세요.")
                    : ProblemDetails.of(ErrorCode.FORBIDDEN, "이 요청을 수행할 권한이 없습니다."))));
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** 기본 bcrypt, 저장 형식은 {bcrypt}... (알고리즘 교체 가능) */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    private static void writeProblem(HttpServletResponse res, JsonMapper jsonMapper, ProblemDetail problem)
            throws IOException {
        res.setStatus(problem.getStatus());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(res.getOutputStream(), problem);
    }
}
