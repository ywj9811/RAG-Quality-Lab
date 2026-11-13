-- 관리자 계정 (ADM-01, docs/phase0/04_erd.md)
CREATE TABLE admin_users (
    id              uuid         PRIMARY KEY,
    username        varchar(50)  NOT NULL,
    password_hash   varchar(255) NOT NULL,
    failed_attempts integer      NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until    timestamptz,
    last_login_at   timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_admin_users_username UNIQUE (username)
);
