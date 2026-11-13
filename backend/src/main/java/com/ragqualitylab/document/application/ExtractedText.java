package com.ragqualitylab.document.application;

import java.util.List;

/** 추출된 텍스트. PDF는 페이지별, 텍스트·마크다운은 페이지 번호 없이 한 덩어리. */
public record ExtractedText(List<Page> pages) {

    public record Page(Integer pageNumber, String text) {
    }

    public int nonBlankLength() {
        return pages.stream().mapToInt(p -> p.text().replaceAll("\\s", "").length()).sum();
    }
}
