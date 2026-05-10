CREATE TABLE IF NOT EXISTS resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    storage_type VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    upload_status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resumes_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_resumes_file_size_positive CHECK (file_size > 0),
    CONSTRAINT ck_resumes_storage_type CHECK (storage_type IN ('LOCAL', 'MINIO')),
    CONSTRAINT ck_resumes_upload_status CHECK (upload_status IN ('UPLOADED', 'FAILED'))
);

ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS storage_type VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE resumes
    ALTER COLUMN storage_type SET DEFAULT 'LOCAL';

UPDATE resumes
SET storage_type = 'LOCAL'
WHERE storage_type IS NULL;

ALTER TABLE resumes
    ALTER COLUMN storage_type SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_resumes_storage_type'
    ) THEN
        ALTER TABLE resumes
            ADD CONSTRAINT ck_resumes_storage_type CHECK (storage_type IN ('LOCAL', 'MINIO'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_resumes_user_id_created_at
    ON resumes (user_id, created_at DESC);
