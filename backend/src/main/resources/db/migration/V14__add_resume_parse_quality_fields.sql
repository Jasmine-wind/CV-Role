ALTER TABLE resume_parse_results
    ADD COLUMN IF NOT EXISTS text_quality_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS text_quality_issues TEXT,
    ADD COLUMN IF NOT EXISTS text_quality_message VARCHAR(1000);

ALTER TABLE resume_parse_results
    DROP CONSTRAINT IF EXISTS ck_resume_parse_results_text_quality_status;

ALTER TABLE resume_parse_results
    ADD CONSTRAINT ck_resume_parse_results_text_quality_status
        CHECK (text_quality_status IS NULL OR text_quality_status IN ('GOOD', 'WARNING', 'FAILED'));

CREATE INDEX IF NOT EXISTS idx_resume_parse_results_text_quality_status
    ON resume_parse_results (text_quality_status);
