CREATE TABLE IF NOT EXISTS ai_resume_suggestions (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    job_description_id BIGINT NOT NULL,
    ai_job_match_result_id BIGINT NOT NULL,
    suggestion_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    suggestions TEXT,
    model_name VARCHAR(100),
    prompt_version VARCHAR(50),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_resume_suggestions_resume_id FOREIGN KEY (resume_id) REFERENCES resumes (id),
    CONSTRAINT fk_ai_resume_suggestions_job_description_id FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id),
    CONSTRAINT fk_ai_resume_suggestions_ai_job_match_result_id FOREIGN KEY (ai_job_match_result_id) REFERENCES ai_job_match_results (id),
    CONSTRAINT uk_ai_resume_suggestions_ai_job_match_result_id UNIQUE (ai_job_match_result_id),
    CONSTRAINT ck_ai_resume_suggestions_status CHECK (suggestion_status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_ai_resume_suggestions_resume_id_updated_at
    ON ai_resume_suggestions (resume_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_resume_suggestions_job_description_id
    ON ai_resume_suggestions (job_description_id);

CREATE INDEX IF NOT EXISTS idx_ai_resume_suggestions_status
    ON ai_resume_suggestions (suggestion_status);
