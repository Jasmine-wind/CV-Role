package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchPromptService;
import org.springframework.stereotype.Service;

@Service
public class AiJobMatchPromptServiceImpl implements AiJobMatchPromptService {

    private static final int MAX_RESUME_STRUCTURED_LENGTH = 3500;
    private static final int MAX_JOB_STRUCTURED_LENGTH = 2500;
    private static final int MAX_RESUME_SUMMARY_LENGTH = 800;

    @Override
    public AiJobMatchPromptDTO buildPrompt(
            String resumeStructuredContent,
            String jobStructuredContent,
            String resumeRawTextSummary) {
        if (resumeStructuredContent == null || resumeStructuredContent.isBlank()) {
            throw new BusinessException(400, "简历结构化解析结果不能为空");
        }
        if (jobStructuredContent == null || jobStructuredContent.isBlank()) {
            throw new BusinessException(400, "岗位描述结构化解析结果不能为空");
        }

        return AiJobMatchPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt("""
                        Prompt 版本：ai_job_match_v1

                        你是简历与岗位匹配分析助手。只根据输入内容分析，不得编造简历中不存在的项目、技能、证书、奖项、公司、学校、职责、结果或量化指标；不得把岗位要求直接写成用户已具备能力。

                        只输出一个 JSON 对象。第一个字符必须是 {，最后一个字符必须是 }。不要 Markdown、代码块或解释文字。
                        JSON 字段固定为：overallScore、strongMatches、weakMatches、missingSkills、weakExperienceDescriptions、evidence、riskNotes。
                        overallScore 为 0 到 100 的整数。
                        strongMatches、weakMatches、missingSkills 每项包含 item、reason。
                        weakExperienceDescriptions 每项包含 section、issue。
                        evidence 每项包含 source、content，source 只能是 resume 或 job。
                        每个数组最多 3 条，每条不超过 40 个中文字符。

                        输出格式：
                        {"overallScore":82,"strongMatches":[{"item":"Java","reason":"简历和岗位均出现 Java"}],"weakMatches":[],"missingSkills":[],"weakExperienceDescriptions":[],"evidence":[{"source":"resume","content":"简历提到 Java"},{"source":"job","content":"岗位要求 Java"}],"riskNotes":[]}

                        简历结构化解析结果：
                        %s

                        岗位描述结构化解析结果：
                        %s

                        简历原文摘要：
                        %s
                        """.formatted(
                        normalize(resumeStructuredContent, MAX_RESUME_STRUCTURED_LENGTH),
                        normalize(jobStructuredContent, MAX_JOB_STRUCTURED_LENGTH),
                        normalizeOptional(resumeRawTextSummary, MAX_RESUME_SUMMARY_LENGTH)))
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
