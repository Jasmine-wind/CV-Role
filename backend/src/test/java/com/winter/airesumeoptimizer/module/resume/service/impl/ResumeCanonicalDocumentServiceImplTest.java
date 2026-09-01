package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillSetDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
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
                .hasSize(2);
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
                .structuredData(data)
                .build();
    }
}
