package com.winter.airesumeoptimizer.module.resume.dto;

import java.util.Locale;

public enum ResumeParseMode {
    FAST,
    BALANCED,
    ACCURATE;

    public static ResumeParseMode from(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        try {
            return ResumeParseMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BALANCED;
        }
    }
}
