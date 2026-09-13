-- Preserve auditable SOURCE snapshots while they are referenced. Earlier migrations used
-- ON DELETE CASCADE for convenient resume cleanup, but that also allowed deleting a referenced
-- SOURCE to erase task/evidence history. A whole-resume delete still cascades from resumes; a
-- direct SOURCE delete now fails closed instead of silently removing history.
ALTER TABLE resume_versions
    DROP CONSTRAINT IF EXISTS fk_resume_versions_source_owner;

ALTER TABLE resume_versions
    ADD CONSTRAINT uk_resume_versions_id_user_resume UNIQUE (id, user_id, resume_id);

ALTER TABLE resume_versions
    ADD CONSTRAINT fk_resume_versions_source_owner
        FOREIGN KEY (source_version_id, user_id, resume_id)
        REFERENCES resume_versions (id, user_id, resume_id) ON DELETE RESTRICT;

ALTER TABLE optimization_tasks
    DROP CONSTRAINT IF EXISTS fk_optimization_tasks_source_version_owner;

ALTER TABLE optimization_tasks
    ADD CONSTRAINT fk_optimization_tasks_source_version_owner
        FOREIGN KEY (source_resume_version_id, user_id)
        REFERENCES resume_versions (id, user_id) ON DELETE RESTRICT;

ALTER TABLE requirement_evidences
    DROP CONSTRAINT IF EXISTS fk_requirement_evidences_resume_version_owner;

ALTER TABLE requirement_evidences
    ADD CONSTRAINT fk_requirement_evidences_resume_version_owner
        FOREIGN KEY (source_resume_version_id, user_id)
        REFERENCES resume_versions (id, user_id) ON DELETE RESTRICT;

CREATE OR REPLACE FUNCTION validate_optimization_task_resume_ownership()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM resume_versions source_version
        JOIN resume_versions target_version
          ON target_version.id = NEW.target_resume_version_id
         AND target_version.user_id = NEW.user_id
        WHERE source_version.id = NEW.source_resume_version_id
          AND source_version.user_id = NEW.user_id
          AND source_version.resume_id = target_version.resume_id
    ) THEN
        RAISE EXCEPTION 'optimization task source and target must belong to the same resume'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_optimization_task_resume_ownership ON optimization_tasks;
CREATE TRIGGER trg_validate_optimization_task_resume_ownership
    BEFORE INSERT OR UPDATE OF source_resume_version_id, target_resume_version_id, user_id
    ON optimization_tasks
    FOR EACH ROW
    EXECUTE FUNCTION validate_optimization_task_resume_ownership();
