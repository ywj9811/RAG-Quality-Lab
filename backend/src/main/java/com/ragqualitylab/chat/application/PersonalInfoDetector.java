package com.ragqualitylab.chat.application;

import java.util.regex.Pattern;

/**
 * 외부 LLM으로 보내기 전에 흔한 개인정보 형태를 걸러낸다 (RK-04). 완전한 탐지는 아니며 실수 방지용이다.
 * 이메일, 휴대전화 번호, 주민등록번호 형태.
 */
final class PersonalInfoDetector {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)01[016789][-\\s.]?\\d{3,4}[-\\s.]?\\d{4}(?!\\d)");
    private static final Pattern RRN = Pattern.compile("(?<!\\d)\\d{6}[-\\s]?[1-4]\\d{6}(?!\\d)");

    private PersonalInfoDetector() {
    }

    static boolean containsPersonalInfo(String text) {
        return EMAIL.matcher(text).find() || MOBILE.matcher(text).find() || RRN.matcher(text).find();
    }
}
