package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassifyPromptDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeSectionClassifyPromptService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ResumeSectionClassifyPromptServiceImpl implements ResumeSectionClassifyPromptService {

    private static final String PROMPT_VERSION = "resume-section-classify-v2";
    private static final int MAX_BLOCK_TEXT_LENGTH = 260;
    private static final List<String> SECTIONS = List.of(
            "BASIC_INFO",
            "EDUCATION",
            "SKILLS",
            "WORK_EXPERIENCES",
            "INTERNSHIPS",
            "PROJECTS",
            "CAMPUS_EXPERIENCES",
            "AWARDS",
            "CERTIFICATES",
            "SUMMARY",
            "OTHERS");

    private final ObjectMapper objectMapper;

    public ResumeSectionClassifyPromptServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ResumeSectionClassifyPromptDTO buildPrompt(List<ResumeBlockDTO> blocks) {
        try {
            String inputJson = objectMapper.writeValueAsString(toPromptInput(blocks));
            String sectionsJson = objectMapper.writeValueAsString(SECTIONS);
            return ResumeSectionClassifyPromptDTO.builder()
                    .promptVersion(PROMPT_VERSION)
                    .prompt("""
                            classify resume blocks. use only blocks[].text and minimal neighbor context. no fabrication. no rewriting.
                            only classify blocks without locked source section. keep ruleSection when sectionLocked=true.
                            section must be one of allowedSections. unknown => OTHERS. confidence is 0..1.
                            return JSON only, no markdown, no explanation, no original text beyond index references.
                            output fields only: items[].index, items[].section, items[].confidence, items[].reasonCode.
                            allowedSections=%s
                            input=%s
                            JSON={"items":[{"index":0,"section":"BASIC_INFO","confidence":0.95,"reasonCode":"CONTACT_CONTEXT"}]}
                            """.formatted(sectionsJson, inputJson))
                    .build();
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "章节归类 Prompt 序列化失败");
        }
    }

    static List<String> allowedSections() {
        return SECTIONS;
    }

    private Map<String, Object> toPromptInput(List<ResumeBlockDTO> blocks) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("parseMode", firstNonBlank(blocks, "parseMode"));
        input.put("resumeTypeHint", firstNonBlank(blocks, "resumeTypeHint"));
        input.put("blocks", toPromptBlocks(blocks));
        return input;
    }

    private List<Map<String, Object>> toPromptBlocks(List<ResumeBlockDTO> blocks) {
        if (blocks == null) {
            return List.of();
        }
        return blocks.stream()
                .map(block -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("index", block.getIndex());
                    item.put("text", truncate(block.getText()));
                    item.put("prevText", truncate(block.getPrevText()));
                    item.put("nextText", truncate(block.getNextText()));
                    item.put("sourceSection", block.getSourceSection());
                    item.put("ruleSection", block.getRuleSection());
                    item.put("ruleConfidence", block.getRuleConfidence());
                    item.put("sourceSectionConfidence", block.getSourceSectionConfidence());
                    item.put("lockedLevel", block.getLockedLevel());
                    item.put("resumeTypeHint", block.getResumeTypeHint());
                    return item;
                })
                .toList();
    }

    private String firstNonBlank(List<ResumeBlockDTO> blocks, String field) {
        if (blocks == null) {
            return null;
        }
        return blocks.stream()
                .map(block -> "parseMode".equals(field) ? block.getParseMode() : block.getResumeTypeHint())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_BLOCK_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_BLOCK_TEXT_LENGTH);
    }
}
