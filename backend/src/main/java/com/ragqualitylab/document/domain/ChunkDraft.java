package com.ragqualitylab.document.domain;

/** 저장 전 청크. content는 임베딩·검색에 쓰는 텍스트(문서 제목과 소제목을 앞에 붙인다). */
public record ChunkDraft(int index, String content, SourceLocator locator) {
}
