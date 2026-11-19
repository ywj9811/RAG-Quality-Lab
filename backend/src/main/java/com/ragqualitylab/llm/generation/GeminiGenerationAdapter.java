package com.ragqualitylab.llm.generation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Gemini 무료 티어 생성 어댑터. 모델은 설정값(허용 목록)으로만 고른다.
 * 재시도는 Spring AI 재시도 설정(spring.ai.retry.max-attempts=2, 즉 최대 1회)에 맡기고, 전체 대기 시간은 timeout으로 자른다.
 */
@Component
// 채팅 공급자를 끈 경우(RQL_CHAT_PROVIDER=none)에는 만들지 않는다. @ConditionalOnBean은 컴포넌트 스캔 시점에 자동설정 빈을 보지 못한다
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai", matchIfMissing = true)
public class GeminiGenerationAdapter implements GenerationModelPort, DisposableBean {

    private final ChatModel chatModel;
    private final GenerationProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public GeminiGenerationAdapter(ChatModel chatModel, GenerationProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        var options = GoogleGenAiChatOptions.builder()
            .model(properties.model())
            .maxOutputTokens(properties.maxOutputTokens())
            .temperature(properties.temperature())
            .thinkingLevel(GoogleGenAiThinkingLevel.valueOf(properties.thinkingLevel()))
            .build();
        Prompt prompt = new Prompt(List.of(new SystemMessage(request.systemPrompt()),
            new UserMessage(request.userPrompt())), options);

        CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(() -> chatModel.call(prompt), executor);
        ChatResponse response;
        try {
            response = future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new GenerationFailure(GenerationFailure.Kind.TIMEOUT, "생성 모델 응답 시간 초과", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationFailure(GenerationFailure.Kind.UNAVAILABLE, "생성 중단", e);
        } catch (ExecutionException e) {
            throw classify(e.getCause());
        }
        String text = response.getResult() == null || response.getResult().getOutput() == null
            ? "" : response.getResult().getOutput().getText();
        var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        String model = response.getMetadata() != null && response.getMetadata().getModel() != null
            && !response.getMetadata().getModel().isBlank() ? response.getMetadata().getModel() : properties.model();
        return new GenerationResult(text == null ? "" : text, model,
            usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens());
    }

    static GenerationFailure classify(Throwable cause) {
        String message = String.valueOf(cause == null ? null : cause.getMessage());
        boolean rateLimited = message.contains("429") || message.contains("RESOURCE_EXHAUSTED")
            || message.toLowerCase().contains("quota");
        return new GenerationFailure(rateLimited ? GenerationFailure.Kind.RATE_LIMITED : GenerationFailure.Kind.UNAVAILABLE,
            "생성 모델 호출 실패", cause);
    }

    @Override
    public String provider() {
        return "google-genai";
    }

    @Override
    public String modelId() {
        return properties.model();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
