package com.ragqualitylab.evaluation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    List<Evaluation> findByAnswerMessageIdOrderByCreatedAtDesc(UUID answerMessageId);

    @Query("select e from Evaluation e where e.answerMessageId = :id and e.status in ('PENDING', 'RUNNING')")
    Optional<Evaluation> findInProgress(UUID id);

    /** 실행 시작은 한 번만. */
    @Modifying
    @Query("update Evaluation e set e.status = 'RUNNING' where e.id = :id and e.status = 'PENDING'")
    int claim(UUID id);

    @Modifying
    @Query("update Evaluation e set e.status = 'FAILED', e.errorCode = 'INTERRUPTED', e.completedAt = :now "
        + "where e.status in ('PENDING', 'RUNNING')")
    int failInterrupted(Instant now);
}
