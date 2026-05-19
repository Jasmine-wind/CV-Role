package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextQualityCheckService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ResumeTextQualityCheckServiceImpl implements ResumeTextQualityCheckService {

    private static final int MIN_USABLE_LENGTH = 80;
    private static final double MAX_ABNORMAL_CHAR_RATIO = 0.20;
    private static final int MAX_NEWLINE_COUNT_FOR_LONG_TEXT = 2;
    private static final int LONG_TEXT_LENGTH = 500;

    @Override
    public ResumeTextQualityResultDTO check(String extractedText, String fileType) {
        String text = extractedText == null ? "" : extractedText.strip();
        String normalizedFileType = fileType == null ? "" : fileType.strip().toUpperCase(Locale.ROOT);
        List<String> issues = new ArrayList<>();

        if (text.isBlank()) {
            issues.add("EMPTY_TEXT");
            if ("PDF".equals(normalizedFileType)) {
                issues.add("SCANNED_PDF");
                return failed(issues, "未能从文件中提取到有效文本，请确认文件不是扫描版图片 PDF");
            }
            return failed(issues, "未能从文件中提取到有效文本，请重新上传可复制文字的 PDF 或 DOCX");
        }

        if (text.length() < MIN_USABLE_LENGTH) {
            issues.add("TOO_SHORT_TEXT");
        }
        if (abnormalCharRatio(text) > MAX_ABNORMAL_CHAR_RATIO) {
            issues.add("ABNORMAL_CHAR_RATIO");
        }
        if (text.length() >= LONG_TEXT_LENGTH && countNewlines(text) <= MAX_NEWLINE_COUNT_FOR_LONG_TEXT) {
            issues.add("LOW_LINE_BREAKS");
        }

        if (issues.contains("ABNORMAL_CHAR_RATIO")) {
            return failed(issues, "文件文本内容异常，建议重新上传可复制文字的 PDF 或 DOCX");
        }
        if (!issues.isEmpty()) {
            return ResumeTextQualityResultDTO.builder()
                    .status("WARNING")
                    .issues(List.copyOf(issues))
                    .message(resolveWarningMessage(issues))
                    .build();
        }

        return ResumeTextQualityResultDTO.builder()
                .status("GOOD")
                .issues(List.of())
                .message("文本质量正常")
                .build();
    }

    private ResumeTextQualityResultDTO failed(List<String> issues, String message) {
        return ResumeTextQualityResultDTO.builder()
                .status("FAILED")
                .issues(List.copyOf(issues))
                .message(message)
                .build();
    }

    private String resolveWarningMessage(List<String> issues) {
        if (issues.contains("TOO_SHORT_TEXT")) {
            return "提取文本过短，解析结果可能不完整";
        }
        if (issues.contains("LOW_LINE_BREAKS")) {
            return "提取文本换行较少，章节识别可能不准确";
        }
        return "提取文本质量存在问题，解析结果可能不完整";
    }

    private double abnormalCharRatio(String text) {
        long abnormalCount = text.chars()
                .filter(ch -> !Character.isLetterOrDigit(ch)
                        && !Character.isWhitespace(ch)
                        && !isCommonPunctuation(ch))
                .count();
        return text.isEmpty() ? 0 : (double) abnormalCount / text.length();
    }

    private boolean isCommonPunctuation(int ch) {
        return "，。；：、（）()[]【】{}<>《》+-*/.=_%&@#|!？?~·,.;:'\"".indexOf(ch) >= 0;
    }

    private int countNewlines(String text) {
        return (int) text.chars().filter(ch -> ch == '\n').count();
    }
}
