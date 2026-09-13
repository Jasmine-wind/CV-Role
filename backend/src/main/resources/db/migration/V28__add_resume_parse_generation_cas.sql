-- Parse attempts are request-scoped and may outlive the application instance that started them.
-- Keep the current generation/token on the one parse row so final writes can be CAS guarded
-- across instances. Existing rows receive generation zero and no active token until claimed.
ALTER TABLE resume_parse_results
    ADD COLUMN IF NOT EXISTS parse_generation BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS parse_token VARCHAR(64);

ALTER TABLE resume_parse_results
    DROP CONSTRAINT IF EXISTS ck_resume_parse_results_parse_generation;

ALTER TABLE resume_parse_results
    ADD CONSTRAINT ck_resume_parse_results_parse_generation
        CHECK (parse_generation >= 0);

CREATE INDEX IF NOT EXISTS idx_resume_parse_results_parse_claim
    ON resume_parse_results (resume_id, parse_generation, parse_token);
