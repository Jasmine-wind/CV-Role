package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Small PDF-only layout hint extractor.
 *
 * <p>This is intentionally not a document layout engine. It collects visible PDF glyphs once,
 * groups adjacent glyphs into spans, then groups nearby spans into visual lines. No resume
 * vocabulary or section meaning is used here. The resulting blocks are source-backed hints and
 * are safe to discard when a plain-text candidate is healthier.</p>
 */
public class ResumeLayoutLiteExtractor {

    private static final double LINE_Y_TOLERANCE = 2.5d;
    private static final double FONT_SIZE_TOLERANCE = 0.75d;
    private static final double NORMAL_SPACE_RATIO = 0.30d;
    private static final double MAX_SPAN_GAP_RATIO = 2.25d;
    /** A material same-baseline gap is treated as a possible column boundary, never as inline text. */
    private static final double COLUMN_GAP_MIN = 12.0d;
    private static final double COLUMN_GAP_RATIO = 1.25d;
    private static final Pattern BULLET_PREFIX = Pattern.compile("^[\\s>*•·●▪■◆◇○◦▶►✓✔-]+.*$");

    public LayoutLiteResult extract(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return LayoutLiteResult.empty();
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return extract(document);
        }
    }

    public LayoutLiteResult extract(PDDocument document) throws IOException {
        if (document == null) {
            return LayoutLiteResult.empty();
        }
        int pageCount = document.getNumberOfPages();
        if (pageCount <= 0) {
            return LayoutLiteResult.empty();
        }
        GlyphCollector collector = new GlyphCollector();
        collector.getText(document);
        List<Glyph> glyphs = collector.glyphs();
        if (glyphs.isEmpty()) {
            // PDFTextStripper deliberately skips pages without a contents stream. Preserve the
            // physical page count even when a document is blank or has only blank pages.
            return new LayoutLiteResult("", List.of(), pageCount, false);
        }
        VisualLineResult visualLineResult = visualLines(glyphs);
        List<VisualLine> lines = visualLineResult.lines();
        List<ResumeBlockDTO> blocks = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int order = 0;
        for (VisualLine line : lines) {
            String lineText = line.text();
            if (lineText.isBlank()) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(lineText);
            String id = "layout-p%03d-l%04d".formatted(line.page(), order);
            blocks.add(ResumeBlockDTO.builder()
                    .id(id)
                    .index(order)
                    .originalIndex(order)
                    .displayOrder(order)
                    .text(lineText)
                    .page(line.page())
                    .x(line.x())
                    .y(line.y())
                    .width(line.width())
                    .height(line.height())
                    .fontSize(line.fontSize())
                    .fontName(line.fontName())
                    .boldHint(line.boldHint())
                    .indent((int) Math.round(line.x()))
                    .bulletHint(BULLET_PREFIX.matcher(lineText).matches())
                    .role(ResumeSourceBlockRole.UNKNOWN)
                    .sourceBlockIds(List.of(id))
                    .sourceOccurrenceIds(List.of(id + "-occurrence"))
                    .sourceType("pdf-layout-lite")
                    .build());
            order++;
        }
        return new LayoutLiteResult(
                text.toString(), List.copyOf(blocks), pageCount, visualLineResult.parallelColumnSignal());
    }

    private VisualLineResult visualLines(List<Glyph> source) {
        List<Glyph> glyphs = source.stream()
                .filter(glyph -> glyph.text() != null && !glyph.text().isBlank())
                .sorted(Comparator.comparingInt(Glyph::page)
                        .thenComparingDouble(Glyph::y)
                        .thenComparingDouble(Glyph::x))
                .toList();
        List<LineBuilder> builders = new ArrayList<>();
        for (Glyph glyph : glyphs) {
            LineBuilder current = builders.isEmpty() ? null : builders.get(builders.size() - 1);
            if (current == null || current.page != glyph.page()
                    || Math.abs(current.baseline - glyph.y()) > LINE_Y_TOLERANCE) {
                current = new LineBuilder(glyph);
                builders.add(current);
            } else {
                current.add(glyph);
            }
        }
        List<VisualLine> lines = new ArrayList<>();
        boolean parallelColumnSignal = false;
        for (LineBuilder builder : builders) {
            SegmentResult result = builder.buildSegments();
            lines.addAll(result.lines());
            parallelColumnSignal |= result.largeGapSplit();
        }
        return new VisualLineResult(List.copyOf(lines), parallelColumnSignal);
    }

    private static boolean isVisibleTextPosition(PDGraphicsState graphicsState, TextPosition position) {
        if (position == null) {
            return false;
        }
        String unicode = position.getUnicode();
        if (unicode != null && !unicode.isEmpty()
                && unicode.codePoints().allMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT)) {
            return false;
        }
        if (graphicsState == null || graphicsState.getTextState() == null) {
            return true;
        }
        RenderingMode mode = graphicsState.getTextState().getRenderingMode();
        if (mode == null) {
            return true;
        }
        boolean fillVisible = mode.isFill() && graphicsState.getNonStrokeAlphaConstant() > 0.0d;
        boolean strokeVisible = mode.isStroke() && graphicsState.getAlphaConstant() > 0.0d;
        return fillVisible || strokeVisible;
    }

    private static String fontName(TextPosition position) {
        if (position == null || position.getFont() == null || position.getFont().getName() == null) {
            return "";
        }
        return position.getFont().getName();
    }

    private static boolean hasFiniteGeometry(TextPosition position) {
        return position != null
                && Double.isFinite(position.getXDirAdj())
                && Double.isFinite(position.getYDirAdj())
                && Double.isFinite(position.getWidthDirAdj())
                && Double.isFinite(position.getHeightDir())
                && position.getWidthDirAdj() >= 0.0f
                && position.getHeightDir() >= 0.0f;
    }

    /** PDFBox can report a negative point size for text whose direction is 180 degrees. */
    private static double positiveFontSize(TextPosition position) {
        double size = Math.abs(position.getFontSizeInPt());
        if (!Double.isFinite(size) || size <= 0.0d) {
            size = Math.abs(position.getFontSize());
        }
        if (!Double.isFinite(size) || size <= 0.0d) {
            size = Math.abs(position.getHeightDir());
        }
        return Double.isFinite(size) && size > 0.0d ? size : 1.0d;
    }

    private record Glyph(
            int page,
            String text,
            double x,
            double y,
            double width,
            double height,
            double fontSize,
            String fontName,
            boolean boldHint) {
    }

    private record Span(
            String text,
            double x,
            double y,
            double width,
            double height,
            double fontSize,
            String fontName,
            boolean boldHint) {
    }

    private record VisualLine(
            int page,
            String text,
            double x,
            double y,
            double width,
            double height,
            double fontSize,
            String fontName,
            boolean boldHint) {
    }

    private record VisualLineResult(List<VisualLine> lines, boolean parallelColumnSignal) {
    }

    private record SegmentResult(List<VisualLine> lines, boolean largeGapSplit) {
    }

    public record LayoutLiteResult(
            String text, List<ResumeBlockDTO> blocks, int pageCount, boolean hasParallelColumns) {

        public LayoutLiteResult {
            text = text == null ? "" : text;
            blocks = blocks == null ? List.of() : blocks.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }

        /** Compatibility constructor for callers that only need the three original values. */
        public LayoutLiteResult(String text, List<ResumeBlockDTO> blocks, int pageCount) {
            this(text, blocks, pageCount, false);
        }

        public static LayoutLiteResult empty() {
            return new LayoutLiteResult("", List.of(), 0, false);
        }
    }

    private static final class LineBuilder {
        private final int page;
        private final double baseline;
        private final List<Glyph> glyphs = new ArrayList<>();

        private LineBuilder(Glyph first) {
            this.page = first.page();
            this.baseline = first.y();
            this.glyphs.add(first);
        }

        private void add(Glyph glyph) {
            glyphs.add(glyph);
        }

        private SegmentResult buildSegments() {
            List<Glyph> ordered = glyphs.stream()
                    .sorted(Comparator.comparingDouble(Glyph::x))
                    .toList();
            List<Span> spans = new ArrayList<>();
            for (Glyph glyph : ordered) {
                Span previous = spans.isEmpty() ? null : spans.get(spans.size() - 1);
                double gap = previous == null ? 0.0d : glyph.x() - (previous.x() + previous.width());
                boolean sameSpan = previous != null
                        && !isColumnGap(gap, previous.fontSize())
                        && Math.abs(previous.fontSize() - glyph.fontSize()) <= FONT_SIZE_TOLERANCE
                        && sameFont(previous.fontName(), glyph.fontName())
                        && gap >= -1.0d
                        && gap <= Math.max(4.0d, previous.fontSize() * MAX_SPAN_GAP_RATIO);
                if (!sameSpan) {
                    spans.add(new Span(glyph.text(), glyph.x(), glyph.y(), glyph.width(), glyph.height(),
                            glyph.fontSize(), glyph.fontName(), glyph.boldHint()));
                    continue;
                }
                String separator = gap > previous.fontSize() * NORMAL_SPACE_RATIO ? " " : "";
                String merged = previous.text() + separator + glyph.text();
                spans.set(spans.size() - 1, new Span(
                        merged,
                        previous.x(),
                        Math.min(previous.y(), glyph.y()),
                        Math.max(previous.x() + previous.width(), glyph.x() + glyph.width()) - previous.x(),
                        Math.max(previous.height(), glyph.height()),
                        Math.max(previous.fontSize(), glyph.fontSize()),
                        previous.fontName(),
                        previous.boldHint() || glyph.boldHint()));
            }
            List<List<Span>> segments = new ArrayList<>();
            List<Span> currentSegment = new ArrayList<>();
            boolean largeGapSplit = false;
            for (Span span : spans) {
                if (!currentSegment.isEmpty()) {
                    Span previous = currentSegment.get(currentSegment.size() - 1);
                    double gap = span.x() - (previous.x() + previous.width());
                    if (isColumnGap(gap, previous.fontSize())) {
                        segments.add(currentSegment);
                        currentSegment = new ArrayList<>();
                        largeGapSplit = true;
                    }
                }
                currentSegment.add(span);
            }
            if (!currentSegment.isEmpty()) {
                segments.add(currentSegment);
            }
            List<VisualLine> lines = segments.stream().map(this::build).toList();
            return new SegmentResult(lines, largeGapSplit && segments.size() > 1);
        }

        private boolean isColumnGap(double gap, double fontSize) {
            return gap >= Math.max(COLUMN_GAP_MIN, Math.max(0.0d, fontSize) * COLUMN_GAP_RATIO);
        }

        private VisualLine build(List<Span> spans) {
            StringBuilder line = new StringBuilder();
            for (int index = 0; index < spans.size(); index++) {
                Span span = spans.get(index);
                if (index > 0) {
                    Span previous = spans.get(index - 1);
                    double gap = span.x() - (previous.x() + previous.width());
                    line.append(gap > Math.max(4.0d, previous.fontSize() * NORMAL_SPACE_RATIO) ? " " : "");
                }
                line.append(span.text());
            }
            double x = spans.stream().mapToDouble(Span::x).min().orElse(0.0d);
            double right = spans.stream().mapToDouble(span -> span.x() + span.width()).max().orElse(x);
            double top = spans.stream().mapToDouble(span -> span.y() - span.height()).min().orElse(baseline);
            double bottom = spans.stream().mapToDouble(Span::y).max().orElse(baseline);
            Span first = spans.get(0);
            return new VisualLine(page, line.toString().strip(), x, top, right - x, bottom - top,
                    first.fontSize(), first.fontName(), spans.stream().anyMatch(Span::boldHint));
        }

        private boolean sameFont(String left, String right) {
            return normalizeFont(left).equals(normalizeFont(right));
        }

        private String normalizeFont(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[-_, ]", "");
        }
    }

    private static final class GlyphCollector extends PDFTextStripper {
        private final List<Glyph> glyphs = new ArrayList<>();

        private GlyphCollector() throws IOException {
            super();
            setSortByPosition(false);
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            // PDFTextStripper normally supplies non-null pages, but keeping this hook defensive
            // prevents a malformed page callback from turning an optional hint into a hard failure.
            if (page != null) {
                super.startPage(page);
            }
        }

        @Override
        protected void processTextPosition(TextPosition textPosition) {
            if (isVisibleTextPosition(getGraphicsState(), textPosition)
                    && hasFiniteGeometry(textPosition)) {
                String unicode = textPosition.getUnicode();
                if (unicode != null && !unicode.isBlank()) {
                    String font = fontName(textPosition);
                    boolean bold = font.toLowerCase(Locale.ROOT).matches(".*(?:bold|black|semibold|demi|heavy).*");
                    glyphs.add(new Glyph(
                            Math.max(1, getCurrentPageNo()),
                            unicode,
                            textPosition.getXDirAdj(),
                            textPosition.getYDirAdj(),
                            textPosition.getWidthDirAdj(),
                            textPosition.getHeightDir(),
                            positiveFontSize(textPosition),
                            font,
                            bold));
                }
                super.processTextPosition(textPosition);
            }
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            // The collector derives output from glyphs. Avoid a second text-order decision here.
        }

        private List<Glyph> glyphs() {
            return List.copyOf(glyphs);
        }
    }
}
