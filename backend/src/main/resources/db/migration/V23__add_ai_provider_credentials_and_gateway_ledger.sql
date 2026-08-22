-- Phase 7 is additive: deployment-owned system credentials remain configuration only.
-- User credentials are encrypted application-side before persistence.

CREATE TABLE ai_provider_credentials (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider_type VARCHAR(50) NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
    base_url VARCHAR(2048) NOT NULL,
    encrypted_api_key TEXT NOT NULL,
    encryption_key_version VARCHAR(100) NOT NULL,
    model VARCHAR(200) NOT NULL,
    config_json TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'DISABLED',
    credential_revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_provider_credentials_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_ai_provider_credentials_user_provider UNIQUE (user_id, provider_type),
    CONSTRAINT uk_ai_provider_credentials_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_ai_provider_credentials_provider
        CHECK (provider_type = 'OPENAI_COMPATIBLE'),
    CONSTRAINT ck_ai_provider_credentials_base_url_not_blank
        CHECK (btrim(base_url) <> ''),
    CONSTRAINT ck_ai_provider_credentials_key_not_blank
        CHECK (btrim(encrypted_api_key) <> ''),
    CONSTRAINT ck_ai_provider_credentials_model_not_blank
        CHECK (btrim(model) <> ''),
    CONSTRAINT ck_ai_provider_credentials_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_ai_provider_credentials_revision_positive
        CHECK (credential_revision > 0)
);

CREATE INDEX idx_ai_provider_credentials_user_status
    ON ai_provider_credentials (user_id, status);

ALTER TABLE optimization_tasks
    ALTER COLUMN model_snapshot TYPE VARCHAR(200),
    ADD COLUMN ai_source_snapshot VARCHAR(30) NOT NULL DEFAULT 'SYSTEM_DEFAULT',
    ADD COLUMN ai_provider_snapshot VARCHAR(50),
    -- ai_credential_id is a nullable live reference; the immutable ID is retained
    -- separately so a deleted Credential can fail closed without deleting history.
    ADD COLUMN ai_credential_id BIGINT,
    ADD COLUMN ai_credential_id_snapshot BIGINT,
    ADD COLUMN ai_credential_revision BIGINT,
    ADD COLUMN ai_base_url_snapshot VARCHAR(2048),
    ADD COLUMN ai_config_snapshot TEXT;

ALTER TABLE optimization_tasks
    ADD CONSTRAINT fk_optimization_tasks_ai_credential
        FOREIGN KEY (ai_credential_id, user_id)
        REFERENCES ai_provider_credentials (id, user_id)
        ON DELETE SET NULL (ai_credential_id),
    ADD CONSTRAINT ck_optimization_tasks_ai_source
        CHECK (ai_source_snapshot IN ('SYSTEM_DEFAULT', 'USER_BYOK')),
    ADD CONSTRAINT ck_optimization_tasks_ai_byok_snapshot
        CHECK (
            ai_source_snapshot <> 'USER_BYOK'
            OR (
                ai_provider_snapshot = 'OPENAI_COMPATIBLE'
                AND ai_credential_id_snapshot IS NOT NULL
                AND ai_credential_revision IS NOT NULL
                AND ai_base_url_snapshot IS NOT NULL
                AND model_snapshot IS NOT NULL
                AND ai_config_snapshot IS NOT NULL
            )
        );

CREATE INDEX idx_optimization_tasks_ai_credential_id
    ON optimization_tasks (ai_credential_id);

CREATE TABLE ai_usage_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    optimization_task_id BIGINT,
    operation VARCHAR(100) NOT NULL,
    source VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(200) NOT NULL,
    credential_revision BIGINT,
    outcome VARCHAR(20) NOT NULL,
    failure_code VARCHAR(100),
    latency_ms BIGINT NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_usage_records_user_id
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_usage_records_task_id
        FOREIGN KEY (optimization_task_id, user_id)
        REFERENCES optimization_tasks (id, user_id)
        ON DELETE SET NULL (optimization_task_id),
    CONSTRAINT ck_ai_usage_records_source
        CHECK (source IN ('SYSTEM_DEFAULT', 'USER_BYOK')),
    CONSTRAINT ck_ai_usage_records_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT ck_ai_usage_records_latency_nonnegative
        CHECK (latency_ms >= 0)
);

CREATE INDEX idx_ai_usage_records_user_created
    ON ai_usage_records (user_id, created_at DESC);

CREATE INDEX idx_ai_usage_records_task_created
    ON ai_usage_records (optimization_task_id, created_at DESC);
