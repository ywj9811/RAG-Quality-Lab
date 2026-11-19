package com.ragqualitylab.llm.embedding;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Spring AI EmbeddingModel(Ollama) 어댑터. 반환 차원이 설정과 다르면 저장하지 않도록 실패시킨다. */
@Component
public class OllamaEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;
    private final String modelId;

    public OllamaEmbeddingAdapter(EmbeddingModel embeddingModel, EmbeddingProperties properties,
                                  @Value("${spring.ai.ollama.embedding.model}") String modelId) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.modelId = modelId;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += properties.batchSize()) {
            List<String> batch = texts.subList(from, Math.min(from + properties.batchSize(), texts.size()));
            result.addAll(call(() -> embeddingModel.embed(batch)));
        }
        result.forEach(this::checkDimensions);
        return result;
    }

    @Override
    public float[] embedQuery(String query) {
        String input = properties.queryInstruction().isEmpty() ? query : properties.queryInstruction() + query;
        float[] vector = call(() -> embeddingModel.embed(input));
        checkDimensions(vector);
        return vector;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public int dimensions() {
        return properties.dimensions();
    }

    private void checkDimensions(float[] vector) {
        if (vector.length != properties.dimensions()) {
            throw new IllegalStateException(
                "임베딩 차원 불일치: 기대 " + properties.dimensions() + ", 실제 " + vector.length + " (모델 " + modelId + ")");
        }
    }

    private static <T> T call(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EmbeddingUnavailableException("임베딩 서버 호출 실패", e);
        }
    }
}
