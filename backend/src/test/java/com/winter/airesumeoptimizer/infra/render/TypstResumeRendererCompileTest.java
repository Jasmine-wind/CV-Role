package com.winter.airesumeoptimizer.infra.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 真实 Typst 编译验证：仅当本机存在 typst 可执行文件时运行（CI 已安装）；
 * 二进制缺失环境跳过而不是失败，保证渲染 seam 的单元语义仍被其它测试覆盖。
 */
class TypstResumeRendererCompileTest {

    private static boolean typstAvailable;

    @TempDir
    Path tempDir;

    private final TypstResumeRenderer renderer = new TypstResumeRenderer(
            new TypstRenderProperties(), new TypstResumeSourceMapper(), new PdfLayoutInspector());

    @BeforeAll
    static void detectTypst() {
        try {
            Process process = new ProcessBuilder("typst", "--version").start();
            typstAvailable = process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            typstAvailable = false;
        }
    }

    @ParameterizedTest
    @EnumSource(ResumeTemplateId.class)
    void everyBuiltInTemplateCompilesToReadablePdf(ResumeTemplateId template) throws IOException {
        assumeTrue(typstAvailable, "本机未安装 typst，跳过真实编译验证");

        ResumePdfRenderResult result = renderer.render(realisticDocument(), template);
        byte[] pdf = result.pdf();

        assertThat(pdf.length).isGreaterThan(2000);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(result.layout().pageCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.layout().overflowDetected()).isFalse();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(result.layout().pageCount());
        }
    }

    @Test
    void renderingIsDeterministicForSameInput() {
        assumeTrue(typstAvailable, "本机未安装 typst，跳过真实编译验证");

        byte[] first = renderer.render(realisticDocument(), ResumeTemplateId.CLASSIC).pdf();
        byte[] second = renderer.render(realisticDocument(), ResumeTemplateId.CLASSIC).pdf();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void injectionPayloadOnlyRendersAsLiteralText() throws IOException {
        assumeTrue(typstAvailable, "本机未安装 typst，跳过真实编译验证");

        ResumeDocumentDTO document = realisticDocument();
        document.getSections().get(0).getEntries().get(0).getBullets().add(
                ResumeDocumentBulletDTO.builder()
                        .id("b-inject")
                        .text("\" ] #import \"missing.typ\": x #read(\"/etc/passwd\") #panic(\"pwned\")")
                        .build());

        byte[] pdf = renderer.render(document, ResumeTemplateId.MINIMAL).pdf();

        // 编译成功说明注入片段没有执行任何 Typst 代码（#panic / #import 会直接编译失败）。
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void timeoutTerminatesCompilerAndReturnsRetryableFailure() throws IOException {
        Path slowCompiler = tempDir.resolve("slow-typst");
        Files.writeString(slowCompiler, "#!/bin/sh\nsleep 10\n");
        assertThat(slowCompiler.toFile().setExecutable(true)).isTrue();

        TypstRenderProperties slow = new TypstRenderProperties();
        slow.setTypstBinary(slowCompiler.toString());
        slow.setTimeout(Duration.ofMillis(100));
        TypstResumeRenderer slowRenderer = new TypstResumeRenderer(
                slow, new TypstResumeSourceMapper(), new PdfLayoutInspector());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> slowRenderer.render(realisticDocument(), ResumeTemplateId.CLASSIC))
                .isInstanceOf(ResumeRenderException.class)
                .hasMessageContaining("超时");
    }

    @Test
    void compileFailureSurfacesAsRenderException() {
        // 用一个不存在的可执行文件模拟编译器故障：必须 fail closed 且不泄露进程细节。
        TypstRenderProperties broken = new TypstRenderProperties();
        broken.setTypstBinary("/nonexistent/typst-binary");
        TypstResumeRenderer brokenRenderer = new TypstResumeRenderer(
                broken, new TypstResumeSourceMapper(), new PdfLayoutInspector());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> brokenRenderer.render(realisticDocument(), ResumeTemplateId.CLASSIC))
                .isInstanceOf(ResumeRenderException.class);
    }

    private ResumeDocumentDTO realisticDocument() {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("张三")
                        .contacts(Stream.of(
                                        contact("c-1", "电话", "13800000000"),
                                        contact("c-2", "邮箱", "zhangsan@example.com"),
                                        contact("c-3", "城市", "北京"))
                                .toList())
                        .build())
                .sections(List.of(
                        ResumeDocumentSectionDTO.builder()
                                .id("s-1")
                                .kind("EXPERIENCE")
                                .title("工作经历")
                                .entries(List.of(ResumeDocumentEntryDTO.builder()
                                        .id("e-1")
                                        .heading("某科技有限公司")
                                        .meta("2022.07 - 至今 · Java 后端")
                                        .bullets(new java.util.ArrayList<>(List.of(
                                                ResumeDocumentBulletDTO.builder()
                                                        .id("b-1")
                                                        .text("负责核心交易链路开发，日均处理订单 100 万笔")
                                                        .build(),
                                                ResumeDocumentBulletDTO.builder()
                                                        .id("b-2")
                                                        .text("基于 Spring Boot 与 Redis 完成缓存设计")
                                                        .build())))
                                        .build()))
                                .build(),
                        ResumeDocumentSectionDTO.builder()
                                .id("s-2")
                                .kind("SKILL")
                                .title("技能")
                                .entries(List.of(ResumeDocumentEntryDTO.builder()
                                        .id("e-2")
                                        .heading("")
                                        .meta("")
                                        .bullets(List.of(ResumeDocumentBulletDTO.builder()
                                                .id("b-3")
                                                .text("Java / Spring Boot / MySQL / Redis")
                                                .build()))
                                        .build()))
                                .build()))
                .build();
    }

    private ResumeDocumentContactDTO contact(String id, String label, String value) {
        return ResumeDocumentContactDTO.builder().id(id).label(label).value(value).build();
    }
}
