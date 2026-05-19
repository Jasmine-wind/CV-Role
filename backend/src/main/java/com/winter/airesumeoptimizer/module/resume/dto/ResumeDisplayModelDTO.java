package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "简历解析结果前端展示模型")
public class ResumeDisplayModelDTO {

    private Overview overview;

    private SkillSummary skillSummary;

    private List<EducationCard> educationCards;

    private List<ExperienceCard> workExperienceCards;

    private List<ExperienceCard> internshipCards;

    private List<ExperienceCard> campusExperienceCards;

    private List<ProjectCard> projectCards;

    private List<AchievementCard> achievementCards;

    private List<String> certificateTags;

    private SummaryCard summaryCard;

    private List<String> pendingItems;

    private DisplayMeta displayMeta;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Overview {
        private String name;
        private String targetRole;
        private String resumeType;
        private String highestDegree;
        private String workYears;
        private List<String> coreSkills;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillSummary {
        private List<String> topSkills;
        private List<SkillGroup> groups;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillGroup {
        private String name;
        private List<String> skills;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EducationCard {
        private String school;
        private String degree;
        private String major;
        private String timeRange;
        private String summary;
        private ResumeSourceRefDTO sourceRef;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceCard {
        private String company;
        private String position;
        private String timeRange;
        private String summary;
        private List<String> responsibilities;
        private Boolean collapsed;
        private ResumeSourceRefDTO sourceRef;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectCard {
        private String name;
        private String summary;
        private List<String> techStack;
        private List<String> responsibilities;
        private Boolean collapsed;
        private ResumeSourceRefDTO sourceRef;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AchievementCard {
        private String title;
        private String meta;
        private ResumeSourceRefDTO sourceRef;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryCard {
        private String content;
        private Boolean collapsed;
        private ResumeSourceRefDTO sourceRef;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisplayMeta {
        private String generatedBy;
        private Boolean aiDisplayUsed;
        private Boolean aiDisplayFallback;
        private String aiDisplayErrorMessage;
        private Long aiDisplayDurationMs;
        private Boolean cacheHit;
        private String cacheKeyDigest;
        private String displayPromptVersion;
        private String displayAdapterVersion;
        private String modelName;
    }
}
