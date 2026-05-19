package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredParsePromptDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructuredParsePromptService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ResumeStructuredParsePromptServiceImpl implements ResumeStructuredParsePromptService {

    private static final String PROMPT_VERSION = "resume-structured-parse-v2";
    private static final int MAX_BLOCK_TEXT_LENGTH = 300;

    private final ObjectMapper objectMapper;

    public ResumeStructuredParsePromptServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ResumeStructuredParsePromptDTO buildPrompt(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings) {
        try {
            String blocksJson = objectMapper.writeValueAsString(toPromptBlocks(blocks));
            String ruleJson = objectMapper.writeValueAsString(toPromptRuleContent(ruleStructuredContent));
            String warningsJson = objectMapper.writeValueAsString(qualityWarnings == null ? List.of() : qualityWarnings);

            return ResumeStructuredParsePromptDTO.builder()
                    .promptVersion(PROMPT_VERSION)
                    .prompt("""
                            你是简历结构化解析校正器。请只基于输入 classifiedBlocks 和 ruleStructuredContent 补全结构化 JSON。

                            约束：
                            1. 不得编造输入中不存在的学校、公司、项目、技能、证书、奖项、电话、邮箱、姓名。
                            2. 不确定的字段返回 null 或空数组，不要猜测。
                            3. 技能 skills 只能放技术词、工具、框架、数据库、中间件、编程语言、平台，不要放整句描述。
                            4. education 只放教育相关内容，不要混入技术栈。
                            5. workExperiences、projects、internships、campusExperiences 不要互相复制整段内容。
                            6. others 只放无法归类但有价值的内容，已归入其他字段的内容不要重复放入 others。
                            7. qualityWarnings 可以保留输入警告，也可以补充明显解析风险。
                            8. 只返回有把握补全或纠错的字段；ruleStructuredContent 中未列为 missingFields 的稳定字段不要输出。
                            9. 字段可以省略；数组最多 8 项，每项最多 160 字。
                            10. 输出必须是合法 JSON，不要 Markdown，不要解释，首字符必须是 {，末字符必须是 }。

                            可返回字段 schema:
                            {
                              "name": null,
                              "phone": null,
                              "email": null,
                              "basicInfo": {
                                "name": null,
                                "phone": null,
                                "email": null,
                                "gender": null,
                                "age": null,
                                "degree": null,
                                "location": null,
                                "jobIntention": null,
                                "workYears": null,
                                "resumeType": "STUDENT|INTERN|EXPERIENCED|UNKNOWN"
                              },
                              "jobIntention": null,
                              "highestEducation": null,
                              "resumeType": "STUDENT|INTERN|EXPERIENCED|UNKNOWN",
                              "education": [],
                              "skills": [],
                              "workExperiences": [],
                              "internships": [],
                              "projects": [],
                              "campusExperiences": [],
                              "awards": [],
                              "certificates": [],
                              "summary": null,
                              "others": [],
                              "qualityWarnings": []
                            }

                            classifiedBlocks:
                            %s

                            ruleStructuredContent:
                            %s

                            inputQualityWarnings:
                            %s
                            """.formatted(blocksJson, ruleJson, warningsJson))
                    .build();
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "结构化解析 Prompt 序列化失败");
        }
    }

    private List<Map<String, Object>> toPromptBlocks(List<ResumeBlockDTO> blocks) {
        if (blocks == null) {
            return List.of();
        }
        return blocks.stream()
                .map(block -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("index", block.getIndex());
                    item.put("sourceSection", block.getSourceSection());
                    item.put("sectionLocked", Boolean.TRUE.equals(block.getSectionLocked()));
                    item.put("text", truncate(block.getText()));
                    return item;
                })
                .toList();
    }

    private Map<String, Object> toPromptRuleContent(ResumeStructuredContentDTO content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content == null) {
            return result;
        }
        List<String> missingFields = new java.util.ArrayList<>();
        result.put("parseMode", content.getParseMode());
        addMissing(missingFields, "name", content.getName());
        addMissing(missingFields, "phone", content.getPhone());
        addMissing(missingFields, "email", content.getEmail());
        addMissing(missingFields, "jobIntention", content.getJobIntention());
        addMissing(missingFields, "highestEducation", content.getHighestEducation());
        addMissing(missingFields, "resumeType", content.getResumeType());
        addMissing(missingFields, "education", content.getEducation());
        addMissing(missingFields, "skills", content.getSkills());
        addMissing(missingFields, "workExperiences", content.getWorkExperiences());
        addMissing(missingFields, "internships", content.getInternships());
        addMissing(missingFields, "projects", content.getProjects());
        addMissing(missingFields, "campusExperiences", content.getCampusExperiences());
        addMissing(missingFields, "awards", content.getAwards());
        addMissing(missingFields, "certificates", content.getCertificates());
        addMissing(missingFields, "summary", content.getSummary());
        addMissing(missingFields, "others", content.getOthers());
        result.put("missingFields", missingFields);
        result.put("stableFieldsOmitted", true);
        if ("ACCURATE".equals(content.getParseMode())) {
            result.put("contextPolicy", "ACCURATE_MORE_CONTEXT");
            result.put("basicInfoDebug", summarizeBasicInfoDebug(content));
        }
        return result;
    }

    private Map<String, Object> summarizeBasicInfoDebug(ResumeStructuredContentDTO content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content.getBasicInfoDebug() == null) {
            return result;
        }
        content.getBasicInfoDebug().forEach((field, detail) -> {
            if (detail == null || (!"LOW_CONFIDENCE".equals(detail.getStatus()) && !"REJECTED".equals(detail.getStatus()))) {
                return;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", detail.getStatus());
            item.put("confidence", detail.getConfidence());
            item.put("evidence", truncate(detail.getEvidence(), 120));
            item.put("rejectReason", detail.getRejectReason());
            result.put(field, item);
        });
        return result;
    }

    private void addMissing(List<String> missingFields, String fieldName, String value) {
        if (value == null || value.isBlank() || "UNKNOWN".equals(value)) {
            missingFields.add(fieldName);
        }
    }

    private void addMissing(List<String> missingFields, String fieldName, List<String> values) {
        if (values == null || values.isEmpty()) {
            missingFields.add(fieldName);
        }
    }

    private String truncate(String value) {
        return truncate(value, MAX_BLOCK_TEXT_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
