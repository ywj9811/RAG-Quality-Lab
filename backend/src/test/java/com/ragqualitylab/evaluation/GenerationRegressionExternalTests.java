package com.ragqualitylab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragqualitylab.TestcontainersConfiguration;
import com.ragqualitylab.chat.application.PromptBuilder;
import com.ragqualitylab.chat.application.RagProperties;
import com.ragqualitylab.document.application.DocumentService;
import com.ragqualitylab.document.domain.DocumentRepository;
import com.ragqualitylab.document.domain.DocumentStatus;
import com.ragqualitylab.llm.generation.GenerationFailure;
import com.ragqualitylab.llm.generation.GenerationModelPort;
import com.ragqualitylab.llm.generation.GenerationRequest;
import com.ragqualitylab.retrieval.application.RetrievalService;
import com.ragqualitylab.retrieval.application.RetrievedChunk;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 골든셋 생성 회귀 (08 계획 4.5절, 수동 전용). 실제 Ollama + Gemini 무료 티어, 생성 호출 최대 40회.
 * 실행: cd backend && RQL_ENV_FILE=../.env ./gradlew externalTest --tests '*GenerationRegression*'
 * 결과: build/reports/generation-regression.json
 * 측정: 기대 동작(ANSWER/REFUSE) 일치율, ANSWER 질문의 인용 포함률. 채팅과 같은 검색·프롬프트·임계값을 쓴다.
 */
@Tag("external")
@SpringBootTest(properties = "spring.ai.google.genai.api-key=${GEMINI_API_KEY}")
@Import(TestcontainersConfiguration.class)
class GenerationRegressionExternalTests {

    /** 무료 RPM(10~20)보다 느리게 보낸다 */
    static final long PAUSE_MS = 6500;

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.document.upload-dir", () -> uploadDir.toString());
    }

    @Autowired DocumentService documentService;
    @Autowired DocumentRepository documents;
    @Autowired RetrievalService retrieval;
    @Autowired PromptBuilder prompts;
    @Autowired GenerationModelPort generation;
    @Autowired RagProperties rag;

    @Test
    void goldenQuestionsGeneration() throws Exception {
        indexKnowledgeBase();
        List<GoldenQuestion> questions = GoldenQuestion.load();
        int match = 0;
        int answerable = 0;
        int answeredWithCitation = 0;
        int errors = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GoldenQuestion q : questions) {
            List<RetrievedChunk> chunks = retrieval.search(q.question());
            String actual;
            String text = "";
            boolean cited = false;
            if (chunks.isEmpty() || chunks.getFirst().score() < rag.minScore()) {
                actual = "REFUSE";
            } else {
                List<GenerationRequest.Source> sources = chunks.stream().map(c -> new GenerationRequest.Source(c.rank(),
                    c.filename() + (c.locator().heading() == null ? "" : " › " + c.locator().heading()), c.content())).toList();
                try {
                    text = generation.generate(prompts.build(new GenerationRequest.Parts(sources, List.of(), q.question()))).text().strip();
                    actual = text.startsWith(PromptBuilder.INSUFFICIENT_MARKER) ? "REFUSE" : "ANSWER";
                    cited = text.matches("(?s).*\\[\\d.*");
                } catch (GenerationFailure e) {
                    actual = "ERROR:" + e.kind();
                    errors++;
                }
                Thread.sleep(PAUSE_MS);
            }
            boolean ok = actual.equals(q.expectedBehavior());
            match += ok ? 1 : 0;
            if (q.expectedBehavior().equals("ANSWER")) {
                answerable++;
                answeredWithCitation += actual.equals("ANSWER") && cited ? 1 : 0;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", q.id());
            row.put("type", q.type());
            row.put("expected", q.expectedBehavior());
            row.put("actual", actual);
            row.put("match", ok);
            row.put("top1Score", chunks.isEmpty() ? null : Math.round(chunks.getFirst().score() * 1000) / 1000.0);
            row.put("answer", text.length() > 300 ? text.substring(0, 300) + "…" : text);
            rows.add(row);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("model", generation.modelId());
        report.put("promptVersion", prompts.version());
        report.put("questions", questions.size());
        report.put("behaviorMatchRate", Math.round(1000.0 * match / questions.size()) / 1000.0);
        report.put("answerableWithCitationRate", Math.round(1000.0 * answeredWithCitation / answerable) / 1000.0);
        report.put("errors", errors);
        report.put("rows", rows);
        Path out = Path.of("build/reports/generation-regression.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("GENERATION_REPORT behaviorMatch=" + report.get("behaviorMatchRate") + " citation="
            + report.get("answerableWithCitationRate") + " errors=" + errors);
        assertThat(errors).as("외부 오류").isLessThan(questions.size());
    }

    private void indexKnowledgeBase() throws Exception {
        List<UUID> ids = new ArrayList<>();
        try (var files = Files.list(GoldenQuestion.KNOWLEDGE_DIR)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                ids.add(documentService.upload(p.getFileName().toString(), Files.readAllBytes(p), null, "regression").getId());
            }
        }
        long deadline = System.currentTimeMillis() + 300_000;
        while (!ids.stream().allMatch(id -> documents.findById(id).orElseThrow().getStatus() == DocumentStatus.READY)) {
            assertThat(System.currentTimeMillis()).isLessThan(deadline);
            Thread.sleep(500);
        }
    }
}
