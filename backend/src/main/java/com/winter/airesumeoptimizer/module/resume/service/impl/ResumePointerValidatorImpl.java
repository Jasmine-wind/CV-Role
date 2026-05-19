package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumePointerValidator;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumePointerValidatorImpl implements ResumePointerValidator {

    @Override
    public boolean validLineId(Integer lineId, List<ResumeIndexedLineDTO> indexedLines) {
        return lineId != null && indexedLines != null && indexedLines.stream()
                .anyMatch(line -> lineId.equals(line.getLineId()));
    }

    @Override
    public boolean validLineRange(Integer startLine, Integer endLine, List<ResumeIndexedLineDTO> indexedLines) {
        if (startLine == null || endLine == null || startLine > endLine) {
            return false;
        }
        return validLineId(startLine, indexedLines) && validLineId(endLine, indexedLines);
    }

    @Override
    public boolean validEntityLine(Integer lineId, List<ResumeIndexedLineDTO> indexedLines) {
        ResumeIndexedLineDTO line = lineById(lineId, indexedLines);
        return line != null
                && !Boolean.TRUE.equals(line.getIsNoise())
                && !isFieldLabel(line.getNormalizedText());
    }

    @Override
    public ResumeSourceRefDTO sourceRef(Integer startLine, Integer endLine, List<ResumeIndexedLineDTO> indexedLines) {
        if (!validLineRange(startLine, endLine, indexedLines)) {
            return null;
        }
        List<ResumeIndexedLineDTO> lines = indexedLines.stream()
                .filter(line -> line.getLineId() != null && line.getLineId() >= startLine && line.getLineId() <= endLine)
                .sorted(Comparator.comparing(ResumeIndexedLineDTO::getLineId))
                .filter(line -> !Boolean.TRUE.equals(line.getIsNoise()))
                .toList();
        String text = lines.stream()
                .map(ResumeIndexedLineDTO::getText)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (text.isBlank()) {
            return null;
        }
        return ResumeSourceRefDTO.builder()
                .startLine(startLine)
                .endLine(endLine)
                .text(text)
                .build();
    }

    private ResumeIndexedLineDTO lineById(Integer lineId, List<ResumeIndexedLineDTO> indexedLines) {
        if (lineId == null || indexedLines == null) {
            return null;
        }
        return indexedLines.stream()
                .filter(line -> lineId.equals(line.getLineId()))
                .findFirst()
                .orElse(null);
    }

    private boolean isFieldLabel(String value) {
        if (value == null) {
            return true;
        }
        return value.strip().matches("^(公司名称|职位名称|工作时间|工作描述|项目名称|项目描述|开发环境|技术选型|毕业院校|学历|专业|姓名|电话|邮箱|未识别)[:：]?$");
    }
}
