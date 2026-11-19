package com.ragqualitylab.chat.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 익명 채팅 설정 (CHAT-03·07·08, 승인 #11·#12, 03_architecture 5절).
 * idleTimeout / maxLifetime: 세션 유휴 만료(30분) / 절대 만료(24시간)
 * cookieSecure: HTTPS 배포 시 true
 * maxQuestionChars: 질문 최대 길이(1000자)
 * perIpPerMinute / perSessionPerMinute: 분당 요청 상한(10 / 5)
 * sessionsPerIpPerHour: IP별 시간당 세션 생성 상한(20)
 * ipHashSalt: IP 해시 솔트. 비어 있으면 기동 시 무작위 값(재시작하면 같은 IP도 다른 해시)
 */
@ConfigurationProperties("app.chat")
public record ChatProperties(Duration idleTimeout, Duration maxLifetime, Boolean cookieSecure,
                             Integer maxQuestionChars, Integer perIpPerMinute, Integer perSessionPerMinute,
                             Integer sessionsPerIpPerHour, String ipHashSalt) {

    public ChatProperties {
        if (idleTimeout == null) {
            idleTimeout = Duration.ofMinutes(30);
        }
        if (maxLifetime == null) {
            maxLifetime = Duration.ofHours(24);
        }
        if (cookieSecure == null) {
            cookieSecure = false;
        }
        if (maxQuestionChars == null) {
            maxQuestionChars = 1000;
        }
        if (perIpPerMinute == null) {
            perIpPerMinute = 10;
        }
        if (perSessionPerMinute == null) {
            perSessionPerMinute = 5;
        }
        if (sessionsPerIpPerHour == null) {
            sessionsPerIpPerHour = 20;
        }
        if (ipHashSalt == null) {
            ipHashSalt = "";
        }
    }
}
