package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeDisplayModelDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillEvidenceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillSetDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeCanonicalDocumentService;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 候选解析 → canonical 交付文档的可信边界测试（Slice A Gate 1/2 的后端单元层）。
 * 文件级端到端断言见中文简历 fixture 集成测试。
 */
class ResumeCanonicalDocumentServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumeCanonicalDocumentServiceImpl service = new ResumeCanonicalDocumentServiceImpl(objectMapper);

    @Test
    void buildShouldPreserveDuplicateSkillDescriptionOccurrences() {
        String description = "熟悉 Java";
        ResumeSkillSetDTO skills = ResumeSkillSetDTO.builder()
                .groups(Map.of("backend", List.of("Java")))
                .descriptions(List.of(description, description))
                .evidence(List.of(
                        ResumeSkillEvidenceDTO.builder()
                                .description(description).keywords(List.of("Java")).build(),
                        ResumeSkillEvidenceDTO.builder()
                                .description(description).keywords(List.of("Java")).build()))
                .build();
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("Java\n" + description + "\n" + description)
                .structuredData(ResumeStructuredDataDTO.builder().skills(skills).build())
                .build();

        ResumeDocumentEntryDTO skill = sectionOf(service.build(content).document(), "SKILL")
                .getEntries().get(0);

        assertThat(skill.getSkillDescriptions()).containsExactly(description, description);
        assertThat(skill.getSkillDescriptionSourceRefs()).hasSize(2);
        assertThat(skill.getSkillDescriptionSourceRefs().get(0).getSourceOccurrenceIds())
                .isNotEqualTo(skill.getSkillDescriptionSourceRefs().get(1).getSourceOccurrenceIds());
    }

    @Test
    void buildShouldProjectTypedContactsAndStructuredSections() {
        ResumeStructuredContentDTO content = realisticContent();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);
        ResumeDocumentDTO document = result.document();

        assertThat(document.getSchemaVersion()).isEqualTo(ResumeDocumentDTO.SCHEMA_VERSION);
        assertThat(document.getBasics().getName()).isEqualTo("李华");
        assertThat(document.getBasics().getJobIntention()).isEqualTo("Java 后端开发工程师");
        assertThat(document.getBasics().getHighestEducation()).isEqualTo("本科");
        assertThat(document.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getType, ResumeDocumentContactDTO::getValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("PHONE", "13800000000"),
                        org.assertj.core.groups.Tuple.tuple("EMAIL", "lihua@example.com"),
                        org.assertj.core.groups.Tuple.tuple("LOCATION", "上海"),
                        org.assertj.core.groups.Tuple.tuple("GITHUB", "github.com/lihua"));

        // 默认 canonical 投影使用 Recruiter reading order；V1 list order 仍由用户编辑后保留。
        assertThat(document.getSections())
                .extracting(ResumeDocumentSectionDTO::getKind)
                .containsExactly("EXPERIENCE", "PROJECT", "EDUCATION", "SKILL", "ACHIEVEMENT", "CERTIFICATE");
        // 章节白名单：不允许出现 raw/system 兜底章节。
        assertThat(document.getSections())
                .extracting(ResumeDocumentSectionDTO::getTitle)
                .doesNotContain("未识别章节", "其他原始内容", "原始简历内容");

        ResumeDocumentSectionDTO experience = sectionOf(document, "EXPERIENCE");
        assertThat(experience.getTitle()).isEqualTo("工作经历");
        ResumeDocumentEntryDTO entry = experience.getEntries().get(0);
        assertThat(entry.getOrganization()).isEqualTo("某科技有限公司");
        assertThat(entry.getRole()).isEqualTo("Java 后端工程师");
        assertThat(entry.getStartDate()).isEqualTo("2022.07");
        assertThat(entry.getEndDate()).isEqualTo("至今");
        assertThat(entry.getBullets()).hasSize(1);

        ResumeDocumentSectionDTO skill = sectionOf(document, "SKILL");
        ResumeDocumentEntryDTO skillEntry = skill.getEntries().get(0);
        assertThat(skillEntry.getGroup()).isEqualTo("编程语言");
        assertThat(skillEntry.getSkillItems()).containsExactly("Java", "Python");

        ResumeDocumentSectionDTO education = sectionOf(document, "EDUCATION");
        ResumeDocumentEntryDTO educationEntry = education.getEntries().get(0);
        assertThat(educationEntry.getSchool()).isEqualTo("某大学");
        assertThat(educationEntry.getDegree()).isEqualTo("本科");
        assertThat(educationEntry.getStartDate()).isEqualTo("2018.09");
        assertThat(educationEntry.getEndDate()).isEqualTo("2022.06");

        // 确定性 ID：同一输入重复构建逐字段一致。
        ResumeCanonicalDocumentService.BuildResult again = service.build(realisticContent());
        assertThat(again.document()).usingRecursiveComparison().isEqualTo(document);
    }

    @Test
    void buildShouldPreserveSkillDescriptionsSeparatelyFromSkillTags() {
        ResumeSkillSetDTO skills = ResumeSkillSetDTO.builder()
                .keywords(List.of("Git", "Docker", "PostgreSQL"))
                .groups(Map.of("tool", List.of("Git", "Docker"), "database", List.of("PostgreSQL")))
                .descriptions(List.of("工具：Git、Docker", "构建可重复发布流程"))
                .evidence(List.of(
                        ResumeSkillEvidenceDTO.builder()
                                .skill("Git").keywords(List.of("Git"))
                                .sourceText("工具：Git、Docker").description("工具：Git、Docker").build(),
                        ResumeSkillEvidenceDTO.builder()
                                .skill("Docker").keywords(List.of("Docker"))
                                .sourceText("工具：Git、Docker").description("工具：Git、Docker").build(),
                        ResumeSkillEvidenceDTO.builder()
                                .sourceText("构建可重复发布流程").description("构建可重复发布流程").keywords(List.of()).build(),
                        ResumeSkillEvidenceDTO.builder()
                                .skill("PostgreSQL").keywords(List.of("PostgreSQL"))
                                .sourceText("数据库：PostgreSQL").description("数据库：PostgreSQL").build()))
                .build();
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("Synthetic Candidate")
                .rawText("Synthetic Candidate\n工具：Git、Docker\n构建可重复发布流程\n数据库：PostgreSQL")
                .structuredData(ResumeStructuredDataDTO.builder().skills(skills).build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();
        ResumeDocumentSectionDTO skill = sectionOf(document, "SKILL");

        assertThat(skill.getEntries()).anySatisfy(entry -> {
            assertThat(entry.getSkillItems()).containsExactly("Git", "Docker");
            assertThat(entry.getSkillDescriptions()).contains("构建可重复发布流程");
        });
        assertThat(skill.getEntries()).allSatisfy(entry ->
                assertThat(entry.getSkillItems()).doesNotContain("构建可重复发布流程"));
    }

    @Test
    void buildShouldPreserveAwardDateSeparatelyFromAwardTitle() {
        ResumeAchievementDTO award = ResumeAchievementDTO.builder()
                .title("校级竞赛一等奖")
                .timeRange("2022.09 – 2022.12")
                .evidence(List.of("2022.09 - 2022.12，校级竞赛一等奖"))
                .build();
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("Synthetic Candidate")
                .rawText("Synthetic Candidate\n2022.09 - 2022.12，校级竞赛一等奖")
                .structuredData(ResumeStructuredDataDTO.builder().achievements(List.of(award)).build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();
        ResumeDocumentEntryDTO entry = sectionOf(document, "ACHIEVEMENT").getEntries().get(0);

        assertThat(entry.getBullets()).singleElement()
                .extracting(bullet -> bullet.getText())
                .asString()
                .contains("校级竞赛一等奖", "2022.09", "2022.12");
    }

    @Test
    void buildShouldPreserveDateOnlyAwardWithAttachedChineseDate() {
        ResumeAchievementDTO award = ResumeAchievementDTO.builder()
                .title("创新实践奖")
                .date("2024年")
                .evidence(List.of("2024年创新实践奖"))
                .build();
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("Synthetic Candidate")
                .rawText("Synthetic Candidate\\n2024年创新实践奖")
                .structuredData(ResumeStructuredDataDTO.builder().achievements(List.of(award)).build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();

        assertThat(sectionOf(document, "ACHIEVEMENT").getEntries()).singleElement()
                .extracting(entry -> entry.getBullets().get(0).getText())
                .asString()
                .contains("创新实践奖", "2024年");
    }

    @Test
    void buildShouldRouteInvalidContactsToUnresolvedCandidates() {
        ResumeStructuredContentDTO content = realisticContent();
        content.setPhone("13800");
        content.setEmail("lihua#example");

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.document().getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getType)
                .containsExactly("LOCATION", "GITHUB");
        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE.equals(item.getKind()))
                .isEmpty();
    }

    @Test
    void buildShouldCollectOthersAndUnrepresentedLinesAsFragments() {
        ResumeStructuredContentDTO content = realisticContent();
        content.getStructuredData().setOthers(List.of("自我评价：责任心强"));
        content.setRawText("李华\n某科技有限公司\n自我评价：责任心强\n完全无关的一行附加说明");

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.unresolvedItems())
                .extracting(ResumeUnresolvedItemDTO::getKind)
                .contains(ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT);
        assertThat(result.unresolvedItems())
                .extracting(ResumeUnresolvedItemDTO::getCanonicalDraft)
                .anySatisfy(draft -> assertThat(draft).contains("自我评价：责任心强"))
                .anySatisfy(draft -> assertThat(draft).contains("完全无关的一行附加说明"));
        // 已被正式文档表示的行不得重复成为候选。
        assertThat(result.unresolvedItems())
                .extracting(ResumeUnresolvedItemDTO::getCanonicalDraft)
                .noneSatisfy(draft -> assertThat(draft).contains("某科技有限公司"));
    }

    @Test
    void buildShouldDropCandidateValuesNotFoundInSourceText() {
        ResumeStructuredContentDTO content = realisticContent();
        content.setRawText("李华\nlihua@example.com\n某大学\n本科\nJava 后端工程师\n负责订单服务开发");
        content.setPhone("13999999999");
        content.getStructuredData().setExperiences(List.of(
                com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO.builder()
                        .type("WORK")
                        .organization("伪造公司")
                        .role("Java 后端工程师")
                        .bullets(List.of("负责订单服务开发"))
                        .build()));

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.document().getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getValue)
                .containsExactly("lihua@example.com");
        assertThat(result.document().getSections())
                .filteredOn(section -> "EXPERIENCE".equals(section.getKind()))
                .isEmpty();
        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE.equals(item.getKind()))
                .singleElement()
                .satisfies(item -> assertThat(item.getCanonicalDraft()).contains("EXPERIENCE"));
    }

    @Test
    void buildShouldNotTreatAJavaPrefixInJavaScriptAsSourceBacked() {
        ResumeStructuredContentDTO content = realisticContent();
        content.setRawText("李华\nlihua@example.com\n某大学\n本科\nJavaScript");
        content.getStructuredData().setExperiences(List.of(ResumeExperienceDTO.builder()
                .type("WORK")
                .organization("某公司")
                .role("Java")
                .bullets(List.of("JavaScript"))
                .build()));

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.document().getSections())
                .filteredOn(section -> "EXPERIENCE".equals(section.getKind()))
                .isEmpty();
        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE.equals(item.getKind()))
                .isNotEmpty();
    }

    @Test
    void buildShouldExposeMissingContactAsARequiredReviewCandidate() {
        ResumeStructuredContentDTO content = realisticContent();
        content.setPhone(null);
        content.setEmail(null);

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE.equals(item.getKind()))
                .singleElement()
                .satisfies(item -> assertThat(item.getCanonicalDraft()).contains("PHONE"));
    }

    @Test
    void buildShouldExposeMissingNameAsAnEditableReviewCandidate() {
        ResumeStructuredContentDTO content = realisticContent();
        content.setName(null);

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_NAME_CANDIDATE.equals(item.getKind()))
                .singleElement()
                .satisfies(item -> assertThat(item.getCanonicalDraft()).contains("text"));
    }

    @Test
    void buildShouldNotJoinSeparateChineseLinesIntoOneSourceFact() {
        ResumeStructuredContentDTO content = realisticContent();
        content.setRawText("李华\nlihua@example.com\n某大学\n本科\n北京\n上海\nJava");
        content.getStructuredData().setExperiences(List.of(ResumeExperienceDTO.builder()
                .type("WORK")
                .organization("北京上海")
                .role("Java")
                .bullets(List.of("Java"))
                .build()));

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.document().getSections())
                .filteredOn(section -> "EXPERIENCE".equals(section.getKind()))
                .isEmpty();
        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE.equals(item.getKind()))
                .isNotEmpty();
    }

    @Test
    void buildShouldRouteEntriesWithoutReliableTitlesToReviewCandidates() {
        ResumeStructuredContentDTO content = realisticContent();
        content.setRawText("李华\nlihua@example.com\n2018 - 2022 计算机科学与技术 本科\nJava 后端工程师\n负责订单服务开发\n负责支付模块\n完成联调");
        content.getStructuredData().setEducation(List.of("2018 - 2022 计算机科学与技术 本科"));
        content.getStructuredData().setExperiences(List.of(ResumeExperienceDTO.builder()
                .type("WORK")
                .role("Java 后端工程师")
                .bullets(List.of("负责订单服务开发"))
                .build()));
        content.getStructuredData().setProjects(List.of(ResumeProjectDTO.builder()
                .description("负责支付模块")
                .responsibilities(List.of("完成联调"))
                .build()));

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE.equals(item.getKind()))
                .hasSize(3)
                .allSatisfy(item -> assertThat(item.getSourceRef()).isNotBlank());
        assertThat(result.document().getSections())
                .filteredOn(section -> List.of("EDUCATION", "EXPERIENCE", "PROJECT").contains(section.getKind()))
                .isEmpty();
    }

    @Test
    void buildShouldNotUseTokenPercentageToHideMissingSourceFacts() {
        ResumeStructuredContentDTO content = realisticContent();
        content.setRawText("2018.09 - 2022.06 某大学 计算机科学与技术 本科 未识别事实");

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.unresolvedItems())
                .extracting(ResumeUnresolvedItemDTO::getCanonicalDraft)
                .anySatisfy(draft -> assertThat(draft).contains("未识别事实"));
    }

    @Test
    void buildShouldUseIndexedLinesAsSourceWhenRawTextIsAbsent() {
        String educationLine = "2020 - 2024 某大学 软件工程 本科";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .indexedLines(List.of(ResumeIndexedLineDTO.builder()
                        .lineId(1)
                        .text(educationLine)
                        .sourceBlockId("indexed-block-1")
                        .sourceBlockIds(List.of("indexed-block-1"))
                        .sourceOccurrenceIds(List.of("indexed-occurrence-1"))
                        .sectionHint("EDUCATION")
                        .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .education(List.of(educationLine))
                        .build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        ResumeDocumentEntryDTO entry = sectionOf(result.document(), "EDUCATION").getEntries().get(0);
        assertThat(entry.getSchool()).isEqualTo("某大学");
        assertThat(entry.getSourceOccurrenceIds()).containsExactly("indexed-occurrence-1");
        assertThat(result.unresolvedItems()).extracting(ResumeUnresolvedItemDTO::getCanonicalDraft)
                .noneMatch(draft -> draft.contains(educationLine));
    }

    @Test
    void buildShouldNotLetAPartialIndexedViewHideRawTextOccurrences() {
        String name = "候选人";
        String first = "2020 - 2024 某大学 软件工程 本科";
        String omitted = "未归类的补充说明";
        String last = "软件设计师";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name(name)
                .rawText(name + "\n" + first + "\n" + omitted + "\n" + last)
                .indexedLines(List.of(
                        ResumeIndexedLineDTO.builder().lineId(1).text(name)
                                .sourceOccurrenceIds(List.of("occurrence-name")).build(),
                        ResumeIndexedLineDTO.builder().lineId(2).text(first)
                                .sourceOccurrenceIds(List.of("occurrence-first")).build(),
                        ResumeIndexedLineDTO.builder().lineId(3).text(last)
                                .sourceOccurrenceIds(List.of("occurrence-last")).build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .education(List.of(first))
                        .certificates(List.of(last))
                        .build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT.equals(item.getKind()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getCanonicalDraft()).contains(omitted);
                    assertThat(item.getSourceRef()).isEqualTo("raw-line-3-occurrence");
                });
    }

    @Test
    void buildShouldNamespaceDuplicateOccurrenceIdsWithinOneRawSourceView() {
        String certificate = "软件设计师";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawSections(List.of(ResumeRawSectionDTO.builder()
                        .normalizedSection("CERTIFICATES")
                        .blocks(List.of(
                                ResumeRawSectionBlockDTO.builder()
                                        .id("block-a")
                                        .text(certificate)
                                        .sourceOccurrenceIds(List.of("same-occurrence"))
                                        .build(),
                                ResumeRawSectionBlockDTO.builder()
                                        .id("block-b")
                                        .text(certificate)
                                        .sourceOccurrenceIds(List.of("same-occurrence"))
                                        .build()))
                        .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .certificates(List.of(certificate, certificate))
                        .build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();

        assertThat(sectionOf(document, "CERTIFICATE").getEntries())
                .extracting(ResumeDocumentEntryDTO::getSourceOccurrenceIds)
                .containsExactly(List.of("same-occurrence"), List.of("same-occurrence~2"));
    }

    @Test
    void buildShouldAllocateDuplicateExperienceOrganizationsToDistinctOccurrences() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("候选人")
                .rawText("候选人\nA公司\nA公司")
                .structuredData(ResumeStructuredDataDTO.builder()
                        .experiences(List.of(
                                ResumeExperienceDTO.builder().type("WORK").organization("A公司").build(),
                                ResumeExperienceDTO.builder().type("WORK").organization("A公司").build()))
                        .build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();

        assertThat(sectionOf(document, "EXPERIENCE").getEntries()).hasSize(2);
        assertThat(sectionOf(document, "EXPERIENCE").getEntries())
                .extracting(ResumeDocumentEntryDTO::getSourceOccurrenceIds)
                .containsExactly(List.of("raw-line-2-occurrence"), List.of("raw-line-3-occurrence"));
    }

    @Test
    void buildShouldKeepWrappedExperienceBulletWithCompleteSpanProvenance() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("候选人")
                .rawText("候选人\n公司\n负责订单\n和支付")
                .structuredData(ResumeStructuredDataDTO.builder()
                        .experiences(List.of(
                                ResumeExperienceDTO.builder()
                                        .type("WORK")
                                        .organization("公司")
                                        .description("负责订单")
                                        .bullets(List.of("负责订单"))
                                        .build(),
                                ResumeExperienceDTO.builder()
                                        .type("WORK")
                                        .description("和支付")
                                        .bullets(List.of("和支付"))
                                        .build()))
                        .build())
                .build();

        ResumeDocumentEntryDTO entry = sectionOf(service.build(content).document(), "EXPERIENCE")
                .getEntries().get(0);

        assertThat(entry.getBullets()).singleElement().satisfies(bullet -> {
            assertThat(bullet.getText()).isEqualTo("负责订单和支付");
            assertThat(bullet.getSourceOccurrenceIds())
                    .containsExactly("raw-line-3-occurrence", "raw-line-4-occurrence");
        });
    }

    @Test
    void buildShouldKeepAnUnrepresentedSingleCharacterSourceFact() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("候选人")
                .rawText("候选人\n13800000000\nmail@example.com\nA")
                .phone("13800000000")
                .email("mail@example.com")
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT.equals(item.getKind()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getCanonicalDraft()).contains("A");
                    assertThat(item.getSourceRef()).isEqualTo("raw-line-4-occurrence");
                });
    }

    @Test
    void buildShouldPreserveDuplicateUnknownNameOccurrencesByOccurrence() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("候选人\n候选人")
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.unresolvedItems())
                .filteredOn(item -> item.getCanonicalDraft() != null
                        && item.getCanonicalDraft().contains("候选人"))
                .extracting(ResumeUnresolvedItemDTO::getSourceRef)
                .containsExactly("raw-line-1-occurrence", "raw-line-2-occurrence");
    }

    @Test
    void buildShouldPreserveDuplicateNamelessEducationCandidatesByOccurrence() {
        String line = "2018 - 2022 计算机科学 本科";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("候选人")
                .rawText(line + "\n" + line)
                .structuredData(ResumeStructuredDataDTO.builder()
                        .education(List.of(line))
                        .build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.unresolvedItems())
                .filteredOn(item -> item.getCanonicalDraft() != null
                        && item.getCanonicalDraft().contains(line))
                .extracting(ResumeUnresolvedItemDTO::getSourceRef)
                .containsExactly("raw-line-1-occurrence", "raw-line-2-occurrence");
    }

    @Test
    void buildShouldRejectAReferenceThatCrossesExplicitSourceSections() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawSections(List.of(
                        ResumeRawSectionDTO.builder()
                                .normalizedSection("SUMMARY")
                                .blocks(List.of(ResumeRawSectionBlockDTO.builder()
                                        .id("summary-line")
                                        .originalIndex(0)
                                        .text("A")
                                        .sourceOccurrenceIds(List.of("summary-occurrence"))
                                        .build()))
                                .build(),
                        ResumeRawSectionDTO.builder()
                                .normalizedSection("EDUCATION")
                                .blocks(List.of(ResumeRawSectionBlockDTO.builder()
                                        .id("education-line")
                                        .originalIndex(1)
                                        .text("B")
                                        .sourceOccurrenceIds(List.of("education-occurrence"))
                                        .build()))
                                .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .summary("AB")
                        .summarySourceRef(ResumeSourceRefDTO.builder()
                                .text("A\nB")
                                .sourceOccurrenceIds(List.of("summary-occurrence", "education-occurrence"))
                                .build())
                        .build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.document().getSections())
                .filteredOn(section -> "SUMMARY".equals(section.getKind()))
                .isEmpty();
    }

    @Test
    void buildShouldKeepSectionBoundaryForSameTextValues() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawSections(List.of(
                        ResumeRawSectionDTO.builder()
                                .normalizedSection("WORK_EXPERIENCES")
                                .blocks(List.of(ResumeRawSectionBlockDTO.builder()
                                        .id("work-company")
                                        .originalIndex(0)
                                        .text("工作公司")
                                        .sourceOccurrenceIds(List.of("work-company-occurrence"))
                                        .build(),
                                        ResumeRawSectionBlockDTO.builder()
                                        .id("work-line")
                                        .originalIndex(1)
                                        .text("同一条描述")
                                        .sourceOccurrenceIds(List.of("work-occurrence"))
                                        .build()))
                                .build(),
                        ResumeRawSectionDTO.builder()
                                .normalizedSection("EDUCATION")
                                .blocks(List.of(ResumeRawSectionBlockDTO.builder()
                                        .id("education-line")
                                        .originalIndex(2)
                                        .text("同一条描述")
                                        .sourceOccurrenceIds(List.of("education-occurrence"))
                                        .build()))
                                .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .experiences(List.of(ResumeExperienceDTO.builder()
                                .type("WORK").organization("工作公司")
                                .description("同一条描述").bullets(List.of("同一条描述")).build()))
                        .education(List.of("同一条描述"))
                        .build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();

        assertThat(sectionOf(document, "EXPERIENCE").getEntries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getSourceOccurrenceIds())
                            .containsExactly("work-company-occurrence", "work-occurrence");
                    assertThat(entry.getBullets()).singleElement()
                            .extracting(bullet -> bullet.getSourceOccurrenceIds())
                            .isEqualTo(List.of("work-occurrence"));
                });
        assertThat(document.getSections())
                .filteredOn(section -> "EDUCATION".equals(section.getKind()))
                .isEmpty();
    }

    @Test
    void buildShouldUseRawSectionBlocksAsSourceWhenOtherSourceViewsAreAbsent() {
        String certificate = "软件设计师";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawSections(List.of(ResumeRawSectionDTO.builder()
                        .id("section-certificates")
                        .normalizedSection("CERTIFICATES")
                        .blocks(List.of(ResumeRawSectionBlockDTO.builder()
                                .id("raw-certificate-1")
                                .text(certificate)
                                .sourceOccurrenceIds(List.of("certificate-occurrence-1"))
                                .build()))
                        .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .certificates(List.of(certificate))
                        .build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        ResumeDocumentEntryDTO entry = sectionOf(result.document(), "CERTIFICATE").getEntries().get(0);
        assertThat(entry.getSourceOccurrenceIds()).containsExactly("certificate-occurrence-1");
        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT.equals(item.getKind()))
                .extracting(ResumeUnresolvedItemDTO::getCanonicalDraft)
                .noneMatch(draft -> draft.contains(certificate));
    }

    @Test
    void buildShouldTreatGeneratedRawAndTextSectionViewsAsOneOccurrence() {
        String certificate = "软件设计师";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("李明")
                .email("liming@example.com")
                .rawText("李明\nliming@example.com\n" + certificate)
                .rawSections(List.of(
                        ResumeRawSectionDTO.builder()
                                .normalizedSection("GENERAL")
                                .blocks(List.of(
                                        ResumeRawSectionBlockDTO.builder()
                                                .id("source-line-0")
                                                .originalIndex(0)
                                                .text("李明")
                                                .sourceOccurrenceIds(List.of("source-occurrence-0"))
                                                .build(),
                                        ResumeRawSectionBlockDTO.builder()
                                                .id("source-line-1")
                                                .originalIndex(1)
                                                .text("liming@example.com")
                                                .sourceOccurrenceIds(List.of("source-occurrence-1"))
                                                .build()))
                                .build(),
                        ResumeRawSectionDTO.builder()
                                .normalizedSection("CERTIFICATES")
                                .blocks(List.of(ResumeRawSectionBlockDTO.builder()
                                        .id("source-line-2")
                                        .originalIndex(2)
                                        .text(certificate)
                                        .sourceOccurrenceIds(List.of("source-occurrence-2"))
                                        .build()))
                                .build()))
                .sections(List.of(
                        ResumeTextSectionDTO.builder()
                                .sectionType("GENERAL")
                                .lines(List.of("李明", "liming@example.com"))
                                .build(),
                        ResumeTextSectionDTO.builder()
                                .sectionType("CERTIFICATES")
                                .lines(List.of(certificate))
                                .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .certificates(List.of(certificate))
                        .build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(sectionOf(result.document(), "CERTIFICATE").getEntries()).hasSize(1);
        assertThat(result.unresolvedItems()).isEmpty();
    }

    @Test
    void buildShouldSurfaceAnUnrepresentedDuplicateSourceOccurrence() {
        String educationLine = "2020 - 2024 某大学 软件工程 本科";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText(educationLine + "\n" + educationLine)
                .structuredData(ResumeStructuredDataDTO.builder()
                        .education(List.of(educationLine))
                        .build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(sectionOf(result.document(), "EDUCATION").getEntries()).hasSize(1);
        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT.equals(item.getKind()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getCanonicalDraft()).contains(educationLine);
                    assertThat(item.getSourceRef()).isEqualTo("raw-line-2-occurrence");
                });
    }

    @Test
    void buildFromStructuredJsonShouldRecoverTopLevelFieldsFromPartialStructuredData() throws Exception {
        String educationLine = "2020 - 2024 某大学 软件工程 本科";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText(educationLine)
                .education(List.of(educationLine))
                .structuredData(ResumeStructuredDataDTO.builder().build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content));

        assertThat(sectionOf(result.document(), "EDUCATION").getEntries())
                .singleElement()
                .satisfies(entry -> assertThat(entry.getSchool()).isEqualTo("某大学"));
        assertThat(result.unresolvedItems())
                .filteredOn(item -> ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT.equals(item.getKind()))
                .isEmpty();
    }

    @Test
    void buildFromStructuredJsonShouldIgnoreEmptyHistoricalSemanticEntries() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .structuredData(ResumeStructuredDataDTO.builder()
                        .experiences(List.of(ResumeExperienceDTO.builder().build()))
                        .projects(List.of(ResumeProjectDTO.builder().build()))
                        .achievements(List.of(ResumeAchievementDTO.builder().build()))
                        .build())
                .build();

        assertThatThrownBy(() -> service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)))
                .isInstanceOf(com.winter.airesumeoptimizer.common.exception.BusinessException.class)
                .hasMessageContaining("无法形成可编辑章节");
    }

    @Test
    void buildFromStructuredJsonShouldRecoverSourceRowsWhenStructuredDataIsEmpty() throws Exception {
        String educationLine = "2020 - 2024 某大学 软件工程 本科";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawSections(List.of(ResumeRawSectionDTO.builder()
                        .id("education-section")
                        .normalizedSection("EDUCATION")
                        .blocks(List.of(ResumeRawSectionBlockDTO.builder()
                                .id("education-block")
                                .sourceOccurrenceIds(List.of("education-occurrence"))
                                .text(educationLine)
                                .build()))
                        .build()))
                .structuredData(ResumeStructuredDataDTO.builder().build())
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(sectionOf(document, "EDUCATION").getEntries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getSchool()).isEqualTo("某大学");
                    assertThat(entry.getSourceOccurrenceIds()).containsExactly("education-occurrence");
                });
    }

    @Test
    void buildFromStructuredJsonShouldPreserveTextSectionBlockOccurrences() throws Exception {
        String certificate = "软件设计师";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("CERTIFICATES")
                        .blocks(List.of(
                                ResumeBlockDTO.builder().id("certificate-block-1")
                                        .sourceOccurrenceIds(List.of("certificate-occurrence-1"))
                                        .text(certificate).build(),
                                ResumeBlockDTO.builder().id("certificate-block-2")
                                        .sourceOccurrenceIds(List.of("certificate-occurrence-2"))
                                        .text(certificate).build()))
                        .build()))
                .structuredData(ResumeStructuredDataDTO.builder().build())
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(sectionOf(document, "CERTIFICATE").getEntries()).hasSize(2);
        assertThat(sectionOf(document, "CERTIFICATE").getEntries())
                .extracting(ResumeDocumentEntryDTO::getSourceOccurrenceIds)
                .containsExactly(List.of("certificate-occurrence-1"), List.of("certificate-occurrence-2"));
    }

    @Test
    void buildShouldRetainNewOccurrencesWhenASecondSourceViewAlsoContainsAMirror() {
        String certificate = "软件设计师";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .indexedLines(List.of(ResumeIndexedLineDTO.builder()
                        .lineId(1)
                        .text(certificate)
                        .sourceOccurrenceIds(List.of("occ-a"))
                        .sectionHint("CERTIFICATES")
                        .build()))
                .rawSections(List.of(ResumeRawSectionDTO.builder()
                        .normalizedSection("CERTIFICATES")
                        .blocks(List.of(ResumeRawSectionBlockDTO.builder()
                                .id("raw-certificate")
                                .text(certificate)
                                .sourceOccurrenceIds(List.of("occ-a", "occ-b"))
                                .build()))
                        .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .certificates(List.of(certificate, certificate))
                        .build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();

        assertThat(sectionOf(document, "CERTIFICATE").getEntries())
                .extracting(ResumeDocumentEntryDTO::getSourceOccurrenceIds)
                .containsExactly(List.of("occ-a"), List.of("occ-b"));
    }

    @Test
    void buildShouldNotPromoteSharedBlockIdsToOccurrenceIdentity() {
        String certificate = "软件设计师";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("CERTIFICATES")
                        .blocks(List.of(
                                ResumeBlockDTO.builder()
                                        .id("shared-logical-block")
                                        .sourceBlockIds(List.of("shared-logical-block"))
                                        .text(certificate)
                                        .build(),
                                ResumeBlockDTO.builder()
                                        .id("shared-logical-block")
                                        .sourceBlockIds(List.of("shared-logical-block"))
                                        .text(certificate)
                                        .build()))
                        .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .certificates(List.of(certificate, certificate))
                        .build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();

        assertThat(sectionOf(document, "CERTIFICATE").getEntries()).hasSize(2);
        assertThat(sectionOf(document, "CERTIFICATE").getEntries())
                .extracting(ResumeDocumentEntryDTO::getSourceOccurrenceIds)
                .allSatisfy(ids -> assertThat(ids).isNotNull()
                        .doesNotContain("shared-logical-block"))
                .doesNotHaveDuplicates();
    }

    @Test
    void buildShouldNotTreatAConflictingBlockIdAsARepresentedOccurrence() {
        String certificate = "软件设计师";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("候选人")
                .sections(List.of(
                        ResumeTextSectionDTO.builder()
                                .sectionType("BASIC_INFO")
                                .blocks(List.of(ResumeBlockDTO.builder()
                                        .id("name-block")
                                        .sourceOccurrenceIds(List.of("name-occurrence"))
                                        .text("候选人")
                                        .build()))
                                .build(),
                        ResumeTextSectionDTO.builder()
                                .sectionType("CERTIFICATES")
                                .blocks(List.of(
                                        ResumeBlockDTO.builder()
                                                .id("logical-first")
                                                .sourceOccurrenceIds(List.of("shared-occurrence"))
                                                .text(certificate)
                                                .build(),
                                        // This logical block ID deliberately collides with the first
                                        // occurrence ID, but its own occurrence is independent.
                                        ResumeBlockDTO.builder()
                                                .id("shared-occurrence")
                                                .sourceBlockIds(List.of("shared-occurrence"))
                                                .sourceOccurrenceIds(List.of("second-occurrence"))
                                                .text(certificate)
                                                .build()))
                                .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .certificates(List.of(certificate))
                        .build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(sectionOf(result.document(), "CERTIFICATE").getEntries()).singleElement()
                .extracting(ResumeDocumentEntryDTO::getSourceOccurrenceIds)
                .isEqualTo(List.of("shared-occurrence"));
        assertThat(result.unresolvedItems())
                .filteredOn(item -> item.getCanonicalDraft() != null
                        && item.getCanonicalDraft().contains(certificate))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getKind()).isEqualTo(ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT);
                    assertThat(item.getSourceRef()).isEqualTo("second-occurrence");
                });
    }

    @Test
    void buildFromStructuredJsonShouldRetainDisplayOnlyCardsInMixedSnapshotsWithoutProvenance() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("公司 A\n工程师\n负责 A")
                .structuredData(ResumeStructuredDataDTO.builder()
                        .experiences(List.of(ResumeExperienceDTO.builder()
                                .type("WORK")
                                .organization("公司 A")
                                .role("工程师")
                                .bullets(List.of("负责 A"))
                                .build()))
                        .build())
                .displayModel(ResumeDisplayModelDTO.builder()
                        .overview(ResumeDisplayModelDTO.Overview.builder().name("历史候选人").build())
                        .workExperienceCards(List.of(
                                ResumeDisplayModelDTO.ExperienceCard.builder()
                                        .company("公司 A").position("工程师").summary("负责 A").build(),
                                ResumeDisplayModelDTO.ExperienceCard.builder()
                                        .company("公司 B").position("架构师").summary("负责 B").build()))
                        .build())
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(document.getBasics().getName()).isEqualTo("历史候选人");
        assertThat(sectionOf(document, "EXPERIENCE").getEntries())
                .extracting(ResumeDocumentEntryDTO::getOrganization)
                .containsExactly("公司 A", "公司 B");
        assertThat(sectionOf(document, "EXPERIENCE").getEntries().get(1).getSourceRef()).isNull();
    }

    @Test
    void buildFromStructuredJsonShouldRetainConflictingHistoricalDisplayOverviewValues() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("Alice")
                .rawText("Alice")
                .displayModel(ResumeDisplayModelDTO.builder()
                        .overview(ResumeDisplayModelDTO.Overview.builder().name("Bob").build())
                        .build())
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(document.getBasics().getName()).isEqualTo("Alice\nBob");
    }

    @Test
    void buildFromStructuredJsonShouldKeepIdOnlyHistoricalBlocksSourceFree() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("CERTIFICATES")
                        .blocks(List.of(ResumeBlockDTO.builder()
                                .id("display-row-1")
                                .sourceBlockIds(List.of("display-row-1"))
                                .text("历史证书")
                                .build()))
                        .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .certificates(List.of("历史证书"))
                        .build())
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(sectionOf(document, "CERTIFICATE").getEntries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getSourceRef()).isNull();
                    assertThat(entry.getSourceOccurrenceIds()).isNull();
                });
    }

    @Test
    void buildFromStructuredJsonShouldKeepDisplayCardsNotCoveredByPartialSemanticData() throws Exception {
        ResumeStructuredDataDTO semantic = ResumeStructuredDataDTO.builder()
                .experiences(List.of(ResumeExperienceDTO.builder()
                        .type("WORK")
                        .organization("公司 A")
                        .role("工程师")
                        .bullets(List.of("负责 A"))
                        .build()))
                .build();
        ResumeDisplayModelDTO display = ResumeDisplayModelDTO.builder()
                .workExperienceCards(List.of(
                        ResumeDisplayModelDTO.ExperienceCard.builder()
                                .company("公司 A").position("工程师").summary("负责 A").build(),
                        ResumeDisplayModelDTO.ExperienceCard.builder()
                                .company("公司 B").position("架构师").summary("负责 B").build()))
                .build();
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .structuredData(semantic)
                .displayModel(display)
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();
        assertThat(document.getSections()).filteredOn(section -> "EXPERIENCE".equals(section.getKind()))
                .singleElement()
                .satisfies(section -> assertThat(section.getEntries())
                        .extracting(ResumeDocumentEntryDTO::getOrganization)
                        .containsExactly("公司 A", "公司 B"));
        assertThat(document.getSections()).filteredOn(section -> "EXPERIENCE".equals(section.getKind()))
                .flatExtracting(ResumeDocumentSectionDTO::getEntries)
                .allSatisfy(entry -> assertThat(entry.getSourceRef()).isNull());
    }

    @Test
    void buildFromStructuredJsonShouldMergeDisplayModelVariantsInsteadOfDroppingLaterCards() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .displayModel(ResumeDisplayModelDTO.builder()
                        .overview(ResumeDisplayModelDTO.Overview.builder().name("候选人").build())
                        .build())
                .ruleDisplayModel(ResumeDisplayModelDTO.builder()
                        .projectCards(List.of(ResumeDisplayModelDTO.ProjectCard.builder()
                                .name("规则项目").summary("规则摘要").build()))
                        .build())
                .aiDisplayModel(ResumeDisplayModelDTO.builder()
                        .projectCards(List.of(ResumeDisplayModelDTO.ProjectCard.builder()
                                .name("AI 项目").summary("AI 摘要").build()))
                        .build())
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(sectionOf(document, "PROJECT").getEntries())
                .extracting(ResumeDocumentEntryDTO::getOrganization)
                .containsExactly("规则项目", "AI 项目");
        assertThat(document.getBasics().getName()).isEqualTo("候选人");
    }

    @Test
    void buildFromStructuredJsonShouldKeepTwoNamelessDateFreeProjectNamesSeparate() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("项目甲\n描述甲\n项目甲\n描述甲")
                .structuredData(ResumeStructuredDataDTO.builder()
                        .projects(List.of(
                                ResumeProjectDTO.builder().name("项目甲").description("描述甲").build(),
                                ResumeProjectDTO.builder().name("项目甲").description("描述甲").build()))
                        .build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();

        assertThat(sectionOf(document, "PROJECT").getEntries()).hasSize(2);
        assertThat(sectionOf(document, "PROJECT").getEntries())
                .extracting(entry -> entry.getBullets().stream()
                        .map(bullet -> bullet.getText()).toList())
                .containsExactly(List.of("描述甲"), List.of("描述甲"));
        assertThat(sectionOf(document, "PROJECT").getEntries())
                .extracting(ResumeDocumentEntryDTO::getSourceOccurrenceIds)
                .allSatisfy(ids -> assertThat(ids).isNotNull())
                .doesNotHaveDuplicates();
    }

    @Test
    void buildShouldKeepNonContiguousProjectCandidateForReviewWithoutForgedSourceRef() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("项目甲\n不属于项目甲的其他经历\n项目甲负责交付")
                .structuredData(ResumeStructuredDataDTO.builder()
                        .projects(List.of(ResumeProjectDTO.builder()
                                .name("项目甲")
                                .description("项目甲负责交付")
                                .build()))
                        .build())
                .build();

        ResumeCanonicalDocumentService.BuildResult result = service.build(content);

        assertThat(result.document().getSections())
                .filteredOn(section -> "PROJECT".equals(section.getKind()))
                .isEmpty();
        assertThat(result.unresolvedItems()).filteredOn(item ->
                ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE.equals(item.getKind())).singleElement()
                .satisfies(item -> {
                    assertThat(item.getKind()).isEqualTo(ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE);
                    assertThat(item.getSourceRef()).isNull();
                    assertThat(item.getCanonicalDraft()).contains("项目甲", "项目甲负责交付");
                    assertThat(item.getCanonicalDraft()).contains("sourceOccurrenceIds");
                });
    }

    @Test
    void buildFromStructuredJsonShouldNotInventProvenanceForPlainHistoricalTextLines() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("PROJECTS")
                        .heading("项目经历")
                        .lines(List.of("历史展示项目"))
                        .build()))
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(sectionOf(document, "PROJECT").getEntries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getSourceRef()).isNull();
                    assertThat(entry.getSourceOccurrenceIds()).isNull();
                    assertThat(entry.getBullets()).extracting("text").containsExactly("历史展示项目");
                });
    }

    @Test
    void buildFromStructuredJsonShouldRetainLinesAlongsidePartialTextSectionBlocks() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("CERTIFICATES")
                        .blocks(List.of(ResumeBlockDTO.builder()
                                .id("certificate-block")
                                .sourceOccurrenceIds(List.of("certificate-occurrence"))
                                .text("证书甲")
                                .build()))
                        .lines(List.of("证书甲", "证书乙"))
                        .build()))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .certificates(List.of("证书甲", "证书乙"))
                        .build())
                .build();

        ResumeDocumentDTO document = service.build(content).document();

        assertThat(sectionOf(document, "CERTIFICATE").getEntries()).hasSize(2);
        assertThat(sectionOf(document, "CERTIFICATE").getEntries())
                .extracting(entry -> entry.getBullets().get(0).getText())
                .containsExactly("证书甲", "证书乙");
    }

    @Test
    void buildFromStructuredJsonShouldNotSuppressSameTextAcrossHistoricalSections() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .structuredData(ResumeStructuredDataDTO.builder()
                        .certificates(List.of("Java"))
                        .build())
                .sections(List.of(
                        ResumeTextSectionDTO.builder()
                                .sectionType("SKILLS")
                                .lines(List.of("Java"))
                                .build(),
                        ResumeTextSectionDTO.builder()
                                .sectionType("CERTIFICATES")
                                .lines(List.of("Java"))
                                .build()))
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(sectionOf(document, "SKILL").getEntries()).singleElement()
                .satisfies(entry -> assertThat(entry.getBullets()).extracting("text").containsExactly("Java"));
        assertThat(sectionOf(document, "CERTIFICATE").getEntries()).singleElement()
                .satisfies(entry -> assertThat(entry.getBullets()).extracting("text").containsExactly("Java"));
    }

    @Test
    void buildFromStructuredJsonShouldNotSuppressSameTextAcrossHistoricalExperienceTypes() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .workExperiences(List.of("同一行经历"))
                .internships(List.of("同一行经历"))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .experiences(List.of(ResumeExperienceDTO.builder()
                                .type("WORK")
                                .description("同一行经历")
                                .build()))
                        .build())
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(document.getSections()).filteredOn(section -> "EXPERIENCE".equals(section.getKind()))
                .extracting(ResumeDocumentSectionDTO::getTitle)
                .containsExactly("工作经历", "实习经历");
        assertThat(document.getSections()).filteredOn(section -> "EXPERIENCE".equals(section.getKind()))
                .flatExtracting(ResumeDocumentSectionDTO::getEntries)
                .extracting(entry -> entry.getBullets().get(0).getText())
                .containsExactly("同一行经历", "同一行经历");
    }

    @Test
    void buildFromStructuredJsonShouldProjectEvidenceOnlySkillsWithoutInventingReferences() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .structuredData(ResumeStructuredDataDTO.builder()
                        .skills(ResumeSkillSetDTO.builder()
                                .evidence(List.of(ResumeSkillEvidenceDTO.builder()
                                        .skill("Java")
                                        .sourceText("Java")
                                        .description("使用 Java")
                                        .build()))
                                .build())
                        .build())
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();
        ResumeDocumentEntryDTO entry = sectionOf(document, "SKILL").getEntries().get(0);

        assertThat(entry.getSkillItems()).containsExactly("Java");
        assertThat(entry.getSkillDescriptions()).containsExactly("使用 Java", "Java");
        assertThat(entry.getSourceRef()).isNull();
        assertThat(entry.getSourceOccurrenceIds()).isNull();
    }

    @Test
    void buildFromStructuredJsonShouldKeepDisplayModelOnlySnapshotsReadable() throws Exception {
        ResumeDisplayModelDTO display = ResumeDisplayModelDTO.builder()
                .overview(ResumeDisplayModelDTO.Overview.builder()
                        .name("历史候选人")
                        .targetRole("平台工程师")
                        .highestDegree("硕士")
                        .coreSkills(List.of("Java"))
                        .build())
                .projectCards(List.of(ResumeDisplayModelDTO.ProjectCard.builder()
                        .name("历史项目")
                        .summary("保留展示摘要")
                        .techStack(List.of("Spring Boot"))
                        .build()))
                .build();
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .displayModel(display)
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(document.getBasics().getName()).isEqualTo("历史候选人");
        assertThat(document.getBasics().getJobIntention()).isEqualTo("平台工程师");
        assertThat(sectionOf(document, "PROJECT").getEntries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getOrganization()).isEqualTo("历史项目");
                    assertThat(entry.getBullets()).extracting("text").contains("保留展示摘要");
                    assertThat(entry.getSourceRef()).isNull();
                });
        assertThat(sectionOf(document, "SKILL").getEntries()).singleElement()
                .satisfies(entry -> assertThat(entry.getSkillItems()).containsExactly("Java"));
    }

    @Test
    void buildFromStructuredJsonShouldAcceptNestedSkillEvidenceObjects() throws Exception {
        String skillLine = "Java";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText(skillLine)
                .structuredData(ResumeStructuredDataDTO.builder()
                        .skills(ResumeSkillSetDTO.builder()
                                .keywords(List.of(skillLine))
                                .evidence(List.of(ResumeSkillEvidenceDTO.builder()
                                        .skill(skillLine)
                                        .sourceText(skillLine)
                                        .keywords(List.of(skillLine))
                                        .build()))
                                .build())
                        .build())
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(sectionOf(document, "SKILL").getEntries())
                .isNotEmpty();
    }

    @Test
    void buildFromStructuredJsonShouldProjectHistoricalTopLevelAwardsAndBasicScalars() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .basicInfo(new LinkedHashMap<>(Map.of(
                        "name", "历史候选人",
                        "phone", "13900000000",
                        "求职意向", "平台工程师",
                        "学历", "硕士",
                        "major", "信息系统")))
                .awards(List.of("2021 校级奖学金"))
                .build();

        ResumeDocumentDTO document = service.buildFromStructuredJson(
                objectMapper.writeValueAsString(content)).document();

        assertThat(document.getBasics().getName()).isEqualTo("历史候选人");
        assertThat(document.getBasics().getJobIntention()).isEqualTo("平台工程师");
        assertThat(document.getBasics().getHighestEducation()).isEqualTo("硕士");
        assertThat(document.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getType, ResumeDocumentContactDTO::getValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("PHONE", "13900000000"),
                        org.assertj.core.groups.Tuple.tuple("OTHER", "信息系统"));
        assertThat(sectionOf(document, "ACHIEVEMENT").getEntries())
                .singleElement()
                .satisfies(entry -> assertThat(entry.getAwardTitle()).isEqualTo("2021 校级奖学金"));
    }

    @Test
    void buildFromStructuredJsonShouldKeepHistoricalProjectSemanticFields() throws Exception {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .structuredData(ResumeStructuredDataDTO.builder()
                        .projects(List.of(ResumeProjectDTO.builder()
                                .name("历史项目")
                                .role("项目负责人")
                                .startDate("2020.01")
                                .endDate("2021.02")
                                .description("保留原始项目说明")
                                .build()))
                        .build())
                .build();

        ResumeDocumentEntryDTO entry = sectionOf(
                service.buildFromStructuredJson(objectMapper.writeValueAsString(content)).document(),
                "PROJECT").getEntries().get(0);

        assertThat(entry.getOrganization()).isEqualTo("历史项目");
        assertThat(entry.getRole()).isEqualTo("项目负责人");
        assertThat(entry.getStartDate()).isEqualTo("2020.01");
        assertThat(entry.getEndDate()).isEqualTo("2021.02");
        assertThat(entry.getBullets()).extracting("text").contains("保留原始项目说明");
    }

    @Test
    void buildFromStructuredJsonShouldProjectGenericSemanticHeadingAndMetaWithoutProvenance() {
        String historical = """
                {
                  "basicInfo": {
                    "name": "历史候选人",
                    "phone": "13900000000",
                    "email": "history@example.com"
                  },
                  "structuredData": {
                    "experiences": [
                      {
                        "heading": "旧科技公司",
                        "meta": "2020 - 2022 · Java 后端",
                        "bullets": ["负责服务开发"]
                      }
                    ],
                    "projects": [
                      {
                        "heading": "旧项目",
                        "meta": "2021 - 2022 · 项目负责人",
                        "responsibilities": ["交付核心功能"]
                      }
                    ],
                    "achievements": [
                      {
                        "heading": "校级一等奖",
                        "meta": "2022"
                      }
                    ]
                  }
                }
                """;

        ResumeDocumentDTO document = service.buildFromStructuredJson(historical).document();

        ResumeDocumentEntryDTO experience = sectionOf(document, "EXPERIENCE").getEntries().get(0);
        assertThat(experience.getOrganization()).isEqualTo("旧科技公司");
        assertThat(experience.getRole()).isEqualTo("Java 后端");
        assertThat(experience.getStartDate()).isEqualTo("2020");
        assertThat(experience.getEndDate()).isEqualTo("2022");
        assertThat(experience.getSourceRef()).isNull();
        assertThat(experience.getSourceOccurrenceIds()).isNull();

        ResumeDocumentEntryDTO project = sectionOf(document, "PROJECT").getEntries().get(0);
        assertThat(project.getOrganization()).isEqualTo("旧项目");
        assertThat(project.getRole()).isEqualTo("项目负责人");
        assertThat(project.getStartDate()).isEqualTo("2021");
        assertThat(project.getEndDate()).isEqualTo("2022");
        assertThat(project.getSourceRef()).isNull();
        assertThat(project.getSourceOccurrenceIds()).isNull();

        ResumeDocumentEntryDTO achievement = sectionOf(document, "ACHIEVEMENT").getEntries().get(0);
        assertThat(achievement.getAwardTitle()).isEqualTo("校级一等奖");
        assertThat(achievement.getAwardDate()).isEqualTo("2022");
        assertThat(achievement.getSourceRef()).isNull();
        assertThat(achievement.getSourceOccurrenceIds()).isNull();
    }

    @Test
    void buildFromStructuredJsonShouldMergeMirroredEducationViewsWithoutDroppingRepeatedRows() {
        String historical = """
                {
                  "education": ["某大学", "某大学"],
                  "structuredData": {
                    "education": [
                      {"heading": "某大学", "meta": "2018 - 2022 · 软件工程"},
                      {"heading": "某大学", "meta": "2018 - 2022 · 软件工程"}
                    ]
                  }
                }
                """;

        List<ResumeDocumentEntryDTO> education = sectionOf(
                service.buildFromStructuredJson(historical).document(), "EDUCATION").getEntries();

        assertThat(education).hasSize(2);
        assertThat(education).allSatisfy(entry -> {
            assertThat(entry.getSchool()).isEqualTo("某大学");
            assertThat(entry.getStartDate()).isEqualTo("2018");
            assertThat(entry.getEndDate()).isEqualTo("2022");
            assertThat(entry.getSourceRef()).isNull();
            assertThat(entry.getSourceOccurrenceIds()).isNull();
        });
    }

    @Test
    void buildFromStructuredJsonShouldRetainDifferentHistoricalEducationOccurrences() {
        String historical = """
                {
                  "education": [
                    {"school": "某大学", "sourceOccurrenceIds": ["education-root"]}
                  ],
                  "structuredData": {
                    "education": [
                      {"school": "某大学", "sourceOccurrenceIds": ["education-nested"]}
                    ]
                  }
                }
                """;

        List<ResumeDocumentEntryDTO> education = sectionOf(
                service.buildFromStructuredJson(historical).document(), "EDUCATION").getEntries();

        assertThat(education).hasSize(2);
        assertThat(education).allSatisfy(entry -> {
            assertThat(entry.getSourceRef()).isNull();
            assertThat(entry.getSourceOccurrenceIds()).isNull();
        });
    }

    @Test
    void buildFromStructuredJsonShouldProjectTypedEducationObjectWithoutProvenance() {
        String historical = """
                {
                  "structuredData": {
                    "education": [
                      {
                        "school": "某大学",
                        "degree": "硕士",
                        "startDate": "2018",
                        "endDate": "2021"
                      }
                    ]
                  }
                }
                """;

        ResumeDocumentEntryDTO education = sectionOf(
                service.buildFromStructuredJson(historical).document(), "EDUCATION")
                .getEntries().get(0);

        assertThat(education.getSchool()).isEqualTo("某大学");
        assertThat(education.getDegree()).isEqualTo("硕士");
        assertThat(education.getStartDate()).isEqualTo("2018");
        assertThat(education.getEndDate()).isEqualTo("2021");
        assertThat(education.getSourceRef()).isNull();
        assertThat(education.getSourceOccurrenceIds()).isNull();
    }

    @Test
    void buildFromStructuredJsonShouldProjectGenericEducationObjectWithoutDroppingMetadata() {
        String historical = """
                {
                  "structuredData": {
                    "education": [
                      {
                        "heading": "某大学",
                        "meta": "2018 - 2022 · 软件工程",
                        "degree": "本科"
                      }
                    ]
                  }
                }
                """;

        ResumeDocumentEntryDTO education = sectionOf(
                service.buildFromStructuredJson(historical).document(), "EDUCATION")
                .getEntries().get(0);

        assertThat(education.getSchool()).isEqualTo("某大学");
        assertThat(education.getDegree()).isEqualTo("本科");
        assertThat(education.getStartDate()).isEqualTo("2018");
        assertThat(education.getEndDate()).isEqualTo("2022");
        assertThat(education.getBullets()).extracting("text")
                .containsExactly("2018 - 2022 · 软件工程");
        assertThat(education.getSourceRef()).isNull();
        assertThat(education.getSourceOccurrenceIds()).isNull();
    }

    @Test
    void buildFromStructuredJsonShouldReproveGenericEducationAgainstSourceBeforeAppending() {
        String historical = """
                {
                  "basicInfo": {"name": "候选人"},
                  "rawSections": [{
                    "normalizedSection": "GENERAL",
                    "blocks": [{"id": "b-edu", "text": "某大学 2020 - 2024 · 本科 计算机", "sourceOccurrenceIds": ["occ-edu"]}]
                  }],
                  "structuredData": {
                    "education": [{"heading": "某大学", "meta": "2020 - 2024 · 本科 计算机"}]
                  }
                }
                """;

        ResumeDocumentDTO document = service.buildFromStructuredJson(historical).document();

        assertThat(sectionOf(document, "EDUCATION").getEntries()).hasSize(1);
        assertThat(sectionOf(document, "EDUCATION").getEntries().get(0).getSchool())
                .isEqualTo("某大学");
        assertThat(sectionOf(document, "EDUCATION").getEntries().get(0).getSourceRef())
                .isNull();
    }

    @Test
    void buildFromStructuredJsonShouldSuppressSourceBackedGenericEducationMirror() {
        String historical = """
                {
                  "rawSections": [{
                    "normalizedSection": "EDUCATION",
                    "blocks": [{"id": "b-edu", "text": "某大学 2020 - 2024 · 本科 计算机", "sourceOccurrenceIds": ["occ-edu"]}]
                  }],
                  "structuredData": {
                    "education": [{"heading": "某大学", "meta": "2020 - 2024 · 本科 计算机"}]
                  }
                }
                """;

        ResumeDocumentDTO document = service.buildFromStructuredJson(historical).document();

        assertThat(sectionOf(document, "EDUCATION").getEntries()).hasSize(1);
        assertThat(sectionOf(document, "EDUCATION").getEntries().get(0).getSourceOccurrenceIds())
                .containsExactly("occ-edu");
    }

    @Test
    void buildFromStructuredJsonShouldKeepUnprovedGenericEducationAsReviewCandidate() {
        String historical = """
                {
                  "rawSections": [{
                    "normalizedSection": "EDUCATION",
                    "blocks": [{"id": "b-edu", "text": "真实大学", "sourceOccurrenceIds": ["occ-real"]}]
                  }],
                  "structuredData": {
                    "education": [{"heading": "虚构大学", "meta": "2020 - 2024"}]
                  }
                }
                """;

        var result = service.buildFromStructuredJson(historical);

        assertThat(sectionOf(result.document(), "EDUCATION").getEntries())
                .extracting(ResumeDocumentEntryDTO::getSchool)
                .containsExactly("真实大学");
        assertThat(result.unresolvedItems()).anySatisfy(item -> {
            assertThat(item.getKind()).isEqualTo(ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE);
            assertThat(item.getCanonicalDraft()).contains("虚构大学");
        });
    }

    @Test
    void buildFromStructuredJsonShouldRejectFakeHistoricalEducationOccurrence() {
        String historical = """
                {
                  "basicInfo": {"name": "候选人"},
                  "rawSections": [{
                    "normalizedSection": "GENERAL",
                    "blocks": [{"id": "b-edu", "text": "真实大学", "sourceOccurrenceIds": ["occ-real"]}]
                  }],
                  "structuredData": {
                    "education": [{
                      "heading": "真实大学",
                      "sourceOccurrenceIds": ["occ-fake"]
                    }]
                  }
                }
                """;

        var result = service.buildFromStructuredJson(historical);

        assertThat(result.document().getSections())
                .filteredOn(section -> "EDUCATION".equals(section.getKind()))
                .flatExtracting(ResumeDocumentSectionDTO::getEntries)
                .isEmpty();
        assertThat(result.unresolvedItems()).anySatisfy(item ->
                assertThat(item.getKind()).isEqualTo(ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE));
    }

    @Test
    void buildFromStructuredJsonShouldKeepHistoricalEducationSourceTextWithoutProvenance() {
        String historical = """
                {
                  "structuredData": {
                    "education": [
                      {"sourceRef": {"text": "某大学"}}
                    ]
                  }
                }
                """;

        ResumeDocumentEntryDTO education = sectionOf(
                service.buildFromStructuredJson(historical).document(), "EDUCATION")
                .getEntries().get(0);

        assertThat(education.getBullets()).extracting("text").containsExactly("某大学");
        assertThat(education.getSourceRef()).isNull();
        assertThat(education.getSourceOccurrenceIds()).isNull();
    }

    @Test
    void buildFromStructuredJsonShouldRejectUnknownHistoricalEducationBulletFields() {
        String historical = """
                {
                  "structuredData": {
                    "education": [
                      {"school": "某大学", "bullets": [{"text": "课程", "unexpected": "丢失"}]}
                    ]
                  }
                }
                """;

        assertThatThrownBy(() -> service.buildFromStructuredJson(historical))
                .hasMessage("简历结构化内容中的文本列表格式不正确");
    }

    @Test
    void buildFromStructuredJsonShouldKeepHistoricalAchievementSourceTextWithoutProvenance() {
        String historical = """
                {
                  "structuredData": {
                    "achievements": [
                      {"sourceRef": {"text": "一等奖"}}
                    ]
                  }
                }
                """;

        ResumeDocumentEntryDTO achievement = sectionOf(
                service.buildFromStructuredJson(historical).document(), "ACHIEVEMENT")
                .getEntries().get(0);

        assertThat(achievement.getBullets()).extracting("text").containsExactly("一等奖");
        assertThat(achievement.getSourceRef()).isNull();
        assertThat(achievement.getSourceOccurrenceIds()).isNull();
    }

    @Test
    void buildFromStructuredJsonShouldProjectGenericDisplayCardFieldsSectionAware() {
        String historical = """
                {
                  "displayModel": {
                    "educationCards": [
                      {"heading": "某大学", "meta": "2018 - 2022 · 软件工程"}
                    ],
                    "workExperienceCards": [
                      {"heading": "某公司", "meta": "2020 - 2021 · 后端工程师"}
                    ],
                    "projectCards": [
                      {"heading": "某项目", "meta": "负责交付"}
                    ],
                    "achievementCards": [
                      {"heading": "校级奖学金", "meta": "2022"}
                    ]
                  }
                }
                """;

        ResumeDocumentDTO document = service.buildFromStructuredJson(historical).document();
        ResumeDocumentEntryDTO education = sectionOf(document, "EDUCATION").getEntries().get(0);
        ResumeDocumentEntryDTO experience = sectionOf(document, "EXPERIENCE").getEntries().get(0);
        ResumeDocumentEntryDTO project = sectionOf(document, "PROJECT").getEntries().get(0);
        ResumeDocumentEntryDTO achievement = sectionOf(document, "ACHIEVEMENT").getEntries().get(0);

        assertThat(education.getSchool()).isEqualTo("某大学");
        assertThat(education.getBullets()).extracting("text")
                .containsExactly("2018 - 2022 · 软件工程");
        assertThat(experience.getOrganization()).isEqualTo("某公司");
        assertThat(experience.getRole()).isEqualTo("后端工程师");
        assertThat(project.getOrganization()).isEqualTo("某项目");
        assertThat(project.getBullets()).extracting("text").containsExactly("负责交付");
        assertThat(achievement.getAwardTitle()).isEqualTo("校级奖学金");
        assertThat(achievement.getAwardDate()).isEqualTo("2022");
        assertThat(document.getSections()).flatExtracting(ResumeDocumentSectionDTO::getEntries)
                .allSatisfy(entry -> {
                    assertThat(entry.getSourceRef()).isNull();
                    assertThat(entry.getSourceOccurrenceIds()).isNull();
                });
    }

    @Test
    void buildFromStructuredJsonShouldRetainConflictingAchievementDisplayHeading() {
        String historical = """
                {
                  "displayModel": {
                    "achievementCards": [
                      {"title": "已有标题", "heading": "历史标题", "meta": "2022"}
                    ]
                  }
                }
                """;

        ResumeDocumentEntryDTO achievement = sectionOf(
                service.buildFromStructuredJson(historical).document(), "ACHIEVEMENT")
                .getEntries().get(0);

        assertThat(achievement.getAwardTitle()).isEqualTo("已有标题\n历史标题");
        assertThat(achievement.getAwardDate()).isEqualTo("2022");
        assertThat(achievement.getBullets()).isEmpty();
        assertThat(achievement.getSourceRef()).isNull();
        assertThat(achievement.getSourceOccurrenceIds()).isNull();
    }

    @Test
    void buildFromStructuredJsonShouldKeepGenericSkillFieldsInDescriptionSlots() {
        String historical = """
                {
                  "structuredData": {
                    "skills": {
                      "heading": "技能补充",
                      "meta": "熟悉 Kubernetes"
                    }
                  },
                  "displayModel": {
                    "skillSummary": {
                      "heading": "技能摘要",
                      "meta": "可独立完成服务部署",
                      "groups": [
                        {"heading": "后端技术", "meta": "Java / Spring Boot"}
                      ]
                    }
                  }
                }
                """;

        ResumeDocumentDTO document = service.buildFromStructuredJson(historical).document();

        assertThat(sectionOf(document, "SKILL").getEntries())
                .flatExtracting(ResumeDocumentEntryDTO::getSkillDescriptions)
                .contains("技能补充", "熟悉 Kubernetes", "技能摘要", "可独立完成服务部署", "Java / Spring Boot");
        assertThat(document.getSections()).flatExtracting(ResumeDocumentSectionDTO::getEntries)
                .allSatisfy(entry -> {
                    assertThat(entry.getSourceRef()).isNull();
                    assertThat(entry.getSourceOccurrenceIds()).isNull();
                });
    }

    @Test
    void buildFromStructuredJsonShouldNotRepeatGlobalSkillDescriptionsPerGroup() {
        String historical = """
                {
                  "structuredData": {
                    "skills": {
                      "heading": "技能补充",
                      "meta": "熟悉 Kubernetes",
                      "groups": {"backend": ["Java"], "ops": ["Docker"]}
                    }
                  }
                }
                """;

        List<ResumeDocumentEntryDTO> entries = sectionOf(
                service.buildFromStructuredJson(historical).document(), "SKILL").getEntries();

        assertThat(entries).hasSize(2);
        assertThat(entries).flatExtracting(ResumeDocumentEntryDTO::getSkillDescriptions)
                .containsExactly("技能补充", "熟悉 Kubernetes");
    }

    @Test
    void buildFromStructuredJsonShouldUpgradeEmbeddedGenericV1WithoutAddingProvenance() {
        String historical = """
                {
                  "schemaVersion": "RESUME_DOCUMENT_V1",
                  "basics": {
                    "name": "历史候选人",
                    "contacts": [
                      {"type": "PHONE", "label": "电话", "value": "13900000000"},
                      {"type": "EMAIL", "label": "邮箱", "value": "history@example.com"}
                    ]
                  },
                  "sections": [
                    {
                      "kind": "EXPERIENCE", "title": "工作经历", "entries": [
                        {
                          "heading": "旧科技公司",
                          "meta": "2020 - 2022 · Java 后端",
                          "bullets": [{"text": "负责服务开发"}]
                        }
                      ]
                    },
                    {
                      "kind": "ACHIEVEMENT", "title": "荣誉奖项", "entries": [
                        {"heading": "校级一等奖", "meta": "2022", "bullets": []}
                      ]
                    }
                  ]
                }
                """;

        ResumeDocumentDTO document = service.buildFromStructuredJson(historical).document();

        ResumeDocumentEntryDTO experience = sectionOf(document, "EXPERIENCE").getEntries().get(0);
        assertThat(experience.getOrganization()).isEqualTo("旧科技公司");
        assertThat(experience.getRole()).isEqualTo("Java 后端");
        assertThat(experience.getStartDate()).isEqualTo("2020");
        assertThat(experience.getEndDate()).isEqualTo("2022");
        assertThat(experience.getSourceRef()).isNull();
        assertThat(experience.getSourceOccurrenceIds()).isNull();

        ResumeDocumentEntryDTO achievement = sectionOf(document, "ACHIEVEMENT").getEntries().get(0);
        assertThat(achievement.getAwardTitle()).isEqualTo("校级一等奖");
        assertThat(achievement.getAwardDate()).isEqualTo("2022");
        assertThat(achievement.getSourceRef()).isNull();
        assertThat(achievement.getSourceOccurrenceIds()).isNull();
    }

    @Test
    void buildFromStructuredJsonShouldRejectNonTextualHistoricalScalarInsteadOfCoercingIt() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("basicInfo").put("学历", 1234);
        root.putArray("sections");
        assertThatThrownBy(() -> service.buildFromStructuredJson(objectMapper.writeValueAsString(root)))
                .isInstanceOf(com.winter.airesumeoptimizer.common.exception.BusinessException.class)
                .hasMessageContaining("格式不正确");
    }

    @Test
    void buildFromStructuredJsonShouldRejectMalformedEmptyHistoricalSnapshot() {
        assertThatThrownBy(() -> service.buildFromStructuredJson("{\"foo\":\"bar\"}"))
                .isInstanceOf(com.winter.airesumeoptimizer.common.exception.BusinessException.class)
                .hasMessageContaining("格式不正确");
    }

    @Test
    void buildShouldFailClosedWhenReviewSidecarWouldOverflow() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText(java.util.stream.IntStream.range(0, 61)
                        .mapToObj(index -> "未归类内容" + index)
                        .collect(java.util.stream.Collectors.joining("\n")))
                .build();

        assertThatThrownBy(() -> service.build(content))
                .isInstanceOf(com.winter.airesumeoptimizer.common.exception.BusinessException.class)
                .hasMessageContaining("审查上限");
    }

    @Test
    void buildShouldReturnEmptyDocumentForNullCandidate() {
        ResumeCanonicalDocumentService.BuildResult result = service.build(null);

        assertThat(result.document().getSections()).isEmpty();
        assertThat(result.unresolvedItems()).isEmpty();
    }

    private ResumeDocumentSectionDTO sectionOf(ResumeDocumentDTO document, String kind) {
        return document.getSections().stream()
                .filter(section -> kind.equals(section.getKind()))
                .findFirst()
                .orElseThrow();
    }

    private ResumeStructuredContentDTO realisticContent() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("language", List.of("Java", "Python"));

        ResumeStructuredDataDTO data = ResumeStructuredDataDTO.builder()
                .summary(null)
                .education(List.of("2018.09 - 2022.06 某大学 计算机科学与技术 本科"))
                .skills(ResumeSkillSetDTO.builder().groups(groups).build())
                .experiences(List.of(ResumeExperienceDTO.builder()
                        .type("WORK")
                        .organization("某科技有限公司")
                        .role("Java 后端工程师")
                        .startDate("2022.07")
                        .endDate("至今")
                        .description("负责订单服务开发")
                        .bullets(List.of("负责订单服务开发"))
                        .build()))
                .projects(List.of(ResumeProjectDTO.builder()
                        .name("订单中台")
                        .role("核心开发")
                        .startDate("2023.01")
                        .endDate("2023.06")
                        .description("统一订单模型")
                        .techStack(List.of("Spring Boot", "MySQL"))
                        .responsibilities(List.of("设计订单状态机"))
                        .build()))
                .achievements(List.of(ResumeAchievementDTO.builder()
                        .title("优秀员工")
                        .date("2023")
                        .build()))
                .certificates(List.of("CET-6"))
                .build();

        Map<String, String> basicInfo = new LinkedHashMap<>();
        basicInfo.put("location", "上海");
        basicInfo.put("github", "github.com/lihua");

        return ResumeStructuredContentDTO.builder()
                .name("李华")
                .phone("13800000000")
                .email("lihua@example.com")
                .basicInfo(basicInfo)
                .jobIntention("Java 后端开发工程师")
                .highestEducation("本科")
                .rawText("李华\n13800000000\nlihua@example.com\n上海\ngithub.com/lihua\nJava 后端开发工程师\n本科\n"
                        + "2018.09 - 2022.06 某大学 计算机科学与技术 本科\n某科技有限公司\nJava 后端工程师\n"
                        + "2022.07 至今\n负责订单服务开发\n订单中台\n核心开发\n2023.01 - 2023.06\n统一订单模型\n"
                        + "Spring Boot\nMySQL\n设计订单状态机\nJava Python\n优秀员工\n2023\nCET-6")
                .structuredData(data)
                .build();
    }
}
