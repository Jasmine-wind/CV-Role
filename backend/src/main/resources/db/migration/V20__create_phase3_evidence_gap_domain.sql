-- Phase 3 is intentionally additive. The formal evidence/gap analysis of an
-- OptimizationTask becomes the source of truth for job analysis results.
-- V1 ai_job_match_results stays readable for compatibility but receives no new
-- writes from the main flow. Rolling the application back only requires
-- deploying the previous version; these tables remain unused until restored.

-- Composite ownership foreign keys below reference (id, user_id), so the
-- referenced tables need matching unique constraints.
ALTER TABLE optimization_tasks
    ADD CONSTRAINT uk_optimization_tasks_id_user_id UNIQUE (id, user_id);

CREATE TABLE evidence_analyses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    optimization_task_id BIGINT NOT NULL,
    matched_count INT NOT NULL DEFAULT 0,
    expression_gap_count INT NOT NULL DEFAULT 0,
    no_evidence_count INT NOT NULL DEFAULT 0,
    model_name VARCHAR(100),
    prompt_version VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_analyses_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_evidence_analyses_task_owner
        FOREIGN KEY (optimization_task_id, user_id)
        REFERENCES optimization_tasks (id, user_id) ON DELETE CASCADE,
    CONSTRAINT uk_evidence_analyses_optimization_task_id UNIQUE (optimization_task_id),
    CONSTRAINT uk_evidence_analyses_id_user_id UNIQUE (id, user_id),
    CONSTRAINT ck_evidence_analyses_counts CHECK (
        matched_count >= 0 AND expression_gap_count >= 0 AND no_evidence_count >= 0
    )
);

CREATE INDEX idx_evidence_analyses_user_created_at
    ON evidence_analyses (user_id, created_at DESC);

CREATE TABLE evidence_requirements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    evidence_analysis_id BIGINT NOT NULL,
    requirement_text VARCHAR(500) NOT NULL,
    importance VARCHAR(20) NOT NULL,
    match_level VARCHAR(20) NOT NULL,
    conclusion VARCHAR(300),
    suggestion VARCHAR(300),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_requirements_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_evidence_requirements_analysis_owner
        FOREIGN KEY (evidence_analysis_id, user_id)
        REFERENCES evidence_analyses (id, user_id) ON DELETE CASCADE,
    CONSTRAINT uk_evidence_requirements_id_user_id UNIQUE (id, user_id),
    CONSTRAINT ck_evidence_requirements_text_not_blank CHECK (btrim(requirement_text) <> ''),
    CONSTRAINT ck_evidence_requirements_importance CHECK (importance IN ('REQUIRED', 'BONUS')),
    CONSTRAINT ck_evidence_requirements_match_level CHECK (
        match_level IN ('MATCHED', 'EXPRESSION_GAP', 'NO_EVIDENCE')
    ),
    CONSTRAINT ck_evidence_requirements_display_order CHECK (display_order >= 0)
);

CREATE INDEX idx_evidence_requirements_analysis_order
    ON evidence_requirements (evidence_analysis_id, display_order);

CREATE TABLE requirement_evidences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    evidence_requirement_id BIGINT NOT NULL,
    source_resume_version_id BIGINT NOT NULL,
    section_label VARCHAR(100),
    evidence_text VARCHAR(1000) NOT NULL,
    expression_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_requirement_evidences_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_requirement_evidences_requirement_owner
        FOREIGN KEY (evidence_requirement_id, user_id)
        REFERENCES evidence_requirements (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_requirement_evidences_resume_version_owner
        FOREIGN KEY (source_resume_version_id, user_id)
        REFERENCES resume_versions (id, user_id) ON DELETE CASCADE,
    CONSTRAINT uk_requirement_evidences_id_user_id UNIQUE (id, user_id),
    CONSTRAINT ck_requirement_evidences_text_not_blank CHECK (btrim(evidence_text) <> ''),
    CONSTRAINT ck_requirement_evidences_expression_status CHECK (
        expression_status IN ('ADEQUATE', 'WEAK')
    )
);

CREATE INDEX idx_requirement_evidences_requirement_id
    ON requirement_evidences (evidence_requirement_id);
