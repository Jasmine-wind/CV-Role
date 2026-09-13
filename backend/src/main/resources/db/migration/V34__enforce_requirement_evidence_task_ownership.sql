-- Formal evidence is scoped to the frozen SOURCE of the OptimizationTask whose
-- EvidenceAnalysis owns the requirement. The existing user-only foreign key is
-- insufficient: a same-user SOURCE from another Resume would otherwise be valid.
-- Validate history before installing the write-time guard; malformed history must
-- stop the upgrade rather than being silently grandfathered.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM requirement_evidences evidence
        LEFT JOIN evidence_requirements requirement
          ON requirement.id = evidence.evidence_requirement_id
         AND requirement.user_id = evidence.user_id
        LEFT JOIN evidence_analyses analysis
          ON analysis.id = requirement.evidence_analysis_id
         AND analysis.user_id = requirement.user_id
        LEFT JOIN optimization_tasks task
          ON task.id = analysis.optimization_task_id
         AND task.user_id = analysis.user_id
        WHERE requirement.id IS NULL
           OR analysis.id IS NULL
           OR task.id IS NULL
           OR task.source_resume_version_id IS DISTINCT FROM evidence.source_resume_version_id
    ) THEN
        RAISE EXCEPTION 'Phase 34 migration stopped: requirement evidence crosses task SOURCE ownership';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION validate_requirement_evidence_task_ownership()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM evidence_requirements requirement
        JOIN evidence_analyses analysis
          ON analysis.id = requirement.evidence_analysis_id
         AND analysis.user_id = requirement.user_id
        JOIN optimization_tasks task
          ON task.id = analysis.optimization_task_id
         AND task.user_id = analysis.user_id
        WHERE requirement.id = NEW.evidence_requirement_id
          AND requirement.user_id = NEW.user_id
          AND task.source_resume_version_id = NEW.source_resume_version_id
    ) THEN
        RAISE EXCEPTION 'requirement evidence must reference the owning task SOURCE version'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_requirement_evidence_task_ownership ON requirement_evidences;
CREATE TRIGGER trg_validate_requirement_evidence_task_ownership
    BEFORE INSERT OR UPDATE OF evidence_requirement_id, source_resume_version_id, user_id
    ON requirement_evidences
    FOR EACH ROW
    EXECUTE FUNCTION validate_requirement_evidence_task_ownership();

-- Existing evidence must not become detached from its task through a later
-- reparenting update. When descendants already exist, relationship changes fail
-- closed; creating a new empty analysis/requirement remains possible.
CREATE OR REPLACE FUNCTION reject_evidence_requirement_reparent_with_children()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF (NEW.evidence_analysis_id IS DISTINCT FROM OLD.evidence_analysis_id
            OR NEW.user_id IS DISTINCT FROM OLD.user_id)
       AND EXISTS (
           SELECT 1
           FROM requirement_evidences evidence
           WHERE evidence.evidence_requirement_id = OLD.id
       ) THEN
        RAISE EXCEPTION 'evidence requirement with formal evidence cannot be reparented'
            USING ERRCODE = '55006';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reject_evidence_requirement_reparent ON evidence_requirements;
CREATE TRIGGER trg_reject_evidence_requirement_reparent
    BEFORE UPDATE OF evidence_analysis_id, user_id
    ON evidence_requirements
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_requirement_reparent_with_children();

CREATE OR REPLACE FUNCTION reject_evidence_analysis_reparent_with_children()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF (NEW.optimization_task_id IS DISTINCT FROM OLD.optimization_task_id
            OR NEW.user_id IS DISTINCT FROM OLD.user_id)
       AND EXISTS (
           SELECT 1
           FROM evidence_requirements requirement
           JOIN requirement_evidences evidence
             ON evidence.evidence_requirement_id = requirement.id
           WHERE requirement.evidence_analysis_id = OLD.id
       ) THEN
        RAISE EXCEPTION 'evidence analysis with formal evidence cannot be reparented'
            USING ERRCODE = '55006';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reject_evidence_analysis_reparent ON evidence_analyses;
CREATE TRIGGER trg_reject_evidence_analysis_reparent
    BEFORE UPDATE OF optimization_task_id, user_id
    ON evidence_analyses
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_analysis_reparent_with_children();

CREATE OR REPLACE FUNCTION reject_task_source_change_with_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF (NEW.source_resume_version_id IS DISTINCT FROM OLD.source_resume_version_id
            OR NEW.user_id IS DISTINCT FROM OLD.user_id)
       AND EXISTS (
           SELECT 1
           FROM evidence_analyses analysis
           JOIN evidence_requirements requirement
             ON requirement.evidence_analysis_id = analysis.id
            AND requirement.user_id = analysis.user_id
           JOIN requirement_evidences evidence
             ON evidence.evidence_requirement_id = requirement.id
            AND evidence.user_id = requirement.user_id
           WHERE analysis.optimization_task_id = OLD.id
             AND analysis.user_id = OLD.user_id
       ) THEN
        RAISE EXCEPTION 'optimization task with formal evidence cannot change SOURCE ownership'
            USING ERRCODE = '55006';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reject_task_source_change_with_evidence ON optimization_tasks;
CREATE TRIGGER trg_reject_task_source_change_with_evidence
    BEFORE UPDATE OF source_resume_version_id, user_id
    ON optimization_tasks
    FOR EACH ROW
    EXECUTE FUNCTION reject_task_source_change_with_evidence();
