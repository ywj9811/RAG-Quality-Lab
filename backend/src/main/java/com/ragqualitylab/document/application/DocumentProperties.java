package com.ragqualitylab.document.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 문서 업로드·저장·청크 설정 (승인 #13, 03_architecture 5절).
 * maxUploadSize: 업로드 최대 크기(기본 10MB)
 * uploadDir: 원본 파일 저장 디렉터리
 * chunkSizeChars / chunkOverlapChars: 청크 길이와 앞뒤 겹침(문자 수, 기본 800/120)
 */
@ConfigurationProperties("app.document")
public record DocumentProperties(DataSize maxUploadSize, String uploadDir, Integer chunkSizeChars,
                                 Integer chunkOverlapChars) {

    public DocumentProperties {
        if (maxUploadSize == null) {
            maxUploadSize = DataSize.ofMegabytes(10);
        }
        if (uploadDir == null || uploadDir.isBlank()) {
            uploadDir = "../data/uploads";
        }
        if (chunkSizeChars == null) {
            chunkSizeChars = 800;
        }
        if (chunkOverlapChars == null) {
            chunkOverlapChars = 120;
        }
        if (chunkOverlapChars >= chunkSizeChars / 2) {
            throw new IllegalArgumentException("chunk-overlap-chars는 chunk-size-chars의 절반보다 작아야 합니다.");
        }
    }
}
