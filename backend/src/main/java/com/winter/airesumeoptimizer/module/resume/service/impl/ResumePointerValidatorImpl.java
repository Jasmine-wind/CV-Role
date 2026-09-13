package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumePointerValidator;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
        ResumeIndexedLineDTO start = lineById(startLine, indexedLines);
        ResumeIndexedLineDTO end = lineById(endLine, indexedLines);
        List<ResumeIndexedLineDTO> range = linesInRange(startLine, endLine, indexedLines);
        if (start == null || end == null || range.size() != endLine - startLine + 1) {
            return false;
        }
        for (int index = 0; index < range.size(); index++) {
            ResumeIndexedLineDTO line = range.get(index);
            if (!Objects.equals(line.getRawSectionId(), start.getRawSectionId())
                    || !Objects.equals(line.getLineId(), startLine + index)) {
                return false;
            }
        }
        return true;
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
        ResumeIndexedLineDTO startLineValue = lineById(startLine, indexedLines);
        ResumeIndexedLineDTO endLineValue = lineById(endLine, indexedLines);
        if (startLineValue == null || endLineValue == null) {
            return null;
        }
        List<ResumeIndexedLineDTO> lines = linesInRange(startLine, endLine, indexedLines).stream()
                .filter(line -> !Boolean.TRUE.equals(line.getIsNoise()))
                .toList();
        if (lines.isEmpty()) {
            return null;
        }
        String text = lines.stream()
                .map(ResumeIndexedLineDTO::getText)
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        if (text.isBlank()) {
            return null;
        }
        ResumeIndexedLineDTO first = lines.get(0);
        boolean samePage = lines.stream()
                .allMatch(line -> Objects.equals(line.getPage(), first.getPage()));
        return ResumeSourceRefDTO.builder()
                .startLine(startLine)
                .endLine(endLine)
                .text(text)
                .sourceBlockIds(lines.stream()
                        .flatMap(line -> sourceBlockIds(line).stream())
                        .distinct()
                        .toList())
                .sourceOccurrenceIds(lines.stream()
                        .flatMap(line -> sourceOccurrenceIds(line).stream())
                        .distinct()
                        .toList())
                .page(samePage ? first.getPage() : null)
                .x(samePage ? minCoordinate(lines.stream().map(ResumeIndexedLineDTO::getX).toList()) : null)
                .y(samePage ? minCoordinate(lines.stream().map(ResumeIndexedLineDTO::getY).toList()) : null)
                .width(samePage ? boundingWidth(lines) : null)
                .height(samePage ? boundingHeight(lines) : null)
                .fontSize(first.getFontSize())
                .fontName(first.getFontName())
                .boldHint(lines.stream().anyMatch(line -> Boolean.TRUE.equals(line.getBoldHint())))
                .indent(first.getIndent())
                .bulletHint(lines.stream().anyMatch(line -> Boolean.TRUE.equals(line.getBulletHint())))
                .role(first.getRole())
                .sourceType(first.getSourceType())
                .build();
    }

    private List<String> sourceBlockIds(ResumeIndexedLineDTO line) {
        if (line == null) {
            return List.of();
        }
        if (line.getSourceBlockIds() != null && !line.getSourceBlockIds().isEmpty()) {
            return line.getSourceBlockIds().stream()
                    .filter(this::hasUsableId)
                    .map(String::strip)
                    .distinct()
                    .toList();
        }
        return hasUsableId(line.getSourceBlockId())
                ? List.of(line.getSourceBlockId().strip()) : List.of();
    }

    private List<String> sourceOccurrenceIds(ResumeIndexedLineDTO line) {
        if (line == null) {
            return List.of();
        }
        if (line.getSourceOccurrenceIds() != null && !line.getSourceOccurrenceIds().isEmpty()) {
            List<String> ids = line.getSourceOccurrenceIds().stream()
                    .filter(this::hasUsableOccurrenceId)
                    .map(String::strip)
                    .distinct()
                    .toList();
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        // A missing line-level occurrence cannot be recovered from a visual block ID. Keep the
        // reference occurrence-free rather than upgrading sourceBlockId into an occurrence.
        return line.getLineId() == null ? List.of() : List.of("indexed-line-" + line.getLineId());
    }

    private boolean hasUsableOccurrenceId(String value) {
        return hasUsableId(value);
    }

    private boolean hasUsableId(String value) {
        return value != null && !value.isBlank()
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private Double minCoordinate(List<Double> values) {
        return values.stream().filter(value -> value != null).min(Double::compareTo).orElse(null);
    }

    private Double boundingWidth(List<ResumeIndexedLineDTO> lines) {
        Double left = minCoordinate(lines.stream().map(ResumeIndexedLineDTO::getX).toList());
        var right = lines.stream()
                .filter(line -> line.getX() != null && line.getWidth() != null)
                .mapToDouble(line -> line.getX() + line.getWidth())
                .max();
        return left == null || right.isEmpty() ? null : right.getAsDouble() - left;
    }

    private Double boundingHeight(List<ResumeIndexedLineDTO> lines) {
        Double top = minCoordinate(lines.stream().map(ResumeIndexedLineDTO::getY).toList());
        var bottom = lines.stream()
                .filter(line -> line.getY() != null && line.getHeight() != null)
                .mapToDouble(line -> line.getY() + line.getHeight())
                .max();
        return top == null || bottom.isEmpty() ? null : bottom.getAsDouble() - top;
    }

    private List<ResumeIndexedLineDTO> linesInRange(
            Integer startLine, Integer endLine, List<ResumeIndexedLineDTO> indexedLines) {
        if (startLine == null || endLine == null || indexedLines == null) {
            return List.of();
        }
        return indexedLines.stream()
                .filter(Objects::nonNull)
                .filter(line -> line.getLineId() != null
                        && line.getLineId() >= startLine && line.getLineId() <= endLine)
                .sorted(Comparator.comparing(ResumeIndexedLineDTO::getLineId))
                .toList();
    }

    private ResumeIndexedLineDTO lineById(Integer lineId, List<ResumeIndexedLineDTO> indexedLines) {
        if (lineId == null || indexedLines == null) {
            return null;
        }
        return indexedLines.stream()
                .filter(Objects::nonNull)
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
