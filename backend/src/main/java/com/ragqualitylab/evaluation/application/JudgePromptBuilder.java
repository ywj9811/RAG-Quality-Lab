package com.ragqualitylab.evaluation.application;

import com.ragqualitylab.llm.common.PromptBlocks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Judge 프롬프트 조립. 시스템 지시는 prompts/judge/v1.md, 버전은 "judge-v1@<해시 8자리>". 데이터는 태그 블록에 넣는다. */
@Component
public class JudgePromptBuilder {

    static final String RESOURCE = "prompts/judge/v1.md";

    private final String systemPrompt;
    private final String version;

    public JudgePromptBuilder() {
        try {
            byte[] bytes = new ClassPathResource(RESOURCE).getContentAsByteArray();
            this.systemPrompt = new String(bytes, StandardCharsets.UTF_8).strip();
            this.version = "judge-v1@" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String version() {
        return version;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String userPrompt(JudgeInput input, Rubric rubric) {
        StringBuilder sb = new StringBuilder("<rubric version=\"").append(rubric.version()).append("\">\n");
        for (Rubric.Criterion c : rubric.criteria()) {
            sb.append("- ").append(c.key()).append(" (만점 ").append(c.maxScore()).append("): ").append(c.question()).append('\n');
        }
        sb.append("</rubric>\n\n<sources>\n");
        for (JudgeInput.Source s : input.sources()) {
            sb.append("<source id=\"").append(s.index()).append("\" label=\"").append(PromptBlocks.escapeAttr(s.label()))
                .append("\">\n").append(PromptBlocks.escape(s.text())).append("\n</source>\n");
        }
        sb.append("</sources>\n\n");
        if (!input.history().isEmpty()) {
            sb.append("<history>\n");
            for (JudgeInput.Turn t : input.history()) {
                sb.append("사용자: ").append(PromptBlocks.escape(t.question())).append('\n')
                    .append("도우미: ").append(PromptBlocks.escape(t.answer())).append('\n');
            }
            sb.append("</history>\n\n");
        }
        sb.append("<question>\n").append(PromptBlocks.escape(input.question())).append("\n</question>\n\n");
        sb.append("<answer>\n").append(PromptBlocks.escape(input.answer())).append("\n</answer>");
        return sb.toString();
    }
}
