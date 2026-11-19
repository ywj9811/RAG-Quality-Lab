package com.ragqualitylab.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/** evaluation/golden_questions.jsonl 한 줄 (08_knowledge_base_plan 4.2절). */
record GoldenQuestion(
    String id,
    @JsonProperty("document_id") String documentId,
    String question,
    @JsonProperty("reference_answer") String referenceAnswer,
    @JsonProperty("supporting_sections") List<String> supportingSections,
    @JsonProperty("expected_behavior") String expectedBehavior,
    String difficulty,
    List<String> tags,
    @JsonProperty("review_status") String reviewStatus,
    @JsonProperty("reviewed_at") String reviewedAt) {

    static final Path FILE = Path.of("../evaluation/golden_questions.jsonl");
    static final Path KNOWLEDGE_DIR = Path.of("../knowledge/base");

    String type() {
        return tags.getFirst();
    }

    static List<GoldenQuestion> load() throws IOException {
        JsonMapper json = JsonMapper.builder().build();
        return Files.readAllLines(FILE).stream()
            .filter(line -> !line.isBlank())
            .map(line -> json.readValue(line, GoldenQuestion.class))
            .toList();
    }
}
