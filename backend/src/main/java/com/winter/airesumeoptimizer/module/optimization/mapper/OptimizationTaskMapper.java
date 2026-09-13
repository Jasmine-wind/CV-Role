package com.winter.airesumeoptimizer.module.optimization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OptimizationTaskMapper extends BaseMapper<OptimizationTask> {

    /**
     * Serializes export-artifact insertion with parent task deletion. The lock is acquired in
     * the same transaction that inserts the artifact metadata, after the external object exists.
     */
    @Select("""
            SELECT *
            FROM optimization_tasks
            WHERE id = #{taskId}
              AND user_id = #{userId}
            FOR UPDATE
            """)
    OptimizationTask selectOwnedForUpdate(
            @Param("userId") Long userId,
            @Param("taskId") Long taskId);

    /** Attaches only an active MATCH_ANALYSIS execution belonging to this exact task. */
    @Update("""
            UPDATE optimization_tasks
            SET async_task_id = #{asyncTaskId},
                status = 'PENDING',
                error_code = NULL,
                error_message = NULL,
                finished_at = NULL,
                updated_at = #{now}
            WHERE id = #{optimizationTaskId}
              AND user_id = #{userId}
              AND status <> 'SUCCESS'
              AND (async_task_id IS NULL OR status IN ('FAILED', 'CANCELLED'))
              AND EXISTS (
                  SELECT 1
                  FROM async_tasks
                  WHERE async_tasks.id = #{asyncTaskId}
                    AND async_tasks.user_id = #{userId}
                    AND async_tasks.task_type = 'MATCH_ANALYSIS'
                    AND async_tasks.biz_type = 'OPTIMIZATION_TASK'
                    AND async_tasks.biz_id = #{optimizationTaskId}
                    AND async_tasks.status IN ('PENDING', 'RUNNING')
              )
            """)
    int attachAsyncTaskIfActive(
            @Param("userId") Long userId,
            @Param("optimizationTaskId") Long optimizationTaskId,
            @Param("asyncTaskId") Long asyncTaskId,
            @Param("now") java.time.LocalDateTime now);

    /**
     * Advances a task only when the callback still owns the attached active async execution.
     * The async row predicate closes the retry/cancel-to-late-worker gap at the database boundary.
     */
    @Update("""
            UPDATE optimization_tasks
            SET status = 'RUNNING',
                started_at = #{now},
                updated_at = #{now}
            WHERE id = #{optimizationTaskId}
              AND user_id = #{userId}
              AND async_task_id = #{asyncTaskId}
              AND status = 'PENDING'
              AND EXISTS (
                  SELECT 1
                  FROM async_tasks
                  WHERE async_tasks.id = #{asyncTaskId}
                    AND async_tasks.user_id = #{userId}
                    AND async_tasks.task_type = 'MATCH_ANALYSIS'
                    AND async_tasks.biz_type = 'OPTIMIZATION_TASK'
                    AND async_tasks.biz_id = #{optimizationTaskId}
                    AND async_tasks.status IN ('PENDING', 'RUNNING')
              )
            """)
    int markRunningIfCurrent(
            @Param("userId") Long userId,
            @Param("optimizationTaskId") Long optimizationTaskId,
            @Param("asyncTaskId") Long asyncTaskId,
            @Param("now") java.time.LocalDateTime now);

    @Update("""
            UPDATE optimization_tasks
            SET status = 'SUCCESS',
                prompt_snapshot = #{promptSnapshot},
                error_code = NULL,
                error_message = NULL,
                finished_at = #{now},
                updated_at = #{now}
            WHERE id = #{optimizationTaskId}
              AND user_id = #{userId}
              AND async_task_id = #{asyncTaskId}
              AND status = 'RUNNING'
              AND EXISTS (
                  SELECT 1
                  FROM async_tasks
                  WHERE async_tasks.id = #{asyncTaskId}
                    AND async_tasks.user_id = #{userId}
                    AND async_tasks.task_type = 'MATCH_ANALYSIS'
                    AND async_tasks.biz_type = 'OPTIMIZATION_TASK'
                    AND async_tasks.biz_id = #{optimizationTaskId}
                    AND async_tasks.status IN ('PENDING', 'RUNNING')
              )
            """)
    int markSuccessIfCurrent(
            @Param("userId") Long userId,
            @Param("optimizationTaskId") Long optimizationTaskId,
            @Param("asyncTaskId") Long asyncTaskId,
            @Param("promptSnapshot") String promptSnapshot,
            @Param("now") java.time.LocalDateTime now);

    @Update("""
            UPDATE optimization_tasks
            SET status = 'FAILED',
                error_code = #{errorCode},
                error_message = #{errorMessage},
                finished_at = #{now},
                updated_at = #{now}
            WHERE id = #{optimizationTaskId}
              AND user_id = #{userId}
              AND async_task_id = #{asyncTaskId}
              AND status IN ('PENDING', 'RUNNING')
              AND EXISTS (
                  SELECT 1
                  FROM async_tasks
                  WHERE async_tasks.id = #{asyncTaskId}
                    AND async_tasks.user_id = #{userId}
                    AND async_tasks.task_type = 'MATCH_ANALYSIS'
                    AND async_tasks.biz_type = 'OPTIMIZATION_TASK'
                    AND async_tasks.biz_id = #{optimizationTaskId}
                    AND async_tasks.status IN ('PENDING', 'RUNNING')
              )
            """)
    int markFailedIfCurrent(
            @Param("userId") Long userId,
            @Param("optimizationTaskId") Long optimizationTaskId,
            @Param("asyncTaskId") Long asyncTaskId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("now") java.time.LocalDateTime now);
}
