package com.winter.airesumeoptimizer.infra.render;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Typst CLI 同步渲染实现。
 *
 * <p>隔离措施：每次渲染使用独立临时目录并以 --root 限制文件访问；内置模板的包解析目录
 * 指向空目录，禁止外部包依赖；固定 creation timestamp 与 PDF metadata 保证同一输入逐字节确定。
 * 用户内容只存在于转义后的数据文件，模板为内置只读资源，二者都不会读取外部文件。
 */
@Slf4j
@Component
public class TypstResumeRenderer implements ResumePdfRenderer {

    private static final String DATA_FILENAME = "data.typ";
    private static final String ENTRY_FILENAME = "main.typ";
    private static final String OUTPUT_FILENAME = "output.pdf";
    private static final List<String> AUDITED_FONT_FILES = List.of(
            "NotoSansCJK-Regular.ttc", "NotoSansCJK-Bold.ttc");
    private static final List<Path> DEFAULT_FONT_DIRECTORIES = List.of(
            Path.of("/opt/resume-fonts"),
            Path.of("/usr/share/fonts/google-noto-sans-cjk-fonts"),
            Path.of("/usr/share/fonts/opentype/noto"),
            Path.of("/usr/share/fonts/truetype/noto"));

    private final TypstRenderProperties properties;
    private final TypstResumeSourceMapper sourceMapper;
    private final PdfLayoutInspector layoutInspector;

    public TypstResumeRenderer(
            TypstRenderProperties properties,
            TypstResumeSourceMapper sourceMapper,
            PdfLayoutInspector layoutInspector) {
        this.properties = properties;
        this.sourceMapper = sourceMapper;
        this.layoutInspector = layoutInspector;
    }

    @Override
    public ResumePdfRenderResult render(ResumeDocumentDTO document, ResumeTemplateId template) {
        if (template == null) {
            throw new ResumeRenderException("缺少简历模板");
        }
        String dataSource = sourceMapper.mapToDataSource(document);
        String templateSource = loadTemplate(template);

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("resume-render-");
            Files.writeString(workDir.resolve(DATA_FILENAME), dataSource, StandardCharsets.UTF_8);
            Files.writeString(workDir.resolve(ENTRY_FILENAME), templateSource, StandardCharsets.UTF_8);
            // 空的包目录：内置模板找不到任何外部包；这不是 OS 级网络沙箱。
            Files.createDirectories(workDir.resolve(".packages"));
            Files.createDirectories(workDir.resolve(".package-cache"));

            Path outputPath = workDir.resolve(OUTPUT_FILENAME);
            Process process = startCompilation(workDir, outputPath);
            waitForCompletion(process);
            if (!Files.exists(outputPath) || Files.size(outputPath) == 0) {
                throw new ResumeRenderException("简历 PDF 生成失败");
            }
            byte[] pdf = Files.readAllBytes(outputPath);
            pdf = applyStableDocumentMetadata(pdf, document);
            verifyAuditedFontWeights(pdf);
            return new ResumePdfRenderResult(pdf, layoutInspector.inspect(pdf));
        } catch (IOException exception) {
            throw new ResumeRenderException("简历渲染失败，请稍后重试", exception);
        } finally {
            deleteQuietly(workDir);
        }
    }

    /**
     * 给浏览器原生 viewer 一个稳定的人类可读文档标题，并固定 PDFBox 写入的日期。
     * 这不会改变页面内容或 Preview receipt 绑定，只避免 blob URL 成为唯一可见标题。
     */
    private byte[] applyStableDocumentMetadata(byte[] pdf, ResumeDocumentDTO document) {
        try (PDDocument pdfDocument = Loader.loadPDF(pdf)) {
            PDDocumentInformation info = pdfDocument.getDocumentInformation();
            String name = document == null || document.getBasics() == null
                    ? null : document.getBasics().getName();
            String title = name == null || name.isBlank() ? "简历预览" : name.strip() + " · 简历";
            info.setTitle(title);
            info.setAuthor("CV-Role");
            info.setCreator("CV-Role Resume Renderer");
            info.setSubject("岗位优化简历");
            Calendar epoch = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            epoch.clear();
            epoch.set(1970, Calendar.JANUARY, 1, 0, 0, 0);
            info.setCreationDate(epoch);
            info.setModificationDate(epoch);
            ByteArrayOutputStream output = new ByteArrayOutputStream(pdf.length + 512);
            pdfDocument.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ResumeRenderException("简历 PDF 生成失败");
        }
    }

    private String loadTemplate(ResumeTemplateId template) {
        ClassPathResource resource = new ClassPathResource(template.getResourcePath());
        if (!resource.exists()) {
            throw new ResumeRenderException("简历模板缺失");
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ResumeRenderException("简历模板读取失败", exception);
        }
    }

    private Process startCompilation(Path workDir, Path outputPath) throws IOException {
        List<String> command = new ArrayList<>(List.of(
                properties.getTypstBinary(),
                "compile",
                ENTRY_FILENAME,
                OUTPUT_FILENAME,
                "--root", workDir.toString(),
                "--package-path", workDir.resolve(".packages").toString(),
                "--package-cache-path", workDir.resolve(".package-cache").toString(),
                "--creation-timestamp", "0"));
        Path fontPath = prepareAuditedFontDirectory(workDir);
        // Always provide an isolated directory containing only the audited static Regular/Bold
        // faces. An empty configuration must not silently re-enable host variable/Thin fonts.
        command.add("--font-path");
        command.add(fontPath.toString());
        command.add("--ignore-system-fonts");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        // 不使用 PIPE：先 wait 再读取可能因子进程输出填满缓冲区而死锁；同时避免诊断原文把简历内容写入日志。
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            return builder.start();
        } catch (IOException exception) {
            log.error("Typst 编译器启动失败: binary={}", properties.getTypstBinary(), exception);
            throw new ResumeRenderException("简历渲染服务暂不可用，请稍后重试", exception);
        }
    }

    private Path prepareAuditedFontDirectory(Path workDir) {
        String configuredPath = properties.getFontPath();
        Path sourceDirectory;
        if (configuredPath == null || configuredPath.isBlank()) {
            sourceDirectory = DEFAULT_FONT_DIRECTORIES.stream()
                    .filter(this::hasAuditedFontFiles)
                    .findFirst()
                    .orElseThrow(() -> new ResumeRenderException(
                            "简历字体环境不可用，请配置包含 NotoSansCJK Regular/Bold 的字体目录"));
        } else {
            try {
                sourceDirectory = Path.of(configuredPath).toAbsolutePath().normalize();
            } catch (InvalidPathException exception) {
                throw new ResumeRenderException("简历字体环境不可用，请稍后重试", exception);
            }
            if (!hasAuditedFontFiles(sourceDirectory)) {
                throw new ResumeRenderException("简历字体环境不可用，请稍后重试");
            }
        }

        Path isolatedDirectory = workDir.resolve(".resume-fonts");
        try {
            Files.createDirectories(isolatedDirectory);
            for (String fileName : AUDITED_FONT_FILES) {
                Files.copy(sourceDirectory.resolve(fileName), isolatedDirectory.resolve(fileName),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ResumeRenderException("简历字体环境不可用，请稍后重试", exception);
        }
        return isolatedDirectory;
    }

    private boolean hasAuditedFontFiles(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        return AUDITED_FONT_FILES.stream()
                .map(directory::resolve)
                .allMatch(Files::isRegularFile);
    }

    /**
     * Defensive post-condition for environments that accidentally bypass the isolated font path.
     * A generated PDF containing a light/thin CJK face is rejected rather than delivered with a
     * visually different weight.
     */
    private void verifyAuditedFontWeights(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }
                for (COSName name : resources.getFontNames()) {
                    PDFont font = resources.getFont(name);
                    String fontName = font == null || font.getName() == null
                            ? "" : font.getName().toLowerCase(Locale.ROOT);
                    if (fontName.contains("thin") || fontName.contains("extralight")
                            || fontName.contains("demilight") || fontName.contains("light")
                            || fontName.contains("variable") || fontName.contains("-vf")) {
                        throw new ResumeRenderException("简历字体环境不可用，请稍后重试");
                    }
                }
            }
        } catch (IOException exception) {
            throw new ResumeRenderException("简历 PDF 字体无法检查", exception);
        }
    }

    private void waitForCompletion(Process process) {
        long timeoutMillis = properties.getTimeout().toMillis();
        boolean finished;
        try {
            finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            terminateAndWait(process);
            Thread.currentThread().interrupt();
            throw new ResumeRenderException("简历渲染被中断");
        }
        if (!finished) {
            terminateAndWait(process);
            throw new ResumeRenderException("简历渲染超时，请稍后重试");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            // 不记录 Typst stderr：诊断可能包含生成的数据行、临时路径和用户简历正文。
            log.warn("Typst 编译失败: exitCode={}", exitCode);
            throw new ResumeRenderException("简历排版编译失败，请检查内容后重试");
        }
    }

    private void terminateAndWait(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        boolean restoreInterrupt = Thread.interrupted();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                log.error("Typst 编译进程强制终止超时: pid={}", process.pid());
            }
        } catch (InterruptedException exception) {
            restoreInterrupt = true;
            log.warn("等待 Typst 编译进程终止时被中断: pid={}", process.pid());
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void deleteQuietly(Path workDir) {
        if (workDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 单次清理失败不中断其余文件清理，最终由日志发现残留。
                }
            });
        } catch (IOException exception) {
            log.warn("渲染临时目录清理失败: dir={}", workDir, exception);
        }
    }
}
