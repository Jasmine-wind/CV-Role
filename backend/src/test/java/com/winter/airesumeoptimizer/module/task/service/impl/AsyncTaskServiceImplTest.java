package com.winter.airesumeoptimizer.module.task.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.task.entity.AsyncTask;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.mapper.AsyncTaskMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AsyncTaskServiceImplTest {

    private final AsyncTaskMapper asyncTaskMapper = mock(AsyncTaskMapper.class);
    private final AsyncTaskServiceImpl service = new AsyncTaskServiceImpl(asyncTaskMapper);

    @Test
    void createTaskShouldInsertPendingTask() {
        when(asyncTaskMapper.insert(any(AsyncTask.class))).thenAnswer(invocation -> {
            AsyncTask task = invocation.getArgument(0);
            task.setId(100L);
            return 1;
        });

        Long taskId = service.createTask(1L, AsyncTaskType.RESUME_PARSE, "RESUME", 10L);

        assertThat(taskId).isEqualTo(100L);
        ArgumentCaptor<AsyncTask> taskCaptor = ArgumentCaptor.forClass(AsyncTask.class);
        verify(asyncTaskMapper).insert(taskCaptor.capture());
        AsyncTask savedTask = taskCaptor.getValue();
        assertThat(savedTask.getUserId()).isEqualTo(1L);
        assertThat(savedTask.getTaskType()).isEqualTo("RESUME_PARSE");
        assertThat(savedTask.getBizType()).isEqualTo("RESUME");
        assertThat(savedTask.getBizId()).isEqualTo(10L);
        assertThat(savedTask.getStatus()).isEqualTo("PENDING");
        assertThat(savedTask.getProgress()).isZero();
        assertThat(savedTask.getCreatedAt()).isNotNull();
        assertThat(savedTask.getUpdatedAt()).isNotNull();
    }

    @Test
    void markSuccessShouldStoreResultReference() {
        when(asyncTaskMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.markSuccess(100L, "RESUME_PARSE", 200L, "解析完成");

        verify(asyncTaskMapper).update(any(), any(Wrapper.class));
    }

    @Test
    void markFailedShouldStoreFailureReason() {
        when(asyncTaskMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.markFailed(100L, "AI_TIMEOUT", "模型调用超时");

        verify(asyncTaskMapper).update(any(), any(Wrapper.class));
    }

    @Test
    void updateProgressShouldRejectInvalidProgress() {
        assertThatThrownBy(() -> service.updateProgress(100L, 101, "进度错误"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("任务进度必须在 0 到 100 之间");
    }

    @Test
    void getTaskShouldReturnOwnedTask() {
        AsyncTask task = new AsyncTask();
        task.setId(100L);
        task.setUserId(1L);
        task.setTaskType("RESUME_PARSE");
        task.setBizType("RESUME");
        task.setBizId(10L);
        task.setStatus("RUNNING");
        task.setProgress(40);
        task.setMessage("正在解析");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        when(asyncTaskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        var taskVO = service.getTask(100L, 1L);

        assertThat(taskVO.getTaskId()).isEqualTo(100L);
        assertThat(taskVO.getTaskType()).isEqualTo("RESUME_PARSE");
        assertThat(taskVO.getStatus()).isEqualTo("RUNNING");
        assertThat(taskVO.getProgress()).isEqualTo(40);
        assertThat(taskVO.getMessage()).isEqualTo("正在解析");
    }

    @Test
    void getTaskShouldRejectUnownedTaskAsNotFound() {
        when(asyncTaskMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getTask(100L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("任务不存在");
    }

    @Test
    void findActiveTaskShouldReturnRunningTask() {
        AsyncTask task = new AsyncTask();
        task.setId(100L);
        task.setUserId(1L);
        task.setTaskType("RESUME_PARSE");
        task.setBizType("RESUME");
        task.setBizId(10L);
        task.setStatus("RUNNING");
        task.setProgress(30);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        when(asyncTaskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        var taskVO = service.findActiveTask(1L, AsyncTaskType.RESUME_PARSE, "RESUME", 10L);

        assertThat(taskVO).isNotNull();
        assertThat(taskVO.getTaskId()).isEqualTo(100L);
        assertThat(taskVO.getStatus()).isEqualTo("RUNNING");
    }
}
