ALTER TABLE resume_parse_results
    ADD COLUMN IF NOT EXISTS parse_quality_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS parse_quality_warnings TEXT,
    ADD COLUMN IF NOT EXISTS parse_quality_message VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS parse_quality_score INTEGER;

ALTER TABLE resume_parse_results
    DROP CONSTRAINT IF EXISTS ck_resume_parse_results_parse_quality_status;

ALTER TABLE resume_parse_results
    ADD CONSTRAINT ck_resume_parse_results_parse_quality_status
        CHECK (parse_quality_status IS NULL OR parse_quality_status IN ('GOOD', 'WARNING', 'FAILED'));

CREATE INDEX IF NOT EXISTS idx_resume_parse_results_parse_quality_status
    ON resume_parse_results (parse_quality_status);
