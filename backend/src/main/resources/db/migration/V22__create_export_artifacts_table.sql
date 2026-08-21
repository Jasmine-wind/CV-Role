-- Phase 6 is intentionally additive. V1 tables and the Phase 2/3/4 formal domain remain
-- untouched; rolling the application back only requires deploying the previous version,
-- the new table stays unused until the Phase 6 application is restored.
--
-- export_artifacts records successfully generated PDF derivations of the TARGET resume
-- version. A row only exists after PDF compilation, object storage write and this insert
-- all succeed; the application compensates (deletes the stored object) when the insert
-- fails. Template identity and versions plus the renderer version are recorded so a past
-- export stays explainable even after templates or the renderer evolve.

-- Phase 2 gave optimization_tasks no composite (id, user_id) uniqueness; export_artifacts
-- needs it for the composite ownership foreign key. Adding it is safe because id is
-- already the primary key.
CREATE UNIQUE INDEX IF NOT EXISTS uk_optimization_tasks_id_user_id
    ON optimization_tasks (id, user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_optimization_tasks_id_user_target_version
    ON optimization_tasks (id, user_id, target_resume_version_id);

CREATE TABLE export_artifacts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    optimization_task_id BIGINT NOT NULL,
    target_resume_version_id BIGINT NOT NULL,
    content_revision BIGINT NOT NULL,
    template_id VARCHAR(30) NOT NULL,
    template_version VARCHAR(20) NOT NULL,
    renderer_version VARCHAR(50) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    file_size BIGINT NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'READY',
    page_count INT NOT NULL,
    missing_contact BOOLEAN NOT NULL,
    page_limit_exceeded BOOLEAN NOT NULL,
    overflow_detected BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_export_artifacts_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_export_artifacts_task_target_owner
        FOREIGN KEY (optimization_task_id, user_id, target_resume_version_id)
        REFERENCES optimization_tasks (id, user_id, target_resume_version_id) ON DELETE CASCADE,
    CONSTRAINT fk_export_artifacts_target_version_owner
        FOREIGN KEY (target_resume_version_id, user_id)
        REFERENCES resume_versions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT uk_export_artifacts_id_user_id UNIQUE (id, user_id),
    CONSTRAINT ck_export_artifacts_content_revision_nonnegative
        CHECK (content_revision >= 0),
    CONSTRAINT ck_export_artifacts_file_size_positive
        CHECK (file_size > 0),
    CONSTRAINT ck_export_artifacts_template_id_not_blank
        CHECK (btrim(template_id) <> ''),
    CONSTRAINT ck_export_artifacts_template_version_not_blank
        CHECK (btrim(template_version) <> ''),
    CONSTRAINT ck_export_artifacts_renderer_version_not_blank
        CHECK (btrim(renderer_version) <> ''),
    CONSTRAINT ck_export_artifacts_storage_key_not_blank
        CHECK (btrim(storage_key) <> ''),
    CONSTRAINT ck_export_artifacts_checksum_sha256_hex
        CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_export_artifacts_status
        CHECK (status IN ('READY', 'DELETE_PENDING')),
    CONSTRAINT ck_export_artifacts_page_count_positive
        CHECK (page_count > 0)
);

CREATE INDEX idx_export_artifacts_user_task_created_at
    ON export_artifacts (user_id, optimization_task_id, created_at DESC);
