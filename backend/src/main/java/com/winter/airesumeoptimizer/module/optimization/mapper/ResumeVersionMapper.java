package com.winter.airesumeoptimizer.module.optimization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResumeVersionMapper extends BaseMapper<ResumeVersion> {

    /**
     * Materializes a parsed SOURCE only while the parse attempt still owns the durable claim.
     * The row lock taken by the claim predicate makes the insert and the subsequent parse-result
     * CAS one transaction: a stale worker inserts zero rows instead of leaving an orphan SOURCE.
     */
    @Insert("""
            INSERT INTO resume_versions (
                user_id,
                resume_id,
                version_type,
                source_type,
                content_status,
                structured_content,
                content_revision,
                created_at,
                updated_at
            )
            SELECT
                #{source.userId},
                #{source.resumeId},
                #{source.versionType},
                #{source.sourceType},
                #{source.contentStatus},
                #{source.structuredContent},
                #{source.contentRevision},
                #{source.createdAt},
                #{source.updatedAt}
            WHERE EXISTS (
                SELECT 1
                FROM resume_parse_results
                WHERE resume_id = #{resumeId}
                  AND parse_generation = #{parseGeneration}
                  AND parse_token = #{parseToken}
                  AND parse_status = 'PROCESSING'
                FOR UPDATE
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "source.id", keyColumn = "id")
    int insertIfCurrentParseClaim(
            @Param("source") ResumeVersion source,
            @Param("resumeId") Long resumeId,
            @Param("parseGeneration") Long parseGeneration,
            @Param("parseToken") String parseToken);
}
