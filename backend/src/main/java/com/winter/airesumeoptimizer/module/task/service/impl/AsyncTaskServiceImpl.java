package com.winter.airesumeoptimizer.module.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.task.entity.AsyncTask;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskStatus;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.mapper.AsyncTaskMapper;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncTaskServiceImpl implements AsyncTaskService {

    private static final int MESSAGE_MAX_LENGTH = 255;
    private static final int RESULT_SUMMARY_MAX_LENGTH = 500;
    private static final int ERROR_CODE_MAX_LENGTH = 100;

    private final AsyncTaskMapper asyncTaskMapper;

    public AsyncTaskServiceImpl(AsyncTaskMapper asyncTaskMapper) {
        this.asyncTaskMapper = asyncTaskMapper;
    }

    @Override
    @Transactional
    public Long createTask(Long userId, AsyncTaskType taskType, String bizType, Long bizId) {
        validateUserId(userId);
        if (taskType == null) {
            throw new BusinessException(400, "任务类型不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        AsyncTask task = new AsyncTask();
        task.setUserId(userId);
        task.setTaskType(taskType.name());
        task.setBizType(normalizeBlank(bizType));
        task.setBizId(bizId);
        task.setStatus(AsyncTaskStatus.PENDING.name());
        task.setProgress(0);
        task.setMessage("任务已创建");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        int rows = asyncTaskMapper.insert(task);
        if (rows != 1 || task.getId() == null) {
            throw new BusinessException(500, "任务创建失败");
        }
        return task.getId();
    }

    @Override
    @Transactional
    public void markRunning(Long taskId, String message) {
        LocalDateTime now = LocalDateTime.now();
        updateTask(taskId, new UpdateWrapper<AsyncTask>()
                .eq("id", taskId)
                .set("status", AsyncTaskStatus.RUNNING.name())
                .set("started_at", now)
                .set("message", truncate(message, MESSAGE_MAX_LENGTH))
                .set("updated_at", now));
    }

    @Override
    @Transactional
    public void updateProgress(Long taskId, int progress, String message) {
        validateProgress(progress);
        updateTask(taskId, new UpdateWrapper<AsyncTask>()
                .eq("id", taskId)
                .set("status", AsyncTaskStatus.RUNNING.name())
                .set("progress", progress)
                .set("message", truncate(message, MESSAGE_MAX_LENGTH))
                .set("updated_at", LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void markSuccess(Long taskId, String resultType, Long resultId, String resultSummary) {
        LocalDateTime now = LocalDateTime.now();
        updateTask(taskId, new UpdateWrapper<AsyncTask>()
                .eq("id", taskId)
                .set("status", AsyncTaskStatus.SUCCESS.name())
                .set("progress", 100)
                .set("message", "任务完成")
                .set("result_type", normalizeBlank(resultType))
                .set("result_id", resultId)
                .set("result_summary", truncate(resultSummary, RESULT_SUMMARY_MAX_LENGTH))
                .set("error_code", null)
                .set("error_message", null)
                .set("finished_at", now)
                .set("updated_at", now));
    }

    @Override
    @Transactional
    public void markFailed(Long taskId, String errorCode, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        updateTask(taskId, new UpdateWrapper<AsyncTask>()
                .eq("id", taskId)
                .set("status", AsyncTaskStatus.FAILED.name())
                .set("message", "任务失败")
                .set("error_code", truncate(errorCode, ERROR_CODE_MAX_LENGTH))
                .set("error_message", errorMessage)
                .set("finished_at", now)
                .set("updated_at", now));
    }

    @Override
    public AsyncTaskVO getTask(Long taskId, Long currentUserId) {
        validateUserId(currentUserId);
        if (taskId == null) {
            throw new BusinessException(400, "任务 ID 不能为空");
        }

        AsyncTask task = asyncTaskMapper.selectOne(new QueryWrapper<AsyncTask>()
                .eq("id", taskId)
                .eq("user_id", currentUserId));
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return toVO(task);
    }

    private void updateTask(Long taskId, UpdateWrapper<AsyncTask> updateWrapper) {
        if (taskId == null) {
            throw new BusinessException(400, "任务 ID 不能为空");
        }
        int rows = asyncTaskMapper.update(null, updateWrapper);
        if (rows != 1) {
            throw new BusinessException(404, "任务不存在");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private void validateProgress(int progress) {
        if (progress < 0 || progress > 100) {
            throw new BusinessException(400, "任务进度必须在 0 到 100 之间");
        }
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() <= maxLength) {
            return stripped;
        }
        return stripped.substring(0, maxLength);
    }

    private AsyncTaskVO toVO(AsyncTask task) {
        return AsyncTaskVO.builder()
                .taskId(task.getId())
                .taskType(task.getTaskType())
                .bizType(task.getBizType())
                .bizId(task.getBizId())
                .status(task.getStatus())
                .progress(task.getProgress())
                .message(task.getMessage())
                .resultType(task.getResultType())
                .resultId(task.getResultId())
                .resultSummary(task.getResultSummary())
                .errorCode(task.getErrorCode())
                .errorMessage(task.getErrorMessage())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
