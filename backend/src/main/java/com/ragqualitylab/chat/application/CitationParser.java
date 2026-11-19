package com.ragqualitylab.chat.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 답변의 인용 표시를 확인한다 (CHAT-04, RK-09).
 * [1], [1, 3], [1][3] 형식을 인식해 [1][3]처럼 하나씩 다시 쓰고, 근거 범위를 벗어난 번호는 지운다.
 */
final class CitationParser {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2}(?:\\s*,\\s*\\d{1,2})*)]");

    private CitationParser() {
    }

    static Result parse(String answer, int sourceCount) {
        Set<Integer> cited = new LinkedHashSet<>();
        StringBuilder out = new StringBuilder();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            List<Integer> valid = new ArrayList<>();
            for (String part : m.group(1).split(",")) {
                int n = Integer.parseInt(part.strip());
                if (n >= 1 && n <= sourceCount && !valid.contains(n)) {
                    valid.add(n);
                }
            }
            cited.addAll(valid);
            StringBuilder replacement = new StringBuilder();
            valid.forEach(n -> replacement.append('[').append(n).append(']'));
            m.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }
        m.appendTail(out);
        return new Result(out.toString().replaceAll("[ \\t]+\\n", "\n").strip(), cited);
    }

    record Result(String text, Set<Integer> cited) {
    }
}
