ALTER TABLE resume_parse_results
    ADD COLUMN IF NOT EXISTS cleaned_text TEXT,
    ADD COLUMN IF NOT EXISTS section_result TEXT;
