package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Generic, conservative entry boundary detector shared by experience-like sections.
 * It uses row shape, metadata, typography and local adjacency; it does not contain project,
 * school or company names.
 */
public final class ResumeEntryBoundaryDetector {

    private static final Pattern DATE_SIGNAL = Pattern.compile(
            "(?i).*(?:19|20)\\d{2}(?:[./年-]\\s*\\d{1,2})?.*(?:至今|present|[-~—至到]).*|.*(?:19|20)\\d{2}[./年-]\\s*\\d{1,2}.*");
    private static final Pattern ROLE_SIGNAL = Pattern.compile(
            "(?i).*(?:工程师|实习生|经理|总监|专员|负责人|成员|组长|研究员|干事|干部|engineer|developer|intern|manager|lead|member|analyst|designer).*" );
    private static final Pattern NARRATIVE_SIGNAL = Pattern.compile(
            "(?i).*(?:负责|参与|参加|完成|实现|开发|维护|优化|设计|构建|支持|提升|降低|处理|熟悉|掌握|通过|用于|获得|获奖|built|developed|designed|implemented|maintained|improved|managed|using|experience|won|awarded|competition|debate).*" );
    private static final Pattern NARRATIVE_START = Pattern.compile(
            "(?i)^(?:(?:19|20)\\d{2}[./年-]\\s*\\d{1,2}(?:[./月-]\\s*\\d{1,2})?\\s*)?(?:负责|参与|参加|完成|实现|开发|维护|优化|设计|构建|支持|提升|降低|处理|熟悉|掌握|通过|用于|获得|获奖|built|developed|designed|implemented|maintained|improved|managed|using|won|awarded|competition|debate).*" );

    public List<EntryGroup> group(List<ResumeRawSectionBlockDTO> sourceBlocks) {
        List<ResumeRawSectionBlockDTO> lines = sourceBlocks == null ? List.of() : sourceBlocks.stream()
                .filter(block -> block != null && block.getText() != null && !block.getText().isBlank())
                .toList();
        if (lines.isEmpty()) {
            return List.of();
        }
        List<EntryGroup> result = new ArrayList<>();
        List<ResumeRawSectionBlockDTO> current = new ArrayList<>();
        List<ResumeRawSectionBlockDTO> headers = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            ResumeRawSectionBlockDTO line = lines.get(index);
            ResumeRawSectionBlockDTO next = index + 1 < lines.size() ? lines.get(index + 1) : null;
            if (isPairedHeader(line, next)) {
                close(result, current, headers);
                current = new ArrayList<>();
                headers = new ArrayList<>();
                current.add(line);
                current.add(next);
                headers.add(line);
                headers.add(next);
                index += 2;
                continue;
            }
            if (isEntryHeader(line, next)) {
                close(result, current, headers);
                current = new ArrayList<>();
                headers = new ArrayList<>();
                current.add(line);
                headers.add(line);
                index++;
                continue;
            }
            if (current.isEmpty()) {
                current = new ArrayList<>();
                headers = new ArrayList<>();
            }
            current.add(line);
            index++;
        }
        close(result, current, headers);
        return List.copyOf(result);
    }

    private void close(List<EntryGroup> result, List<ResumeRawSectionBlockDTO> current,
            List<ResumeRawSectionBlockDTO> headers) {
        if (current != null && !current.isEmpty()) {
            result.add(new EntryGroup(List.copyOf(current), List.copyOf(headers), headers != null && !headers.isEmpty()));
        }
    }

    private boolean isPairedHeader(ResumeRawSectionBlockDTO first, ResumeRawSectionBlockDTO second) {
        if (first == null || second == null || isBullet(first) || isBullet(second)
                || !headerLikeTitle(first.getText()) || !metadataRow(second.getText())) {
            return false;
        }
        return closeEnough(first, second);
    }

    private boolean headerLikeTitle(String text) {
        return shortNonNarrative(text)
                && (!NARRATIVE_SIGNAL.matcher(text.strip()).matches()
                || ROLE_SIGNAL.matcher(text.strip()).matches() && !NARRATIVE_START.matcher(text.strip()).matches());
    }

    private boolean isEntryHeader(ResumeRawSectionBlockDTO line, ResumeRawSectionBlockDTO next) {
        if (line == null || isBullet(line) || !shortNonNarrative(line.getText())) {
            return false;
        }
        ResumeSourceBlockRole role = line.getRole();
        if (role == ResumeSourceBlockRole.ENTRY_HEADER) {
            return true;
        }
        if (metadataRow(line.getText())) {
            return true;
        }
        // Typography is only a supporting signal: a bold row must still look like metadata or
        // be followed by a plausible body row. Font size alone is never a heading boundary.
        return Boolean.TRUE.equals(line.getBoldHint())
                && (metadataRow(line.getText()) || next != null && !shortNonNarrative(next.getText()));
    }

    private boolean metadataRow(String text) {
        if (text == null || text.isBlank() || text.length() > 100) {
            return false;
        }
        return DATE_SIGNAL.matcher(text.strip()).matches()
                || ROLE_SIGNAL.matcher(text.strip()).matches()
                || text.matches("^[^:：]{1,24}[:：]\\s*.+$")
                || text.matches("^.+\\s+[|｜·•]\\s+.+$");
    }

    private boolean shortNonNarrative(String text) {
        if (text == null || text.isBlank() || text.strip().length() > 100) {
            return false;
        }
        String value = text.strip();
        return !isBulletText(value)
                && (!NARRATIVE_SIGNAL.matcher(value).matches() || !NARRATIVE_START.matcher(value).matches())
                && !value.matches(".*[。！？!?；;]$");
    }

    private boolean closeEnough(ResumeRawSectionBlockDTO first, ResumeRawSectionBlockDTO second) {
        if (first.getPage() != null && second.getPage() != null && !first.getPage().equals(second.getPage())) {
            return false;
        }
        if (first.getY() == null || second.getY() == null) {
            return true;
        }
        double font = first.getFontSize() == null ? 10.0d : first.getFontSize();
        return second.getY() >= first.getY() && second.getY() - first.getY() <= Math.max(20.0d, font * 2.6d);
    }

    private boolean isBullet(ResumeRawSectionBlockDTO block) {
        return block != null && (Boolean.TRUE.equals(block.getBulletHint()) || isBulletText(block.getText()));
    }

    private boolean isBulletText(String value) {
        return value != null && value.matches("^[\\s>*•·●▪■◆◇○◦▶►✓✔-]+.*$");
    }

    public record EntryGroup(
            List<ResumeRawSectionBlockDTO> lines,
            List<ResumeRawSectionBlockDTO> headers,
            boolean hasHeader) {

        public String headerText() {
            return headers == null ? "" : headers.stream()
                    .map(ResumeRawSectionBlockDTO::getText)
                    .filter(value -> value != null && !value.isBlank())
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
        }

        public List<String> texts() {
            return lines == null ? List.of() : lines.stream()
                    .map(ResumeRawSectionBlockDTO::getText)
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
        }
    }
}
