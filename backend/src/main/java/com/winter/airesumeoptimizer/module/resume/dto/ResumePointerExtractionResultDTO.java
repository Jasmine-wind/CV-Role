package com.winter.airesumeoptimizer.module.resume.dto;

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
public class ResumePointerExtractionResultDTO {

    private ResumePointerExtractorType extractorType;

    private String promptVersion;

    private Boolean aiInvoked;

    private Boolean cacheHit;

    private List<BasicInfoPointer> basicInfoPointers;

    private List<EducationPointer> educationPointers;

    private List<WorkExperiencePointer> workExperiencePointers;

    private List<ProjectPointer> projectPointers;

    private List<AchievementPointer> achievementPointers;

    private List<SummaryPointer> summaryPointers;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasicInfoPointer {
        private Integer nameLine;
        private Integer phoneLine;
        private Integer emailLine;
        private Double confidence;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EducationPointer {
        private Integer schoolLine;
        private Integer degreeLine;
        private Integer timeLine;
        private List<Integer> descriptionLineRange;
        private Double confidence;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkExperiencePointer {
        private Integer companyLine;
        private Integer positionLine;
        private Integer timeLine;
        private List<Integer> descriptionLineRange;
        private Double confidence;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectPointer {
        private Integer nameLine;
        private List<Integer> descriptionLineRange;
        private List<Integer> techStackLines;
        private List<Integer> responsibilityLineRange;
        private Double confidence;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AchievementPointer {
        private Integer titleLine;
        private Integer timeLine;
        private List<Integer> descriptionLineRange;
        private Double confidence;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryPointer {
        private List<Integer> lineRange;
        private Double confidence;
    }
}
