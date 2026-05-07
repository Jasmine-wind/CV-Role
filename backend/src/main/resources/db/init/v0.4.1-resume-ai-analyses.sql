CREATE TABLE IF NOT EXISTS resume_ai_analyses (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    analysis_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    score INTEGER,
    strengths TEXT,
    problems TEXT,
    suggestions_summary TEXT,
    model_name VARCHAR(100),
    prompt_version VARCHAR(50),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resume_ai_analyses_resume_id FOREIGN KEY (resume_id) REFERENCES resumes (id),
    CONSTRAINT uk_resume_ai_analyses_resume_id UNIQUE (resume_id),
    CONSTRAINT ck_resume_ai_analyses_status CHECK (analysis_status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT ck_resume_ai_analyses_score CHECK (score IS NULL OR (score >= 0 AND score <= 100))
);

CREATE INDEX IF NOT EXISTS idx_resume_ai_analyses_status
    ON resume_ai_analyses (analysis_status);
