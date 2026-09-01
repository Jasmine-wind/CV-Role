package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.render.PdfLayoutInspection;
import com.winter.airesumeoptimizer.infra.render.PdfLayoutInspector;
import com.winter.airesumeoptimizer.infra.render.ResumePdfRenderResult;
import com.winter.airesumeoptimizer.infra.render.ResumeTemplateId;
import com.winter.airesumeoptimizer.infra.render.ResumePdfRenderer;
import com.winter.airesumeoptimizer.infra.render.TypstRenderProperties;
import com.winter.airesumeoptimizer.infra.render.TypstResumeRenderer;
import com.winter.airesumeoptimizer.infra.render.TypstResumeSourceMapper;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.fixture.ResumeFixtures;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDocumentQualityValidator;
import com.winter.airesumeoptimizer.module.resume.service.ResumeCanonicalDocumentService;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.resume.enums.ResumeQualityStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Slice A Gate 1/2/3 的文件级验证：真实风格中文简历 → 抽取 → 规则解析 →
 * canonical 文档 → 确定性验证 →（可选）Typst 渲染/PDF 质量。
 *
 * <p>人工核对清单：标准夹具的事实面 = 李明 / 13812345678 / liming.dev@example.com /
 * 华东理工大学 软件工程 本科 2016.09-2020.06 / 上海云启科技 2022.07-至今 /
 * 杭州数澜 2020.07-2022.06 / 订单中台重构、实时风控平台两个项目 / 技能三组。
 */
class ResumeParseDeliveryChainTest {

    private static final FileStorageService fileStorageService = mock(FileStorageService.class);
    private static final ResumeTextExtractionServiceImpl extraction =
            new ResumeTextExtractionServiceImpl(fileStorageService);
    private static final ResumeTextCleanServiceImpl cleaner = new ResumeTextCleanServiceImpl();
    private static final ResumeStructureParseServiceImpl structureParse = new ResumeStructureParseServiceImpl();
    private static final ResumeCanonicalDocumentServiceImpl canonicalService =
            new ResumeCanonicalDocumentServiceImpl(new ObjectMapper());
    private static final ResumeDocumentQualityValidatorImpl validator = new ResumeDocumentQualityValidatorImpl();

    private static boolean typstAvailable;

    @BeforeAll
    static void prepareFixtures() throws IOException {
        // 已提交的文件夹具可用于纯解析/质量测试；只有缺失时才需要本机 Typst 生成。
        typstAvailable = typstAvailable();
        if (typstAvailable) {
            ResumeFixtures.ensureFiles(Path.of("src/test/resources"));
        }
    }

    private static boolean typstAvailable() {
        try {
            Process process = new ProcessBuilder("typst", "--version").start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            return false;
        }
    }

    private static void givenFile(String objectKey, String resourcePath) throws IOException {
        byte[] bytes = ResumeFixtures.read(resourcePath);
        when(fileStorageService.loadAsStream(objectKey)).thenReturn(new ByteArrayInputStream(bytes));
    }

    private static ResumeCanonicalDocumentService.BuildResult runChain(String objectKey, String resourcePath, String fileType)
            throws IOException {
        givenFile(objectKey, resourcePath);
        String extracted = extraction.extractText(objectKey, fileType);
        ResumeTextCleanResultDTO cleanResult = cleaner.cleanAndSplitSections(extracted);
        ResumeStructuredContentDTO structured =
                structureParse.parse(cleanResult.getCleanedText(), cleanResult.getSections());
        structured.setRawText(cleanResult.getCleanedText());
        return canonicalService.build(structured);
    }

    @Test
    void standardPdfShouldDeliverContactsBoundariesAndReadyStatus() throws IOException {
        ResumeCanonicalDocumentService.BuildResult result =
                runChain("standard.pdf", ResumeFixtures.STANDARD_PDF, "pdf");
        ResumeDocumentDTO document = result.document();
        String documentText = documentText(document);

        // Gate 1：联系方式零丢失、零类型错配。
        assertThat(document.getBasics().getName()).isEqualTo("李明");
        assertThat(document.getBasics().getJobIntention()).isEqualTo("Java 后端工程师");
        assertThat(document.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getType, ResumeDocumentContactDTO::getValue)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("PHONE", "13812345678"),
                        org.assertj.core.groups.Tuple.tuple("EMAIL", "liming.dev@example.com"),
                        org.assertj.core.groups.Tuple.tuple("GITHUB", "github.com/liming-dev"),
                        org.assertj.core.groups.Tuple.tuple("LOCATION", "上海"));

        // Gate 1：教育/经历/项目/技能边界正确，关键事实与原材料一致。
        assertThat(documentText).contains("华东理工大学");
        assertThat(documentText).contains("上海云启科技有限公司");
        assertThat(documentText).contains("杭州数澜信息技术有限公司");
        assertThat(documentText).contains("订单中台重构");
        assertThat(documentText).contains("实时风控平台");

        ResumeDocumentSectionDTO education = sectionOf(document, "EDUCATION");
        assertThat(education.getEntries())
                .extracting(ResumeDocumentEntryDTO::getSchool)
                .contains("华东理工大学");
        assertThat(sectionOf(document, "SUMMARY").getEntries())
                .flatExtracting(ResumeDocumentEntryDTO::getBullets)
                .extracting(ResumeDocumentBulletDTO::getText)
                .anySatisfy(text -> assertThat(text).contains("系统建设"));

        ResumeDocumentSectionDTO skill = sectionOf(document, "SKILL");
        assertThat(skill.getEntries())
                .flatExtracting(ResumeDocumentEntryDTO::getSkillItems)
                .contains("Java", "Spring Boot", "MySQL");

        // 两个工作经历必须各自承载自己的 bullets，不能把换行碎片或第二家公司变成无标题条目。
        ResumeDocumentSectionDTO experience = sectionOf(document, "EXPERIENCE");
        assertThat(experience.getEntries()).hasSize(2);
        assertThat(experience.getEntries())
                .extracting(ResumeDocumentEntryDTO::getOrganization)
                .containsExactly("上海云启科技有限公司", "杭州数澜信息技术有限公司");
        assertThat(experience.getEntries().get(0).getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .anySatisfy(text -> assertThat(text).contains("日均处理订单"));

        // 两个项目必须是两个独立条目，不能被合并；日期是最小可靠边界。
        ResumeDocumentSectionDTO project = sectionOf(document, "PROJECT");
        assertThat(project.getEntries()).hasSize(2);
        assertThat(project.getEntries())
                .extracting(ResumeDocumentEntryDTO::getOrganization)
                .containsExactly("订单中台重构", "实时风控平台");

        // Gate 1：不允许系统兜底章节进入正式文档。
        assertThat(document.getSections())
                .extracting(ResumeDocumentSectionDTO::getTitle)
                .doesNotContain("未识别章节", "其他原始内容", "原始简历内容");

        // Gate 1：正常简历不应被打入确认流程（无阻断项、无未决候选）。
        ResumeDocumentQualityValidator.ValidationResult validation =
                validator.validate(document, result.unresolvedItems());
        assertThat(validation.qualityStatus()).isEqualTo(ResumeQualityStatus.QUALITY_READY);
        assertThat(result.unresolvedItems()).isEmpty();
    }

    @Test
    void standardDocxShouldDeliverTheSameFacts() throws IOException {
        ResumeCanonicalDocumentService.BuildResult result =
                runChain("standard.docx", ResumeFixtures.STANDARD_DOCX, "docx");
        ResumeDocumentDTO document = result.document();

        assertThat(document.getBasics().getName()).isEqualTo("李明");
        assertThat(document.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getType, ResumeDocumentContactDTO::getValue)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("PHONE", "13812345678"),
                        org.assertj.core.groups.Tuple.tuple("EMAIL", "liming.dev@example.com"),
                        org.assertj.core.groups.Tuple.tuple("GITHUB", "github.com/liming-dev"));
        assertThat(sectionOf(document, "EXPERIENCE").getEntries()).hasSize(2);
        assertThat(sectionOf(document, "PROJECT").getEntries()).hasSize(2);
        assertThat(documentText(document)).contains("华东理工大学");
    }

    @Test
    void mixedLanguagePdfShouldDeliverEnglishFactsAndStayReady() throws IOException {
        ResumeCanonicalDocumentService.BuildResult result =
                runChain("mixed.pdf", ResumeFixtures.MIXED_PDF, "pdf");
        ResumeDocumentDTO document = result.document();

        assertThat(document.getBasics().getName()).isEqualTo("李明");
        assertThat(document.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getType, ResumeDocumentContactDTO::getValue)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("PHONE", "13812345678"),
                        org.assertj.core.groups.Tuple.tuple("EMAIL", "liming.dev@example.com"),
                        org.assertj.core.groups.Tuple.tuple("GITHUB", "github.com/liming-dev"),
                        org.assertj.core.groups.Tuple.tuple("LOCATION", "上海"));
        assertThat(documentText(document)).contains(
                "Backend platform engineer focused on reliable services",
                "Designed Spring Boot services",
                "订单结算平台",
                "PostgreSQL",
                "AWS Certified Developer");
        assertThat(sectionOf(document, "EXPERIENCE").getEntries())
                .extracting(ResumeDocumentEntryDTO::getOrganization)
                .containsExactly("上海云启科技有限公司", "杭州数澜信息技术有限公司");
        assertThat(sectionOf(document, "PROJECT").getEntries())
                .extracting(ResumeDocumentEntryDTO::getOrganization)
                .containsExactly("订单结算平台", "实时风控平台");
        assertThat(validator.validate(document, result.unresolvedItems()).qualityStatus())
                .isEqualTo(ResumeQualityStatus.QUALITY_READY);
        assertThat(result.unresolvedItems()).isEmpty();
    }

    @Test
    void ambiguousPdfShouldFailClosedIntoNeedsReviewWithoutInventedFacts() throws IOException {
        ResumeCanonicalDocumentService.BuildResult result =
                runChain("ambiguous.pdf", ResumeFixtures.AMBIGUOUS_PDF, "pdf");
        ResumeDocumentDTO document = result.document();

        ResumeDocumentQualityValidator.ValidationResult validation =
                validator.validate(document, result.unresolvedItems());

        // Gate 2：无法可靠判断 → NEEDS_REVIEW，且不允许宣称可直接投递。
        assertThat(validation.qualityStatus()).isEqualTo(ResumeQualityStatus.QUALITY_NEEDS_REVIEW);
        // Gate 2：不猜测事实 —— 残缺的电话/邮箱不得被补造成合法联系方式。
        assertThat(document.getBasics().getContacts())
                .noneMatch(contact -> {
                    String type = contact.getType();
                    String value = contact.getValue();
                    if ("PHONE".equals(type)) {
                        return com.winter.airesumeoptimizer.module.resume.service
                                .ResumeDocumentQualityValidator.isValidPhone(value);
                    }
                    if ("EMAIL".equals(type)) {
                        return com.winter.airesumeoptimizer.module.resume.service
                                .ResumeDocumentQualityValidator.isValidEmail(value);
                    }
                    return false;
                });
        // 无法归属的内容必须显式保留在未决候选，而不是进入正式文档。
        assertThat(result.unresolvedItems()).isNotEmpty();
        assertThat(document.getSections())
                .extracting(ResumeDocumentSectionDTO::getTitle)
                .doesNotContain("未识别章节", "其他原始内容", "原始简历内容");
    }

    @Test
    void onePageAndTwoPageResumesShouldBothRenderWithoutOrphanFinalPage() throws IOException {
        assumeTrue(typstAvailable, "本机未安装 typst，跳过真实渲染验证");

        ResumeCanonicalDocumentService.BuildResult onePage =
                runChain("standard.pdf", ResumeFixtures.STANDARD_PDF, "pdf");
        ResumePdfRenderResult standardRendered = render(onePage.document());
        assertThat(standardRendered.layout().overflowDetected()).isFalse();
        assertThat(standardRendered.layout().pageCount()).isEqualTo(1);
        assertThat(standardRendered.layout().minimumFontSizeInPt()).isGreaterThanOrEqualTo(9.0f);

        ResumeCanonicalDocumentService.BuildResult twoPage =
                runChain("two-page.pdf", ResumeFixtures.TWO_PAGE_PDF, "pdf");
        ResumePdfRenderResult twoPageRendered = render(twoPage.document());
        PdfLayoutInspection layout = twoPageRendered.layout();
        assertThat(layout.pageCount()).isEqualTo(2);
        // Gate 3：两页可接受；末页既不能只剩一两行，也不能形成稀疏尾页。
        assertThat(layout.finalPageLineCount()).isGreaterThanOrEqualTo(3);
        assertThat(layout.finalPageContentRatio())
                .isGreaterThanOrEqualTo(com.winter.airesumeoptimizer.module.export.service
                        .ExportPreflightChecker.MIN_FINAL_PAGE_CONTENT_RATIO);
    }

    private ResumePdfRenderResult render(ResumeDocumentDTO document) {
        TypstRenderProperties properties = new TypstRenderProperties();
        String configuredFontPath = System.getenv("APP_RENDER_FONT_PATH");
        if (configuredFontPath != null && !configuredFontPath.isBlank()) {
            properties.setFontPath(configuredFontPath);
        }
        ResumePdfRenderer renderer = new TypstResumeRenderer(
                properties, new TypstResumeSourceMapper(), new PdfLayoutInspector());
        return renderer.render(document, ResumeTemplateId.CLASSIC);
    }

    @Test
    void validCompactOnePageDocumentPassesPdfGate() {
        assumeTrue(typstAvailable, "本机未安装 typst，跳过真实渲染验证");
        ResumeDocumentDTO document = compactDocument();

        assertThat(validator.validate(document, List.of()).qualityStatus())
                .isEqualTo(ResumeQualityStatus.QUALITY_READY);
        ResumePdfRenderResult rendered = render(document);

        assertThat(rendered.layout().pageCount()).isEqualTo(1);
        assertThat(rendered.layout().overflowDetected()).isFalse();
        assertThat(rendered.layout().minimumFontSizeInPt()).isGreaterThanOrEqualTo(9.0f);
    }

    private static ResumeDocumentDTO compactDocument() {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO.builder()
                        .name("李华")
                        .contacts(List.of(
                                ResumeDocumentContactDTO.builder().type("PHONE").label("电话").value("13800000000").build(),
                                ResumeDocumentContactDTO.builder().type("EMAIL").label("邮箱").value("lihua@example.com").build()))
                        .build())
                .sections(List.of(
                        ResumeDocumentSectionDTO.builder()
                                .kind("EXPERIENCE").title("工作经历")
                                .entries(List.of(ResumeDocumentEntryDTO.builder()
                                        .organization("某科技有限公司").role("Java 后端工程师")
                                        .startDate("2022.07").endDate("至今")
                                        .bullets(List.of(ResumeDocumentBulletDTO.builder()
                                                .text("负责订单服务开发与性能优化").build()))
                                        .build()))
                                .build(),
                        ResumeDocumentSectionDTO.builder()
                                .kind("PROJECT").title("项目经历")
                                .entries(List.of(ResumeDocumentEntryDTO.builder()
                                        .organization("订单中台").startDate("2023.01").endDate("2023.06")
                                        .bullets(List.of(ResumeDocumentBulletDTO.builder()
                                                .text("设计统一订单模型").build()))
                                        .build()))
                                .build(),
                        ResumeDocumentSectionDTO.builder()
                                .kind("EDUCATION").title("教育经历")
                                .entries(List.of(ResumeDocumentEntryDTO.builder()
                                        .school("某大学").degree("本科").major("计算机科学")
                                        .startDate("2018.09").endDate("2022.06")
                                        .bullets(List.of()).build()))
                                .build(),
                        ResumeDocumentSectionDTO.builder()
                                .kind("SKILL").title("技能")
                                .entries(List.of(ResumeDocumentEntryDTO.builder()
                                        .group("后端").skillItems(List.of("Java", "Spring Boot"))
                                        .bullets(List.of()).build()))
                                .build()))
                .build();
    }

    private static ResumeDocumentSectionDTO sectionOf(ResumeDocumentDTO document, String kind) {
        return document.getSections().stream()
                .filter(section -> kind.equals(section.getKind()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少章节类型：" + kind));
    }

    private static String documentText(ResumeDocumentDTO document) {
        StringBuilder builder = new StringBuilder();
        if (document.getBasics() != null) {
            builder.append(document.getBasics().getName()).append('\n');
            if (document.getBasics().getContacts() != null) {
                document.getBasics().getContacts()
                        .forEach(contact -> builder.append(contact.getValue()).append('\n'));
            }
        }
        if (document.getSections() != null) {
            for (ResumeDocumentSectionDTO section : document.getSections()) {
                builder.append(section.getTitle()).append('\n');
                for (ResumeDocumentEntryDTO entry : section.getEntries()) {
                    Stream.of(entry.getOrganization(), entry.getRole(), entry.getSchool(),
                                    entry.getDegree(), entry.getMajor(), entry.getStartDate(), entry.getEndDate())
                            .filter(value -> value != null)
                            .forEach(value -> builder.append(value).append('\n'));
                    if (entry.getSkillItems() != null) {
                        entry.getSkillItems().forEach(item -> builder.append(item).append('\n'));
                    }
                    if (entry.getBullets() != null) {
                        entry.getBullets().forEach(bullet -> builder.append(bullet.getText()).append('\n'));
                    }
                }
            }
        }
        return builder.toString();
    }
}
