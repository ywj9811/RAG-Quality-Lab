package com.ragqualitylab.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class UploadValidatorTests {

    final UploadValidator validator = new UploadValidator(
        new DocumentProperties(DataSize.ofBytes(100), "/tmp/x", 800, 120));

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static ErrorCode codeOf(Runnable r) {
        try {
            r.run();
        } catch (ApiException e) {
            return e.code();
        }
        throw new AssertionError("expected ApiException");
    }

    @Test
    void acceptsMarkdownTextAndPdf() {
        assertThat(validator.validate("a.md", utf8("# 제목")).kind()).isEqualTo(ContentKind.MARKDOWN);
        assertThat(validator.validate("a.TXT", utf8("본문")).kind()).isEqualTo(ContentKind.TEXT);
        assertThat(validator.validate("a.pdf", utf8("%PDF-1.7 ...")).kind()).isEqualTo(ContentKind.PDF);
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThat(codeOf(() -> validator.validate("a.docx", utf8("x")))).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertThat(codeOf(() -> validator.validate("noext", utf8("x")))).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsDisguisedContent() {
        assertThat(codeOf(() -> validator.validate("fake.pdf", utf8("not a pdf")))).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        byte[] binary = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01};
        assertThat(codeOf(() -> validator.validate("bin.txt", binary))).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertThat(codeOf(() -> validator.validate("nul.md", utf8("a\u0000b")))).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsEmptyAndTooLarge() {
        assertThat(codeOf(() -> validator.validate("a.md", new byte[0]))).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(codeOf(() -> validator.validate("a.md", new byte[101]))).isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void normalizesFilename() {
        assertThat(UploadValidator.normalizeFilename("../../etc/passwd.md")).isEqualTo("passwd.md");
        assertThat(UploadValidator.normalizeFilename("C:\\Users\\me\\노트.md")).isEqualTo("노트.md");
        assertThat(UploadValidator.normalizeFilename("a\u0007b.md")).isEqualTo("ab.md");
        // NFD(맥 파일명) → NFC
        assertThat(UploadValidator.normalizeFilename("\u1112\u1161\u11ab.md")).isEqualTo("한.md");
        String longName = "가".repeat(300) + ".md";
        String normalized = UploadValidator.normalizeFilename(longName);
        assertThat(normalized).hasSize(UploadValidator.MAX_FILENAME_LENGTH).endsWith(".md");
        assertThatThrownBy(() -> UploadValidator.normalizeFilename("../")).isInstanceOf(ApiException.class);
    }
}
