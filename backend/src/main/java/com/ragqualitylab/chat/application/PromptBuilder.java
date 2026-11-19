package com.ragqualitylab.chat.application;

import com.ragqualitylab.llm.generation.GenerationRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 생성 프롬프트 조립 (CHAT-04, 명세 6절 인젝션 방어).
 * 시스템 지시는 Git으로 관리하는 파일(prompts/generation/v1.md)에서 읽고, 근거·이전 대화·질문은 사용자 메시지의 태그 블록에 넣는다.
 * 데이터 안에 블록 태그가 있으면 이스케이프해 블록을 빠져나가지 못하게 한다.
 * 버전 문자열은 "gen-v1@<내용 해시 8자리>"로, 파일 내용이 바뀌면 달라진다.
 */
@Component
public class PromptBuilder {

    static final String VERSION = "gen-v1";
    static final String RESOURCE = "prompts/generation/v1.md";
    public static final String INSUFFICIENT_MARKER = "[근거 부족]";

    private final String systemPrompt;
    private final String version;

    public PromptBuilder() {
        try {
            byte[] bytes = new ClassPathResource(RESOURCE).getContentAsByteArray();
            this.systemPrompt = new String(bytes, StandardCharsets.UTF_8).strip();
            this.version = VERSION + "@" + HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String version() {
        return version;
    }

    public GenerationRequest build(GenerationRequest.Parts parts) {
        StringBuilder user = new StringBuilder();
        user.append("<sources>\n");
        for (GenerationRequest.Source s : parts.sources()) {
            user.append("<source id=\"").append(s.index()).append("\" label=\"").append(escapeAttr(s.label()))
                .append("\">\n").append(escape(s.text())).append("\n</source>\n");
        }
        user.append("</sources>\n\n");
        if (!parts.history().isEmpty()) {
            user.append("<history>\n");
            for (GenerationRequest.Turn t : parts.history()) {
                user.append("사용자: ").append(escape(t.question())).append('\n');
                user.append("도우미: ").append(escape(t.answer())).append('\n');
            }
            user.append("</history>\n\n");
        }
        user.append("<question>\n").append(escape(parts.question())).append("\n</question>");
        return new GenerationRequest(systemPrompt, user.toString());
    }

    /** 블록 태그를 흉내 낸 문자열을 무력화한다. */
    static String escape(String text) {
        return text.replaceAll("(?i)<(/?)(sources|source|history|question)", "&lt;$1$2");
    }

    private static String escapeAttr(String text) {
        return escape(text).replace("\"", "'");
    }

    static List<String> blockTags() {
        return List.of("sources", "source", "history", "question");
    }
}
