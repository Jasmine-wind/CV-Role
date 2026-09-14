package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructureHealthEvaluation;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.enums.ResumeQualityStatus;
import com.winter.airesumeoptimizer.module.resume.service.ResumeCanonicalDocumentService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureHealthEvaluator;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Deterministic regression gate for the PII-free Resume Structure Recovery v1 corpus. */
class ResumeStructureRecoveryCorpusTest {

    private static final String MANIFEST = "/resume-recovery-corpus/manifest.json";
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d -]{8,16}\\d)(?!\\d)");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumeStructureParseServiceImpl parser = new ResumeStructureParseServiceImpl();
    private final ResumeStructureHealthEvaluator evaluator = new ResumeStructureHealthEvaluator();
    private final ResumeCanonicalDocumentServiceImpl canonicalService =
            new ResumeCanonicalDocumentServiceImpl(objectMapper);
    private final ResumeDocumentQualityValidatorImpl qualityValidator =
            new ResumeDocumentQualityValidatorImpl();

    @Test
    void c22ShouldKeepUndatedProjectAndReviewOnlyUnclassifiedContent() throws IOException {
        String source = readText("/resume-recovery-corpus/22-undated-omni-gateway-boundaries.txt");
        assertThat(EMAIL.matcher(source).find()).isFalse();
        assertThat(PHONE.matcher(source).find()).isFalse();

        ResumeStructuredContentDTO structured = parser.parse(source);
        List<ResumeProjectDTO> projects = structured.getStructuredData().getProjects();
        assertThat(projects).hasSize(2);
        assertThat(structured.getName()).isEqualTo("合成候选");
        assertThat(projects)
                .extracting(ResumeProjectDTO::getName)
                .containsExactly("Resume Evidence Workspace", "OmniGateway 高可用多智能体网关");
        assertThat(projects.get(0).getStartDate()).isEqualTo("2026.03");
        assertThat(projects.get(0).getEndDate()).isEqualTo("2026.05");
        assertThat(projects.get(1).getStartDate()).isNull();
        assertThat(projects.get(1).getEndDate()).isNull();
        assertThat(projects)
                .extracting(ResumeProjectDTO::getRole)
                .containsExactly("个人项目 ｜ 后端开发", "团队项目 ｜ Python Agent 负责人");
        assertThat(projects)
                .extracting(ResumeProjectDTO::getDescription)
                .containsExactly(
                        "构建冻结原文与岗位版简历之间的可审阅证据链路",
                        "基于 MCP 与向量检索构建多模型调用的高可用路由与故障恢复能力");
        assertThat(projects.get(0).getResponsibilities()).containsExactly(
                "实现来源事实校验、内容版本 CAS 与项目边界检查",
                "建立预览回执与导出质量门，避免未确认内容进入交付文档");
        assertThat(projects.get(1).getResponsibilities()).containsExactly(
                "设计请求编排、流量保护和可追溯审计流程",
                "实现 Agent 服务隔离与降级策略",
                "为工具调用补充超时、重试和可观测性");
        assertThat(projects.get(0).getSourceRef()).isNotNull();
        assertThat(projects.get(0).getSourceRef().getText())
                .contains("Resume Evidence Workspace", "个人项目 ｜ 后端开发", "内容版本 CAS")
                .doesNotContain("OmniGateway", "Python Agent");
        assertThat(projects.get(1).getSourceRef()).isNotNull();
        assertThat(projects.get(1).getSourceRef().getText())
                .contains("OmniGateway 高可用多智能体网关", "团队项目 ｜ Python Agent 负责人", "Agent 服务隔离")
                .doesNotContain("Resume Evidence Workspace", "2026.03");
        assertThat(structured.getStructuredData().getSkills().getDescriptions())
                .contains("Java 理解集合、异常处理、并发与 JVM 基础，能够编写可维护的后端服务")
                .anySatisfy(skill -> assertThat(skill).contains("PostgreSQL", "MySQL"))
                .anySatisfy(skill -> assertThat(skill).contains("Git", "Maven", "Linux", "Docker"));
        assertThat(structured.getStructuredData().getOthers())
                .containsExactly("该合成材料的归属有待人工确认")
                .doesNotContain("合成候选", "River University · 本科 · 软件工程");

        ResumeCanonicalDocumentService.BuildResult canonical = canonicalService.build(structured);
        ResumeDocumentDTO document = canonical.document();
        assertThat(document.getBasics().getName()).isEqualTo("合成候选");
        ResumeDocumentSectionDTO projectSection = sectionOf(document, "PROJECT");
        assertThat(projectSection.getEntries()).hasSize(2);
        assertThat(projectSection.getEntries())
                .extracting(ResumeDocumentEntryDTO::getOrganization)
                .containsExactly("Resume Evidence Workspace", "OmniGateway 高可用多智能体网关");

        assertThat(structured.getStructuredData().getAchievements())
                .extracting(ResumeAchievementDTO::getTitle)
                .containsExactly("Synthetic Software Contest 省二等奖", "Synthetic Challenge 省三等奖");
        assertThat(structured.getStructuredData().getAchievements().get(0).getDate())
                .isEqualTo("2025.04");
        assertThat(structured.getStructuredData().getAchievements().get(0).getTimeRange()).isNull();
        assertThat(structured.getStructuredData().getAchievements().get(1).getDate()).isNull();
        assertThat(structured.getStructuredData().getAchievements().get(1).getTimeRange())
                .isEqualTo("2024.07 – 2024.09");
        ResumeAchievementDTO award = structured.getStructuredData().getAchievements().get(1);
        assertThat(award.getTimeRange()).isEqualTo("2024.07 – 2024.09");
        assertThat(award.getTitle()).doesNotContain("2024.07", "2024.09");
        assertThat(award.getEvidence()).singleElement().satisfies(evidence -> {
            assertThat(evidence).containsOnlyOnce("2024.07").containsOnlyOnce("2024.09");
        });

        List<String> exportableSectionValues = exportableSectionValues(document);
        assertThat(exportableSectionValues)
                .doesNotContain(
                        "合成候选",
                        "River University · 本科 · 软件工程",
                        "该合成材料的归属有待人工确认")
                .noneMatch(value -> value.contains("该合成材料的归属有待人工确认"));
        assertThat(document.getSections())
                .extracting(ResumeDocumentSectionDTO::getTitle)
                .doesNotContain("补充内容", "未识别章节", "其他原始内容", "原始简历内容");
        assertThat(document.getSections())
                .extracting(ResumeDocumentSectionDTO::getKind)
                .doesNotContain("OTHER");
        assertThat(canonical.unresolvedItems())
                .anySatisfy(item -> assertThat(item.getCanonicalDraft())
                        .contains("该合成材料的归属有待人工确认"));
        assertThat(qualityValidator.validate(document, canonical.unresolvedItems()).qualityStatus())
                .isEqualTo(ResumeQualityStatus.QUALITY_NEEDS_REVIEW);
    }

    @Test
    void syntheticCorpusShouldMeetDeterministicRecoveryHealthGate() throws IOException {
        JsonNode manifest = readJson(MANIFEST);
        assertThat(manifest.path("schemaVersion").asText()).isEqualTo("resume-structure-recovery-v1");
        assertThat(manifest.path("piiPolicy").asText()).isEqualTo("synthetic-only-no-real-identifiers");

        JsonNode cases = manifest.path("cases");
        assertThat(cases.isArray()).isTrue();
        assertThat(cases.size()).isBetween(15, 25);
        assertThat(manifest.path("caseCount").asInt()).isEqualTo(cases.size());

        Set<String> ids = new HashSet<>();
        int totalSourceFacts = 0;
        int totalRepresentedFacts = 0;
        int totalBoundaryViolations = 0;
        int totalDuplicates = 0;
        List<String> sourceFacts = new ArrayList<>();
        List<JsonNode> caseResults = new ArrayList<>();
        for (JsonNode testCase : cases) {
            String id = testCase.path("id").asText();
            String file = testCase.path("file").asText();
            assertThat(ids.add(id)).as("duplicate corpus id %s", id).isTrue();
            String source = readText("/resume-recovery-corpus/" + file);
            assertThat(EMAIL.matcher(source).find()).as("email in synthetic case %s", id).isFalse();
            assertThat(PHONE.matcher(source).find()).as("phone in synthetic case %s", id).isFalse();

            ResumeStructuredContentDTO first = parser.parse(source);
            ResumeStructureHealthEvaluation health = evaluator.evaluate(first);
            ResumeStructureHealthEvaluation repeat = evaluator.evaluate(parser.parse(source));
            assertThat(repeat).as("non-deterministic health for %s", id).isEqualTo(health);
            assertThat(first.getRawText()).isEqualTo(source.strip());
            for (JsonNode expected : testCase.path("expectedFacts")) {
                sourceFacts.add(expected.asText());
                assertThat(first.getRawText())
                        .as("missing source fact %s in %s", expected.asText(), id)
                        .contains(expected.asText());
            }
            assertThat(health.sourceCoverage())
                    .as("source coverage for %s", id)
                    .isGreaterThanOrEqualTo(testCase.path("minSourceCoverage").asInt());
            assertThat(health.hardInvariantPass())
                    .as("hard invariants for %s: %s", id, health.hardInvariantViolations())
                    .isTrue();
            assertThat(health.entryBoundaryViolations())
                    .as("entry boundary violations for %s", id)
                    .isZero();
            assertThat(health.duplicateSourceCount())
                    .as("duplicate source ownership for %s", id)
                    .isZero();
            totalSourceFacts += health.meaningfulSourceCount();
            totalRepresentedFacts += health.representedSourceCount();
            totalBoundaryViolations += health.entryBoundaryViolations();
            totalDuplicates += health.duplicateSourceCount();
            caseResults.add(objectMapper.createObjectNode()
                    .put("id", id)
                    .put("healthScore", health.healthScore())
                    .put("sourceCoverage", health.sourceCoverage())
                    .put("meaningfulSourceCount", health.meaningfulSourceCount())
                    .put("representedSourceCount", health.representedSourceCount())
                    .put("orphanContentCount", health.orphanContentCount())
                    .put("duplicateSourceCount", health.duplicateSourceCount())
                    .put("entryBoundaryViolations", health.entryBoundaryViolations())
                    .put("fragmentedLineCount", health.fragmentedLineCount())
                    .put("hardInvariantPass", health.hardInvariantPass()));
        }

        writeEffectReport(caseResults, totalSourceFacts, totalRepresentedFacts);
        Path reportPath = Path.of("target", "resume-recovery-effect-report.json");
        byte[] firstReport = Files.readAllBytes(reportPath);
        writeEffectReport(caseResults, totalSourceFacts, totalRepresentedFacts);
        assertThat(Files.readAllBytes(reportPath)).isEqualTo(firstReport);
        assertAggregateOnlyReport(firstReport, sourceFacts);

        assertThat(totalSourceFacts).isPositive();
        assertThat(totalRepresentedFacts).isEqualTo(totalSourceFacts);
        assertThat(totalBoundaryViolations).isZero();
        assertThat(totalDuplicates).isZero();
    }

    private ResumeDocumentSectionDTO sectionOf(ResumeDocumentDTO document, String kind) {
        return document.getSections().stream()
                .filter(section -> kind.equals(section.getKind()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> exportableSectionValues(ResumeDocumentDTO document) {
        List<String> values = new ArrayList<>();
        for (ResumeDocumentSectionDTO section : document.getSections()) {
            values.add(section.getTitle());
            for (ResumeDocumentEntryDTO entry : section.getEntries()) {
                addIfPresent(values, entry.getOrganization());
                addIfPresent(values, entry.getRole());
                addIfPresent(values, entry.getSchool());
                addIfPresent(values, entry.getDegree());
                addIfPresent(values, entry.getMajor());
                addIfPresent(values, entry.getStartDate());
                addIfPresent(values, entry.getEndDate());
                addIfPresent(values, entry.getLocation());
                addIfPresent(values, entry.getEnvironment());
                addIfPresent(values, entry.getMentor());
                addIfPresent(values, entry.getGroup());
                addIfPresent(values, entry.getAwardTitle());
                addIfPresent(values, entry.getAwardLevel());
                addIfPresent(values, entry.getAwardCompetition());
                addIfPresent(values, entry.getAwardRanking());
                addIfPresent(values, entry.getAwardDate());
                if (entry.getSkillItems() != null) values.addAll(entry.getSkillItems());
                if (entry.getSkillDescriptions() != null) values.addAll(entry.getSkillDescriptions());
                if (entry.getTechStack() != null) values.addAll(entry.getTechStack());
                if (entry.getBullets() != null) {
                    entry.getBullets().stream()
                            .map(ResumeDocumentBulletDTO::getText)
                            .forEach(values::add);
                }
            }
        }
        return values;
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private void assertAggregateOnlyReport(byte[] reportBytes, List<String> sourceFacts) throws IOException {
        JsonNode report = objectMapper.readTree(reportBytes);
        assertThat(iterable(report.fieldNames())).containsExactlyInAnyOrder(
                "schemaVersion", "corpusCaseCount", "totalSourceFacts", "totalRepresentedFacts",
                "aggregateSourceCoverage", "cases");
        report.path("cases").forEach(result -> assertThat(iterable(result.fieldNames()))
                .containsExactlyInAnyOrder(
                        "id", "healthScore", "sourceCoverage", "meaningfulSourceCount",
                        "representedSourceCount", "orphanContentCount", "duplicateSourceCount",
                        "entryBoundaryViolations", "fragmentedLineCount", "hardInvariantPass"));

        String serialized = new String(reportBytes, StandardCharsets.UTF_8);
        assertThat(serialized)
                .doesNotContain("liming.dev@example.com", "13812345678", "上海云启科技有限公司");
        sourceFacts.forEach(fact -> assertThat(serialized)
                .as("effect report must not contain source fact: %s", fact)
                .doesNotContain(fact));
    }

    private <T> Iterable<T> iterable(java.util.Iterator<T> iterator) {
        return () -> iterator;
    }

    private void writeEffectReport(List<JsonNode> cases, int totalSourceFacts, int totalRepresentedFacts) throws IOException {
        JsonNode report = objectMapper.createObjectNode()
                .put("schemaVersion", "resume-structure-recovery-v1-effect-report")
                .put("corpusCaseCount", cases.size())
                .put("totalSourceFacts", totalSourceFacts)
                .put("totalRepresentedFacts", totalRepresentedFacts)
                .put("aggregateSourceCoverage", totalSourceFacts == 0 ? 100 : totalRepresentedFacts * 100 / totalSourceFacts)
                .set("cases", objectMapper.valueToTree(cases));
        Path reportPath = Path.of("target", "resume-recovery-effect-report.json");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
    }

    private JsonNode readJson(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("missing corpus resource %s", path).isNotNull();
            return objectMapper.readTree(input);
        }
    }

    private String readText(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("missing corpus resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }
}
