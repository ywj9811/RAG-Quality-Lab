package com.ragqualitylab.chat.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 세션 토큰: 256비트 난수(Base64URL), 저장은 SHA-256 16진수 (ADR-0006). */
public final class SessionTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 시간 차이로 값을 추측하지 못하도록 상수 시간 비교. */
    public static boolean matches(String token, String expectedHash) {
        if (token == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256Hex(token).getBytes(StandardCharsets.US_ASCII),
            expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
