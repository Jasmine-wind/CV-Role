package com.winter.airesumeoptimizer.module.job.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobDetailVO {

    private Long id;
    private String title;
    private String companyName;
    private String jobCategory;
    private String location;
    private String description;
    private String requirements;
    private List<String> requiredSkills;
    private LocalDateTime updatedAt;
}
