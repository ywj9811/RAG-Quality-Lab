package com.ragqualitylab.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitationAndPiiTests {

    @Test
    void keepsOnlyCitationsWithinSourceRange() {
        CitationParser.Result r = CitationParser.parse("가 [1] 나 [3] 다 [0] 라 [2]", 2);
        assertThat(r.cited()).containsExactly(1, 2);
        assertThat(r.text()).isEqualTo("가 [1] 나  다  라 [2]");
    }

    @Test
    void splitsGroupedCitations() {
        CitationParser.Result r = CitationParser.parse("가 [2, 4]. 나 [1,9]. 다 [3][3]", 4);
        assertThat(r.cited()).containsExactly(2, 4, 1, 3);
        assertThat(r.text()).isEqualTo("가 [2][4]. 나 [1]. 다 [3][3]");
    }

    @Test
    void detectsCommonPersonalInfoPatterns() {
        assertThat(PersonalInfoDetector.containsPersonalInfo("a.b@example.co.kr")).isTrue();
        assertThat(PersonalInfoDetector.containsPersonalInfo("01012345678")).isTrue();
        assertThat(PersonalInfoDetector.containsPersonalInfo("주민번호 900101-1234567")).isTrue();
        assertThat(PersonalInfoDetector.containsPersonalInfo("@Transactional과 @Service 차이는?")).isFalse();
        assertThat(PersonalInfoDetector.containsPersonalInfo("HTTP 상태 코드 404와 500")).isFalse();
        assertThat(PersonalInfoDetector.containsPersonalInfo("timeout 30000 ms, port 18080")).isFalse();
    }
}
