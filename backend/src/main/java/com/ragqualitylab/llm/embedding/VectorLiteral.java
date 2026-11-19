package com.ragqualitylab.llm.embedding;

/** pgvector 입력 형식 '[0.1,0.2,...]' 문자열. 새 의존성 없이 ?::vector 캐스트로 전달한다 (ADR-0005). */
public final class VectorLiteral {

    private VectorLiteral() {
    }

    public static String of(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            float v = vector[i];
            if (!Float.isFinite(v)) {
                throw new IllegalArgumentException("Vector contains non-finite value at " + i);
            }
            sb.append(v);
        }
        return sb.append(']').toString();
    }
}
