CREATE TABLE IF NOT EXISTS async_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    biz_type VARCHAR(50),
    biz_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress INT NOT NULL DEFAULT 0,
    message VARCHAR(255),
    result_type VARCHAR(50),
    result_id BIGINT,
    result_summary VARCHAR(500),
    error_code VARCHAR(100),
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_async_tasks_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_async_tasks_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_async_tasks_progress CHECK (progress >= 0 AND progress <= 100)
);

CREATE INDEX IF NOT EXISTS idx_async_tasks_user_id_created_at
    ON async_tasks (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_async_tasks_user_id_status
    ON async_tasks (user_id, status);

CREATE INDEX IF NOT EXISTS idx_async_tasks_user_biz_type_biz_id
    ON async_tasks (user_id, biz_type, biz_id);
