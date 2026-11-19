package com.ragqualitylab.chat.web;

import com.ragqualitylab.chat.application.ChatProperties;
import com.ragqualitylab.chat.application.ChatService;
import com.ragqualitylab.chat.application.ChatSessionService;
import com.ragqualitylab.chat.domain.ChatSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방문자 채팅 API (docs/phase0/05_api_contract.md 1절).
 * 세션 토큰은 HttpOnly 쿠키(rql_chat, Path=/api/chat)로만 주고받으며 URL·본문·로그에 나오지 않는다.
 */
@RestController
@RequestMapping("/api/chat/sessions")
public class ChatController {

    static final String COOKIE = "rql_chat";
    static final String NOTICE = "입력 내용은 저장되며 외부 AI 서비스(Google Gemini)로 전송됩니다. 개인정보를 입력하지 마세요.";

    private final ChatSessionService sessions;
    private final ChatService chat;
    private final ChatProperties properties;

    public ChatController(ChatSessionService sessions, ChatService chat, ChatProperties properties) {
        this.sessions = sessions;
        this.chat = chat;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> create(HttpServletRequest request,
                                                  @CookieValue(name = COOKIE, required = false) String previous) {
        ChatSessionService.CreatedSession created = sessions.create(request.getRemoteAddr(), previous);
        ChatSession s = created.session();
        return ResponseEntity.created(URI.create("/api/chat/sessions/" + s.getId()))
            .header(HttpHeaders.SET_COOKIE, cookie(created.token(), false).toString())
            .body(new SessionResponse(s.getId(), s.getStatus().name(), s.getExpiresAt(),
                properties.idleTimeout().toSeconds(), NOTICE));
    }

    @GetMapping("/{id}")
    public ChatService.SessionView get(@PathVariable UUID id,
                                       @CookieValue(name = COOKIE, required = false) String token) {
        return chat.view(id, token);
    }

    @PostMapping("/{id}/messages")
    public ChatService.AskResult ask(@PathVariable UUID id, @Valid @RequestBody AskRequest body,
                                     @CookieValue(name = COOKIE, required = false) String token,
                                     HttpServletRequest request) {
        return chat.ask(id, token, request.getRemoteAddr(), body.content());
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@PathVariable UUID id,
                                      @CookieValue(name = COOKIE, required = false) String token) {
        sessions.close(id, token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", true).toString()).build();
    }

    /** 세션 쿠키(Max-Age 없음): 브라우저를 닫으면 사라진다 (ADR-0006). */
    private ResponseCookie cookie(String value, boolean delete) {
        var builder = ResponseCookie.from(COOKIE, value)
            .httpOnly(true)
            .secure(properties.cookieSecure())
            .sameSite("Strict")
            .path("/api/chat");
        if (delete) {
            builder.maxAge(0);
        }
        return builder.build();
    }

    public record AskRequest(@NotNull String content) {
    }

    public record SessionResponse(UUID sessionId, String status, Instant expiresAt, long idleTimeoutSeconds,
                                  String notice) {
    }
}
