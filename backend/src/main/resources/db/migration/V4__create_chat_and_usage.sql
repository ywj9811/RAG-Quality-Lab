-- 익명 채팅 세션·메시지·검색 스냅샷·LLM 사용량 (CHAT-01~08, docs/phase0/04_erd.md, ADR-0006·0007)
CREATE TABLE chat_sessions (
    id                uuid        PRIMARY KEY,
    access_token_hash varchar(64) NOT NULL CHECK (length(access_token_hash) = 64),
    status            varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED', 'EXPIRED')),
    client_ip_hash    varchar(64),
    created_at        timestamptz NOT NULL,
    last_activity_at  timestamptz NOT NULL,
    expires_at        timestamptz NOT NULL,
    closed_at         timestamptz,
    CONSTRAINT uk_chat_sessions_token UNIQUE (access_token_hash)
);
CREATE INDEX idx_chat_sessions_status_activity ON chat_sessions (status, last_activity_at);
CREATE INDEX idx_chat_sessions_created ON chat_sessions (created_at);

CREATE TABLE chat_messages (
    id               uuid        PRIMARY KEY,
    session_id       uuid        NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    seq              integer     NOT NULL CHECK (seq >= 1),
    role             varchar(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content          text        NOT NULL,
    answer_type      varchar(30) CHECK (answer_type IN ('ANSWERED', 'INSUFFICIENT_EVIDENCE', 'ERROR')),
    model_id         varchar(100),
    prompt_version   varchar(100),
    retrieval_query  text,
    retrieval_params jsonb,
    input_tokens     integer,
    output_tokens    integer,
    retrieval_ms     integer,
    generation_ms    integer,
    latency_ms       integer,
    error_code       varchar(50),
    created_at       timestamptz NOT NULL,
    CONSTRAINT uk_chat_messages_session_seq UNIQUE (session_id, seq)
);
CREATE INDEX idx_chat_messages_created ON chat_messages (created_at);

CREATE TABLE retrieval_records (
    id                         uuid             PRIMARY KEY,
    answer_message_id          uuid             NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    document_id                uuid             REFERENCES documents (id) ON DELETE SET NULL,
    chunk_id                   uuid             REFERENCES document_chunks (id) ON DELETE SET NULL,
    rank                       integer          NOT NULL CHECK (rank >= 1),
    score                      double precision NOT NULL,
    cited                      boolean          NOT NULL DEFAULT false,
    text_snapshot              text             NOT NULL,
    source_locator             jsonb            NOT NULL,
    document_filename_snapshot varchar(255)     NOT NULL,
    document_version_snapshot  integer          NOT NULL
);
CREATE INDEX idx_retrieval_records_answer ON retrieval_records (answer_message_id, rank);

CREATE TABLE llm_usage_records (
    id             uuid         PRIMARY KEY,
    operation_type varchar(20)  NOT NULL CHECK (operation_type IN ('EMBED_DOC', 'EMBED_QUERY', 'GENERATE', 'JUDGE')),
    provider       varchar(30)  NOT NULL,
    model_id       varchar(100) NOT NULL,
    message_id     uuid,
    evaluation_id  uuid,
    input_tokens   integer,
    output_tokens  integer,
    latency_ms     integer,
    status         varchar(20)  NOT NULL
        CHECK (status IN ('RESERVED', 'SUCCESS', 'RATE_LIMITED', 'QUOTA_BLOCKED', 'TIMEOUT', 'ERROR')),
    created_at     timestamptz  NOT NULL
);
CREATE INDEX idx_llm_usage_op_created ON llm_usage_records (operation_type, created_at);
