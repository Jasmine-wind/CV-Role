package com.winter.airesumeoptimizer.module.embedding.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.module.embedding.dto.RagContextDTO;
import com.winter.airesumeoptimizer.module.embedding.service.ResumeRagService;
import com.winter.airesumeoptimizer.module.embedding.service.SemanticMatchService;
import com.winter.airesumeoptimizer.module.embedding.vo.SemanticMatchItemVO;
import com.winter.airesumeoptimizer.module.embedding.vo.SemanticMatchResultVO;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumeRagServiceImpl implements ResumeRagService {

    private static final int DEFAULT_TOP_K = 3;
    private static final int MAX_CONTEXT_LENGTH = 1800;

    private final SemanticMatchService semanticMatchService;

    public ResumeRagServiceImpl(SemanticMatchService semanticMatchService) {
        this.semanticMatchService = semanticMatchService;
    }

    @Override
    public RagContextDTO buildContext(Long userId, Long resumeId, Long jobDescriptionId, Integer topK) {
        try {
            SemanticMatchResultVO result = semanticMatchService.match(
                    userId,
                    resumeId,
                    jobDescriptionId,
                    topK == null ? DEFAULT_TOP_K : topK);
            return buildAvailableContext(result.getMatches());
        } catch (BusinessException exception) {
            if (exception.getCode() != null && exception.getCode() == 400) {
                return unavailable(LogSanitizer.sanitize(exception.getMessage()));
            }
            throw exception;
        }
    }

    private RagContextDTO buildAvailableContext(List<SemanticMatchItemVO> matches) {
        if (matches == null || matches.isEmpty()) {
            return unavailable("没有可用语义相似片段");
        }

        StringBuilder context = new StringBuilder();
        context.append("RAG 检索增强上下文：以下片段只来自当前用户自己的简历和目标岗位。")
                .append("这些片段只能作为辅助定位依据，不得替代原始简历、目标岗位或匹配分析结果。\n");
        for (int index = 0; index < matches.size(); index++) {
            SemanticMatchItemVO item = matches.get(index);
            context.append("\n片段 ").append(index + 1)
                    .append("，相似度：").append(formatScore(item.getSimilarityScore()))
                    .append("\n简历片段：").append(normalize(item.getResumeChunkText()))
                    .append("\n岗位片段：").append(normalize(item.getJobDescriptionChunkText()))
                    .append("\n辅助原因：该简历片段与岗位片段在向量空间中相近，可用于辅助匹配或优化建议。")
                    .append("\n");
        }

        return RagContextDTO.builder()
                .used(true)
                .matchCount(matches.size())
                .contextText(truncate(context.toString()))
                .note("已使用当前用户自己的简历和目标岗位向量片段")
                .build();
    }

    private RagContextDTO unavailable(String note) {
        return RagContextDTO.builder()
                .used(false)
                .matchCount(0)
                .contextText("未使用 RAG 上下文：" + note)
                .note(note)
                .build();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "未提供";
        }
        return value.strip()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+", " ");
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "未知";
        }
        return String.format("%.4f", score);
    }

    private String truncate(String value) {
        if (value.length() <= MAX_CONTEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CONTEXT_LENGTH) + "\n[RAG 上下文过长，已截断]";
    }
}
