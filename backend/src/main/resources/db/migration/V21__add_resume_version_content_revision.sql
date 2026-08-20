-- Phase 4 is intentionally additive. V1 tables and the Phase 2/Phase 3 formal domain
-- remain untouched; rolling the application back only requires deploying the previous
-- version, the new column stays unused until the Phase 4 application is restored.
--
-- Structured Resume Data stays in resume_versions.structured_content, the single formal
-- content column. Phase 4 only adds a monotonically increasing content revision used for
-- optimistic concurrency control of workspace saves:
--   content_revision = 0  -> the version was never edited in the workspace; the content is
--                            still the frozen parsed snapshot (SOURCE) or its untouched copy
--                            (TARGETED).
--   content_revision > 0  -> the TARGETED version holds the canonical editable Resume
--                            Document written through the workspace with an atomic
--                            expectedRevision check.
-- SOURCE versions and task snapshots are never written by the workspace, so their revision
-- remains 0.

ALTER TABLE resume_versions
    ADD COLUMN IF NOT EXISTS content_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE resume_versions
    ADD CONSTRAINT ck_resume_versions_content_revision_nonnegative
    CHECK (content_revision >= 0);
