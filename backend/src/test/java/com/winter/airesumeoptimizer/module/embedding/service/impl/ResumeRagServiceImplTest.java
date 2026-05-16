package com.winter.airesumeoptimizer.module.embedding.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.embedding.service.SemanticMatchService;
import com.winter.airesumeoptimizer.module.embedding.vo.SemanticMatchItemVO;
import com.winter.airesumeoptimizer.module.embedding.vo.SemanticMatchResultVO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeRagServiceImplTest {

    private final SemanticMatchService semanticMatchService = mock(SemanticMatchService.class);
    private final ResumeRagServiceImpl service = new ResumeRagServiceImpl(semanticMatchService);

    @Test
    void buildContextShouldFormatSemanticMatches() {
        when(semanticMatchService.match(1L, 10L, 20L, 3)).thenReturn(SemanticMatchResultVO.builder()
                .resumeId(10L)
                .jobDescriptionId(20L)
                .matches(List.of(SemanticMatchItemVO.builder()
                        .resumeChunkText("Java 项目经验")
                        .jobDescriptionChunkText("Java 后端职责")
                        .similarityScore(0.82)
                        .build()))
                .build());

        var result = service.buildContext(1L, 10L, 20L, null);

        assertThat(result.isUsed()).isTrue();
        assertThat(result.getMatchCount()).isEqualTo(1);
        assertThat(result.getContextText()).contains("当前用户自己的简历和目标岗位描述");
        assertThat(result.getContextText()).contains("Java 项目经验");
        assertThat(result.getContextText()).contains("Java 后端职责");
    }

    @Test
    void buildContextShouldReturnUnavailableWhenSemanticMatchHasNoVectors() {
        when(semanticMatchService.match(1L, 10L, 20L, 3))
                .thenThrow(new BusinessException(400, "简历向量尚未生成，不能进行语义相似度查询"));

        var result = service.buildContext(1L, 10L, 20L, 3);

        assertThat(result.isUsed()).isFalse();
        assertThat(result.getMatchCount()).isZero();
        assertThat(result.getContextText()).contains("未使用 RAG 上下文");
        assertThat(result.getNote()).contains("简历向量尚未生成");
    }

    @Test
    void buildContextShouldRethrowOwnershipErrors() {
        when(semanticMatchService.match(1L, 10L, 20L, 3))
                .thenThrow(new BusinessException(404, "简历不存在"));

        assertThatThrownBy(() -> service.buildContext(1L, 10L, 20L, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");
    }
}
