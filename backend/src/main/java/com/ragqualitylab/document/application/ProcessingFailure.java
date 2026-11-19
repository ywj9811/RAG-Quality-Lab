package com.ragqualitylab.document.application;

/** 문서 처리 실패. code는 documents.failure_code에 저장되어 관리자 화면에 안내된다. */
public class ProcessingFailure extends RuntimeException {

    public static final String FILE_MISSING = "FILE_MISSING";
    public static final String TEXT_EXTRACTION_FAILED = "TEXT_EXTRACTION_FAILED";
    public static final String NO_TEXT = "NO_TEXT";
    public static final String EMBEDDING_UNAVAILABLE = "EMBEDDING_UNAVAILABLE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private final String code;

    public ProcessingFailure(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
