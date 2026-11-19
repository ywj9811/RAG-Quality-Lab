package com.ragqualitylab.evaluation.application;

import java.util.List;

/**
 * Judge 입력 스냅샷 (EVAL-02): 질문, 직전 대화, 답변, 생성 당시 검색 근거.
 * 골든셋 reference_answer와 생성 모델 이름은 넣지 않는다 (명세 3.3, RK-01).
 */
public record JudgeInput(String question, List<Turn> history, String answer, List<Source> sources) {

    public record Turn(String question, String answer) {
    }

    public record Source(int index, String label, String text) {
    }
}
