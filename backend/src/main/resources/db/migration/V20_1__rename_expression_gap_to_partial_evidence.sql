-- Phase 3 semantics are narrowed to what the frozen ResumeVersion can prove.
-- Existing formal analyses were produced with the superseded EXPRESSION_GAP
-- contract and potentially looser evidence validation. They are derived data,
-- so invalidate them and keep the frozen inputs available for a safe retry.
UPDATE async_tasks async_task
SET status = 'FAILED',
    progress = 0,
    message = '分析规则已更新，请重试',
    result_type = NULL,
    result_id = NULL,
    result_summary = NULL,
    error_code = 'EVIDENCE_MODEL_UPDATED',
    error_message = '岗位证据分析规则已更新，请重试。',
    finished_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
FROM optimization_tasks optimization_task
WHERE async_task.id = optimization_task.async_task_id
  AND EXISTS (
      SELECT 1
      FROM evidence_analyses analysis
      WHERE analysis.optimization_task_id = optimization_task.id
  );

UPDATE optimization_tasks optimization_task
SET status = 'FAILED',
    error_code = 'EVIDENCE_MODEL_UPDATED',
    error_message = '岗位证据分析规则已更新，请重试。',
    finished_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM evidence_analyses analysis
    WHERE analysis.optimization_task_id = optimization_task.id
);

-- Child rows are removed by ON DELETE CASCADE. V1 history is not involved.
DELETE FROM evidence_analyses;

ALTER TABLE evidence_analyses
    RENAME COLUMN expression_gap_count TO partial_evidence_count;

ALTER TABLE evidence_requirements
    DROP CONSTRAINT ck_evidence_requirements_match_level;

ALTER TABLE evidence_requirements
    ADD CONSTRAINT ck_evidence_requirements_match_level CHECK (
        match_level IN ('MATCHED', 'PARTIAL_EVIDENCE', 'NO_EVIDENCE')
    );

ALTER TABLE requirement_evidences
    DROP CONSTRAINT ck_requirement_evidences_expression_status;

ALTER TABLE requirement_evidences
    RENAME COLUMN expression_status TO support_level;

ALTER TABLE requirement_evidences
    ADD CONSTRAINT ck_requirement_evidences_support_level CHECK (
        support_level IN ('SUFFICIENT', 'PARTIAL')
    );
