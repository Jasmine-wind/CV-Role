-- Phase 2 is intentionally additive. V1 tables remain the compatibility source for
-- parsing and matching while the formal domain becomes authoritative for the main flow.
-- Rolling the application back therefore only requires deploying the previous version;
-- these tables can remain unused until the Phase 2 application is restored.

CREATE UNIQUE INDEX IF NOT EXISTS uk_resumes_id_user_id
    ON resumes (id, user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_job_descriptions_id_user_id
    ON job_descriptions (id, user_id);

CREATE TABLE job_targets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    legacy_job_description_id BIGINT,
    title VARCHAR(200) NOT NULL,
    raw_jd TEXT NOT NULL,
    source_type VARCHAR(30) NOT NULL DEFAULT 'USER_INPUT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_targets_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_job_targets_legacy_job_description_owner
        FOREIGN KEY (legacy_job_description_id, user_id)
        REFERENCES job_descriptions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT uk_job_targets_id_user_id UNIQUE (id, user_id),
    CONSTRAINT uk_job_targets_legacy_job_description_id UNIQUE (legacy_job_description_id),
    CONSTRAINT ck_job_targets_raw_jd_not_blank CHECK (btrim(raw_jd) <> ''),
    CONSTRAINT ck_job_targets_source_type CHECK (source_type IN ('USER_INPUT', 'LEGACY_IMPORT'))
);

CREATE INDEX idx_job_targets_user_id_created_at
    ON job_targets (user_id, created_at DESC);

CREATE TABLE resume_versions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    source_version_id BIGINT,
    job_target_id BIGINT,
    legacy_match_result_id BIGINT,
    version_type VARCHAR(20) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    content_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    structured_content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resume_versions_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_resume_versions_resume_owner
        FOREIGN KEY (resume_id, user_id)
        REFERENCES resumes (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_resume_versions_source_owner
        FOREIGN KEY (source_version_id, user_id)
        REFERENCES resume_versions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_resume_versions_job_target_owner
        FOREIGN KEY (job_target_id, user_id)
        REFERENCES job_targets (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_resume_versions_legacy_match_result
        FOREIGN KEY (legacy_match_result_id)
        REFERENCES ai_job_match_results (id) ON DELETE CASCADE,
    CONSTRAINT uk_resume_versions_id_user_id UNIQUE (id, user_id),
    CONSTRAINT uk_resume_versions_legacy_match_result_id UNIQUE (legacy_match_result_id),
    CONSTRAINT ck_resume_versions_version_type CHECK (version_type IN ('SOURCE', 'TARGETED')),
    CONSTRAINT ck_resume_versions_source_type CHECK (source_type IN ('PARSED_UPLOAD', 'JOB_DERIVATION', 'LEGACY_IMPORT')),
    CONSTRAINT ck_resume_versions_content_status CHECK (content_status IN ('PENDING', 'READY', 'FAILED')),
    CONSTRAINT ck_resume_versions_shape CHECK (
        (version_type = 'SOURCE' AND source_version_id IS NULL AND job_target_id IS NULL)
        OR
        (version_type = 'TARGETED' AND source_version_id IS NOT NULL AND job_target_id IS NOT NULL)
    ),
    CONSTRAINT ck_resume_versions_ready_content CHECK (
        content_status <> 'READY' OR (structured_content IS NOT NULL AND btrim(structured_content) <> '')
    )
);

CREATE INDEX idx_resume_versions_user_resume_created_at
    ON resume_versions (user_id, resume_id, created_at DESC);

CREATE INDEX idx_resume_versions_user_job_target
    ON resume_versions (user_id, job_target_id);

CREATE TABLE optimization_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_resume_version_id BIGINT NOT NULL,
    target_resume_version_id BIGINT NOT NULL,
    job_target_id BIGINT NOT NULL,
    async_task_id BIGINT,
    analysis_result_id BIGINT,
    legacy_match_result_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resume_input_snapshot TEXT,
    job_input_snapshot TEXT NOT NULL,
    prompt_snapshot TEXT NOT NULL DEFAULT '{}',
    rules_snapshot TEXT NOT NULL DEFAULT '{}',
    provider_snapshot VARCHAR(100),
    model_snapshot VARCHAR(100),
    template_version VARCHAR(50) NOT NULL DEFAULT 'NOT_SELECTED',
    error_code VARCHAR(100),
    error_message VARCHAR(1000),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_optimization_tasks_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_optimization_tasks_source_version_owner
        FOREIGN KEY (source_resume_version_id, user_id)
        REFERENCES resume_versions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_optimization_tasks_target_version_owner
        FOREIGN KEY (target_resume_version_id, user_id)
        REFERENCES resume_versions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_optimization_tasks_job_target_owner
        FOREIGN KEY (job_target_id, user_id)
        REFERENCES job_targets (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_optimization_tasks_async_task_id
        FOREIGN KEY (async_task_id) REFERENCES async_tasks (id) ON DELETE SET NULL,
    CONSTRAINT fk_optimization_tasks_analysis_result_id
        FOREIGN KEY (analysis_result_id) REFERENCES ai_job_match_results (id) ON DELETE SET NULL,
    CONSTRAINT fk_optimization_tasks_legacy_match_result_id
        FOREIGN KEY (legacy_match_result_id) REFERENCES ai_job_match_results (id) ON DELETE CASCADE,
    CONSTRAINT uk_optimization_tasks_legacy_match_result_id UNIQUE (legacy_match_result_id),
    CONSTRAINT ck_optimization_tasks_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_optimization_tasks_job_snapshot_not_blank CHECK (btrim(job_input_snapshot) <> ''),
    CONSTRAINT ck_optimization_tasks_distinct_versions CHECK (source_resume_version_id <> target_resume_version_id)
);

CREATE INDEX idx_optimization_tasks_user_id_created_at
    ON optimization_tasks (user_id, created_at DESC);

CREATE INDEX idx_optimization_tasks_user_id_status
    ON optimization_tasks (user_id, status);

CREATE INDEX idx_optimization_tasks_async_task_id
    ON optimization_tasks (async_task_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM ai_job_match_results match_result
        JOIN resumes resume ON resume.id = match_result.resume_id
        JOIN job_descriptions job_description ON job_description.id = match_result.job_description_id
        WHERE resume.user_id <> job_description.user_id
    ) THEN
        RAISE EXCEPTION 'Phase 2 migration stopped: legacy match data crosses user ownership';
    END IF;
END $$;

INSERT INTO job_targets (
    user_id,
    legacy_job_description_id,
    title,
    raw_jd,
    source_type,
    created_at,
    updated_at
)
SELECT
    user_id,
    id,
    title,
    raw_text,
    'LEGACY_IMPORT',
    created_at,
    updated_at
FROM job_descriptions
ON CONFLICT (legacy_job_description_id) DO NOTHING;

INSERT INTO resume_versions (
    user_id,
    resume_id,
    version_type,
    source_type,
    content_status,
    structured_content,
    created_at,
    updated_at
)
SELECT
    resume.user_id,
    resume.id,
    'SOURCE',
    'LEGACY_IMPORT',
    CASE
        WHEN parse_result.parse_status = 'SUCCESS'
            AND parse_result.structured_json IS NOT NULL
            AND btrim(parse_result.structured_json) <> '' THEN 'READY'
        WHEN parse_result.parse_status = 'FAILED' THEN 'FAILED'
        ELSE 'PENDING'
    END,
    parse_result.structured_json,
    resume.created_at,
    COALESCE(parse_result.updated_at, resume.updated_at)
FROM resumes resume
LEFT JOIN resume_parse_results parse_result ON parse_result.resume_id = resume.id;

INSERT INTO resume_versions (
    user_id,
    resume_id,
    source_version_id,
    job_target_id,
    legacy_match_result_id,
    version_type,
    source_type,
    content_status,
    structured_content,
    created_at,
    updated_at
)
SELECT
    resume.user_id,
    resume.id,
    source_version.id,
    job_target.id,
    match_result.id,
    'TARGETED',
    'JOB_DERIVATION',
    source_version.content_status,
    source_version.structured_content,
    match_result.created_at,
    match_result.updated_at
FROM ai_job_match_results match_result
JOIN resumes resume ON resume.id = match_result.resume_id
JOIN job_targets job_target ON job_target.legacy_job_description_id = match_result.job_description_id
JOIN resume_versions source_version
    ON source_version.resume_id = resume.id
    AND source_version.source_type = 'LEGACY_IMPORT'
ON CONFLICT (legacy_match_result_id) DO NOTHING;

INSERT INTO optimization_tasks (
    user_id,
    source_resume_version_id,
    target_resume_version_id,
    job_target_id,
    analysis_result_id,
    legacy_match_result_id,
    status,
    resume_input_snapshot,
    job_input_snapshot,
    prompt_snapshot,
    rules_snapshot,
    provider_snapshot,
    model_snapshot,
    template_version,
    error_message,
    finished_at,
    created_at,
    updated_at
)
SELECT
    resume.user_id,
    target_version.source_version_id,
    target_version.id,
    target_version.job_target_id,
    match_result.id,
    match_result.id,
    match_result.match_status,
    source_version.structured_content,
    job_target.raw_jd,
    json_build_object(
        'jobParsePromptVersion', job_description.prompt_version,
        'matchPromptVersion', match_result.prompt_version
    )::TEXT,
    '{}',
    'LEGACY_SYSTEM_DEFAULT',
    match_result.model_name,
    'NOT_SELECTED',
    match_result.error_message,
    CASE WHEN match_result.match_status IN ('SUCCESS', 'FAILED') THEN match_result.updated_at END,
    match_result.created_at,
    match_result.updated_at
FROM ai_job_match_results match_result
JOIN resumes resume ON resume.id = match_result.resume_id
JOIN job_descriptions job_description ON job_description.id = match_result.job_description_id
JOIN resume_versions target_version ON target_version.legacy_match_result_id = match_result.id
JOIN resume_versions source_version ON source_version.id = target_version.source_version_id
JOIN job_targets job_target ON job_target.id = target_version.job_target_id
ON CONFLICT (legacy_match_result_id) DO NOTHING;

DO $$
DECLARE
    missing_resume_count BIGINT;
    missing_job_target_count BIGINT;
    missing_task_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO missing_resume_count
    FROM resumes resume
    WHERE NOT EXISTS (
        SELECT 1
        FROM resume_versions version
        WHERE version.resume_id = resume.id
          AND version.source_type = 'LEGACY_IMPORT'
    );

    SELECT COUNT(*) INTO missing_job_target_count
    FROM job_descriptions job_description
    WHERE NOT EXISTS (
        SELECT 1
        FROM job_targets target
        WHERE target.legacy_job_description_id = job_description.id
    );

    SELECT COUNT(*) INTO missing_task_count
    FROM ai_job_match_results match_result
    WHERE NOT EXISTS (
        SELECT 1
        FROM optimization_tasks task
        WHERE task.legacy_match_result_id = match_result.id
    );

    IF missing_resume_count <> 0 OR missing_job_target_count <> 0 OR missing_task_count <> 0 THEN
        RAISE EXCEPTION
            'Phase 2 migration verification failed: resumes=%, job_targets=%, optimization_tasks=%',
            missing_resume_count,
            missing_job_target_count,
            missing_task_count;
    END IF;
END $$;
