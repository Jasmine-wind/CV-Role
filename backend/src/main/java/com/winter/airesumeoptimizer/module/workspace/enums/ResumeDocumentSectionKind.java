package com.winter.airesumeoptimizer.module.workspace.enums;

import java.util.Arrays;

/**
 * 简历章节类型。未知类型统一归一为 {@link #CUSTOM}，保证编辑器可以处理任意章节。
 */
public enum ResumeDocumentSectionKind {

    SUMMARY,
    EDUCATION,
    EXPERIENCE,
    PROJECT,
    SKILL,
    ACHIEVEMENT,
    CERTIFICATE,
    OTHER,
    CUSTOM;

    public static ResumeDocumentSectionKind fromValue(String value) {
        if (value == null) {
            return CUSTOM;
        }
        return Arrays.stream(values())
                .filter(kind -> kind.name().equalsIgnoreCase(value.strip()))
                .findFirst()
                .orElse(CUSTOM);
    }
}
