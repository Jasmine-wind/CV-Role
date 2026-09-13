-- Do not silently grandfather malformed historical task edges when the same-resume database
-- guard is introduced. Existing violations fail migration and require an explicit data repair.
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
    ) THEN
        RAISE EXCEPTION 'Phase 33 migration stopped: optimization task crosses resume ownership';
    END IF;
END;
$$;
