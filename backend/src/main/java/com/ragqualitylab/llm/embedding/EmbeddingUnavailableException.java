package com.ragqualitylab.llm.embedding;

/** 임베딩 서버(Ollama) 호출 실패. */
public class EmbeddingUnavailableException extends RuntimeException {

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
