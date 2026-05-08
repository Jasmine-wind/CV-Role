package com.winter.airesumeoptimizer.module.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchCalculationResultDTO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import com.winter.airesumeoptimizer.module.job.service.JobMatchService;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class JobMatchServiceImpl implements JobMatchService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final Map<String, String> SKILL_ALIASES = Map.ofEntries(
            Map.entry("spring", "spring"),
            Map.entry("springboot", "springboot"),
            Map.entry("mybatis", "mybatis"),
            Map.entry("mybatisplus", "mybatisplus"),
            Map.entry("vue", "vue"),
            Map.entry("vue3", "vue3"),
            Map.entry("openaiapi", "openaiapi"),
            Map.entry("restful", "restful")
    );

    private final ObjectMapper objectMapper;

    public JobMatchServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JobMatchCalculationResultDTO calculateMatch(ResumeStructuredContentDTO resumeContent, Job job) {
        if (job == null) {
            throw new BusinessException(400, "岗位信息不能为空");
        }

        List<String> jobSkills = parseRequiredSkills(job.getRequiredSkills());
        if (jobSkills.isEmpty()) {
            throw new BusinessException(400, "岗位技能要求不能为空");
        }

        Set<String> normalizedResumeSkills = normalizeSkills(resumeContent == null ? null : resumeContent.getSkills());
        List<String> matchedItems = jobSkills.stream()
                .filter(skill -> normalizedResumeSkills.contains(normalizeSkill(skill)))
                .toList();
        List<String> missingItems = jobSkills.stream()
                .filter(skill -> !normalizedResumeSkills.contains(normalizeSkill(skill)))
                .toList();

        int matchScore = calculateScore(matchedItems.size(), jobSkills.size());
        return JobMatchCalculationResultDTO.builder()
                .matchScore(matchScore)
                .matchedItems(matchedItems)
                .missingItems(missingItems)
                .matchReason(buildMatchReason(matchScore, matchedItems, missingItems, normalizedResumeSkills.isEmpty()))
                .build();
    }

    private List<String> parseRequiredSkills(String requiredSkills) {
        if (requiredSkills == null || requiredSkills.isBlank()) {
            return List.of();
        }

        String normalized = requiredSkills.trim();
        List<String> skills;
        if (normalized.startsWith("[")) {
            try {
                skills = objectMapper.readValue(normalized, STRING_LIST_TYPE);
            } catch (JsonProcessingException exception) {
                throw new BusinessException(500, "岗位技能关键词格式不正确");
            }
        } else {
            skills = Arrays.stream(normalized.split(",")).toList();
        }

        return skills.stream()
                .map(skill -> skill == null ? "" : skill.trim())
                .filter(skill -> !skill.isEmpty())
                .distinct()
                .toList();
    }

    private Set<String> normalizeSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return Set.of();
        }

        Set<String> normalizedSkills = new LinkedHashSet<>();
        for (String skill : skills) {
            String normalized = normalizeSkill(skill);
            if (!normalized.isBlank()) {
                normalizedSkills.add(normalized);
            }
        }
        return normalizedSkills;
    }

    private String normalizeSkill(String skill) {
        if (skill == null) {
            return "";
        }
        String normalized = skill.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s._+-]", "");
        return SKILL_ALIASES.getOrDefault(normalized, normalized);
    }

    private int calculateScore(int matchedCount, int totalCount) {
        return (int) Math.round(matchedCount * 100.0 / totalCount);
    }

    private String buildMatchReason(
            int matchScore,
            List<String> matchedItems,
            List<String> missingItems,
            boolean resumeSkillsEmpty) {
        if (resumeSkillsEmpty) {
            return "简历技能信息不足，暂未命中岗位要求，建议先补充技能关键词。";
        }
        if (missingItems.isEmpty()) {
            return "简历技能与岗位要求匹配度较高，已覆盖全部岗位技能关键词。";
        }
        if (matchedItems.isEmpty()) {
            return "暂未命中岗位技能关键词，建议优先补充岗位要求中的核心技能。";
        }
        return "当前匹配分数为 " + matchScore + "，已命中 "
                + matchedItems.size() + " 项技能，仍缺少 "
                + missingItems.size() + " 项岗位要求技能。";
    }
}
