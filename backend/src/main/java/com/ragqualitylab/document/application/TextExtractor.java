package com.ragqualitylab.document.application;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * 원본에서 텍스트를 추출한다. 스캔 PDF처럼 텍스트가 거의 없으면 NO_TEXT로 실패한다 (DOC-06: OCR은 MVP 제외).
 */
@Component
public class TextExtractor {

    /** 이보다 적은 글자(공백 제외)만 추출되면 텍스트가 없는 문서로 본다. */
    static final int MIN_TEXT_CHARS = 20;

    public ExtractedText extract(ContentKind kind, byte[] content) {
        ExtractedText extracted = switch (kind) {
            case MARKDOWN, TEXT -> new ExtractedText(List.of(new ExtractedText.Page(null, decodeText(content))));
            case PDF -> extractPdf(content);
        };
        if (extracted.nonBlankLength() < MIN_TEXT_CHARS) {
            throw new ProcessingFailure(ProcessingFailure.NO_TEXT, "추출된 텍스트가 없습니다", null);
        }
        return extracted;
    }

    static String decodeText(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static ExtractedText extractPdf(byte[] content) {
        try {
            var reader = new PagePdfDocumentReader(new ByteArrayResource(content));
            List<ExtractedText.Page> pages = new ArrayList<>();
            for (var doc : reader.get()) {
                Object pageNumber = doc.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
                String text = doc.getText() == null ? "" : doc.getText();
                pages.add(new ExtractedText.Page(pageNumber instanceof Number n ? n.intValue() : null, text));
            }
            return new ExtractedText(pages);
        } catch (RuntimeException e) {
            throw new ProcessingFailure(ProcessingFailure.TEXT_EXTRACTION_FAILED, "PDF 텍스트 추출 실패", e);
        }
    }
}
