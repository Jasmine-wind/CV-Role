-- A parsed SOURCE may be reviewed before it is used by an optimization task. Once a
-- task/evidence snapshot references it, its bytes and revision are immutable.
CREATE OR REPLACE FUNCTION validate_resume_source_snapshot_immutability()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.version_type = 'SOURCE'
       AND (
            OLD.structured_content IS DISTINCT FROM NEW.structured_content
            OR OLD.content_status IS DISTINCT FROM NEW.content_status
            OR OLD.content_revision IS DISTINCT FROM NEW.content_revision
            OR OLD.source_type IS DISTINCT FROM NEW.source_type
            OR OLD.resume_id IS DISTINCT FROM NEW.resume_id
       )
       AND (
            EXISTS (
                SELECT 1 FROM optimization_tasks task
                WHERE task.source_resume_version_id = OLD.id
            )
            OR EXISTS (
                SELECT 1 FROM resume_versions child
                WHERE child.source_version_id = OLD.id
            )
       )
    THEN
        RAISE EXCEPTION 'SOURCE version is frozen after task/evidence reference'
            USING ERRCODE = '55006';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_resume_source_snapshot_immutability ON resume_versions;
CREATE TRIGGER trg_validate_resume_source_snapshot_immutability
    BEFORE UPDATE OF structured_content, content_status, content_revision, source_type, resume_id
    ON resume_versions
    FOR EACH ROW
    EXECUTE FUNCTION validate_resume_source_snapshot_immutability();
