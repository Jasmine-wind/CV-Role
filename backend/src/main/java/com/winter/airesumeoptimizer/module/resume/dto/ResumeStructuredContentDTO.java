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
public class ResumeStructuredContentDTO {

    private String name;

    private String phone;

    private String email;

    private List<String> education;

    private List<String> skills;

    private List<String> projects;

    private List<String> internships;

    private String rawText;
}
