package com.winter.airesumeoptimizer.module.resume.dto;

/**
 * Generic structural role of a source-backed logical block.
 *
 * <p>These roles describe observable document structure only. They deliberately do not encode
 * resume-specific facts such as a particular project, company, or school.</p>
 */
public enum ResumeSourceBlockRole {
    UNKNOWN,
    SECTION_HEADING,
    ENTRY_HEADER,
    METADATA,
    LABEL_VALUE,
    BULLET,
    PARAGRAPH
}
