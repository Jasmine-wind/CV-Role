package com.winter.airesumeoptimizer.module.embedding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.winter.airesumeoptimizer.module.embedding.dto.SemanticMatchRow;
import com.winter.airesumeoptimizer.module.embedding.entity.ResumeEmbedding;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResumeEmbeddingMapper extends BaseMapper<ResumeEmbedding> {

    @Update("""
            UPDATE resume_embeddings
            SET embedding = CAST(#{embedding} AS vector),
                embedding_model = #{embeddingModel},
                embedding_dimension = #{embeddingDimension},
                embedding_status = 'SUCCESS',
                error_message = NULL,
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateSuccessEmbedding(
            @Param("id") Long id,
            @Param("embedding") String embedding,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDimension") Integer embeddingDimension,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE resume_embeddings
            SET embedding = NULL,
                embedding_model = #{embeddingModel},
                embedding_dimension = #{embeddingDimension},
                embedding_status = 'FAILED',
                error_message = #{errorMessage},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateFailedEmbedding(
            @Param("id") Long id,
            @Param("embeddingModel") String embeddingModel,
            @Param("embeddingDimension") Integer embeddingDimension,
            @Param("errorMessage") String errorMessage,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Delete("DELETE FROM resume_embeddings WHERE resume_id = #{resumeId}")
    int deleteByResumeId(@Param("resumeId") Long resumeId);

    @Select("""
            SELECT
                re.id AS resume_embedding_id,
                re.chunk_index AS resume_chunk_index,
                re.chunk_text AS resume_chunk_text,
                jde.id AS job_description_embedding_id,
                jde.chunk_index AS job_description_chunk_index,
                jde.chunk_text AS job_description_chunk_text,
                GREATEST(0, LEAST(1, 1 - (re.embedding <=> jde.embedding))) AS similarity_score
            FROM resume_embeddings re
            JOIN job_description_embeddings jde
                ON jde.job_description_id = #{jobDescriptionId}
                AND jde.user_id = #{userId}
                AND jde.embedding_status = 'SUCCESS'
                AND jde.embedding IS NOT NULL
            WHERE re.resume_id = #{resumeId}
                AND re.user_id = #{userId}
                AND re.embedding_status = 'SUCCESS'
                AND re.embedding IS NOT NULL
            ORDER BY re.embedding <=> jde.embedding ASC
            LIMIT #{topK}
            """)
    List<SemanticMatchRow> selectTopSemanticMatches(
            @Param("userId") Long userId,
            @Param("resumeId") Long resumeId,
            @Param("jobDescriptionId") Long jobDescriptionId,
            @Param("topK") int topK);
}
