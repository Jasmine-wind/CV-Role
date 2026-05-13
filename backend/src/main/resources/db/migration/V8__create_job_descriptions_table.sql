CREATE TABLE IF NOT EXISTS job_descriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    raw_text TEXT NOT NULL,
    parse_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    structured_content TEXT,
    model_name VARCHAR(100),
    prompt_version VARCHAR(50),
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_descriptions_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_job_descriptions_parse_status CHECK (parse_status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_job_descriptions_user_id_created_at
    ON job_descriptions (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_descriptions_parse_status
    ON job_descriptions (parse_status);
