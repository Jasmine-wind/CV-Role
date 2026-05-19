ALTER TABLE job_descriptions
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(30) NOT NULL DEFAULT 'USER_INPUT';

ALTER TABLE job_descriptions
    DROP CONSTRAINT IF EXISTS ck_job_descriptions_source_type;

ALTER TABLE job_descriptions
    ADD CONSTRAINT ck_job_descriptions_source_type
        CHECK (source_type IN ('USER_INPUT', 'PRESET', 'CRAWLED'));

CREATE INDEX IF NOT EXISTS idx_job_descriptions_source_type
    ON job_descriptions (source_type);
