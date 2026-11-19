-- LLM-as-a-Judge 평가 (EVAL-01~07, ADM-04~06, docs/phase0/04_erd.md)
CREATE TABLE evaluations (
    id                uuid         PRIMARY KEY,
    answer_message_id uuid         NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    status            varchar(20)  NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    rubric_version    varchar(20)  NOT NULL,
    judge_model_id    varchar(100) NOT NULL,
    prompt_version    varchar(100) NOT NULL,
    total_score       integer      CHECK (total_score BETWEEN 0 AND 100),
    input_snapshot    jsonb,
    raw_response      text,
    issues            jsonb,
    suggestions       jsonb,
    attempt_count     integer      NOT NULL DEFAULT 0,
    error_code        varchar(50),
    requested_by      uuid         REFERENCES admin_users (id) ON DELETE SET NULL,
    created_at        timestamptz  NOT NULL,
    started_at        timestamptz,
    completed_at      timestamptz
);
CREATE INDEX idx_evaluations_answer_created ON evaluations (answer_message_id, created_at DESC);
CREATE INDEX idx_evaluations_status ON evaluations (status, completed_at);
-- 같은 답변에 진행 중 평가는 하나만 (EVAL-05 중복 요청 정책)
CREATE UNIQUE INDEX uk_evaluations_in_progress ON evaluations (answer_message_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE evaluation_scores (
    id            uuid        PRIMARY KEY,
    evaluation_id uuid        NOT NULL REFERENCES evaluations (id) ON DELETE CASCADE,
    criterion     varchar(30) NOT NULL,
    score         integer     NOT NULL,
    max_score     integer     NOT NULL CHECK (max_score > 0),
    reason        text        NOT NULL,
    CONSTRAINT uk_evaluation_scores UNIQUE (evaluation_id, criterion),
    CONSTRAINT ck_evaluation_scores_range CHECK (score BETWEEN 0 AND max_score)
);
