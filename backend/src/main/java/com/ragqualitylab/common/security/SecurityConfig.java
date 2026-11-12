package com.ragqualitylab.common.security;

import com.ragqualitylab.common.web.ErrorCode;
import com.ragqualitylab.common.web.ProblemDetails;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 1 보안 기본값.
 * - 헬스체크만 공개, 관리자 API는 인증 필요, 정의되지 않은 경로는 모두 거부
 * - 401/403은 Problem Details JSON으로 응답
 * - CSRF는 SPA용 쿠키 방식(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더)
 * 방문자 채팅 경로의 인증·CSRF 정책은 Phase 3(ADR-0006)에서 추가한다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/admin/**").authenticated()
                .anyRequest().denyAll())
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
                .accessDeniedHandler((req, res, e) ->
                    writeProblem(res, jsonMapper, ProblemDetails.of(ErrorCode.FORBIDDEN, "이 요청을 수행할 권한이 없습니다."))));
        return http.build();
    }

    /**
     * Spring Boot가 기본 사용자와 임시 비밀번호를 만들어 로그에 출력하는 것을 막는다.
     * Phase 2에서 DB 기반 관리자 계정(ADM-01)으로 교체한다.
     */
    @Bean
    UserDetailsService noUsersYet() {
        return username -> {
            throw new UsernameNotFoundException("No admin users configured");
        };
    }

    private static void writeProblem(HttpServletResponse res, JsonMapper jsonMapper, ProblemDetail problem)
            throws IOException {
        res.setStatus(problem.getStatus());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(res.getOutputStream(), problem);
    }
}
