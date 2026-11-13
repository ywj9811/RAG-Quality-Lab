package com.ragqualitylab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragqualitylab.TestcontainersConfiguration;
import com.ragqualitylab.document.application.DocumentProcessor;
import com.ragqualitylab.document.application.DocumentService;
import com.ragqualitylab.document.domain.Document;
import com.ragqualitylab.document.domain.DocumentRepository;
import com.ragqualitylab.document.domain.DocumentStatus;
import com.ragqualitylab.llm.embedding.EmbeddingProperties;
import com.ragqualitylab.llm.embedding.OllamaEmbeddingAdapter;
import com.ragqualitylab.retrieval.application.RetrievedChunk;
import com.ragqualitylab.retrieval.infrastructure.ChunkSearchRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 골든셋 검색 회귀 (08_knowledge_base_plan 4.5절, ADR-0003 검증 계획). 외부 LLM 호출 없음, 로컬 Ollama만 사용.
 * 실행: ./gradlew ollamaTest  → 결과: build/reports/retrieval-regression.json
 * - ANSWER 질문: 정답 문서의 근거 소제목 청크가 Top-K 안에 있으면 적중. Recall@K, MRR
 * - REFUSE 질문: Top-1 점수 분포(근거 부족 임계값 보정용)
 * - 설정된 instruction(기본: 없음)과 Qwen3 권장 instruction 비교
 */
@Tag("ollama")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RetrievalRegressionTests {

    static final int K = 5;
    /** 잠정 합격 기준 (08 계획 4.5절). 미달 시 ADR-0003에 따라 4b 모델 전환을 검토한다 */
    static final double MIN_RECALL = 0.8;
    /** 비교용: Qwen3-Embedding 권장 형식의 instruction */
    static final String QWEN_INSTRUCTION =
        "Instruct: Given a Korean technical question about Java and Spring, retrieve relevant passages that answer it\nQuery:";

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.document.upload-dir", () -> uploadDir.toString());
    }

    @Autowired DocumentService documentService;
    @Autowired DocumentProcessor processor;
    @Autowired DocumentRepository documents;
    @Autowired ChunkSearchRepository search;
    @Autowired OllamaEmbeddingAdapter embedding;
    @Autowired EmbeddingModel embeddingModel;
    @Autowired EmbeddingProperties embeddingProperties;
    @Value("${spring.ai.ollama.embedding.model}") String modelId;

    @Test
    void goldenQuestionsRetrieval() throws Exception {
        Map<String, java.util.UUID> docIds = indexKnowledgeBase();
        List<GoldenQuestion> questions = GoldenQuestion.load();

        var qwenInstruction = new OllamaEmbeddingAdapter(embeddingModel,
            new EmbeddingProperties(embeddingProperties.dimensions(), QWEN_INSTRUCTION, embeddingProperties.batchSize()),
            modelId);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("model", modelId);
        report.put("k", K);
        report.put("configuredInstruction", embeddingProperties.queryInstruction());
        Map<String, Object> configured = evaluate(questions, docIds, embedding);
        report.put("configured", configured);
        report.put("qwenInstruction", evaluate(questions, docIds, qwenInstruction));

        Path out = Path.of("build/reports/retrieval-regression.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("RETRIEVAL_REPORT " + Files.readString(out));

        assertThat((double) configured.get("recallAtK")).as("Recall@%d (configured)", K).isGreaterThanOrEqualTo(MIN_RECALL);
    }

    private Map<String, Object> evaluate(List<GoldenQuestion> questions, Map<String, java.util.UUID> docIds,
                                         OllamaEmbeddingAdapter adapter) {
        int answerable = 0;
        int hits = 0;
        double reciprocalRankSum = 0;
        List<String> misses = new ArrayList<>();
        List<Double> refuseTop1 = new ArrayList<>();
        List<Double> answerTop1 = new ArrayList<>();
        Map<String, int[]> byType = new LinkedHashMap<>();
        for (GoldenQuestion q : questions) {
            List<RetrievedChunk> results = search.search(adapter.embedQuery(q.question()), K);
            double top1 = results.isEmpty() ? 0 : results.getFirst().score();
            if (q.expectedBehavior().equals("REFUSE")) {
                refuseTop1.add(round(top1));
                continue;
            }
            answerable++;
            answerTop1.add(round(top1));
            java.util.UUID expectedDoc = docIds.get(q.documentId());
            List<String> headings = q.supportingSections().stream().map(s -> s.split("#", 2)[1]).toList();
            int rank = 0;
            for (RetrievedChunk r : results) {
                if (r.documentId().equals(expectedDoc) && headings.contains(r.locator().heading())) {
                    rank = r.rank();
                    break;
                }
            }
            int[] t = byType.computeIfAbsent(q.type(), k -> new int[2]);
            t[1]++;
            if (rank > 0) {
                hits++;
                t[0]++;
                reciprocalRankSum += 1.0 / rank;
            } else {
                misses.add(q.id() + " " + q.question());
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answerable", answerable);
        m.put("recallAtK", round((double) hits / answerable));
        m.put("mrr", round(reciprocalRankSum / answerable));
        Map<String, String> typeRecall = new LinkedHashMap<>();
        byType.forEach((type, t) -> typeRecall.put(type, t[0] + "/" + t[1]));
        m.put("recallByType", typeRecall);
        m.put("misses", misses);
        m.put("answerTop1Scores", answerTop1);
        m.put("refuseTop1Scores", refuseTop1);
        return m;
    }

    private Map<String, java.util.UUID> indexKnowledgeBase() throws Exception {
        Map<String, java.util.UUID> ids = new LinkedHashMap<>();
        Pattern idPattern = Pattern.compile("(?m)^id:\\s*(\\S+)");
        try (var files = Files.list(GoldenQuestion.KNOWLEDGE_DIR)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                byte[] content = Files.readAllBytes(p);
                Matcher m = idPattern.matcher(new String(content, java.nio.charset.StandardCharsets.UTF_8));
                assertThat(m.find()).isTrue();
                Document doc = documentService.upload(p.getFileName().toString(), content, null, "regression");
                ids.put(m.group(1), doc.getId());
            }
        }
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            boolean allReady = ids.values().stream()
                .allMatch(id -> documents.findById(id).orElseThrow().getStatus() == DocumentStatus.READY);
            if (allReady) {
                return ids;
            }
            assertThat(ids.values().stream().map(id -> documents.findById(id).orElseThrow().getStatus()))
                .doesNotContain(DocumentStatus.FAILED);
            Thread.sleep(500);
        }
        throw new AssertionError("indexing timed out");
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
