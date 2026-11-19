package com.ragqualitylab.retrieval.application;

import com.ragqualitylab.document.domain.SourceLocator;
import java.util.UUID;

/** 검색 결과 한 건. score는 코사인 유사도(1 - 코사인 거리), 클수록 가깝다. */
public record RetrievedChunk(int rank, UUID chunkId, UUID documentId, String filename, int documentVersion,
                             int chunkIndex, String content, SourceLocator locator, double score) {
}
