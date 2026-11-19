package com.ragqualitylab.llm.common;

/** 프롬프트 데이터 블록용 이스케이프. 데이터 안의 블록 태그 흉내를 무력화해 블록을 빠져나가지 못하게 한다 (명세 6절). */
public final class PromptBlocks {

    private PromptBlocks() {
    }

    public static String escape(String text) {
        return text == null ? "" : text.replaceAll("(?i)<(/?)(sources|source|history|question|answer|rubric)", "&lt;$1$2");
    }

    public static String escapeAttr(String text) {
        return escape(text).replace("\"", "'");
    }
}
