package com.ragqualitylab.chat.domain;

/** ANSWERED: 근거로 답함, INSUFFICIENT_EVIDENCE: 근거 부족·범위 밖 안내(CHAT-05), ERROR: 생성 실패. */
public enum AnswerType {
    ANSWERED, INSUFFICIENT_EVIDENCE, ERROR
}
