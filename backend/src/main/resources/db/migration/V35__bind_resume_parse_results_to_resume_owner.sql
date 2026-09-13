-- Bind parse-result rows to the same tenant as their Resume and canonical SOURCE.
-- The resume_id-only foreign keys prevented cross-resume pointers, but did not make
-- the tenant edge explicit for direct SQL writes. Backfill legacy rows before making
-- the denormalized owner mandatory and replace both ownership-sensitive foreign keys.
ALTER TABLE resumes
    ADD CONSTRAINT uk_resumes_owner_id UNIQUE (id, user_id);

ALTER TABLE resume_parse_results
    ADD COLUMN user_id BIGINT;

UPDATE resume_parse_results parse_result
SET user_id = resume.user_id
FROM resumes resume
WHERE resume.id = parse_result.resume_id
  AND parse_result.user_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM resume_parse_results
        WHERE user_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Phase 35 migration stopped: resume parse result has no owning user';
    END IF;
END;
$$;

ALTER TABLE resume_parse_results
    ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE resume_parse_results
    DROP CONSTRAINT IF EXISTS fk_resume_parse_results_resume_id;

ALTER TABLE resume_parse_results
    ADD CONSTRAINT fk_resume_parse_results_resume_owner
        FOREIGN KEY (resume_id, user_id)
        REFERENCES resumes (id, user_id) ON DELETE CASCADE;

ALTER TABLE resume_parse_results
    DROP CONSTRAINT IF EXISTS fk_resume_parse_results_canonical_source_version;

ALTER TABLE resume_parse_results
    ADD CONSTRAINT fk_resume_parse_results_canonical_source_owner
        FOREIGN KEY (canonical_source_version_id, user_id, resume_id)
        REFERENCES resume_versions (id, user_id, resume_id);

CREATE INDEX IF NOT EXISTS idx_resume_parse_results_user_resume
    ON resume_parse_results (user_id, resume_id);

CREATE OR REPLACE FUNCTION validate_resume_parse_canonical_source()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.canonical_source_version_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM resume_versions
        WHERE id = NEW.canonical_source_version_id
          AND user_id = NEW.user_id
          AND resume_id = NEW.resume_id
          AND version_type = 'SOURCE'
          AND source_version_id IS NULL
          AND job_target_id IS NULL
    ) THEN
        RAISE EXCEPTION 'canonical_source_version_id must reference a SOURCE version for the same user and resume'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_resume_parse_canonical_source ON resume_parse_results;
CREATE TRIGGER trg_validate_resume_parse_canonical_source
    BEFORE INSERT OR UPDATE OF canonical_source_version_id, user_id, resume_id
    ON resume_parse_results
    FOR EACH ROW
    EXECUTE FUNCTION validate_resume_parse_canonical_source();

CREATE OR REPLACE FUNCTION validate_resume_version_canonical_pointer()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM resume_parse_results parse_result
        WHERE parse_result.canonical_source_version_id = NEW.id
          AND (
              parse_result.user_id IS DISTINCT FROM NEW.user_id
              OR parse_result.resume_id IS DISTINCT FROM NEW.resume_id
          )
    ) OR EXISTS (
        SELECT 1
        FROM resume_parse_results parse_result
        WHERE parse_result.canonical_source_version_id = NEW.id
    ) AND NOT (
        NEW.version_type = 'SOURCE'
        AND NEW.source_version_id IS NULL
        AND NEW.job_target_id IS NULL
    ) THEN
        RAISE EXCEPTION 'a canonical parse pointer must reference a SOURCE version for the same user and resume'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_resume_version_canonical_pointer ON resume_versions;
CREATE TRIGGER trg_validate_resume_version_canonical_pointer
    BEFORE UPDATE OF version_type, source_version_id, job_target_id, user_id, resume_id
    ON resume_versions
    FOR EACH ROW
    EXECUTE FUNCTION validate_resume_version_canonical_pointer();
