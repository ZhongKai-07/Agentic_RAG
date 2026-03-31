-- ============================================================
-- Identity Context
-- ============================================================

CREATE TABLE t_user (
    user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(64)  NOT NULL UNIQUE,
    display_name  VARCHAR(128) NOT NULL,
    email         VARCHAR(256),
    bu            VARCHAR(32)  NOT NULL,
    team          VARCHAR(64)  NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'MEMBER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_bu_team ON t_user(bu, team);

CREATE TABLE t_knowledge_space (
    space_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(128)  NOT NULL,
    description       TEXT,
    owner_team        VARCHAR(64)   NOT NULL,
    language          VARCHAR(8)    NOT NULL DEFAULT 'zh',
    index_name        VARCHAR(128)  NOT NULL UNIQUE,
    retrieval_config  JSONB         NOT NULL DEFAULT '{}',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE t_access_rule (
    rule_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id               UUID        NOT NULL REFERENCES t_knowledge_space(space_id),
    target_type            VARCHAR(16) NOT NULL,
    target_value           VARCHAR(64) NOT NULL,
    doc_security_clearance VARCHAR(16) NOT NULL DEFAULT 'ALL',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_access_rule_space  ON t_access_rule(space_id);
CREATE INDEX idx_access_rule_target ON t_access_rule(target_type, target_value);

CREATE TABLE t_space_permission (
    permission_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES t_user(user_id),
    space_id       UUID        NOT NULL REFERENCES t_knowledge_space(space_id),
    access_level   VARCHAR(16) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, space_id)
);

-- ============================================================
-- Document Context
-- ============================================================

CREATE TABLE t_document (
    document_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id           UUID         NOT NULL REFERENCES t_knowledge_space(space_id),
    title              VARCHAR(512) NOT NULL,
    file_type          VARCHAR(16)  NOT NULL,
    security_level     VARCHAR(16)  NOT NULL DEFAULT 'ALL',
    status             VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED',
    current_version_id UUID,
    chunk_count        INTEGER      NOT NULL DEFAULT 0,
    uploaded_by        UUID         NOT NULL REFERENCES t_user(user_id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_space        ON t_document(space_id);
CREATE INDEX idx_document_space_status ON t_document(space_id, status);

CREATE TABLE t_document_version (
    version_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID          NOT NULL REFERENCES t_document(document_id) ON DELETE CASCADE,
    version_no    INTEGER       NOT NULL,
    file_path     VARCHAR(1024) NOT NULL,
    file_size     BIGINT        NOT NULL,
    checksum      VARCHAR(64)   NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    UUID          NOT NULL REFERENCES t_user(user_id),
    UNIQUE(document_id, version_no)
);

CREATE TABLE t_document_tag (
    tag_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL REFERENCES t_document(document_id) ON DELETE CASCADE,
    tag_name     VARCHAR(64) NOT NULL,
    UNIQUE(document_id, tag_name)
);
CREATE INDEX idx_document_tag_name ON t_document_tag(tag_name);

CREATE TABLE t_document_process_log (
    log_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID         NOT NULL REFERENCES t_document(document_id),
    version_id   UUID         NOT NULL REFERENCES t_document_version(version_id),
    action       VARCHAR(32)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    message      TEXT,
    started_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

-- ============================================================
-- Conversation Context
-- ============================================================

CREATE TABLE t_chat_session (
    session_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES t_user(user_id),
    space_id       UUID         NOT NULL REFERENCES t_knowledge_space(space_id),
    title          VARCHAR(256),
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_active_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_user_space ON t_chat_session(user_id, space_id);
CREATE INDEX idx_session_active     ON t_chat_session(user_id, status, last_active_at DESC);

CREATE TABLE t_message (
    message_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID        NOT NULL REFERENCES t_chat_session(session_id) ON DELETE CASCADE,
    role          VARCHAR(16) NOT NULL,
    content       TEXT        NOT NULL,
    agent_trace   JSONB,
    token_count   INTEGER,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_session ON t_message(session_id, created_at);

CREATE TABLE t_citation (
    citation_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id     UUID         NOT NULL REFERENCES t_message(message_id) ON DELETE CASCADE,
    citation_index INTEGER      NOT NULL,
    document_id    UUID         NOT NULL REFERENCES t_document(document_id),
    chunk_id       VARCHAR(128) NOT NULL,
    document_title VARCHAR(512) NOT NULL,
    page_number    INTEGER,
    section_path   VARCHAR(512),
    snippet        TEXT         NOT NULL
);
CREATE INDEX idx_citation_message ON t_citation(message_id);
