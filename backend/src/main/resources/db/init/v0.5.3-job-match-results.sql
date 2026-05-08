CREATE TABLE IF NOT EXISTS job_match_results (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    match_score INTEGER NOT NULL,
    matched_items TEXT NOT NULL,
    missing_items TEXT NOT NULL,
    match_reason TEXT NOT NULL,
    suggestions TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_match_results_resume_id FOREIGN KEY (resume_id) REFERENCES resumes (id),
    CONSTRAINT fk_job_match_results_job_id FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT uk_job_match_results_resume_id_job_id UNIQUE (resume_id, job_id),
    CONSTRAINT ck_job_match_results_match_score CHECK (match_score >= 0 AND match_score <= 100)
);

CREATE INDEX IF NOT EXISTS idx_job_match_results_resume_id_updated_at
    ON job_match_results (resume_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_match_results_job_id
    ON job_match_results (job_id);
