package com.ragqualitylab.evaluation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** 답변 한 건에 대한 평가 한 번 (EVAL-04·05). 재평가는 새 행으로 남고 이전 결과를 덮어쓰지 않는다 (ADM-06). */
@Entity
@Table(name = "evaluations")
public class Evaluation {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "answer_message_id", nullable = false, updatable = false)
    private UUID answerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvaluationStatus status;

    @Column(name = "rubric_version", nullable = false, updatable = false, length = 20)
    private String rubricVersion;

    @Column(name = "judge_model_id", nullable = false, length = 100)
    private String judgeModelId;

    @Column(name = "prompt_version", nullable = false, updatable = false, length = 100)
    private String promptVersion;

    @Column(name = "total_score")
    private Integer totalScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "jsonb")
    private String inputSnapshot;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String issues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String suggestions;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Evaluation() {
    }

    public Evaluation(UUID answerMessageId, String rubricVersion, String judgeModelId, String promptVersion,
                      UUID requestedBy, Instant now) {
        this.answerMessageId = answerMessageId;
        this.rubricVersion = rubricVersion;
        this.judgeModelId = judgeModelId;
        this.promptVersion = promptVersion;
        this.requestedBy = requestedBy;
        this.status = EvaluationStatus.PENDING;
        this.createdAt = now;
    }

    public void start(String inputSnapshotJson, Instant now) {
        this.status = EvaluationStatus.RUNNING;
        this.inputSnapshot = inputSnapshotJson;
        this.startedAt = now;
        this.attemptCount++;
    }

    public void complete(int totalScore, String issuesJson, String suggestionsJson, String rawResponse,
                         String actualModelId, Instant now) {
        this.status = EvaluationStatus.COMPLETED;
        this.totalScore = totalScore;
        this.issues = issuesJson;
        this.suggestions = suggestionsJson;
        this.rawResponse = rawResponse;
        if (actualModelId != null) {
            this.judgeModelId = actualModelId;
        }
        this.completedAt = now;
    }

    public void fail(String errorCode, String rawResponse, Instant now) {
        this.status = EvaluationStatus.FAILED;
        this.errorCode = errorCode;
        this.rawResponse = rawResponse;
        this.completedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getAnswerMessageId() { return answerMessageId; }
    public EvaluationStatus getStatus() { return status; }
    public String getRubricVersion() { return rubricVersion; }
    public String getJudgeModelId() { return judgeModelId; }
    public String getPromptVersion() { return promptVersion; }
    public Integer getTotalScore() { return totalScore; }
    public String getIssues() { return issues; }
    public String getSuggestions() { return suggestions; }
    public int getAttemptCount() { return attemptCount; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
