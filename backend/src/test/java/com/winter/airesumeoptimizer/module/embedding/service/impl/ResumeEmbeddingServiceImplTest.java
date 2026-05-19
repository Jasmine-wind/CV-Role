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
import com.winter.airesumeoptimizer.module.embedding.entity.ResumeEmbedding;
import com.winter.airesumeoptimizer.module.embedding.mapper.ResumeEmbeddingMapper;
import com.winter.airesumeoptimizer.module.embedding.service.TextChunkService;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeEmbeddingServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);
    private final ResumeEmbeddingMapper resumeEmbeddingMapper = mock(ResumeEmbeddingMapper.class);
    private final TextChunkService textChunkService = mock(TextChunkService.class);
    private final EmbeddingClientService embeddingClientService = mock(EmbeddingClientService.class);
    private final ResumeEmbeddingServiceImpl service = new ResumeEmbeddingServiceImpl(
            resumeMapper,
            resumeParseResultMapper,
            resumeEmbeddingMapper,
            textChunkService,
            embeddingClientService);

    @Test
    void generateShouldReplaceOldEmbeddingsAndSaveSuccess() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult("SUCCESS"));
        when(textChunkService.splitResumeText("{}", "Java 项目")).thenReturn(List.of("Java 项目"));
        when(embeddingClientService.embed("Java 项目")).thenReturn(List.of(0.1, 0.2, 0.3));
        when(embeddingClientService.modelName()).thenReturn("Qwen/Qwen3-Embedding-0.6B");
        when(embeddingClientService.dimension()).thenReturn(3);
        when(resumeEmbeddingMapper.insert(any(ResumeEmbedding.class))).thenAnswer(invocation -> {
            ResumeEmbedding record = invocation.getArgument(0);
            record.setId(99L);
            return 1;
        });
        when(resumeEmbeddingMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildEmbedding("SUCCESS")));

        var summary = service.generate(1L, 10L);

        assertThat(summary.getEmbeddingStatus()).isEqualTo("SUCCESS");
        assertThat(summary.getTotalChunks()).isEqualTo(1);
        assertThat(summary.getSuccessChunks()).isEqualTo(1);
        verify(resumeEmbeddingMapper).deleteByResumeId(10L);
        verify(resumeEmbeddingMapper).updateSuccessEmbedding(
                eq(99L),
                eq("[0.1,0.2,0.3]"),
                eq("Qwen/Qwen3-Embedding-0.6B"),
                eq(3),
                any(LocalDateTime.class));
    }

    @Test
    void generateShouldRejectUnparsedResume() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult("FAILED"));

        assertThatThrownBy(() -> service.generate(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历解析未成功，不能生成向量");
    }

    private Resume buildResume() {
        Resume resume = new Resume();
        resume.setId(10L);
        resume.setUserId(1L);
        return resume;
    }

    private ResumeParseResult buildParseResult(String status) {
        ResumeParseResult parseResult = new ResumeParseResult();
        parseResult.setResumeId(10L);
        parseResult.setParseStatus(status);
        parseResult.setStructuredJson("{}");
        parseResult.setExtractedText("Java 项目");
        return parseResult;
    }

    private ResumeEmbedding buildEmbedding(String status) {
        ResumeEmbedding embedding = new ResumeEmbedding();
        embedding.setId(99L);
        embedding.setResumeId(10L);
        embedding.setChunkIndex(0);
        embedding.setChunkText("Java 项目");
        embedding.setEmbeddingModel("Qwen/Qwen3-Embedding-0.6B");
        embedding.setEmbeddingDimension(3);
        embedding.setEmbeddingStatus(status);
        return embedding;
    }
}
