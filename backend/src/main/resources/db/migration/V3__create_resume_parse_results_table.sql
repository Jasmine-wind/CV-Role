CREATE TABLE IF NOT EXISTS resume_parse_results (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    parse_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    extracted_text TEXT,
    structured_json TEXT,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resume_parse_results_resume_id FOREIGN KEY (resume_id) REFERENCES resumes (id),
    CONSTRAINT uk_resume_parse_results_resume_id UNIQUE (resume_id),
    CONSTRAINT ck_resume_parse_results_parse_status CHECK (parse_status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_resume_parse_results_parse_status
    ON resume_parse_results (parse_status);

