package com.winter.airesumeoptimizer.module.job.vo;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobListVO {

    private Long id;
    private String title;
    private String companyName;
    private String jobCategory;
    private String location;
    private List<String> requiredSkills;
    private String status;
}
