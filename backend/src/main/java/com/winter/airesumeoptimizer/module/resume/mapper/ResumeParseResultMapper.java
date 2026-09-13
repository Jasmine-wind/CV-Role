package com.winter.airesumeoptimizer.module.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResumeParseResultMapper extends BaseMapper<ResumeParseResult> {

    /**
     * Creates the durable coordination row without racing a concurrent first parse.
     * An existing row is intentionally left untouched; the claim update owns the generation.
     */
    @Insert("""
            INSERT INTO resume_parse_results (
                resume_id,
                user_id,
                parse_status,
                parse_generation,
                parse_token,
                quality_status,
                created_at,
                updated_at
            )
            SELECT resume.id,
                   resume.user_id,
                   'PENDING',
                   0,
                   NULL,
                   'PENDING',
                   #{now},
                   #{now}
            FROM resumes resume
            WHERE resume.id = #{resumeId}
            ON CONFLICT (resume_id) DO NOTHING
            """)
    int ensureParseRow(@Param("resumeId") Long resumeId, @Param("now") LocalDateTime now);

    /**
     * Claims the next parse generation and returns it from the same PostgreSQL statement.
     * The token is opaque and the generation makes accidental token reuse/diagnostics easier.
     */
    @Select("""
            WITH claimed AS (
                UPDATE resume_parse_results
                SET parse_generation = parse_generation + 1,
                    parse_token = #{parseToken},
                    parse_status = 'PROCESSING',
                    quality_status = 'PENDING',
                    extraction_page_count = NULL,
                    extraction_page_count_known = NULL,
                    extraction_image_content_present = NULL,
                    updated_at = #{now}
                WHERE resume_id = #{resumeId}
                RETURNING parse_generation
            )
            SELECT parse_generation FROM claimed
            """)
    Long claimParseGeneration(
            @Param("resumeId") Long resumeId,
            @Param("parseToken") String parseToken,
            @Param("now") LocalDateTime now);

    /**
     * Touches only the row that still belongs to this parse attempt. This is a cheap
     * pre-materialization CAS; the service checks the affected-row count before inserting SOURCE.
     */
    @Update("""
            UPDATE resume_parse_results
            SET updated_at = #{now}
            WHERE resume_id = #{resumeId}
              AND parse_generation = #{parseGeneration}
              AND parse_token = #{parseToken}
              AND parse_status = 'PROCESSING'
            """)
    int touchIfCurrent(
            @Param("resumeId") Long resumeId,
            @Param("parseGeneration") Long parseGeneration,
            @Param("parseToken") String parseToken,
            @Param("now") LocalDateTime now);

    /**
     * Finalizes only the row that still belongs to this parse attempt. The service checks the
     * affected-row count; zero means a newer attempt won and no stale result may be committed.
     */
    @Update("""
            UPDATE resume_parse_results
            SET parse_status = #{parse.parseStatus},
                extracted_text = #{parse.extractedText},
                cleaned_text = #{parse.cleanedText},
                section_result = #{parse.sectionResult},
                structured_json = #{parse.structuredJson},
                error_message = #{parse.errorMessage},
                text_quality_status = #{parse.textQualityStatus},
                text_quality_issues = #{parse.textQualityIssues},
                text_quality_message = #{parse.textQualityMessage},
                extraction_page_count = #{parse.extractionPageCount},
                extraction_page_count_known = #{parse.extractionPageCountKnown},
                extraction_image_content_present = #{parse.extractionImageContentPresent},
                parse_quality_status = #{parse.parseQualityStatus},
                parse_quality_warnings = #{parse.parseQualityWarnings},
                parse_quality_message = #{parse.parseQualityMessage},
                parse_quality_score = #{parse.parseQualityScore},
                quality_status = #{parse.qualityStatus},
                quality_issues = #{parse.qualityIssues},
                unresolved_items = #{parse.unresolvedItems},
                canonical_source_version_id = #{parse.canonicalSourceVersionId},
                parse_token = NULL,
                updated_at = #{parse.updatedAt}
            WHERE resume_id = #{resumeId}
              AND parse_generation = #{parseGeneration}
              AND parse_token = #{parseToken}
              AND parse_status = 'PROCESSING'
            """)
    int updateIfCurrent(
            @Param("parse") ResumeParseResult parseResult,
            @Param("resumeId") Long resumeId,
            @Param("parseGeneration") Long parseGeneration,
            @Param("parseToken") String parseToken);
}
