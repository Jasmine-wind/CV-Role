-- Close write-time ownership races without modifying released V32/V34 migrations.
-- Formal graph identity is assigned when each row is inserted. Business updates may change
-- mutable content/status fields, but they must publish a new row instead of reparenting an
-- existing SOURCE, TARGET, Task, EvidenceAnalysis, or EvidenceRequirement.

-- Revalidate history because the earlier task trigger only ran when optimization_tasks changed;
-- a later direct TARGET reparent could otherwise have bypassed it.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM optimization_tasks task
        JOIN resume_versions source_version
          ON source_version.id = task.source_resume_version_id
         AND source_version.user_id = task.user_id
        JOIN resume_versions target_version
          ON target_version.id = task.target_resume_version_id
         AND target_version.user_id = task.user_id
        WHERE source_version.resume_id <> target_version.resume_id
           OR target_version.source_version_id IS DISTINCT FROM source_version.id
           OR target_version.job_target_id IS DISTINCT FROM task.job_target_id
    ) THEN
        RAISE EXCEPTION 'Phase 36 migration stopped: formal task ownership edges are inconsistent';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION reject_resume_version_ownership_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.user_id IS DISTINCT FROM NEW.user_id
       OR OLD.resume_id IS DISTINCT FROM NEW.resume_id
       OR OLD.version_type IS DISTINCT FROM NEW.version_type
       OR OLD.source_type IS DISTINCT FROM NEW.source_type
       OR OLD.source_version_id IS DISTINCT FROM NEW.source_version_id
       OR OLD.job_target_id IS DISTINCT FROM NEW.job_target_id
       OR OLD.legacy_match_result_id IS DISTINCT FROM NEW.legacy_match_result_id
    THEN
        RAISE EXCEPTION 'resume version ownership is immutable after publication'
            USING ERRCODE = '55006';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reject_resume_version_ownership_change ON resume_versions;
CREATE TRIGGER trg_reject_resume_version_ownership_change
    BEFORE UPDATE OF user_id, resume_id, version_type, source_type, source_version_id,
        job_target_id, legacy_match_result_id
    ON resume_versions
    FOR EACH ROW
    EXECUTE FUNCTION reject_resume_version_ownership_change();

-- Retain the V34 function names so existing trigger dependencies remain stable, but reject
-- reparenting unconditionally. Waiting until a child EXISTS creates a write-skew window where
-- a concurrent child insert and parent reparent cannot see one another under READ COMMITTED.
CREATE OR REPLACE FUNCTION reject_evidence_requirement_reparent_with_children()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.evidence_analysis_id IS DISTINCT FROM OLD.evidence_analysis_id
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
    THEN
        RAISE EXCEPTION 'evidence requirement cannot be reparented'
            USING ERRCODE = '55006';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION reject_evidence_analysis_reparent_with_children()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.optimization_task_id IS DISTINCT FROM OLD.optimization_task_id
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
    THEN
        RAISE EXCEPTION 'evidence analysis cannot be reparented'
            USING ERRCODE = '55006';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION reject_task_source_change_with_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.source_resume_version_id IS DISTINCT FROM OLD.source_resume_version_id
       OR NEW.target_resume_version_id IS DISTINCT FROM OLD.target_resume_version_id
       OR NEW.job_target_id IS DISTINCT FROM OLD.job_target_id
       OR NEW.legacy_match_result_id IS DISTINCT FROM OLD.legacy_match_result_id
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
    THEN
        RAISE EXCEPTION 'formal task input ownership is immutable'
            USING ERRCODE = '55006';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reject_task_source_change_with_evidence ON optimization_tasks;
CREATE TRIGGER trg_reject_task_source_change_with_evidence
    BEFORE UPDATE OF source_resume_version_id, target_resume_version_id, job_target_id,
        legacy_match_result_id, user_id
    ON optimization_tasks
    FOR EACH ROW
    EXECUTE FUNCTION reject_task_source_change_with_evidence();
