package com.ragqualitylab.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 익명 채팅 세션 (CHAT-01, CHAT-07, ADR-0006). 토큰 원문은 저장하지 않고 SHA-256 해시만 둔다. */
@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "access_token_hash", nullable = false, updatable = false, length = 64)
    private String accessTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "client_ip_hash", updatable = false, length = 64)
    private String clientIpHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected ChatSession() {
    }

    public ChatSession(String accessTokenHash, String clientIpHash, Instant now, Duration maxLifetime) {
        this.accessTokenHash = accessTokenHash;
        this.clientIpHash = clientIpHash;
        this.status = SessionStatus.ACTIVE;
        this.createdAt = now;
        this.lastActivityAt = now;
        this.expiresAt = now.plus(maxLifetime);
    }

    /** 유휴 시간 초과 또는 절대 만료 시각이 지났는지. */
    public boolean isExpiredAt(Instant now, Duration idleTimeout) {
        return !now.isBefore(expiresAt) || !now.isBefore(lastActivityAt.plus(idleTimeout));
    }

    public void touch(Instant now) {
        this.lastActivityAt = now;
    }

    public void close(Instant now) {
        this.status = SessionStatus.CLOSED;
        this.closedAt = now;
    }

    public void expire(Instant now) {
        this.status = SessionStatus.EXPIRED;
        this.closedAt = now;
    }

    public UUID getId() { return id; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public SessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getClosedAt() { return closedAt; }
}
