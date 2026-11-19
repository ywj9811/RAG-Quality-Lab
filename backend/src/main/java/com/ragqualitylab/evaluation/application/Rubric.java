package com.ragqualitylab.evaluation.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 평가 루브릭 (명세 5절, EVAL-03). Git으로 관리하는 rubric/v1.json을 읽는다. 만점 합은 100이어야 한다. */
@Component
public class Rubric {

    static final String RESOURCE = "rubric/v1.json";

    private final Definition definition;

    public Rubric(JsonMapper json) {
        try {
            this.definition = json.readValue(new ClassPathResource(RESOURCE).getInputStream(), Definition.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int sum = definition.criteria().stream().mapToInt(Criterion::maxScore).sum();
        if (sum != 100) {
            throw new IllegalStateException("루브릭 만점 합이 100이 아닙니다: " + sum);
        }
    }

    public String version() {
        return definition.version();
    }

    public List<Criterion> criteria() {
        return definition.criteria();
    }

    public List<String> keys() {
        return definition.criteria().stream().map(Criterion::key).toList();
    }

    public record Definition(String version, List<Criterion> criteria) {
    }

    public record Criterion(String key, String label, int maxScore, String question) {
    }
}
