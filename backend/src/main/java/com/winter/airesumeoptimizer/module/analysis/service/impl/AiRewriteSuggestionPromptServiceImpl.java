package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionPromptService;
import org.springframework.stereotype.Service;

@Service
public class AiRewriteSuggestionPromptServiceImpl implements AiRewriteSuggestionPromptService {

    private static final int MAX_ORIGINAL_TEXT_LENGTH = 2000;
    private static final int MAX_REWRITE_TYPE_LENGTH = 30;
    private static final int MAX_TARGET_SECTION_LENGTH = 100;
    private static final int MAX_JOB_STRUCTURED_LENGTH = 2500;
    private static final int MAX_MATCH_RESULT_LENGTH = 2500;
    private static final int MAX_AI_SUGGESTION_LENGTH = 2500;

    @Override
    public AiRewriteSuggestionPromptDTO buildPrompt(
            String originalText,
            String rewriteType,
            String targetSection,
            String jobStructuredContent,
            String aiMatchResult,
            String aiSuggestion) {
        if (originalText == null || originalText.isBlank()) {
            throw new BusinessException(400, "原文片段不能为空");
        }
        if (rewriteType == null || rewriteType.isBlank()) {
            throw new BusinessException(400, "改写对象类型不能为空");
        }
        if (targetSection == null || targetSection.isBlank()) {
            throw new BusinessException(400, "目标简历部分不能为空");
        }

        return AiRewriteSuggestionPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt("""
                        Prompt 版本：rewrite_suggestion_v1

                        你是简历局部改写建议助手。请只基于用户提供的原文片段进行表达优化，可以参考岗位描述结构化结果、AI 匹配结果和 AI 优化建议，但不得把参考内容写成用户已经具备的经历或能力。

                        严格禁止：
                        1. 不得编造原文中不存在的项目、公司、学校、岗位、职责、成果、技能、证书、奖项或量化指标。
                        2. 不得添加用户未提供且无法从原文推出的技术栈。
                        3. 不得代填接口调用量、用户数、性能提升比例、营收、排名等数字。
                        4. 不得生成完整简历，只能改写当前片段。
                        5. 不得输出 Markdown、代码块、解释文字或多余字段。

                        输出要求：
                        1. 只能输出一个 JSON 对象，第一个字符必须是 {，最后一个字符必须是 }。
                        2. JSON 顶层字段固定为 rewrittenText、rewriteReason、caution、needUserSupplement、supplementQuestions。
                        3. rewrittenText 是改写建议文本；如果原文太短或信息不足，必须保持保守，不能补造事实。
                        4. rewriteReason 说明改写原因，必须说明是否只调整表达、结构或重点。
                        5. caution 说明采用建议前需要确认的事实边界。
                        6. needUserSupplement 是布尔值；当原文太短、缺少职责、缺少技术动作、缺少真实成果或缺少上下文时必须为 true。
                        7. supplementQuestions 是字符串数组；needUserSupplement 为 true 时至少 1 条，最多 5 条；否则返回空数组。
                        8. 如果要补充量化指标，只能在 supplementQuestions 中询问用户真实数据，不能直接生成数字。

                        输出 JSON 示例：
                        {"rewrittenText":"负责 AI 简历优化系统中简历上传与解析模块的后端开发，基于 Spring Boot 实现文件上传接口、用户权限校验和解析结果持久化，并完成前后端联调。","rewriteReason":"在保留原文事实的基础上，补充模块职责、技术栈和工作内容，使表达更具体。","caution":"如果没有实际完成权限校验或持久化功能，不应直接采用该表述。","needUserSupplement":false,"supplementQuestions":[]}

                        原文片段：
                        %s

                        改写对象类型：
                        %s

                        目标简历部分：
                        %s

                        岗位描述结构化结果：
                        %s

                        AI 匹配结果：
                        %s

                        AI 优化建议：
                        %s
                        """.formatted(
                        normalize(originalText, MAX_ORIGINAL_TEXT_LENGTH),
                        normalize(rewriteType, MAX_REWRITE_TYPE_LENGTH),
                        normalize(targetSection, MAX_TARGET_SECTION_LENGTH),
                        normalizeOptional(jobStructuredContent, MAX_JOB_STRUCTURED_LENGTH),
                        normalizeOptional(aiMatchResult, MAX_MATCH_RESULT_LENGTH),
                        normalizeOptional(aiSuggestion, MAX_AI_SUGGESTION_LENGTH)))
                .build();
    }

    private String normalize(String value, int maxLength) {
        return truncate(value.strip().replace("\r\n", "\n").replace('\r', '\n'), maxLength);
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "未提供";
        }
        return normalize(value, maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n[内容过长，已截断]";
    }
}
