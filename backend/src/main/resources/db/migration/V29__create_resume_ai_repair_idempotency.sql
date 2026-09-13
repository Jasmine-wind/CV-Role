-- Durable coordination for the optional, reference-only resume repair call.
-- A CLAIMED row is committed before any provider dispatch. It is intentionally terminal:
-- an abandoned claim is never reclaimed, because a crashed worker may have dispatched a
-- request whose result was lost. A terminal FAILED_NO_DISPATCH row may be claimed again,
-- because that state is proven safe to retry. This preserves at-most-once dispatch after
-- an accepted provider request while retaining recovery for local pre-dispatch failures.
CREATE TABLE resume_ai_repair_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_id BIGINT,
    repair_key VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner_token VARCHAR(64),
    result_json TEXT,
    failure_reason VARCHAR(500),
    provider_dispatch_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resume_ai_repair_attempts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_resume_ai_repair_attempts_resume_owner
        FOREIGN KEY (resume_id, user_id)
        REFERENCES resumes (id, user_id) ON DELETE CASCADE,
    CONSTRAINT uk_resume_ai_repair_attempts_user_key
        UNIQUE (user_id, repair_key),
    CONSTRAINT ck_resume_ai_repair_attempts_key
        CHECK (btrim(repair_key) <> ''),
    CONSTRAINT ck_resume_ai_repair_attempts_status
        CHECK (status IN ('CLAIMED', 'SUCCEEDED', 'FAILED_NO_DISPATCH', 'FAILED_AFTER_DISPATCH')),
    CONSTRAINT ck_resume_ai_repair_attempts_dispatch_count
        CHECK (provider_dispatch_count >= 0),
    CONSTRAINT ck_resume_ai_repair_attempts_terminal_shape
        CHECK (
            (status = 'CLAIMED'
                AND owner_token IS NOT NULL AND btrim(owner_token) <> ''
                AND result_json IS NULL
                AND failure_reason IS NULL)
            OR
            (status = 'SUCCEEDED'
                AND owner_token IS NULL
                AND result_json IS NOT NULL AND btrim(result_json) <> ''
                AND failure_reason IS NULL
                AND provider_dispatch_count > 0)
            OR
            (status IN ('FAILED_NO_DISPATCH', 'FAILED_AFTER_DISPATCH')
                AND owner_token IS NULL
                AND result_json IS NULL
                AND failure_reason IS NOT NULL AND btrim(failure_reason) <> '')
        )
);

CREATE INDEX idx_resume_ai_repair_attempts_resume_updated
    ON resume_ai_repair_attempts (user_id, resume_id, updated_at DESC);
CREATE INDEX idx_resume_ai_repair_attempts_status_updated
    ON resume_ai_repair_attempts (status, updated_at);
