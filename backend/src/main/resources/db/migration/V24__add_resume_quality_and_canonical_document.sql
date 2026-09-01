-- Product Polish Slice A: trustworthy delivery chain for parsed resume content.
--
-- resume_parse_results owns only the deterministic quality verdict, the unresolved
-- review sidecar and a pointer to the current canonical SOURCE version. The
-- canonical document bytes themselves remain exclusively in resume_versions.
-- structured_json keeps its frozen meaning as candidate parse output; nothing here
-- rewrites Phase 1-9 rows.
--
-- Existing rows predate the quality verdict: they default to READY so historical
-- behaviour is preserved without retroactive re-review.

ALTER TABLE resume_parse_results
    ADD COLUMN IF NOT EXISTS quality_status VARCHAR(20) NOT NULL DEFAULT 'READY',
    ADD COLUMN IF NOT EXISTS quality_issues TEXT,
    ADD COLUMN IF NOT EXISTS unresolved_items TEXT,
    ADD COLUMN IF NOT EXISTS canonical_source_version_id BIGINT;

ALTER TABLE resume_parse_results
    DROP CONSTRAINT IF EXISTS ck_resume_parse_results_quality_status;

ALTER TABLE resume_parse_results
    ADD CONSTRAINT ck_resume_parse_results_quality_status
        CHECK (quality_status IN ('PENDING', 'READY', 'NEEDS_REVIEW', 'FAILED'));

CREATE INDEX IF NOT EXISTS idx_resume_parse_results_quality_status
    ON resume_parse_results (quality_status);

-- The pointer must resolve to a SOURCE belonging to the same resume. The id is
-- already unique, but the composite key lets PostgreSQL enforce the resume edge
-- instead of relying only on service-layer checks.
ALTER TABLE resume_versions
    DROP CONSTRAINT IF EXISTS uk_resume_versions_id_resume_id;

ALTER TABLE resume_versions
    ADD CONSTRAINT uk_resume_versions_id_resume_id UNIQUE (id, resume_id);

ALTER TABLE resume_parse_results
    DROP CONSTRAINT IF EXISTS fk_resume_parse_results_canonical_source_version;

ALTER TABLE resume_parse_results
    ADD CONSTRAINT fk_resume_parse_results_canonical_source_version
        FOREIGN KEY (canonical_source_version_id, resume_id)
        REFERENCES resume_versions (id, resume_id);

CREATE INDEX IF NOT EXISTS idx_resume_parse_results_canonical_source_version
    ON resume_parse_results (canonical_source_version_id);

-- A foreign key can enforce the same-resume edge but cannot express the SOURCE
-- discriminator. These small database triggers keep the pointer invariant true
-- even for direct SQL writes; the service layer still performs ownership checks.
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
          AND resume_id = NEW.resume_id
          AND version_type = 'SOURCE'
          AND source_version_id IS NULL
          AND job_target_id IS NULL
    ) THEN
        RAISE EXCEPTION 'canonical_source_version_id must reference a SOURCE version for the same resume'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_resume_parse_canonical_source ON resume_parse_results;
CREATE TRIGGER trg_validate_resume_parse_canonical_source
    BEFORE INSERT OR UPDATE OF canonical_source_version_id, resume_id
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
        FROM resume_parse_results
        WHERE canonical_source_version_id = NEW.id
    ) AND NOT (
        NEW.version_type = 'SOURCE'
        AND NEW.source_version_id IS NULL
        AND NEW.job_target_id IS NULL
    ) THEN
        RAISE EXCEPTION 'a canonical parse pointer must reference a SOURCE version'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_resume_version_canonical_pointer ON resume_versions;
CREATE TRIGGER trg_validate_resume_version_canonical_pointer
    BEFORE UPDATE OF version_type, source_version_id, job_target_id, resume_id
    ON resume_versions
    FOR EACH ROW
    EXECUTE FUNCTION validate_resume_version_canonical_pointer();

-- export_artifacts gains the Slice A gate outcome. Nullable on purpose: rows created
-- before the gate existed keep their frozen Phase 6 meaning and must stay downloadable.

ALTER TABLE export_artifacts
    ADD COLUMN IF NOT EXISTS document_gate_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS orphan_final_page BOOLEAN,
    ADD COLUMN IF NOT EXISTS readability_too_small BOOLEAN;

ALTER TABLE export_artifacts
    DROP CONSTRAINT IF EXISTS ck_export_artifacts_document_gate_status;

ALTER TABLE export_artifacts
    ADD CONSTRAINT ck_export_artifacts_document_gate_status
        CHECK (document_gate_status IS NULL OR document_gate_status IN ('PASS', 'BLOCK'));
