package com.winter.airesumeoptimizer.module.embedding.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.embedding.EmbeddingClientService;
import com.winter.airesumeoptimizer.module.embedding.entity.JobDescriptionEmbedding;
import com.winter.airesumeoptimizer.module.embedding.mapper.JobDescriptionEmbeddingMapper;
import com.winter.airesumeoptimizer.module.embedding.service.TextChunkService;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobDescriptionEmbeddingServiceImplTest {

    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final JobDescriptionEmbeddingMapper jobDescriptionEmbeddingMapper = mock(JobDescriptionEmbeddingMapper.class);
    private final TextChunkService textChunkService = mock(TextChunkService.class);
    private final EmbeddingClientService embeddingClientService = mock(EmbeddingClientService.class);
    private final JobDescriptionEmbeddingServiceImpl service = new JobDescriptionEmbeddingServiceImpl(
            jobDescriptionMapper,
            jobDescriptionEmbeddingMapper,
            textChunkService,
            embeddingClientService);

    @Test
    void generateShouldReplaceOldEmbeddingsAndSaveSuccess() {
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(textChunkService.splitJobDescriptionText("{}", "Java 岗位")).thenReturn(List.of("Java 岗位"));
        when(embeddingClientService.embed("Java 岗位")).thenReturn(List.of(0.1, 0.2, 0.3));
        when(embeddingClientService.modelName()).thenReturn("Qwen/Qwen3-Embedding-0.6B");
        when(embeddingClientService.dimension()).thenReturn(3);
        when(jobDescriptionEmbeddingMapper.insert(any(JobDescriptionEmbedding.class))).thenAnswer(invocation -> {
            JobDescriptionEmbedding record = invocation.getArgument(0);
            record.setId(99L);
            return 1;
        });
        when(jobDescriptionEmbeddingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildEmbedding("SUCCESS")));

        var summary = service.generate(1L, 10L);

        assertThat(summary.getEmbeddingStatus()).isEqualTo("SUCCESS");
        assertThat(summary.getTotalChunks()).isEqualTo(1);
        assertThat(summary.getSuccessChunks()).isEqualTo(1);
        verify(jobDescriptionEmbeddingMapper).deleteByJobDescriptionId(10L);
        verify(jobDescriptionEmbeddingMapper).updateSuccessEmbedding(
                eq(99L),
                eq("[0.1,0.2,0.3]"),
                eq("Qwen/Qwen3-Embedding-0.6B"),
                eq(3),
                any(LocalDateTime.class));
    }

    @Test
    void generateShouldRejectUnparsedJobDescription() {
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("FAILED"));

        assertThatThrownBy(() -> service.generate(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标岗位解析未成功，不能生成向量");
    }

    private JobDescription buildJobDescription(String status) {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(10L);
        jobDescription.setUserId(1L);
        jobDescription.setParseStatus(status);
        jobDescription.setStructuredContent("{}");
        jobDescription.setRawText("Java 岗位");
        return jobDescription;
    }

    private JobDescriptionEmbedding buildEmbedding(String status) {
        JobDescriptionEmbedding embedding = new JobDescriptionEmbedding();
        embedding.setId(99L);
        embedding.setJobDescriptionId(10L);
        embedding.setChunkIndex(0);
        embedding.setChunkText("Java 岗位");
        embedding.setEmbeddingModel("Qwen/Qwen3-Embedding-0.6B");
        embedding.setEmbeddingDimension(3);
        embedding.setEmbeddingStatus(status);
        return embedding;
    }
}
