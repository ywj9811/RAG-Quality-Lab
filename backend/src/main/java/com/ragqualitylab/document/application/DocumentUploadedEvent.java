package com.ragqualitylab.document.application;

import java.util.UUID;

/** 업로드·재처리 요청이 커밋된 뒤 비동기 처리를 시작하기 위한 이벤트. */
public record DocumentUploadedEvent(UUID documentId) {
}
