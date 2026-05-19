package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeBlockReorderService;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumeBlockReorderServiceImpl implements ResumeBlockReorderService {

    private static final List<String> BUSINESS_SECTION_ORDER = List.of(
            "BASIC_INFO",
            "EDUCATION",
            "SKILLS",
            "WORK_EXPERIENCES",
            "PROJECTS",
            "INTERNSHIPS",
            "CAMPUS_EXPERIENCES",
            "AWARDS",
            "CERTIFICATES",
            "SUMMARY",
            "OTHERS");

    @Override
    public List<ResumeBlockDTO> reorder(List<ResumeBlockDTO> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }

        List<ResumeBlockDTO> ordered = blocks.stream()
                .map(this::copyWithStableOriginalIndex)
                .sorted(Comparator
                        .comparingInt((ResumeBlockDTO block) -> sectionOrder(block.getSourceSection()))
                        .thenComparingInt(this::originalOrder))
                .toList();

        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).setDisplayOrder(index);
        }
        return ordered;
    }

    private ResumeBlockDTO copyWithStableOriginalIndex(ResumeBlockDTO block) {
        if (block == null) {
            return ResumeBlockDTO.builder()
                    .originalIndex(Integer.MAX_VALUE)
                    .displayOrder(Integer.MAX_VALUE)
                    .build();
        }
        int originalIndex = block.getOriginalIndex() == null
                ? block.getIndex() == null ? Integer.MAX_VALUE : block.getIndex()
                : block.getOriginalIndex();
        return ResumeBlockDTO.builder()
                .index(block.getIndex())
                .originalIndex(originalIndex)
                .displayOrder(block.getDisplayOrder())
                .text(block.getText())
                .prevText(block.getPrevText())
                .nextText(block.getNextText())
                .sourceType(block.getSourceType())
                .sourceSection(normalizeSection(block.getSourceSection()))
                .ruleSection(normalizeSection(block.getRuleSection()))
                .ruleConfidence(block.getRuleConfidence())
                .sourceSectionConfidence(block.getSourceSectionConfidence())
                .lockedLevel(block.getLockedLevel())
                .resumeTypeHint(block.getResumeTypeHint())
                .parseMode(block.getParseMode())
                .finalSectionSource(block.getFinalSectionSource())
                .sectionLocked(Boolean.TRUE.equals(block.getSectionLocked()) && !"OTHERS".equals(normalizeSection(block.getSourceSection())))
                .build();
    }

    private int sectionOrder(String section) {
        int index = BUSINESS_SECTION_ORDER.indexOf(normalizeSection(section));
        return index < 0 ? BUSINESS_SECTION_ORDER.indexOf("OTHERS") : index;
    }

    private String normalizeSection(String section) {
        if (section == null || section.isBlank() || "GENERAL".equals(section)) {
            return "OTHERS";
        }
        return section;
    }

    private int originalOrder(ResumeBlockDTO block) {
        if (block == null || block.getOriginalIndex() == null) {
            return Integer.MAX_VALUE;
        }
        return block.getOriginalIndex();
    }
}
