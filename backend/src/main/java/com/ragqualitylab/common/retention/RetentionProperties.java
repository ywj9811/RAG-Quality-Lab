package com.ragqualitylab.common.retention;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 보관 기간 (승인 #13, 04_erd 2절).
 * chatRetention: 대화(세션·메시지·검색 스냅샷·평가) 보관 기간, 기본 90일
 * deletedDocumentRetention: 삭제한 문서의 원본 파일·청크 행 보관 기간, 기본 30일
 */
@ConfigurationProperties("app.retention")
public record RetentionProperties(Duration chatRetention, Duration deletedDocumentRetention) {

    public RetentionProperties {
        if (chatRetention == null) {
            chatRetention = Duration.ofDays(90);
        }
        if (deletedDocumentRetention == null) {
            deletedDocumentRetention = Duration.ofDays(30);
        }
    }
}
