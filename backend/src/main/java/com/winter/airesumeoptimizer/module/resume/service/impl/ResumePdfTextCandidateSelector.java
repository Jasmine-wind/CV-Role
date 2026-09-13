package com.winter.airesumeoptimizer.module.resume.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chooses between the inexpensive PDF text extraction orders.
 *
 * <p>This is intentionally a conservative health check, not a layout parser. The legacy
 * candidate wins ties and small differences so that a second extraction order does not cause
 * unnecessary behavior changes.</p>
 */
public final class ResumePdfTextCandidateSelector {

    static final int POSITION_SORTED_MIN_ADVANTAGE = 8;
    static final int LAYOUT_LITE_MIN_ADVANTAGE = 8;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern ORDER_TOKEN_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z0-9+#.-]+");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\(\\+?86\\)|\\+?86|86)?[-\\s]*1[3-9]\\d[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)");
    private static final Pattern URL_PATTERN =
            Pattern.compile("(?i)(?:https?://|www\\.)\\S+|github\\.com/[A-Za-z0-9_.-]+");
    private static final List<String> HEADINGS = List.of(
            "工作经历", "工作经验", "实习经历", "项目经历", "教育经历", "教育背景", "技能", "专业技能",
            "个人总结", "自我评价", "奖项", "证书", "experience", "work experience",
            "professional experience", "internship", "projects", "education", "skills", "summary",
            "profile", "certifications", "awards");
    public Selection select(String legacyText, String positionSortedText) {
        return select(legacyText, positionSortedText, null);
    }

    /**
     * Selects the least surprising extraction candidate. Layout-lite is a candidate, not a
     * preference: it must beat the established stable candidate by a deterministic margin.
     */
    public Selection select(String legacyText, String positionSortedText, String layoutLiteText) {
        String legacy = normalizeCandidate(legacyText);
        String positionSorted = normalizeCandidate(positionSortedText);
        String layoutLite = normalizeCandidate(layoutLiteText);
        int legacyScore = score(legacy);
        int positionScore = score(positionSorted);
        int layoutLiteScore = score(layoutLite);
        boolean legacyAvailable = layoutTextAvailable(legacyText);
        boolean positionAvailable = layoutTextAvailable(positionSortedText);
        CandidateType selected;
        if (!legacyAvailable && !positionAvailable) {
            selected = layoutTextAvailable(layoutLiteText) ? CandidateType.LAYOUT_LITE : CandidateType.NONE;
        } else if (!legacyAvailable) {
            selected = CandidateType.POSITION_SORTED;
        } else if (!positionAvailable) {
            selected = CandidateType.LEGACY;
        } else {
            selected = positionScore >= legacyScore + POSITION_SORTED_MIN_ADVANTAGE
                    ? CandidateType.POSITION_SORTED
                    : CandidateType.LEGACY;
        }
        int stableScore = switch (selected) {
            case POSITION_SORTED -> positionScore;
            case LAYOUT_LITE -> layoutLiteScore;
            case LEGACY -> legacyScore;
            case NONE -> -100;
        };
        if (selected != CandidateType.LAYOUT_LITE
                && layoutTextAvailable(layoutLiteText)
                && layoutOrderCompatible(layoutLite, legacy, positionSorted)
                && layoutLiteScore >= stableScore + LAYOUT_LITE_MIN_ADVANTAGE) {
            selected = CandidateType.LAYOUT_LITE;
        }
        String selectedText = switch (selected) {
            case POSITION_SORTED -> positionSorted;
            case LAYOUT_LITE -> layoutLite;
            case LEGACY -> legacy;
            case NONE -> "";
        };
        return new Selection(selectedText, selected, legacyScore, positionScore, layoutLiteScore);
    }

    private boolean layoutTextAvailable(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * Equal token counts are not enough for a visual candidate: two columns can contain exactly
     * the same facts while changing their reading order. Only an order-preserving layout can be
     * promoted when an established PDFBox candidate exists.
     */
    boolean layoutOrderCompatible(String layout, String legacy, String positionSorted) {
        List<String> layoutTokens = tokens(layout);
        if (layoutTokens.isEmpty()) {
            return false;
        }
        boolean stableAvailable = layoutTextAvailable(legacy) || layoutTextAvailable(positionSorted);
        return !stableAvailable
                || layoutTokens.equals(tokens(legacy))
                || layoutTokens.equals(tokens(positionSorted))
                // A PDFBox candidate that has collapsed a multi-line document into one or two
                // huge lines has no trustworthy reading order to compare against. In that case
                // a visual candidate may recover order, but only when every available stable
                // candidate has the same clear fragmentation failure.
                || stableCandidatesClearlyFragmented(legacy, positionSorted);
    }

    private boolean stableCandidatesClearlyFragmented(String legacy, String positionSorted) {
        List<String> candidates = java.util.stream.Stream.of(legacy, positionSorted)
                .filter(this::layoutTextAvailable)
                .toList();
        return !candidates.isEmpty() && candidates.stream().allMatch(this::clearlyFragmented);
    }

    private boolean clearlyFragmented(String text) {
        List<String> lines = nonBlankLines(text);
        if (lines.isEmpty()) {
            return false;
        }
        int veryShortLines = (int) lines.stream().filter(line -> line.length() <= 2).count();
        return lines.size() <= 2 && text.length() >= 500
                || lines.size() >= 6 && veryShortLines * 2 >= lines.size();
    }

    private List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        java.util.regex.Matcher matcher = ORDER_TOKEN_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            result.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return result;
    }

    public int score(String text) {
        String candidate = normalizeCandidate(text);
        if (candidate.isEmpty()) {
            return -100;
        }

        List<String> lines = nonBlankLines(candidate);
        int score = 5;
        score += Math.min(lines.size(), 20);
        if (lines.size() >= 2) {
            score += 3;
        }
        if (candidate.length() >= 500 && lines.size() <= 2) {
            score -= 8;
        }
        if (EMAIL_PATTERN.matcher(candidate).find()) {
            score += 8;
        }
        if (PHONE_PATTERN.matcher(candidate).find()) {
            score += 6;
        }
        if (URL_PATTERN.matcher(candidate).find()) {
            score += 3;
        }

        int veryShortLines = 0;
        int longLines = 0;
        int veryLongLines = 0;
        int duplicateAdjacentLines = 0;
        String previous = null;
        for (String line : lines) {
            if (line.length() <= 2) {
                veryShortLines++;
            }
            if (line.length() > 180) {
                longLines++;
            }
            if (line.length() > 500) {
                veryLongLines++;
            }
            if (previous != null && normalizeKey(previous).equals(normalizeKey(line))) {
                duplicateAdjacentLines++;
            }
            previous = line;
        }
        if (!lines.isEmpty() && veryShortLines * 4 > lines.size()) {
            score -= 10;
        } else {
            score -= Math.min(6, veryShortLines);
        }
        score -= Math.min(10, longLines * 2);
        score -= Math.min(15, veryLongLines * 5);
        score -= Math.min(12, duplicateAdjacentLines * 4);

        double abnormalRatio = abnormalCharacterRatio(candidate);
        if (abnormalRatio > 0.20) {
            score -= 15;
        } else if (abnormalRatio > 0.10) {
            score -= 5;
        }

        score += Math.min(12, headingCount(lines) * 2);
        return score;
    }

    private String normalizeCandidate(String text) {
        return text == null ? "" : text.strip();
    }

    private List<String> nonBlankLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.lines().toList()) {
            String stripped = line.strip();
            if (!stripped.isEmpty()) {
                lines.add(stripped);
            }
        }
        return lines;
    }

    private int headingCount(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            if (headingKey(line) != null) {
                count++;
            }
        }
        return count;
    }

    private String headingKey(String line) {
        String normalized = line.strip().replaceAll("[：:：|丨]$", "").toLowerCase(Locale.ROOT);
        for (String heading : HEADINGS) {
            if (heading.equals(normalized)) {
                return heading;
            }
        }
        return null;
    }

    private String normalizeKey(String line) {
        return line.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private double abnormalCharacterRatio(String text) {
        long abnormal = text.codePoints()
                .filter(codePoint -> !Character.isLetterOrDigit(codePoint)
                        && !Character.isWhitespace(codePoint)
                        && !isCommonPunctuation(codePoint))
                .count();
        long total = text.codePointCount(0, text.length());
        return total == 0 ? 0 : (double) abnormal / total;
    }

    private boolean isCommonPunctuation(int codePoint) {
        return "，。；：、（）()[]【】{}<>《》+-/*.=_%&@#|!？?~·,.;:'\"".indexOf(codePoint) >= 0;
    }

    public enum CandidateType {
        LEGACY,
        POSITION_SORTED,
        LAYOUT_LITE,
        NONE
    }

    public record Selection(
            String text,
            CandidateType candidateType,
            int legacyScore,
            int positionScore,
            int layoutLiteScore) {

        public Selection {
            text = text == null ? "" : text;
            candidateType = candidateType == null ? CandidateType.LEGACY : candidateType;
        }

        public Selection(String text, CandidateType candidateType, int legacyScore, int positionScore) {
            this(text, candidateType, legacyScore, positionScore, -1);
        }
    }
}
