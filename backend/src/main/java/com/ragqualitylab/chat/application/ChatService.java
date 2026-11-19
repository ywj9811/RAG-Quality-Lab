package com.ragqualitylab.chat.application;

import com.ragqualitylab.chat.domain.AnswerType;
import com.ragqualitylab.chat.domain.ChatMessage;
import com.ragqualitylab.chat.domain.ChatMessageRepository;
import com.ragqualitylab.chat.domain.ChatSession;
import com.ragqualitylab.chat.domain.MessageRole;
import com.ragqualitylab.common.ratelimit.FixedWindowRateLimiter;
import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import com.ragqualitylab.llm.common.CauseSummary;
import com.ragqualitylab.llm.embedding.EmbeddingUnavailableException;
import com.ragqualitylab.llm.generation.GenerationFailure;
import com.ragqualitylab.llm.generation.GenerationModelPort;
import com.ragqualitylab.llm.generation.GenerationRequest;
import com.ragqualitylab.llm.generation.GenerationResult;
import com.ragqualitylab.llm.usage.LlmBudget;
import com.ragqualitylab.llm.usage.OperationType;
import com.ragqualitylab.llm.usage.UsageStatus;
import com.ragqualitylab.retrieval.application.RetrievalProperties;
import com.ragqualitylab.retrieval.application.RetrievalService;
import com.ragqualitylab.retrieval.application.RetrievedChunk;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 질문 → 검색 → 생성 → 저장 (CHAT-03~06, 08).
 * 질문 저장(세션 행 잠금)과 답변 저장은 각각 짧은 트랜잭션이고, 임베딩·생성(느린 외부 호출)은 트랜잭션 밖에서 한다.
 * 실패해도 답변 메시지(ERROR)를 남겨 세션이 "처리 중"에 갇히지 않게 한다.
 * 질문 본문은 로그에 남기지 않는다 (RK-04).
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** 직전 질문에 답이 없고 이 시간 안이면 처리 중으로 본다. 넘으면 비정상 종료로 보고 새 질문을 받는다 */
    static final Duration IN_PROGRESS_WINDOW = Duration.ofMinutes(2);

    static final String MSG_NO_EVIDENCE =
        "등록된 문서에서 질문과 관련된 근거를 찾지 못했습니다. 질문을 바꾸거나 Java·Spring 관련 주제로 물어봐 주세요.";
    static final String MSG_ERROR = "답변을 생성하지 못했습니다.";

    private final ChatSessionService sessions;
    private final ChatMessageRepository messages;
    private final RetrievalRecordRepository retrievalRecords;
    private final RetrievalService retrieval;
    private final RetrievalProperties retrievalProperties;
    private final ObjectProvider<GenerationModelPort> generation;
    private final PromptBuilder prompts;
    private final LlmBudget budget;
    private final ChatProperties chatProperties;
    private final RagProperties ragProperties;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;
    private final FixedWindowRateLimiter perIp;
    private final FixedWindowRateLimiter perSession;

    public ChatService(ChatSessionService sessions, ChatMessageRepository messages,
                       RetrievalRecordRepository retrievalRecords, RetrievalService retrieval,
                       RetrievalProperties retrievalProperties, ObjectProvider<GenerationModelPort> generation,
                       PromptBuilder prompts, LlmBudget budget, ChatProperties chatProperties,
                       RagProperties ragProperties, TransactionTemplate tx, JsonMapper json, Clock clock) {
        this.sessions = sessions;
        this.messages = messages;
        this.retrievalRecords = retrievalRecords;
        this.retrieval = retrieval;
        this.retrievalProperties = retrievalProperties;
        this.generation = generation;
        this.prompts = prompts;
        this.budget = budget;
        this.chatProperties = chatProperties;
        this.ragProperties = ragProperties;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
        this.perIp = new FixedWindowRateLimiter(chatProperties.perIpPerMinute(), Duration.ofMinutes(1), clock);
        this.perSession = new FixedWindowRateLimiter(chatProperties.perSessionPerMinute(), Duration.ofMinutes(1), clock);
    }

    public AskResult ask(UUID sessionId, String token, String clientIp, String rawContent) {
        long started = System.nanoTime();
        String content = validate(rawContent);
        rateLimit(perIp, "ip:" + sessions.ipHash(clientIp));
        rateLimit(perSession, "session:" + sessionId);

        Turn turn = tx.execute(status -> startTurn(sessionId, token, content));
        String query = turn.history().isEmpty() ? content : turn.history().getLast().question() + "\n" + content;
        String params = json.writeValueAsString(Map.of("topK", retrievalProperties.topK(),
            "minScore", ragProperties.minScore()));

        long retrievalStart = System.nanoTime();
        List<RetrievedChunk> chunks;
        try {
            chunks = retrieval.search(query);
        } catch (EmbeddingUnavailableException e) {
            log.warn("검색 실패(임베딩 서버): session={}", sessionId);
            saveError(turn, query, params, ErrorCode.EMBEDDING_UNAVAILABLE, started, null);
            throw new ApiException(ErrorCode.EMBEDDING_UNAVAILABLE, "검색 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
        int retrievalMs = millisSince(retrievalStart);

        if (chunks.isEmpty() || chunks.getFirst().score() < ragProperties.minScore()) {
            ChatMessage answer = saveAnswer(turn, chunks, Set.of(), new ChatMessage.AnswerRecord(MSG_NO_EVIDENCE,
                AnswerType.INSUFFICIENT_EVIDENCE, null, null, query, params, null, null, retrievalMs, null,
                millisSince(started), null));
            return new AskResult(turn.question().getId(), answer.getId(), MSG_NO_EVIDENCE,
                AnswerType.INSUFFICIENT_EVIDENCE, List.of(), answer.getLatencyMs());
        }

        GenerationModelPort model = generation.getIfAvailable();
        if (model == null) {
            saveError(turn, query, params, ErrorCode.UPSTREAM_UNAVAILABLE, started, retrievalMs);
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "답변 생성 기능이 설정되지 않았습니다.");
        }
        UUID reservation;
        try {
            reservation = budget.reserve(OperationType.GENERATE, model.provider(), model.modelId());
        } catch (ApiException e) {
            saveError(turn, query, params, e.code(), started, retrievalMs);
            throw e;
        }

        GenerationRequest request = prompts.build(new GenerationRequest.Parts(toSources(chunks), turn.history(), content));
        long generationStart = System.nanoTime();
        GenerationResult result;
        try {
            result = model.generate(request);
        } catch (GenerationFailure e) {
            int generationMs = millisSince(generationStart);
            ErrorCode code = switch (e.kind()) {
                case RATE_LIMITED -> ErrorCode.UPSTREAM_RATE_LIMITED;
                case TIMEOUT -> ErrorCode.UPSTREAM_TIMEOUT;
                case UNAVAILABLE -> ErrorCode.UPSTREAM_UNAVAILABLE;
            };
            UsageStatus usageStatus = switch (e.kind()) {
                case RATE_LIMITED -> UsageStatus.RATE_LIMITED;
                case TIMEOUT -> UsageStatus.TIMEOUT;
                case UNAVAILABLE -> UsageStatus.ERROR;
            };
            log.warn("생성 실패: session={} kind={} cause={}", sessionId, e.kind(), CauseSummary.of(e.getCause()));
            ChatMessage error = saveError(turn, query, params, code, started, retrievalMs);
            budget.complete(reservation, usageStatus, null, null, generationMs, error.getId(), null);
            throw new ApiException(code, userMessage(code));
        }
        int generationMs = millisSince(generationStart);

        String text = result.text().strip();
        AnswerType type = AnswerType.ANSWERED;
        if (text.startsWith(PromptBuilder.INSUFFICIENT_MARKER)) {
            type = AnswerType.INSUFFICIENT_EVIDENCE;
            text = text.substring(PromptBuilder.INSUFFICIENT_MARKER.length()).strip();
        }
        CitationParser.Result parsed = CitationParser.parse(text, chunks.size());
        Set<Integer> cited = type == AnswerType.ANSWERED ? parsed.cited() : Set.of();
        String finalText = parsed.text().isBlank() ? MSG_NO_EVIDENCE : parsed.text();
        if (parsed.text().isBlank()) {
            type = AnswerType.INSUFFICIENT_EVIDENCE;
        }

        ChatMessage answer = saveAnswer(turn, chunks, cited, new ChatMessage.AnswerRecord(finalText, type,
            result.modelId(), prompts.version(), query, params, result.inputTokens(), result.outputTokens(),
            retrievalMs, generationMs, millisSince(started), null));
        budget.complete(reservation, UsageStatus.SUCCESS, result.inputTokens(), result.outputTokens(), generationMs,
            answer.getId(), null);

        List<Citation> citations = retrievalRecords.citationsFor(List.of(answer.getId()))
            .getOrDefault(answer.getId(), List.of());
        return new AskResult(turn.question().getId(), answer.getId(), finalText, type, citations, answer.getLatencyMs());
    }

    /** 현재 세션 대화 (새로고침 복원용). */
    @Transactional(noRollbackFor = ApiException.class)
    public SessionView view(UUID sessionId, String token) {
        ChatSession session = sessions.requireActive(sessionId, token);
        List<ChatMessage> all = messages.findBySessionIdOrderBySeqAsc(sessionId);
        Map<UUID, List<Citation>> citations = retrievalRecords.citationsFor(all.stream()
            .filter(m -> m.getRole() == MessageRole.ASSISTANT).map(ChatMessage::getId).toList());
        List<MessageView> views = all.stream().map(m -> new MessageView(m.getId(), m.getRole(), m.getContent(),
            m.getAnswerType(), citations.getOrDefault(m.getId(), List.of()), m.getCreatedAt())).toList();
        return new SessionView(session.getId(), session.getStatus().name(), session.getExpiresAt(), views);
    }

    private String validate(String raw) {
        String content = raw == null ? "" : raw.strip();
        if (content.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "질문을 입력해 주세요.");
        }
        if (content.length() > chatProperties.maxQuestionChars()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "질문은 " + chatProperties.maxQuestionChars() + "자 이하로 입력해 주세요.");
        }
        if (ragProperties.blockPersonalInfo() && PersonalInfoDetector.containsPersonalInfo(content)) {
            throw new ApiException(ErrorCode.PERSONAL_INFO_DETECTED,
                "이메일·전화번호·주민등록번호처럼 보이는 내용이 있습니다. 개인정보를 지우고 다시 질문해 주세요.");
        }
        return content;
    }

    private static void rateLimit(FixedWindowRateLimiter limiter, String key) {
        long retryAfter = limiter.tryAcquire(key);
        if (retryAfter > 0) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.", retryAfter);
        }
    }

    private Turn startTurn(UUID sessionId, String token, String content) {
        ChatSession session = sessions.requireActiveForUpdate(sessionId, token);
        Instant now = clock.instant();
        Optional<ChatMessage> last = messages.findFirstBySessionIdOrderBySeqDesc(sessionId);
        if (last.isPresent() && last.get().getRole() == MessageRole.USER
            && last.get().getCreatedAt().isAfter(now.minus(IN_PROGRESS_WINDOW))) {
            throw new ApiException(ErrorCode.MESSAGE_IN_PROGRESS, "이전 질문에 답하는 중입니다. 잠시 기다려 주세요.");
        }
        List<GenerationRequest.Turn> history = history(messages.findBySessionIdOrderBySeqAsc(sessionId));
        int seq = last.map(m -> m.getSeq() + 1).orElse(1);
        ChatMessage question = messages.save(ChatMessage.question(sessionId, seq, content, now));
        session.touch(now);
        return new Turn(sessionId, question, history);
    }

    /** 직전 질문·답변 쌍(오류 답변 제외)을 최근 것부터 글자 수 상한까지 담는다 (CHAT-03). */
    List<GenerationRequest.Turn> history(List<ChatMessage> all) {
        List<GenerationRequest.Turn> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < all.size(); i++) {
            ChatMessage q = all.get(i);
            ChatMessage a = all.get(i + 1);
            if (q.getRole() == MessageRole.USER && a.getRole() == MessageRole.ASSISTANT
                && a.getAnswerType() != AnswerType.ERROR) {
                pairs.add(new GenerationRequest.Turn(q.getContent(), a.getContent()));
                i++;
            }
        }
        int maxPairs = ragProperties.contextMessages() / 2;
        Deque<GenerationRequest.Turn> kept = new ArrayDeque<>();
        int chars = 0;
        for (int i = pairs.size() - 1; i >= 0 && kept.size() < maxPairs; i--) {
            GenerationRequest.Turn t = pairs.get(i);
            int size = t.question().length() + t.answer().length();
            if (chars + size > ragProperties.maxContextChars()) {
                break;
            }
            chars += size;
            kept.addFirst(t);
        }
        return List.copyOf(kept);
    }

    private static List<GenerationRequest.Source> toSources(List<RetrievedChunk> chunks) {
        return chunks.stream().map(c -> new GenerationRequest.Source(c.rank(),
            c.filename() + (c.locator().heading() == null ? "" : " › " + c.locator().heading()), c.content())).toList();
    }

    private ChatMessage saveAnswer(Turn turn, List<RetrievedChunk> chunks, Set<Integer> cited,
                                   ChatMessage.AnswerRecord record) {
        return tx.execute(status -> {
            // 검색 스냅샷은 JDBC로 넣으므로 FK 대상인 답변 행을 먼저 DB에 반영한다
            ChatMessage answer = messages.saveAndFlush(
                ChatMessage.answer(turn.sessionId(), turn.question().getSeq() + 1, record, clock.instant()));
            retrievalRecords.saveAll(answer.getId(), chunks, cited);
            return answer;
        });
    }

    private ChatMessage saveError(Turn turn, String query, String params, ErrorCode code, long started,
                                  Integer retrievalMs) {
        return saveAnswer(turn, List.of(), Set.of(), new ChatMessage.AnswerRecord(MSG_ERROR, AnswerType.ERROR, null,
            null, query, params, null, null, retrievalMs, null, millisSince(started), code.name()));
    }

    private static String userMessage(ErrorCode code) {
        return switch (code) {
            case UPSTREAM_RATE_LIMITED -> "외부 AI 서비스의 무료 사용 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.";
            case UPSTREAM_TIMEOUT -> "답변 생성이 너무 오래 걸립니다. 잠시 후 다시 시도해 주세요.";
            default -> "외부 AI 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
        };
    }

    private static int millisSince(long nanoStart) {
        return (int) Duration.ofNanos(System.nanoTime() - nanoStart).toMillis();
    }

    private record Turn(UUID sessionId, ChatMessage question, List<GenerationRequest.Turn> history) {
    }

    public record AskResult(UUID questionMessageId, UUID answerMessageId, String answer, AnswerType answerType,
                            List<Citation> citations, Integer latencyMs) {
    }

    public record MessageView(UUID id, MessageRole role, String content, AnswerType answerType,
                              List<Citation> citations, Instant createdAt) {
    }

    public record SessionView(UUID sessionId, String status, Instant expiresAt, List<MessageView> messages) {
    }
}
