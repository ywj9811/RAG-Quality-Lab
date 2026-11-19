package com.ragqualitylab.retrieval.application;

import com.ragqualitylab.llm.embedding.EmbeddingPort;
import com.ragqualitylab.retrieval.infrastructure.ChunkSearchRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** 질의를 임베딩해 관련 청크를 찾는다. 채팅(Phase 3)과 골든셋 검색 회귀에서 사용한다. */
@Service
public class RetrievalService {

    private final EmbeddingPort embedding;
    private final ChunkSearchRepository repository;
    private final RetrievalProperties properties;

    public RetrievalService(EmbeddingPort embedding, ChunkSearchRepository repository, RetrievalProperties properties) {
        this.embedding = embedding;
        this.repository = repository;
        this.properties = properties;
    }

    public List<RetrievedChunk> search(String query) {
        return search(query, properties.topK());
    }

    public List<RetrievedChunk> search(String query, int topK) {
        return repository.search(embedding.embedQuery(query), topK);
    }
}
