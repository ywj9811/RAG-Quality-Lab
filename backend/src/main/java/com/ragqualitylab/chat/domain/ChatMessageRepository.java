package com.ragqualitylab.chat.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionIdOrderBySeqAsc(UUID sessionId);

    Optional<ChatMessage> findFirstBySessionIdOrderBySeqDesc(UUID sessionId);
}
