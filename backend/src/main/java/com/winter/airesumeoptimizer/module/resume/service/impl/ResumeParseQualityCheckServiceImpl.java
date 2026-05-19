package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseQualityCheckService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumeParseQualityCheckServiceImpl implements ResumeParseQualityCheckService {

    private static final int LONG_TEXT_LENGTH = 500;
    private static final int MIN_SECTION_COUNT = 2;
    private static final int OTHERS_TOO_MANY_THRESHOLD = 8;
    private static final int DUPLICATE_LINE_WARNING_THRESHOLD = 3;

    @Override
    public ResumeParseQualityResultDTO check(
            ResumeStructuredContentDTO structuredContent,
            ResumeTextCleanResultDTO cleanResult,
            ResumeTextQualityResultDTO textQualityResult) {
        if (structuredContent == null) {
            return result("FAILED", List.of("STRUCTURED_RESULT_EMPTY"), "结构化解析结果为空，请重新上传格式更清晰的简历", 0);
        }

        List<String> warnings = new ArrayList<>();
        if (coreFieldsMissing(structuredContent)) {
            warnings.add("CORE_FIELDS_MISSING");
        }
        if (isBlank(structuredContent.getName())) {
            warnings.add("NAME_MISSING");
        }
        if (isBlank(structuredContent.getPhone()) || isBlank(structuredContent.getEmail())) {
            warnings.add("CONTACT_MISSING");
        }
        if (isEmpty(structuredContent.getEducation())) {
            warnings.add("EDUCATION_MISSING");
        }
        if (isEmpty(structuredContent.getSkills())) {
            warnings.add("SKILLS_MISSING");
        }
        if (resumeTypeUnknown(structuredContent, cleanResult)) {
            warnings.add("RESUME_TYPE_UNKNOWN");
        }
        if (workExperienceExpectedButMissing(structuredContent)) {
            warnings.add("WORK_EXPERIENCE_MISSING");
        }
        if (campusOrInternshipExpectedButMissing(structuredContent)) {
            warnings.add("CAMPUS_OR_INTERNSHIP_MISSING");
        }
        if (experienceOrProjectMissing(structuredContent)) {
            warnings.add("EXPERIENCE_OR_PROJECT_MISSING");
        }
        if (size(structuredContent.getOthers()) > OTHERS_TOO_MANY_THRESHOLD) {
            warnings.add("OTHERS_TOO_MANY");
        }
        if (cleanResult != null
                && cleanResult.getDuplicateLineCount() != null
                && cleanResult.getDuplicateLineCount() >= DUPLICATE_LINE_WARNING_THRESHOLD) {
            warnings.add("DUPLICATE_CONTENT_TOO_MANY");
        }
        if (cleanResult != null
                && cleanResult.getInvalidLineCount() != null
                && cleanResult.getInvalidLineCount() > 0) {
            warnings.add("INVALID_CONTENT_FILTERED");
        }
        if (cleanResult != null
                && cleanResult.getSectionConflictWarnings() != null
                && !cleanResult.getSectionConflictWarnings().isEmpty()) {
            warnings.add("AI_SECTION_CONFLICT");
        }
        if (sectionCount(cleanResult) < MIN_SECTION_COUNT) {
            warnings.add("SECTION_TOO_FEW");
        }
        if (textStructureMismatch(structuredContent, cleanResult)) {
            warnings.add("TEXT_STRUCTURE_MISMATCH");
        }
        if (textQualityResult != null && "WARNING".equals(textQualityResult.getStatus())) {
            warnings.add("TEXT_QUALITY_WARNING");
        }

        if (warnings.contains("CORE_FIELDS_MISSING")) {
            return result("FAILED", warnings, "未能识别到有效的简历核心内容，请检查文件格式或重新上传排版更清晰的简历", calculateScore(warnings));
        }
        if (!warnings.isEmpty()) {
            return result("WARNING", warnings, resolveWarningMessage(warnings), calculateScore(warnings));
        }
        return result("GOOD", List.of(), "解析结果质量正常", 100);
    }

    private boolean coreFieldsMissing(ResumeStructuredContentDTO content) {
        return isBlank(content.getName())
                && isBlank(content.getPhone())
                && isBlank(content.getEmail())
                && isEmpty(content.getEducation())
                && isEmpty(content.getSkills())
                && isEmpty(content.getProjects())
                && isEmpty(content.getInternships())
                && isEmpty(content.getWorkExperiences());
    }

    private boolean resumeTypeUnknown(ResumeStructuredContentDTO content, ResumeTextCleanResultDTO cleanResult) {
        String cleanedText = cleanResult == null ? null : cleanResult.getCleanedText();
        return !isBlank(cleanedText)
                && cleanedText.length() >= 120
                && (isBlank(content.getResumeType()) || "UNKNOWN".equals(content.getResumeType()));
    }

    private boolean workExperienceExpectedButMissing(ResumeStructuredContentDTO content) {
        return "EXPERIENCED".equals(content.getResumeType())
                && isEmpty(content.getWorkExperiences());
    }

    private boolean campusOrInternshipExpectedButMissing(ResumeStructuredContentDTO content) {
        return ("STUDENT".equals(content.getResumeType()) || "INTERN".equals(content.getResumeType()))
                && isEmpty(content.getInternships())
                && isEmpty(content.getCampusExperiences());
    }

    private boolean experienceOrProjectMissing(ResumeStructuredContentDTO content) {
        return isEmpty(content.getWorkExperiences())
                && isEmpty(content.getInternships())
                && isEmpty(content.getProjects());
    }

    private boolean textStructureMismatch(ResumeStructuredContentDTO content, ResumeTextCleanResultDTO cleanResult) {
        String cleanedText = cleanResult == null ? null : cleanResult.getCleanedText();
        return cleanedText != null
                && cleanedText.length() >= LONG_TEXT_LENGTH
                && structuredFieldCount(content) <= 2;
    }

    private int structuredFieldCount(ResumeStructuredContentDTO content) {
        int count = 0;
        if (!isBlank(content.getName())) {
            count++;
        }
        if (!isBlank(content.getPhone())) {
            count++;
        }
        if (!isBlank(content.getEmail())) {
            count++;
        }
        count += isEmpty(content.getEducation()) ? 0 : 1;
        count += isEmpty(content.getSkills()) ? 0 : 1;
        count += isEmpty(content.getProjects()) ? 0 : 1;
        count += isEmpty(content.getWorkExperiences()) ? 0 : 1;
        count += isEmpty(content.getInternships()) ? 0 : 1;
        count += isEmpty(content.getCampusExperiences()) ? 0 : 1;
        count += isEmpty(content.getAwards()) ? 0 : 1;
        count += isEmpty(content.getCertificates()) ? 0 : 1;
        return count;
    }

    private int sectionCount(ResumeTextCleanResultDTO cleanResult) {
        if (cleanResult == null || cleanResult.getSections() == null) {
            return 0;
        }
        return (int) cleanResult.getSections().stream()
                .filter(section -> !"GENERAL".equals(section.getSectionType()))
                .count();
    }

    private String resolveWarningMessage(List<String> warnings) {
        if (warnings.contains("NAME_MISSING")) {
            return "未识别到姓名，建议检查简历顶部个人信息";
        }
        if (warnings.contains("CONTACT_MISSING")) {
            return "手机号或邮箱识别不完整，建议检查联系方式";
        }
        if (warnings.contains("EDUCATION_MISSING")) {
            return "未识别到教育经历，建议检查教育背景章节";
        }
        if (warnings.contains("RESUME_TYPE_UNKNOWN")) {
            return "未能明确识别简历类型，建议检查工作、实习或校园经历章节";
        }
        if (warnings.contains("WORK_EXPERIENCE_MISSING")) {
            return "简历类型为有工作经验，但未识别到工作经历内容";
        }
        if (warnings.contains("CAMPUS_OR_INTERNSHIP_MISSING")) {
            return "简历类型偏校招或实习，但未识别到实习或校园经历内容";
        }
        if (warnings.contains("EXPERIENCE_OR_PROJECT_MISSING")) {
            return "未识别到工作经历、实习经历或项目经历，后续分析依据可能不足";
        }
        if (warnings.contains("SKILLS_MISSING")) {
            return "未识别到技能列表，建议检查简历格式或手动补充";
        }
        if (warnings.contains("SECTION_TOO_FEW")) {
            return "简历章节识别较少，解析结果可能不完整";
        }
        if (warnings.contains("TEXT_STRUCTURE_MISMATCH")) {
            return "简历文本较长但结构化字段较少，建议检查解析结果";
        }
        if (warnings.contains("OTHERS_TOO_MANY")) {
            return "未归类内容较多，建议检查章节标题或手动整理";
        }
        if (warnings.contains("DUPLICATE_CONTENT_TOO_MANY")) {
            return "检测到较多重复文本，解析结果已自动去重但仍建议检查源文件";
        }
        if (warnings.contains("INVALID_CONTENT_FILTERED")) {
            return "检测到无效序号或空内容，解析结果已自动过滤";
        }
        if (warnings.contains("AI_SECTION_CONFLICT")) {
            return "AI 章节归类与规则章节存在冲突，已按置信度策略处理";
        }
        return "解析结果存在质量提示，建议检查后再继续分析";
    }

    private int calculateScore(List<String> warnings) {
        int score = 100;
        for (String warning : warnings) {
            score -= switch (warning) {
                case "CORE_FIELDS_MISSING" -> 60;
                case "TEXT_STRUCTURE_MISMATCH" -> 25;
                case "WORK_EXPERIENCE_MISSING", "CAMPUS_OR_INTERNSHIP_MISSING", "EXPERIENCE_OR_PROJECT_MISSING" -> 20;
                case "EDUCATION_MISSING", "SKILLS_MISSING" -> 15;
                case "NAME_MISSING", "CONTACT_MISSING", "RESUME_TYPE_UNKNOWN", "SECTION_TOO_FEW",
                        "TEXT_QUALITY_WARNING", "OTHERS_TOO_MANY", "DUPLICATE_CONTENT_TOO_MANY",
                        "INVALID_CONTENT_FILTERED", "AI_SECTION_CONFLICT" -> 10;
                default -> 5;
            };
        }
        return Math.max(score, 0);
    }

    private ResumeParseQualityResultDTO result(String status, List<String> warnings, String message, Integer score) {
        return ResumeParseQualityResultDTO.builder()
                .status(status)
                .warnings(List.copyOf(warnings))
                .message(message)
                .score(score)
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
