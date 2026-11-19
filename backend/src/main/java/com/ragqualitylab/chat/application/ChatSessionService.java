package com.ragqualitylab.chat.application;

import com.ragqualitylab.chat.domain.ChatSession;
import com.ragqualitylab.chat.domain.ChatSessionRepository;
import com.ragqualitylab.chat.domain.SessionStatus;
import com.ragqualitylab.common.ratelimit.FixedWindowRateLimiter;
import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 익명 세션 생성·검증·종료·만료 (CHAT-01, 02, 07, ADR-0006). */
@Service
public class ChatSessionService {

    private final ChatSessionRepository sessions;
    private final ChatProperties properties;
    private final Clock clock;
    private final String ipSalt;
    private final FixedWindowRateLimiter sessionCreation;

    public ChatSessionService(ChatSessionRepository sessions, ChatProperties properties, Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
        this.ipSalt = properties.ipHashSalt().isEmpty() ? randomSalt() : properties.ipHashSalt();
        this.sessionCreation = new FixedWindowRateLimiter(properties.sessionsPerIpPerHour(), Duration.ofHours(1), clock);
    }

    /** 새 세션을 만든다. 같은 브라우저의 이전 세션 토큰이 오면 그 세션은 종료한다. */
    @Transactional
    public CreatedSession create(String clientIp, String previousToken) {
        String ipHash = ipHash(clientIp);
        long retryAfter = sessionCreation.tryAcquire(ipHash);
        if (retryAfter > 0) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "새 대화를 너무 자주 시작했습니다. 잠시 후 다시 시도해 주세요.", retryAfter);
        }
        Instant now = clock.instant();
        if (previousToken != null) {
            sessions.findByAccessTokenHash(SessionTokens.sha256Hex(previousToken))
                .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                .ifPresent(s -> s.close(now));
        }
        String token = SessionTokens.newToken();
        ChatSession session = sessions.save(
            new ChatSession(SessionTokens.sha256Hex(token), ipHash, now, properties.maxLifetime()));
        return new CreatedSession(session, token);
    }

    /** 조회용 검증 (잠금 없음). */
    @Transactional(noRollbackFor = ApiException.class)
    public ChatSession requireActive(UUID sessionId, String token) {
        return checkActive(sessions.findById(sessionId), token);
    }

    /** 질문 처리용 검증: 행 잠금으로 같은 세션의 동시 질문을 직렬화한다. 호출자의 트랜잭션 안에서 써야 한다. */
    public ChatSession requireActiveForUpdate(UUID sessionId, String token) {
        return checkActive(sessions.findByIdForUpdate(sessionId), token);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void close(UUID sessionId, String token) {
        checkActive(sessions.findById(sessionId), token).close(clock.instant());
    }

    /** 5분마다 만료 세션을 일괄 정리한다. 요청 시점에도 즉시 판정하므로 이것은 보조 정리다. */
    @Scheduled(fixedDelayString = "${app.chat.expiry-sweep-interval:5m}")
    @Transactional
    public int expireStale() {
        Instant now = clock.instant();
        return sessions.expireStale(now, now.minus(properties.idleTimeout()));
    }

    public String ipHash(String clientIp) {
        return SessionTokens.sha256Hex(ipSalt + "|" + (clientIp == null ? "" : clientIp));
    }

    private ChatSession checkActive(java.util.Optional<ChatSession> found, String token) {
        // 세션이 없거나 토큰이 다르면 존재 여부를 숨기기 위해 똑같이 404
        ChatSession session = found
            .filter(s -> SessionTokens.matches(token, s.getAccessTokenHash()))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "대화를 찾을 수 없습니다."));
        Instant now = clock.instant();
        if (session.getStatus() == SessionStatus.ACTIVE && session.isExpiredAt(now, properties.idleTimeout())) {
            session.expire(now);
        }
        return switch (session.getStatus()) {
            case ACTIVE -> session;
            case CLOSED -> throw new ApiException(ErrorCode.SESSION_CLOSED, "종료된 대화입니다. 새 대화를 시작해 주세요.");
            case EXPIRED -> throw new ApiException(ErrorCode.SESSION_EXPIRED, "대화 시간이 만료되었습니다. 새 대화를 시작해 주세요.");
        };
    }

    private static String randomSalt() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public record CreatedSession(ChatSession session, String token) {
    }
}
