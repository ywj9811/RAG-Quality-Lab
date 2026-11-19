package com.ragqualitylab.admin.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 인증 설정 (ADM-01, docs/phase0/05_api_contract.md 2절).
 * maxFailedAttempts: 잠금까지 허용하는 연속 실패 횟수(기본 5)
 * lockDuration: 잠금 시간(기본 15분)
 * minPasswordLength: 초기 관리자 비밀번호 최소 길이(기본 12)
 */
@ConfigurationProperties("app.admin")
public record AdminAuthProperties(Integer maxFailedAttempts, Duration lockDuration, Integer minPasswordLength) {

    public AdminAuthProperties {
        if (maxFailedAttempts == null) {
            maxFailedAttempts = 5;
        }
        if (lockDuration == null) {
            lockDuration = Duration.ofMinutes(15);
        }
        if (minPasswordLength == null) {
            minPasswordLength = 12;
        }
    }
}
