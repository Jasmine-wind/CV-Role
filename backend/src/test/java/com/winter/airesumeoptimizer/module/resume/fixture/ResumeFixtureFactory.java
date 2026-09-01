package com.winter.airesumeoptimizer.module.resume.fixture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 合成但真实版式的中文简历文件工厂（Slice A Gate fixture）。
 * PDF 使用 Typst + 系统 CJK 字体生成（与生产渲染链一致，自动子集化、体积小）；
 * DOCX 按最小 OOXML 结构生成，与上传校验的魔数/结构要求一致。
 */
public final class ResumeFixtureFactory {

    private ResumeFixtureFactory() {
    }

    /** 用 Typst 编译 A4 中文简历；行序即阅读顺序，内容足够时自然分页。 */
    public static byte[] renderPdf(List<String> lines) throws IOException {
        Path workDir = Files.createTempDirectory("resume-fixture");
        try {
            Path source = workDir.resolve("resume.typ");
            Path output = workDir.resolve("resume.pdf");
            Files.writeString(source, toTypstSource(lines), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder("typst", "compile", source.toString(), output.toString())
                    .redirectErrorStream(true)
                    .start();
            try {
                boolean finished = process.waitFor(60, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IOException("typst 编译超时，无法生成中文简历 fixture");
                }
                if (process.exitValue() != 0 || !Files.exists(output)) {
                    throw new IOException("typst 编译失败：" + new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("typst 编译被中断", exception);
            }
            return Files.readAllBytes(output);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private static String toTypstSource(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        builder.append("#set page(paper: \"a4\", margin: (x: 2cm, y: 1.6cm))\n");
        builder.append("#set text(font: (\"Noto Sans\", \"FandolHei\", \"Noto Sans CJK SC\", \"DejaVu Sans\"), size: 10.5pt, lang: \"zh\")\n");
        builder.append("#set par(leading: 0.7em, justify: false)\n");
        for (String line : lines) {
            // 每行一个独立段落，保证抽取顺序与真实简历一致；
            // 转义 Typst 标记敏感字符（#@<>*_$[] 等），防止邮箱/符号被当作代码。
            builder.append(escapeTypst(line)).append("\n\n");
        }
        return builder.toString();
    }

    private static String escapeTypst(String line) {
        StringBuilder builder = new StringBuilder(line.length() + 8);
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            switch (ch) {
                case '#', '@', '<', '>', '*', '_', '$', '[', ']', '~' -> builder.append('\\').append(ch);
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响夹具生成结果。
                }
            });
        } catch (IOException ignored) {
            // 同上。
        }
    }

    /** 按段落生成最小 OOXML DOCX；每个非空行一个段落。 */
    public static byte[] renderDocx(List<String> lines) throws IOException {
        StringBuilder paragraphs = new StringBuilder();
        for (String line : lines) {
            paragraphs.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                    .append(escapeXml(line))
                    .append("</w:t></w:r></w:p>");
        }
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>%s</w:body>
                </w:document>
                """.formatted(paragraphs);
        String contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """;
        String rels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEntry(zip, "[Content_Types].xml", contentTypes);
            writeEntry(zip, "_rels/.rels", rels);
            writeEntry(zip, "word/document.xml", documentXml);
        }
        return output.toByteArray();
    }

    /** 向 zip 写入一个文本条目。 */
    private static void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        try (OutputStream ignored = OutputStream.nullOutputStream()) {
            zip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        zip.closeEntry();
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
