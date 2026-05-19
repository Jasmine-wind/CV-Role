package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeLineIndexer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumeLineIndexerImpl implements ResumeLineIndexer {

    @Override
    public List<ResumeIndexedLineDTO> index(List<ResumeRawSectionDTO> rawSections) {
        List<ResumeIndexedLineDTO> indexedLines = new ArrayList<>();
        int lineId = 1;
        List<ResumeRawSectionDTO> sections = new ArrayList<>(rawSections == null ? List.of() : rawSections);
        sections.sort(Comparator.comparing(section -> section.getDisplayOrder() == null ? Integer.MAX_VALUE : section.getDisplayOrder()));
        for (ResumeRawSectionDTO section : sections) {
            List<ResumeRawSectionBlockDTO> blocks = new ArrayList<>(section.getBlocks() == null ? List.of() : section.getBlocks());
            blocks.sort(Comparator
                    .comparing((ResumeRawSectionBlockDTO block) -> block.getDisplayOrder() == null ? Integer.MAX_VALUE : block.getDisplayOrder())
                    .thenComparing(block -> block.getOriginalIndex() == null ? Integer.MAX_VALUE : block.getOriginalIndex())
                    .thenComparing(block -> block.getIndex() == null ? Integer.MAX_VALUE : block.getIndex()));
            for (ResumeRawSectionBlockDTO block : blocks) {
                String text = block.getText() == null ? "" : block.getText();
                String normalized = normalize(text);
                indexedLines.add(ResumeIndexedLineDTO.builder()
                        .lineId(lineId++)
                        .page(1)
                        .text(text)
                        .normalizedText(normalized)
                        .sourceType(resolveSourceType(block))
                        .rawSectionId(section.getId())
                        .sectionHint(section.getNormalizedSection())
                        .sectionConfidence(section.getConfidence())
                        .isNoise(isNoise(normalized))
                        .build());
            }
        }
        return indexedLines;
    }

    private String resolveSourceType(ResumeRawSectionBlockDTO block) {
        if (block != null && block.getIconType() != null && !block.getIconType().isBlank()) {
            return "icon-line";
        }
        return "line";
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private boolean isNoise(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (value.matches("^[\\d一二三四五六七八九十]+[.、．)]?$")) {
            return true;
        }
        return value.matches("^[\\s\\-_=+*#·•。.,，、;；:：|/\\\\\\[\\]()（）]+$");
    }
}
