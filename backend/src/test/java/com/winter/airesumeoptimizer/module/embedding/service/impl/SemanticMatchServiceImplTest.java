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
import com.winter.airesumeoptimizer.module.embedding.dto.SemanticMatchRow;
import com.winter.airesumeoptimizer.module.embedding.entity.JobDescriptionEmbedding;
import com.winter.airesumeoptimizer.module.embedding.entity.ResumeEmbedding;
import com.winter.airesumeoptimizer.module.embedding.mapper.JobDescriptionEmbeddingMapper;
import com.winter.airesumeoptimizer.module.embedding.mapper.ResumeEmbeddingMapper;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticMatchServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final ResumeEmbeddingMapper resumeEmbeddingMapper = mock(ResumeEmbeddingMapper.class);
    private final JobDescriptionEmbeddingMapper jobDescriptionEmbeddingMapper = mock(JobDescriptionEmbeddingMapper.class);
    private final SemanticMatchServiceImpl service = new SemanticMatchServiceImpl(
            resumeMapper,
            jobDescriptionMapper,
            resumeEmbeddingMapper,
            jobDescriptionEmbeddingMapper);

    @Test
    void matchShouldReturnTopSemanticMatches() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(resumeEmbeddingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildResumeEmbedding(1024)));
        when(jobDescriptionEmbeddingMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(buildJobDescriptionEmbedding(1024)));
        when(resumeEmbeddingMapper.selectTopSemanticMatches(1L, 10L, 20L, 5))
                .thenReturn(List.of(buildRow(0.82)));

        var result = service.match(1L, 10L, 20L, 5);

        assertThat(result.getResumeId()).isEqualTo(10L);
        assertThat(result.getJobDescriptionId()).isEqualTo(20L);
        assertThat(result.getEmbeddingDimension()).isEqualTo(1024);
        assertThat(result.getOverallSimilarity()).isEqualTo(0.82);
        assertThat(result.getMatches()).hasSize(1);
        assertThat(result.getMatches().getFirst().getResumeChunkText()).isEqualTo("Java 项目经验");
    }

    @Test
    void matchShouldCapTopK() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(resumeEmbeddingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildResumeEmbedding(1024)));
        when(jobDescriptionEmbeddingMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(buildJobDescriptionEmbedding(1024)));
        when(resumeEmbeddingMapper.selectTopSemanticMatches(eq(1L), eq(10L), eq(20L), eq(20)))
                .thenReturn(List.of(buildRow(0.7)));

        var result = service.match(1L, 10L, 20L, 99);

        assertThat(result.getTopK()).isEqualTo(20);
        verify(resumeEmbeddingMapper).selectTopSemanticMatches(1L, 10L, 20L, 20);
    }

    @Test
    void matchShouldRejectMissingResumeEmbeddings() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(resumeEmbeddingMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.match(1L, 10L, 20L, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历向量尚未生成，不能进行语义相似度查询");
    }

    @Test
    void matchShouldRejectDimensionMismatch() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(resumeEmbeddingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildResumeEmbedding(1024)));
        when(jobDescriptionEmbeddingMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(buildJobDescriptionEmbedding(768)));

        assertThatThrownBy(() -> service.match(1L, 10L, 20L, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历向量和目标岗位向量维度不一致，不能进行语义相似度查询");
    }

    private Resume buildResume() {
        Resume resume = new Resume();
        resume.setId(10L);
        resume.setUserId(1L);
        return resume;
    }

    private JobDescription buildJobDescription() {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(20L);
        jobDescription.setUserId(1L);
        return jobDescription;
    }

    private ResumeEmbedding buildResumeEmbedding(Integer dimension) {
        ResumeEmbedding embedding = new ResumeEmbedding();
        embedding.setId(100L);
        embedding.setResumeId(10L);
        embedding.setEmbeddingModel("Qwen/Qwen3-Embedding-0.6B");
        embedding.setEmbeddingDimension(dimension);
        embedding.setEmbeddingStatus("SUCCESS");
        embedding.setEmbedding("[0.1,0.2]");
        return embedding;
    }

    private JobDescriptionEmbedding buildJobDescriptionEmbedding(Integer dimension) {
        JobDescriptionEmbedding embedding = new JobDescriptionEmbedding();
        embedding.setId(200L);
        embedding.setJobDescriptionId(20L);
        embedding.setEmbeddingModel("Qwen/Qwen3-Embedding-0.6B");
        embedding.setEmbeddingDimension(dimension);
        embedding.setEmbeddingStatus("SUCCESS");
        embedding.setEmbedding("[0.1,0.2]");
        return embedding;
    }

    private SemanticMatchRow buildRow(Double score) {
        SemanticMatchRow row = new SemanticMatchRow();
        row.setResumeEmbeddingId(100L);
        row.setResumeChunkIndex(0);
        row.setResumeChunkText("Java 项目经验");
        row.setJobDescriptionEmbeddingId(200L);
        row.setJobDescriptionChunkIndex(0);
        row.setJobDescriptionChunkText("Java 后端职责");
        row.setSimilarityScore(score);
        return row;
    }
}
