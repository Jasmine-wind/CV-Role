CREATE TABLE IF NOT EXISTS ai_job_match_results (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    job_description_id BIGINT NOT NULL,
    match_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    overall_score INTEGER,
    strong_matches TEXT,
    weak_matches TEXT,
    missing_skills TEXT,
    weak_experience_descriptions TEXT,
    evidence TEXT,
    risk_notes TEXT,
    model_name VARCHAR(100),
    prompt_version VARCHAR(50),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_job_match_results_resume_id FOREIGN KEY (resume_id) REFERENCES resumes (id),
    CONSTRAINT fk_ai_job_match_results_job_description_id FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id),
    CONSTRAINT uk_ai_job_match_results_resume_id_job_description_id UNIQUE (resume_id, job_description_id),
    CONSTRAINT ck_ai_job_match_results_status CHECK (match_status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_ai_job_match_results_overall_score CHECK (overall_score IS NULL OR (overall_score >= 0 AND overall_score <= 100))
);

CREATE INDEX IF NOT EXISTS idx_ai_job_match_results_resume_id_updated_at
    ON ai_job_match_results (resume_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_job_match_results_job_description_id
    ON ai_job_match_results (job_description_id);

CREATE INDEX IF NOT EXISTS idx_ai_job_match_results_status
    ON ai_job_match_results (match_status);
