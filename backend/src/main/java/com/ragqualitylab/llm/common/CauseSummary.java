package com.ragqualitylab.llm.common;

/**
 * 외부 호출 실패 원인을 로그용으로 요약한다: 예외 사슬의 클래스 이름과 메시지 앞부분.
 * 요청 본문(질문·근거)은 메시지에 들어가지 않는 외부 오류 메시지만 다룬다.
 */
public final class CauseSummary {

    static final int MAX_MESSAGE = 300;

    private CauseSummary() {
    }

    public static String of(Throwable t) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (Throwable c = t; c != null && depth < 4; c = c.getCause(), depth++) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            sb.append(c.getClass().getSimpleName());
            if (c.getMessage() != null) {
                String m = c.getMessage().replaceAll("\\s+", " ");
                sb.append(": ").append(m.length() > MAX_MESSAGE ? m.substring(0, MAX_MESSAGE) + "…" : m);
            }
        }
        return sb.toString();
    }
}
