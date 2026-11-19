package com.ragqualitylab.support;

import com.ragqualitylab.llm.embedding.EmbeddingPort;
import com.ragqualitylab.llm.embedding.EmbeddingUnavailableException;
import java.util.List;
import java.util.Locale;

/**
 * 테스트용 결정적 임베딩: 단어(공백·문장부호 기준)를 해시해 1024차원에 더한 뒤 정규화한다.
 * 같은 단어를 많이 공유할수록 가까워진다. 외부 서버를 쓰지 않는다.
 */
public class FakeEmbeddingPort implements EmbeddingPort {

    private volatile boolean failing;

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        check();
        return texts.stream().map(FakeEmbeddingPort::vectorOf).toList();
    }

    @Override
    public float[] embedQuery(String query) {
        check();
        return vectorOf(query);
    }

    @Override
    public String modelId() {
        return "fake-embedding";
    }

    @Override
    public int dimensions() {
        return 1024;
    }

    private void check() {
        if (failing) {
            throw new EmbeddingUnavailableException("fake failure", new RuntimeException());
        }
    }

    static float[] vectorOf(String text) {
        float[] v = new float[1024];
        for (String token : text.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+")) {
            if (!token.isBlank()) {
                v[Math.floorMod(token.hashCode(), 1024)] += 1f;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1f;
            return v;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= n;
        }
        return v;
    }
}
