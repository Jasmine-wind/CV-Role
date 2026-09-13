package com.winter.airesumeoptimizer.module.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeAiRepairAttempt;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResumeAiRepairAttemptMapper extends BaseMapper<ResumeAiRepairAttempt> {

    @Insert("""
            INSERT INTO resume_ai_repair_attempts (
                user_id,
                resume_id,
                repair_key,
                status,
                owner_token,
                provider_dispatch_count,
                created_at,
                updated_at
            ) VALUES (
                #{userId},
                #{resumeId},
                #{repairKey},
                'CLAIMED',
                #{ownerToken},
                0,
                #{now},
                #{now}
            )
            ON CONFLICT (user_id, repair_key) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("resumeId") Long resumeId,
            @Param("repairKey") String repairKey,
            @Param("ownerToken") String ownerToken,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT id,
                   user_id,
                   resume_id,
                   repair_key,
                   status,
                   owner_token,
                   result_json,
                   failure_reason,
                   provider_dispatch_count,
                   created_at,
                   updated_at
            FROM resume_ai_repair_attempts
            WHERE user_id = #{userId}
              AND repair_key = #{repairKey}
            """)
    ResumeAiRepairAttempt selectByUserAndKey(
            @Param("userId") Long userId,
            @Param("repairKey") String repairKey);

    @Update("""
            UPDATE resume_ai_repair_attempts
            SET status = 'CLAIMED',
                owner_token = #{ownerToken},
                result_json = NULL,
                failure_reason = NULL,
                provider_dispatch_count = 0,
                updated_at = #{now}
            WHERE user_id = #{userId}
              AND repair_key = #{repairKey}
              AND status = 'FAILED_NO_DISPATCH'
            """)
    int claimRetryIfNoDispatch(
            @Param("userId") Long userId,
            @Param("repairKey") String repairKey,
            @Param("ownerToken") String ownerToken,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE resume_ai_repair_attempts
            SET status = 'SUCCEEDED',
                owner_token = NULL,
                result_json = #{resultJson},
                failure_reason = NULL,
                provider_dispatch_count = #{providerDispatchCount},
                updated_at = #{now}
            WHERE user_id = #{userId}
              AND repair_key = #{repairKey}
              AND status = 'CLAIMED'
              AND owner_token = #{ownerToken}
            """)
    int completeIfOwned(
            @Param("userId") Long userId,
            @Param("repairKey") String repairKey,
            @Param("ownerToken") String ownerToken,
            @Param("resultJson") String resultJson,
            @Param("providerDispatchCount") int providerDispatchCount,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE resume_ai_repair_attempts
            SET status = #{status},
                owner_token = NULL,
                result_json = NULL,
                failure_reason = #{failureReason},
                provider_dispatch_count = #{providerDispatchCount},
                updated_at = #{now}
            WHERE user_id = #{userId}
              AND repair_key = #{repairKey}
              AND status = 'CLAIMED'
              AND owner_token = #{ownerToken}
            """)
    int failIfOwned(
            @Param("userId") Long userId,
            @Param("repairKey") String repairKey,
            @Param("ownerToken") String ownerToken,
            @Param("status") String status,
            @Param("failureReason") String failureReason,
            @Param("providerDispatchCount") int providerDispatchCount,
            @Param("now") LocalDateTime now);
}
