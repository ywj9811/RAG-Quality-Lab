package com.ragqualitylab.chat.domain;

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

/** 질문·답변 (CHAT-06). 답변에는 모델·프롬프트 버전·검색 질의·파라미터·지연·토큰을 함께 기록한다. */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", length = 30)
    private AnswerType answerType;

    @Column(name = "model_id", length = 100)
    private String modelId;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Column(name = "retrieval_query", columnDefinition = "text")
    private String retrievalQuery;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retrieval_params", columnDefinition = "jsonb")
    private String retrievalParams;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "retrieval_ms")
    private Integer retrievalMs;

    @Column(name = "generation_ms")
    private Integer generationMs;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public static ChatMessage question(UUID sessionId, int seq, String content, Instant now) {
        ChatMessage m = new ChatMessage();
        m.sessionId = sessionId;
        m.seq = seq;
        m.role = MessageRole.USER;
        m.content = content;
        m.createdAt = now;
        return m;
    }

    public static ChatMessage answer(UUID sessionId, int seq, AnswerRecord r, Instant now) {
        ChatMessage m = new ChatMessage();
        m.sessionId = sessionId;
        m.seq = seq;
        m.role = MessageRole.ASSISTANT;
        m.content = r.content();
        m.answerType = r.answerType();
        m.modelId = r.modelId();
        m.promptVersion = r.promptVersion();
        m.retrievalQuery = r.retrievalQuery();
        m.retrievalParams = r.retrievalParamsJson();
        m.inputTokens = r.inputTokens();
        m.outputTokens = r.outputTokens();
        m.retrievalMs = r.retrievalMs();
        m.generationMs = r.generationMs();
        m.latencyMs = r.latencyMs();
        m.errorCode = r.errorCode();
        m.createdAt = now;
        return m;
    }

    /** 답변 저장에 필요한 값 묶음. */
    public record AnswerRecord(String content, AnswerType answerType, String modelId, String promptVersion,
                               String retrievalQuery, String retrievalParamsJson, Integer inputTokens,
                               Integer outputTokens, Integer retrievalMs, Integer generationMs, Integer latencyMs,
                               String errorCode) {
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public int getSeq() { return seq; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public AnswerType getAnswerType() { return answerType; }
    public String getModelId() { return modelId; }
    public String getPromptVersion() { return promptVersion; }
    public String getRetrievalQuery() { return retrievalQuery; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public Integer getLatencyMs() { return latencyMs; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
}
