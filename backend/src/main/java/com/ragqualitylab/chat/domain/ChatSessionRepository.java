package com.ragqualitylab.chat.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    /** 같은 세션의 질문을 직렬화하기 위해 행 잠금으로 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatSession s where s.id = :id")
    Optional<ChatSession> findByIdForUpdate(UUID id);

    Optional<ChatSession> findByAccessTokenHash(String accessTokenHash);

    /** 유휴·절대 만료된 ACTIVE 세션을 EXPIRED로 바꾼다. */
    @Modifying
    @Query("update ChatSession s set s.status = 'EXPIRED', s.closedAt = :now "
        + "where s.status = 'ACTIVE' and (s.expiresAt <= :now or s.lastActivityAt <= :idleCutoff)")
    int expireStale(Instant now, Instant idleCutoff);
}
