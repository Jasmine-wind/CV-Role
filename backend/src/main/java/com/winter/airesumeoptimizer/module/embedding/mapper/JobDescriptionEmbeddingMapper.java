package com.winter.airesumeoptimizer.module.embedding.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.winter.airesumeoptimizer.module.embedding.entity.JobDescriptionEmbedding;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JobDescriptionEmbeddingMapper extends BaseMapper<JobDescriptionEmbedding> {

    @Update("""
            UPDATE job_description_embeddings
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
            UPDATE job_description_embeddings
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

    @Delete("DELETE FROM job_description_embeddings WHERE job_description_id = #{jobDescriptionId}")
    int deleteByJobDescriptionId(@Param("jobDescriptionId") Long jobDescriptionId);
}
