package com.ragqualitylab.llm.judge;

import com.ragqualitylab.llm.common.TimedModelCall;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

/** Gemini Judge 어댑터. 같은 ChatModel을 쓰되 모델·옵션은 호출마다 Judge 설정으로 지정한다. JSON 응답 형식을 요청한다. */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai", matchIfMissing = true)
public class GeminiJudgeAdapter implements JudgeModelPort, DisposableBean {

    private final ChatModel chatModel;
    private final JudgeProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public GeminiJudgeAdapter(ChatModel chatModel, JudgeProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public JudgeResponse judge(String systemPrompt, String userPrompt) {
        var options = GoogleGenAiChatOptions.builder()
            .model(properties.model())
            .maxOutputTokens(properties.maxOutputTokens())
            .temperature(properties.temperature())
            .thinkingLevel(GoogleGenAiThinkingLevel.valueOf(properties.thinkingLevel()))
            .responseMimeType("application/json")
            .build();
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)), options);
        ChatResponse response = TimedModelCall.call(executor, properties.timeout(), () -> chatModel.call(prompt));
        String text = response.getResult() == null || response.getResult().getOutput() == null
            ? "" : response.getResult().getOutput().getText();
        var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        String model = response.getMetadata() != null && response.getMetadata().getModel() != null
            && !response.getMetadata().getModel().isBlank() ? response.getMetadata().getModel() : properties.model();
        return new JudgeResponse(text == null ? "" : text, model,
            usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens());
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
