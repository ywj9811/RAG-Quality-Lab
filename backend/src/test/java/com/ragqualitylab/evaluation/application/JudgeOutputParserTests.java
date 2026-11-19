package com.ragqualitylab.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ragqualitylab.support.FakeJudgeModelPort;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JudgeOutputParserTests {

    final JsonMapper json = JsonMapper.builder().build();
    final Rubric rubric = new Rubric(json);

    @Test
    void serverComputesTotalIgnoringModelTotal() {
        var parsed = JudgeOutputParser.parse(FakeJudgeModelPort.VALID, rubric, json);
        assertThat(parsed.total()).isEqualTo(83);
        assertThat(parsed.scores()).extracting(s -> s.criterion())
            .containsExactly("faithfulness", "relevance", "completeness", "citationAccuracy");
        assertThat(parsed.issues()).containsExactly("설명 누락");
    }

    @Test
    void acceptsCodeFences() {
        assertThat(JudgeOutputParser.parse("```json\n" + FakeJudgeModelPort.VALID + "\n```", rubric, json).total()).isEqualTo(83);
    }

    @Test
    void rejectsInvalidOutputs() {
        assertThatThrownBy(() -> JudgeOutputParser.parse("점수는 80점입니다", rubric, json))
            .isInstanceOf(JudgeOutputParser.InvalidJudgeOutput.class);
        assertThatThrownBy(() -> JudgeOutputParser.parse(FakeJudgeModelPort.VALID.replace("\"score\":30", "\"score\":36"), rubric, json))
            .hasMessageContaining("범위");
        assertThatThrownBy(() -> JudgeOutputParser.parse(FakeJudgeModelPort.VALID.replace("\"score\":23", "\"score\":-1"), rubric, json))
            .hasMessageContaining("범위");
        assertThatThrownBy(() -> JudgeOutputParser.parse(FakeJudgeModelPort.VALID.replace("\"score\":15,\"reason\":\"조건 일부 누락\"", "\"score\":15.5,\"reason\":\"x\""), rubric, json))
            .hasMessageContaining("정수");
        assertThatThrownBy(() -> JudgeOutputParser.parse(FakeJudgeModelPort.VALID.replace("\"reason\":\"근거에 부합\"", "\"reason\":\" \""), rubric, json))
            .hasMessageContaining("사유");
        assertThatThrownBy(() -> JudgeOutputParser.parse("{\"scores\":{\"faithfulness\":{\"score\":1,\"reason\":\"x\"}}}", rubric, json))
            .hasMessageContaining("relevance");
    }

    @Test
    void rubricWeightsMatchSpec() {
        assertThat(rubric.version()).isEqualTo("v1");
        assertThat(rubric.criteria()).extracting(Rubric.Criterion::maxScore).containsExactly(35, 25, 20, 20);
    }
}
