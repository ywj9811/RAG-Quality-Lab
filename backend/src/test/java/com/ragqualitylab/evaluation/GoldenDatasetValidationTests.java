package com.ragqualitylab.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragqualitylab.document.application.Chunker;
import com.ragqualitylab.document.application.ContentKind;
import com.ragqualitylab.document.application.DocumentProperties;
import com.ragqualitylab.document.application.ExtractedText;
import com.ragqualitylab.document.domain.ChunkDraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * 골든셋 구조 검증 (08_knowledge_base_plan 4.3절 4번). 외부 호출 없이 매 빌드에서 실행한다.
 * 근거 소제목은 인덱싱과 같은 Chunker로 문서를 나눠 실제 존재하는지 확인한다.
 */
class GoldenDatasetValidationTests {

    static final Set<String> TYPES = Set.of("normal", "paraphrase", "multi_chunk", "out_of_scope", "insufficient");
    static List<GoldenQuestion> questions;
    static Map<String, Set<String>> headingsByDoc = new HashMap<>();

    @BeforeAll
    static void load() throws Exception {
        questions = GoldenQuestion.load();
        Chunker chunker = new Chunker(new DocumentProperties(DataSize.ofMegabytes(10), "/tmp", 800, 120));
        Pattern id = Pattern.compile("(?m)^id:\\s*(\\S+)");
        try (var files = Files.list(GoldenQuestion.KNOWLEDGE_DIR)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".md")).toList()) {
                String text = Files.readString(p);
                Matcher m = id.matcher(text);
                assertThat(m.find()).as("front matter id in %s", p).isTrue();
                List<ChunkDraft> chunks = chunker.chunk(
                    new ExtractedText(List.of(new ExtractedText.Page(null, text))), ContentKind.MARKDOWN, "x");
                headingsByDoc.put(m.group(1), chunks.stream().map(c -> c.locator().heading())
                    .filter(h -> h != null).collect(Collectors.toSet()));
            }
        }
    }

    @Test
    void hasFortyQuestionsWithUniqueIdsAndFivePerDocument() {
        assertThat(questions).hasSize(40);
        assertThat(questions.stream().map(GoldenQuestion::id).distinct()).hasSize(40);
        Map<String, Long> perDoc = questions.stream()
            .collect(Collectors.groupingBy(GoldenQuestion::documentId, Collectors.counting()));
        assertThat(perDoc).hasSize(8).allSatisfy((doc, n) -> assertThat(n).isEqualTo(5L));
        assertThat(perDoc.keySet()).isEqualTo(headingsByDoc.keySet());
    }

    @Test
    void eachDocumentHasThePlannedTypeMix() {
        Map<String, List<GoldenQuestion>> byDoc = questions.stream()
            .collect(Collectors.groupingBy(GoldenQuestion::documentId));
        byDoc.forEach((doc, qs) -> {
            Map<String, Long> types = qs.stream().collect(Collectors.groupingBy(GoldenQuestion::type, Collectors.counting()));
            assertThat(types.get("normal")).as(doc).isEqualTo(2L);
            assertThat(types.get("paraphrase")).as(doc).isEqualTo(1L);
            assertThat(types.get("multi_chunk")).as(doc).isEqualTo(1L);
            assertThat(types.getOrDefault("out_of_scope", 0L) + types.getOrDefault("insufficient", 0L)).as(doc).isEqualTo(1L);
        });
    }

    @Test
    void fieldsAreWellFormed() {
        for (GoldenQuestion q : questions) {
            assertThat(q.question()).as(q.id()).isNotBlank();
            assertThat(q.referenceAnswer()).as(q.id()).isNotBlank();
            assertThat(q.difficulty()).as(q.id()).isIn("easy", "medium", "hard");
            assertThat(q.expectedBehavior()).as(q.id()).isIn("ANSWER", "PARTIAL", "REFUSE");
            assertThat(q.reviewStatus()).as(q.id()).isIn("DRAFT", "APPROVED");
            assertThat(TYPES).as(q.id()).contains(q.type());
            if (q.expectedBehavior().equals("REFUSE")) {
                assertThat(q.supportingSections()).as(q.id()).isEmpty();
            } else {
                assertThat(q.supportingSections()).as(q.id()).isNotEmpty();
            }
            if (q.type().equals("multi_chunk")) {
                assertThat(q.supportingSections()).as(q.id()).hasSizeGreaterThanOrEqualTo(2);
            }
        }
    }

    @Test
    void supportingSectionsExistInTheIndexedDocuments() {
        Set<String> missing = new HashSet<>();
        for (GoldenQuestion q : questions) {
            for (String section : q.supportingSections()) {
                String[] parts = section.split("#", 2);
                if (!headingsByDoc.getOrDefault(parts[0], Set.of()).contains(parts[1])) {
                    missing.add(q.id() + " → " + section);
                }
            }
        }
        assertThat(missing).isEmpty();
    }
}
