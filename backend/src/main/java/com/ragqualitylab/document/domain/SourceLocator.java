package com.ragqualitylab.document.domain;

/**
 * 청크의 원문 위치 (DOC-04). heading: 소제목, page: PDF 페이지(1부터, 없으면 null),
 * charStart/charEnd: 추출 텍스트(페이지 단위) 안에서의 문자 위치 [start, end).
 */
public record SourceLocator(String heading, Integer page, int charStart, int charEnd) {
}
