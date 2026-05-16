CREATE TABLE IF NOT EXISTS resume_embeddings (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding vector,
    embedding_model VARCHAR(100),
    embedding_dimension INTEGER,
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resume_embeddings_resume_id FOREIGN KEY (resume_id) REFERENCES resumes (id),
    CONSTRAINT fk_resume_embeddings_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_resume_embeddings_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_resume_embeddings_embedding_dimension CHECK (embedding_dimension IS NULL OR embedding_dimension > 0),
    CONSTRAINT ck_resume_embeddings_embedding_status CHECK (embedding_status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_resume_embeddings_resume_id_chunk_index
    ON resume_embeddings (resume_id, chunk_index);

CREATE INDEX IF NOT EXISTS idx_resume_embeddings_user_id_status_updated_at
    ON resume_embeddings (user_id, embedding_status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_resume_embeddings_embedding_model
    ON resume_embeddings (embedding_model);

CREATE TABLE IF NOT EXISTS job_description_embeddings (
    id BIGSERIAL PRIMARY KEY,
    job_description_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding vector,
    embedding_model VARCHAR(100),
    embedding_dimension INTEGER,
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_description_embeddings_job_description_id FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id),
    CONSTRAINT fk_job_description_embeddings_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_job_description_embeddings_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_job_description_embeddings_embedding_dimension CHECK (embedding_dimension IS NULL OR embedding_dimension > 0),
    CONSTRAINT ck_job_description_embeddings_embedding_status CHECK (embedding_status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_job_description_embeddings_job_description_id_chunk_index
    ON job_description_embeddings (job_description_id, chunk_index);

CREATE INDEX IF NOT EXISTS idx_job_description_embeddings_user_id_status_updated_at
    ON job_description_embeddings (user_id, embedding_status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_job_description_embeddings_embedding_model
    ON job_description_embeddings (embedding_model);
