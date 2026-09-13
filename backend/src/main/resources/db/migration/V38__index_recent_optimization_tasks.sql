-- Support the Home recent-task summary query and the formal task relationship joins.
-- PostgreSQL does not create indexes automatically for foreign-key columns.
CREATE INDEX IF NOT EXISTS idx_optimization_tasks_user_updated_id
    ON optimization_tasks (user_id, updated_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_optimization_tasks_job_target_id
    ON optimization_tasks (job_target_id);

CREATE INDEX IF NOT EXISTS idx_optimization_tasks_source_resume_version_id
    ON optimization_tasks (source_resume_version_id);

CREATE INDEX IF NOT EXISTS idx_optimization_tasks_target_resume_version_id
    ON optimization_tasks (target_resume_version_id);
