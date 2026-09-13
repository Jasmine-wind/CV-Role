package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructureHealthEvaluation;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureHealthEvaluator;
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
