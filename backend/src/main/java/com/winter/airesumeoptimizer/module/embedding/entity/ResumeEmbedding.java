package com.winter.airesumeoptimizer.module.embedding.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("resume_embeddings")
public class ResumeEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private Long userId;

    private Integer chunkIndex;

    private String chunkText;

    private String embedding;

    private String embeddingModel;

    private Integer embeddingDimension;

    private String embeddingStatus;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
