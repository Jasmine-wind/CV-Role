package com.winter.airesumeoptimizer.module.task.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.task.entity.AsyncTask;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskStatus;
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
    void updateStageShouldStoreMessageWithoutInventingProgress() {
        when(asyncTaskMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.updateStage(100L, "正在理解岗位要求");

        verify(asyncTaskMapper).update(any(), any(Wrapper.class));
    }

    @Test
    void updateProgressShouldRejectInvalidProgress() {
        assertThatThrownBy(() -> service.updateProgress(100L, 101, "进度错误"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("任务进度必须在 0 到 100 之间");
    }

    @Test
    void cancellationShouldJoinParentTransactionOrCreateStandaloneTransaction() throws Exception {
        var attribute = new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()
                .getTransactionAttribute(AsyncTaskServiceImpl.class.getMethod(
                        "cancelActiveTasks", Long.class, String.class, Long.class), AsyncTaskServiceImpl.class);

        assertThat(attribute).isNotNull();
        assertThat(attribute.getPropagationBehavior())
                .isEqualTo(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED);
        assertThat(attribute.rollbackOn(new BusinessException(500, "storage failure"))).isTrue();
    }

    @Test
    void cancelActiveTasksShouldUseOwnerAndBusinessIdentity() {
        when(asyncTaskMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        service.cancelActiveTasks(1L, "RESUME", 10L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AsyncTask>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(asyncTaskMapper).update(any(), captor.capture());
        var update = captor.getValue();
        assertThat(update.getSqlSegment()).contains("user_id =", "biz_type =", "biz_id =", "status IN");
        assertThat(update.getSqlSet()).contains("status=", "finished_at=", "error_code=");
        assertThat(update.getParamNameValuePairs().values())
                .contains(1L, "RESUME", 10L, "PENDING", "RUNNING", "CANCELLED", "RESOURCE_DELETED")
                .doesNotContain("SUCCESS", "FAILED");
    }

    @Test
    void staleCompletionShouldNotOverwriteCancelledTask() {
        AsyncTask task = new AsyncTask();
        task.setId(100L);
        task.setStatus(AsyncTaskStatus.CANCELLED.name());
        when(asyncTaskMapper.update(any(), any(Wrapper.class))).thenReturn(0);
        when(asyncTaskMapper.selectById(100L)).thenReturn(task);

        assertThatCode(() -> service.markSuccess(100L, "RESUME_PARSE_RESULT", 10L, "完成"))
                .doesNotThrowAnyException();
    }

    @Test
    void isActiveShouldOnlyReturnOwnedPendingOrRunningTask() {
        AsyncTask task = new AsyncTask();
        task.setId(100L);
        task.setStatus(AsyncTaskStatus.RUNNING.name());
        when(asyncTaskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        assertThat(service.isActive(1L, 100L)).isTrue();
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
