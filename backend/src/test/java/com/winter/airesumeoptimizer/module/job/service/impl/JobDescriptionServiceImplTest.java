package com.winter.airesumeoptimizer.module.job.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.embedding.mapper.JobDescriptionEmbeddingMapper;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionSubmitDTO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobDescriptionServiceImplTest {

    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final JobDescriptionEmbeddingMapper jobDescriptionEmbeddingMapper = mock(JobDescriptionEmbeddingMapper.class);
    private final JobDescriptionServiceImpl service = new JobDescriptionServiceImpl(
            jobDescriptionMapper,
            aiJobMatchResultMapper,
            jobDescriptionEmbeddingMapper);

    @Test
    void submitShouldSaveJobDescriptionWithPendingStatus() {
        JobDescriptionSubmitDTO request = new JobDescriptionSubmitDTO();
        request.setTitle(" Java 后端开发工程师 ");
        request.setRawText(" 负责 Java 后端开发 ");

        when(jobDescriptionMapper.insert(any(JobDescription.class))).thenAnswer(invocation -> {
            JobDescription jobDescription = invocation.getArgument(0);
            jobDescription.setId(10L);
            return 1;
        });

        JobDescriptionVO result = service.submit(1L, request);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getTitle()).isEqualTo("Java 后端开发工程师");
        assertThat(result.getRawText()).isEqualTo("负责 Java 后端开发");
        assertThat(result.getParseStatus()).isEqualTo("PENDING");
        verify(jobDescriptionMapper).insert(any(JobDescription.class));
    }

    @Test
    void listByUserShouldReturnCurrentUsersJobDescriptions() {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(10L);
        jobDescription.setUserId(1L);
        jobDescription.setTitle("Java 后端开发工程师");
        jobDescription.setRawText("负责 Java 后端开发");
        jobDescription.setParseStatus("SUCCESS");

        when(jobDescriptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(jobDescription));

        List<JobDescriptionVO> results = service.listByUser(1L);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo(10L);
        assertThat(results.getFirst().getTitle()).isEqualTo("Java 后端开发工程师");
        assertThat(results.getFirst().getParseStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void getDetailShouldReturnOwnedJobDescription() {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(10L);
        jobDescription.setUserId(1L);
        jobDescription.setTitle("Java 后端开发工程师");
        jobDescription.setRawText("负责 Java 后端开发");
        jobDescription.setParseStatus("PENDING");

        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(jobDescription);

        JobDescriptionVO result = service.getDetail(1L, 10L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getTitle()).isEqualTo("Java 后端开发工程师");
        assertThat(result.getParseStatus()).isEqualTo("PENDING");
    }

    @Test
    void getDetailShouldRejectOtherUsersJobDescription() {
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getDetail(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位描述不存在");
    }

    @Test
    void submitShouldRejectAnonymousUser() {
        JobDescriptionSubmitDTO request = new JobDescriptionSubmitDTO();
        request.setTitle("Java 后端开发工程师");
        request.setRawText("负责 Java 后端开发");

        assertThatThrownBy(() -> service.submit(null, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登录");
    }

    @Test
    void deleteShouldRemoveAiMatchesAndOwnedJobDescription() {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(10L);
        jobDescription.setUserId(1L);
        jobDescription.setTitle("Java 后端开发工程师");
        jobDescription.setRawText("负责 Java 后端开发");
        jobDescription.setParseStatus("SUCCESS");

        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(jobDescription);

        service.delete(1L, 10L);

        verify(jobDescriptionEmbeddingMapper).deleteByJobDescriptionId(10L);
        verify(aiJobMatchResultMapper).delete(any(Wrapper.class));
        verify(jobDescriptionMapper).deleteById(10L);
    }
}
