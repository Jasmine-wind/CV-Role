package com.winter.airesumeoptimizer.module.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface JobDescriptionMapper extends BaseMapper<JobDescription> {

    @Select("""
            SELECT *
            FROM job_descriptions
            WHERE id = #{jobDescriptionId}
              AND user_id = #{userId}
            FOR UPDATE
            """)
    JobDescription selectOwnedForUpdate(
            @Param("userId") Long userId,
            @Param("jobDescriptionId") Long jobDescriptionId);
}
