-- 문서와 청크 (DOC-01~05, docs/phase0/04_erd.md, ADR-0003·0005)
CREATE TABLE documents (
    id                uuid         PRIMARY KEY,
    document_key      uuid         NOT NULL,
    version           integer      NOT NULL CHECK (version >= 1),
    original_filename varchar(255) NOT NULL,
    stored_path       varchar(500) NOT NULL,
    content_type      varchar(100) NOT NULL,
    size_bytes        bigint       NOT NULL CHECK (size_bytes >= 0),
    content_sha256    varchar(64)  NOT NULL CHECK (length(content_sha256) = 64),
    status            varchar(20)  NOT NULL CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    failure_code      varchar(50),
    chunk_count       integer      NOT NULL DEFAULT 0,
    uploaded_by       uuid         REFERENCES admin_users (id) ON DELETE SET NULL,
    uploaded_at       timestamptz  NOT NULL,
    processed_at      timestamptz,
    deleted_at        timestamptz,
    CONSTRAINT uk_documents_key_version UNIQUE (document_key, version)
);

-- 검색 대상 필터·목록 조회
CREATE INDEX idx_documents_active_status ON documents (status) WHERE deleted_at IS NULL;
-- 같은 내용 중복 업로드 확인
CREATE INDEX idx_documents_sha256_active ON documents (content_sha256) WHERE deleted_at IS NULL;

CREATE TABLE document_chunks (
    id              uuid         PRIMARY KEY,
    document_id     uuid         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index     integer      NOT NULL CHECK (chunk_index >= 0),
    content         text         NOT NULL,
    source_locator  jsonb        NOT NULL,
    embedding       vector(1024) NOT NULL,
    embedding_model varchar(100) NOT NULL,
    embedding_dim   integer      NOT NULL,
    created_at      timestamptz  NOT NULL,
    deleted_at      timestamptz,
    CONSTRAINT uk_document_chunks_doc_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_document_chunks_document ON document_chunks (document_id);
-- 코사인 거리 HNSW. 삭제 필터는 검색 SQL에서 적용한다
CREATE INDEX idx_document_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);
