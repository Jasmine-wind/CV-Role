package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionPromptService;
import org.springframework.stereotype.Service;

@Service
public class AiResumeSuggestionPromptServiceImpl implements AiResumeSuggestionPromptService {

    private static final int MAX_RESUME_STRUCTURED_LENGTH = 3500;
    private static final int MAX_JOB_STRUCTURED_LENGTH = 2500;
    private static final int MAX_MATCH_RESULT_LENGTH = 3000;

    @Override
    public AiResumeSuggestionPromptDTO buildPrompt(
            String resumeStructuredContent,
            String jobStructuredContent,
            String aiMatchResult) {
        if (resumeStructuredContent == null || resumeStructuredContent.isBlank()) {
            throw new BusinessException(400, "简历结构化解析结果不能为空");
        }
        if (jobStructuredContent == null || jobStructuredContent.isBlank()) {
            throw new BusinessException(400, "岗位描述结构化解析结果不能为空");
        }
        if (aiMatchResult == null || aiMatchResult.isBlank()) {
            throw new BusinessException(400, "AI 匹配结果不能为空");
        }

        return AiResumeSuggestionPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt("""
                        Prompt 版本：resume_suggestion_v1

                        你是简历优化建议助手。请只根据输入的简历结构化解析结果、岗位描述结构化解析结果和 AI 匹配结果生成优化建议。

                        严格禁止：
                        1. 不得编造简历中不存在的经历、技能、证书、奖项、公司、学校、项目结果或量化指标。
                        2. 不得把岗位要求直接写成用户已经具备的能力。
                        3. 不得生成完整定制版简历。
                        4. 不得生成可直接替换的完整经历改写文本；局部改写属于后续任务。
                        5. 不得输出 Markdown、代码块、解释文字或多余字段。

                        输出要求：
                        1. 只能输出一个 JSON 对象，第一个字符必须是 {，最后一个字符必须是 }。
                        2. JSON 顶层字段固定为 suggestions。
                        3. suggestions 是数组，最多 8 条；如果依据不足，可以返回空数组或少量 GENERAL 建议。
                        4. 每条建议字段固定为 type、priority、targetSection、issue、suggestion、evidence、caution、relatedItems。
                        5. type 只能是 SKILL_GAP、EXPERIENCE_WEAKNESS、PROJECT_DESCRIPTION、HIGHLIGHT_STRENGTH、STRUCTURE、GENERAL。
                        6. priority 只能是 HIGH、MEDIUM、LOW。
                        7. evidence 必须是字符串数组，至少包含 1 条来自输入内容的依据。
                        8. 对缺失技能，只能建议用户在真实掌握后补充，不能直接建议虚构掌握。
                        9. 对量化指标，只能建议用户补充真实数据，不能代填数字。
                        10. 如果简历内容不足，必须说明信息不足，并建议用户补充真实内容。
                        11. 如果匹配结果不足，建议应保持保守，不扩大结论。

                        输出 JSON 示例：
                        {"suggestions":[{"type":"SKILL_GAP","priority":"HIGH","targetSection":"技能","issue":"岗位要求 Docker，但简历中未体现 Docker 相关内容","suggestion":"如果你确实掌握 Docker，建议在技能部分补充 Docker，并在项目经历中说明真实的容器化部署实践；如果暂未掌握，应先补充学习和真实实践。","evidence":["岗位要求中包含 Docker","AI 匹配结果显示 Docker 为缺失技能"],"caution":"不要在未真实掌握 Docker 的情况下写入简历。","relatedItems":["Docker"]}]}

                        简历结构化解析结果：
                        %s

                        岗位描述结构化解析结果：
                        %s

                        AI 匹配结果：
                        %s
                        """.formatted(
                        normalize(resumeStructuredContent, MAX_RESUME_STRUCTURED_LENGTH),
                        normalize(jobStructuredContent, MAX_JOB_STRUCTURED_LENGTH),
                        normalize(aiMatchResult, MAX_MATCH_RESULT_LENGTH)))
                .build();
    }

    private String normalize(String value, int maxLength) {
        return truncate(value.strip().replace("\r\n", "\n").replace('\r', '\n'), maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n[内容过长，已截断]";
    }
}
