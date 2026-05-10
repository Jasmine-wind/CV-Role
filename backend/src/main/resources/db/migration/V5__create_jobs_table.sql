CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    job_category VARCHAR(50) NOT NULL,
    location VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    requirements TEXT NOT NULL,
    required_skills TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_jobs_title_company_name UNIQUE (title, company_name),
    CONSTRAINT ck_jobs_status CHECK (status IN ('ENABLED', 'DISABLED'))
);

CREATE INDEX IF NOT EXISTS idx_jobs_status_category
    ON jobs (status, job_category);

