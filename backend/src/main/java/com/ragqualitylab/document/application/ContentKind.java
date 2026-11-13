package com.ragqualitylab.document.application;

/** 지원 파일 종류 (DOC-01). */
public enum ContentKind {
    MARKDOWN("text/markdown"),
    TEXT("text/plain"),
    PDF("application/pdf");

    private final String contentType;

    ContentKind(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    public static ContentKind fromContentType(String contentType) {
        for (ContentKind kind : values()) {
            if (kind.contentType.equals(contentType)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown content type: " + contentType);
    }
}
