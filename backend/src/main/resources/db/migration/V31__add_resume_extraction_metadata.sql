-- Preserve extraction facts even when text quality stops parsing before structured_json exists.
-- NULL means the extractor never reached a trustworthy metadata result; page_count_known
-- distinguishes a confirmed zero-page PDF from DOC/DOCX formats without reliable pagination.
ALTER TABLE resume_parse_results
    ADD COLUMN IF NOT EXISTS extraction_page_count INTEGER,
    ADD COLUMN IF NOT EXISTS extraction_page_count_known BOOLEAN,
    ADD COLUMN IF NOT EXISTS extraction_image_content_present BOOLEAN;

ALTER TABLE resume_parse_results
    DROP CONSTRAINT IF EXISTS ck_resume_parse_results_extraction_page_count;

ALTER TABLE resume_parse_results
    ADD CONSTRAINT ck_resume_parse_results_extraction_page_count
        CHECK (extraction_page_count IS NULL OR extraction_page_count >= 0);
