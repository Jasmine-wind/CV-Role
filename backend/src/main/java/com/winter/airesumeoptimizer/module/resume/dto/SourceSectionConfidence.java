package com.winter.airesumeoptimizer.module.resume.dto;

public enum SourceSectionConfidence {
    HIGH,
    MEDIUM,
    LOW;

    public static SourceSectionConfidence from(String value) {
        if (value == null || value.isBlank()) {
            return LOW;
        }
        try {
            return SourceSectionConfidence.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return LOW;
        }
    }
}
