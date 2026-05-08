package com.winter.airesumeoptimizer.module.job.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchCalculationResultDTO;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchSuggestionDTO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import com.winter.airesumeoptimizer.module.job.service.JobMatchSuggestionService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JobMatchSuggestionServiceImpl implements JobMatchSuggestionService {

    private static final String TYPE_SKILL_GAP = "SKILL_GAP";
    private static final String TYPE_PROJECT_DESCRIPTION = "PROJECT_DESCRIPTION";
    private static final String TYPE_HIGHLIGHT_STRENGTH = "HIGHLIGHT_STRENGTH";
    private static final String TYPE_GENERAL = "GENERAL";

    private static final String PRIORITY_HIGH = "HIGH";
    private static final String PRIORITY_MEDIUM = "MEDIUM";
    private static final String PRIORITY_LOW = "LOW";

    @Override
    public List<JobMatchSuggestionDTO> generateSuggestions(JobMatchCalculationResultDTO matchResult, Job job) {
        if (matchResult == null) {
            throw new BusinessException(400, "岗位匹配结果不能为空");
        }

        List<JobMatchSuggestionDTO> suggestions = new ArrayList<>();
        suggestions.add(buildGeneralSuggestion(matchResult, job));
        suggestions.addAll(buildSkillGapSuggestions(matchResult.getMissingItems()));
        suggestions.addAll(buildStrengthSuggestions(matchResult.getMatchedItems()));
        suggestions.add(buildProjectDescriptionSuggestion(job));
        return suggestions;
    }

    private JobMatchSuggestionDTO buildGeneralSuggestion(JobMatchCalculationResultDTO matchResult, Job job) {
        int score = matchResult.getMatchScore() == null ? 0 : matchResult.getMatchScore();
        String jobTitle = resolveJobTitle(job);
        String priority;
        String content;
        if (score < 50) {
            priority = PRIORITY_HIGH;
            content = "当前与「" + jobTitle + "」的匹配度偏低，建议先补齐缺失技能，再调整项目经历中与岗位相关的表述。";
        } else if (score < 80) {
            priority = PRIORITY_MEDIUM;
            content = "当前与「" + jobTitle + "」已有一定匹配基础，建议优先强化缺失技能，并突出已命中的岗位能力。";
        } else {
            priority = PRIORITY_LOW;
            content = "当前与「" + jobTitle + "」匹配度较高，建议继续补充项目成果、量化指标和岗位相关场景。";
        }

        return JobMatchSuggestionDTO.builder()
                .type(TYPE_GENERAL)
                .priority(priority)
                .title("总体优化方向")
                .content(content)
                .relatedItem(resolveJobCategory(job))
                .build();
    }

    private List<JobMatchSuggestionDTO> buildSkillGapSuggestions(List<String> missingItems) {
        if (missingItems == null || missingItems.isEmpty()) {
            return List.of(JobMatchSuggestionDTO.builder()
                    .type(TYPE_SKILL_GAP)
                    .priority(PRIORITY_LOW)
                    .title("保持技能覆盖")
                    .content("当前岗位技能关键词已经全部覆盖，建议在项目经历中补充具体使用场景和产出结果。")
                    .relatedItem("全部岗位技能")
                    .build());
        }

        return missingItems.stream()
                .map(skill -> JobMatchSuggestionDTO.builder()
                        .type(TYPE_SKILL_GAP)
                        .priority(PRIORITY_HIGH)
                        .title("补充缺失技能：" + skill)
                        .content("岗位要求中包含「" + skill + "」，建议在技能清单或项目经历中补充相关学习、实践或使用记录。")
                        .relatedItem(skill)
                        .build())
                .toList();
    }

    private List<JobMatchSuggestionDTO> buildStrengthSuggestions(List<String> matchedItems) {
        if (matchedItems == null || matchedItems.isEmpty()) {
            return List.of(JobMatchSuggestionDTO.builder()
                    .type(TYPE_HIGHLIGHT_STRENGTH)
                    .priority(PRIORITY_MEDIUM)
                    .title("补充可匹配优势")
                    .content("当前暂未命中岗位技能关键词，建议优先从过往项目中提炼与岗位要求相近的技术点。")
                    .relatedItem("岗位技能")
                    .build());
        }

        return matchedItems.stream()
                .limit(3)
                .map(skill -> JobMatchSuggestionDTO.builder()
                        .type(TYPE_HIGHLIGHT_STRENGTH)
                        .priority(PRIORITY_MEDIUM)
                        .title("突出已匹配技能：" + skill)
                        .content("简历已覆盖「" + skill + "」，建议在项目经历中说明使用场景、个人职责和实际效果。")
                        .relatedItem(skill)
                        .build())
                .toList();
    }

    private JobMatchSuggestionDTO buildProjectDescriptionSuggestion(Job job) {
        String category = resolveJobCategory(job);
        return JobMatchSuggestionDTO.builder()
                .type(TYPE_PROJECT_DESCRIPTION)
                .priority(PRIORITY_MEDIUM)
                .title("按岗位方向调整项目描述")
                .content("面向「" + category + "」方向，建议把项目经历改写为：背景、职责、技术方案、结果四部分，减少只罗列技术名词。")
                .relatedItem(category)
                .build();
    }

    private String resolveJobTitle(Job job) {
        if (job == null || job.getTitle() == null || job.getTitle().isBlank()) {
            return "目标岗位";
        }
        return job.getTitle();
    }

    private String resolveJobCategory(Job job) {
        if (job == null || job.getJobCategory() == null || job.getJobCategory().isBlank()) {
            return "目标岗位";
        }
        return job.getJobCategory();
    }
}
