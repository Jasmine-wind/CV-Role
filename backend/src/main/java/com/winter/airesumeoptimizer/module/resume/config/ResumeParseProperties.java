package com.winter.airesumeoptimizer.module.resume.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.resume.parse")
public class ResumeParseProperties {

    private Boolean aiSectionClassifyEnabled = true;

    private String mode = "BALANCED";

    private Double aiSectionClassifyMinConfidence = 0.6;

    private Boolean aiStructuredParseEnabled = true;

    private Integer aiMaxBlocks = 80;

    private Integer aiSectionClassifyBatchMaxChars = 6000;

    public Boolean getAiSectionClassifyEnabled() {
        return aiSectionClassifyEnabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setAiSectionClassifyEnabled(Boolean aiSectionClassifyEnabled) {
        this.aiSectionClassifyEnabled = aiSectionClassifyEnabled;
    }

    public Double getAiSectionClassifyMinConfidence() {
        return aiSectionClassifyMinConfidence;
    }

    public void setAiSectionClassifyMinConfidence(Double aiSectionClassifyMinConfidence) {
        this.aiSectionClassifyMinConfidence = aiSectionClassifyMinConfidence;
    }

    public Boolean getAiStructuredParseEnabled() {
        return aiStructuredParseEnabled;
    }

    public void setAiStructuredParseEnabled(Boolean aiStructuredParseEnabled) {
        this.aiStructuredParseEnabled = aiStructuredParseEnabled;
    }

    public Integer getAiMaxBlocks() {
        return aiMaxBlocks;
    }

    public void setAiMaxBlocks(Integer aiMaxBlocks) {
        this.aiMaxBlocks = aiMaxBlocks;
    }

    public Integer getAiSectionClassifyBatchMaxChars() {
        return aiSectionClassifyBatchMaxChars;
    }

    public void setAiSectionClassifyBatchMaxChars(Integer aiSectionClassifyBatchMaxChars) {
        this.aiSectionClassifyBatchMaxChars = aiSectionClassifyBatchMaxChars;
    }

    public boolean aiSectionClassifyEnabled() {
        return Boolean.TRUE.equals(aiSectionClassifyEnabled);
    }

    public boolean aiStructuredParseEnabled() {
        return Boolean.TRUE.equals(aiStructuredParseEnabled);
    }

    public double minConfidence() {
        if (aiSectionClassifyMinConfidence == null || aiSectionClassifyMinConfidence <= 0 || aiSectionClassifyMinConfidence > 1) {
            return 0.6;
        }
        return aiSectionClassifyMinConfidence;
    }

    public int aiMaxBlocks() {
        if (aiMaxBlocks == null || aiMaxBlocks <= 0) {
            return 80;
        }
        return aiMaxBlocks;
    }

    public int aiSectionClassifyBatchMaxChars() {
        if (aiSectionClassifyBatchMaxChars == null || aiSectionClassifyBatchMaxChars < 1000) {
            return 6000;
        }
        return aiSectionClassifyBatchMaxChars;
    }
}
