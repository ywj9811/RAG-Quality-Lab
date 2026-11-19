package com.ragqualitylab.llm.generation;

public record GenerationResult(String text, String modelId, Integer inputTokens, Integer outputTokens) {
}
