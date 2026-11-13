package com.ragqualitylab.document.application;

import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * DOC-01 업로드 검증: 확장자, 크기, 내용 시그니처(확장자 위장 방지), 파일명 정규화.
 * 저장 파일명은 UUID로 따로 만들고, 원본 파일명은 표시용 메타데이터로만 쓴다.
 */
@Component
public class UploadValidator {

    static final int MAX_FILENAME_LENGTH = 200;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final DocumentProperties properties;

    public UploadValidator(DocumentProperties properties) {
        this.properties = properties;
    }

    public ValidatedUpload validate(String originalFilename, byte[] content) {
        String filename = normalizeFilename(originalFilename);
        ContentKind kind = kindOf(filename);
        if (content.length == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "빈 파일은 업로드할 수 없습니다.");
        }
        if (content.length > properties.maxUploadSize().toBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                "파일은 " + properties.maxUploadSize().toMegabytes() + "MB 이하만 업로드할 수 있습니다.");
        }
        if (!signatureMatches(kind, content)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "파일 내용이 확장자와 맞지 않습니다.");
        }
        return new ValidatedUpload(filename, kind);
    }

    static String normalizeFilename(String raw) {
        String name = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "파일 이름이 올바르지 않습니다.");
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, MAX_FILENAME_LENGTH - ext.length()) + ext;
        }
        return name;
    }

    private static ContentKind kindOf(String filename) {
        int dot = filename.lastIndexOf('.');
        String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "md", "markdown" -> ContentKind.MARKDOWN;
            case "txt" -> ContentKind.TEXT;
            case "pdf" -> ContentKind.PDF;
            default -> throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "PDF, TXT, MD 파일만 업로드할 수 있습니다.");
        };
    }

    private static boolean signatureMatches(ContentKind kind, byte[] content) {
        if (kind == ContentKind.PDF) {
            if (content.length < PDF_MAGIC.length) {
                return false;
            }
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                if (content[i] != PDF_MAGIC[i]) {
                    return false;
                }
            }
            return true;
        }
        return isUtf8Text(content);
    }

    /** UTF-8로 엄격하게 해석되고 NUL 문자가 없어야 텍스트로 본다. */
    static boolean isUtf8Text(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString();
            return text.indexOf('\u0000') < 0;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    public record ValidatedUpload(String filename, ContentKind kind) {
    }
}
