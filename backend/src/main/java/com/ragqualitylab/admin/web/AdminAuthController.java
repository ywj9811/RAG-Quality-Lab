package com.ragqualitylab.admin.web;

import com.ragqualitylab.admin.application.AdminLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 인증 API (docs/phase0/05_api_contract.md 2절). */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AdminLoginService loginService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AdminAuthController(AdminLoginService loginService, SecurityContextRepository securityContextRepository) {
        this.loginService = loginService;
        this.securityContextRepository = securityContextRepository;
    }

    /** XSRF-TOKEN 쿠키를 발급한다. 이후 상태 변경 요청은 X-XSRF-TOKEN 헤더에 값을 담는다. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public AdminMeResponse login(@Valid @RequestBody LoginRequest body,
                                 HttpServletRequest request, HttpServletResponse response) {
        String username = loginService.authenticate(body.username(), body.password());

        // 세션 고정 공격 방지: 인증 성공 시 세션 ID를 바꾼다
        request.getSession(true);
        request.changeSessionId();

        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
            username, null, List.of(new SimpleGrantedAuthority(ROLE_ADMIN)));
        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(auth);
        holderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return new AdminMeResponse(username);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AdminMeResponse me(Principal principal) {
        return new AdminMeResponse(principal.getName());
    }

    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 200) String password) {
    }

    public record AdminMeResponse(String username) {
    }
}
