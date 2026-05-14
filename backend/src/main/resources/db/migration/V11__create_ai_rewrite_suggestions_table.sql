CREATE TABLE IF NOT EXISTS ai_rewrite_suggestions (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    job_description_id BIGINT,
    ai_job_match_result_id BIGINT,
    ai_resume_suggestion_id BIGINT,
    rewrite_type VARCHAR(30) NOT NULL,
    target_section VARCHAR(100) NOT NULL,
    original_text TEXT NOT NULL,
    rewritten_text TEXT,
    rewrite_reason TEXT,
    caution TEXT,
    accept_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rewrite_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    model_name VARCHAR(100),
    prompt_version VARCHAR(50),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_rewrite_suggestions_resume_id FOREIGN KEY (resume_id) REFERENCES resumes (id),
    CONSTRAINT fk_ai_rewrite_suggestions_job_description_id FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id),
    CONSTRAINT fk_ai_rewrite_suggestions_ai_job_match_result_id FOREIGN KEY (ai_job_match_result_id) REFERENCES ai_job_match_results (id),
    CONSTRAINT fk_ai_rewrite_suggestions_ai_resume_suggestion_id FOREIGN KEY (ai_resume_suggestion_id) REFERENCES ai_resume_suggestions (id),
    CONSTRAINT ck_ai_rewrite_suggestions_rewrite_type CHECK (rewrite_type IN ('PROJECT', 'SKILL', 'INTERNSHIP', 'SUMMARY', 'EDUCATION', 'OTHER')),
    CONSTRAINT ck_ai_rewrite_suggestions_accept_status CHECK (accept_status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_ai_rewrite_suggestions_rewrite_status CHECK (rewrite_status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_ai_rewrite_suggestions_resume_id_updated_at
    ON ai_rewrite_suggestions (resume_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_rewrite_suggestions_job_description_id
    ON ai_rewrite_suggestions (job_description_id);

CREATE INDEX IF NOT EXISTS idx_ai_rewrite_suggestions_ai_job_match_result_id
    ON ai_rewrite_suggestions (ai_job_match_result_id);

CREATE INDEX IF NOT EXISTS idx_ai_rewrite_suggestions_ai_resume_suggestion_id
    ON ai_rewrite_suggestions (ai_resume_suggestion_id);

CREATE INDEX IF NOT EXISTS idx_ai_rewrite_suggestions_rewrite_status
    ON ai_rewrite_suggestions (rewrite_status);

CREATE INDEX IF NOT EXISTS idx_ai_rewrite_suggestions_accept_status
    ON ai_rewrite_suggestions (accept_status);
