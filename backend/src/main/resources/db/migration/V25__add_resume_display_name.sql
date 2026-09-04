-- Product Polish UX Corrective Slice: a user-managed label for the uploaded resume.
-- original_filename remains immutable file metadata; display_name never enters
-- ResumeVersion content, Evidence, or OptimizationTask snapshots.

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);

UPDATE resumes
SET display_name = CASE
    WHEN length(regexp_replace(original_filename, '\.[^.]*$', '')) > 0
        THEN regexp_replace(original_filename, '\.[^.]*$', '')
    ELSE original_filename
END
WHERE display_name IS NULL OR btrim(display_name) = '';

ALTER TABLE resumes
    ALTER COLUMN display_name SET NOT NULL;
