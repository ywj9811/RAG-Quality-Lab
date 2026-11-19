package com.ragqualitylab.chat.application;

import com.ragqualitylab.document.domain.SourceLocator;
import java.util.UUID;

/** 답변에 표시하는 인용 (CHAT-04). snippet은 청크 본문 앞부분. */
public record Citation(int index, UUID documentId, String filename, SourceLocator sourceLocator, String snippet) {
}
