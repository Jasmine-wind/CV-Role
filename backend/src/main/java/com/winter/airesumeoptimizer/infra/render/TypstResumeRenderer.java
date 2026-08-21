package com.winter.airesumeoptimizer.infra.render;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Typst CLI 同步渲染实现。
 *
 * <p>隔离措施：每次渲染使用独立临时目录并以 --root 限制文件访问；包解析目录被指向
 * 空目录，禁止任意包加载与网络访问；固定 creation timestamp 保证同一输入逐字节确定。
 * 用户内容只存在于转义后的数据文件，模板为内置只读资源，二者都不会读取外部文件。
 */
@Slf4j
@Component
public class TypstResumeRenderer implements ResumePdfRenderer {

    private static final String DATA_FILENAME = "data.typ";
    private static final String ENTRY_FILENAME = "main.typ";
    private static final String OUTPUT_FILENAME = "output.pdf";

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
            // 空的包目录：typst 找不到任何包，彻底禁止包加载与网络下载。
            Files.createDirectories(workDir.resolve(".packages"));
            Files.createDirectories(workDir.resolve(".package-cache"));

            Path outputPath = workDir.resolve(OUTPUT_FILENAME);
            Process process = startCompilation(workDir, outputPath);
            waitForCompletion(process);
            if (!Files.exists(outputPath) || Files.size(outputPath) == 0) {
                throw new ResumeRenderException("简历 PDF 生成失败");
            }
            byte[] pdf = Files.readAllBytes(outputPath);
            return new ResumePdfRenderResult(pdf, layoutInspector.inspect(pdf));
        } catch (IOException exception) {
            throw new ResumeRenderException("简历渲染失败，请稍后重试", exception);
        } finally {
            deleteQuietly(workDir);
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
        ProcessBuilder builder = new ProcessBuilder(
                properties.getTypstBinary(),
                "compile",
                ENTRY_FILENAME,
                OUTPUT_FILENAME,
                "--root", workDir.toString(),
                "--package-path", workDir.resolve(".packages").toString(),
                "--package-cache-path", workDir.resolve(".package-cache").toString(),
                "--creation-timestamp", "0");
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
