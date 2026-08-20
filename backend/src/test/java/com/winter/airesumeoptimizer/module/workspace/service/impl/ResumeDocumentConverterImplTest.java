package com.winter.airesumeoptimizer.module.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResumeDocumentConverterImplTest {

    private ResumeDocumentConverterImpl converter;

    @BeforeEach
    void setUp() {
        converter = new ResumeDocumentConverterImpl(new ObjectMapper());
    }

    @Test
    void fromParsedSnapshotUsesDisplayModelWhenPresent() {
        String snapshot = """
                {
                  "name": "张三",
                  "phone": "13800000000",
                  "email": "zhangsan@example.com",
                  "jobIntention": "Java 后端",
                  "basicInfo": { "城市": "北京", "姓名": "张三", "resumeType": "EXPERIENCED",
                                 "jobIntention": "Java 后端", "degree": "本科" },
                  "displayModel": {
                    "summaryCard": { "content": "五年后端开发经验" },
                    "educationCards": [
                      { "school": "某大学", "degree": "本科", "major": "计算机", "timeRange": "2016 - 2020" }
                    ],
                    "workExperienceCards": [
                      { "company": "某公司", "position": "Java 开发", "timeRange": "2020 - 至今",
                        "responsibilities": ["负责订单服务开发"], "summary": "主导重构" }
                    ],
                    "projectCards": [
                      { "name": "订单中心", "techStack": ["Java", "Redis"], "responsibilities": ["缓存设计"] }
                    ],
                    "skillSummary": { "groups": [ { "name": "后端", "skills": ["Java", "Spring"] } ] },
                    "achievementCards": [ { "title": "优秀员工", "meta": "2023" } ],
                    "certificateTags": ["软件设计师"],
                    "pendingItems": ["自我评价内容"]
                  }
                }
                """;

        ResumeDocumentDTO document = converter.fromParsedSnapshot(snapshot);

        assertThat(document.getSchemaVersion()).isEqualTo(ResumeDocumentDTO.SCHEMA_VERSION);
        assertThat(document.getBasics().getName()).isEqualTo("张三");
        assertThat(document.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getLabel, ResumeDocumentContactDTO::getValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("电话", "13800000000"),
                        org.assertj.core.groups.Tuple.tuple("邮箱", "zhangsan@example.com"),
                        org.assertj.core.groups.Tuple.tuple("城市", "北京"),
                        org.assertj.core.groups.Tuple.tuple("学历", "本科"),
                        org.assertj.core.groups.Tuple.tuple("求职意向", "Java 后端"));

        assertThat(document.getSections()).extracting(ResumeDocumentSectionDTO::getTitle)
                .containsExactly("个人总结", "教育经历", "工作经历", "项目经历", "技能", "荣誉奖项", "证书", "其他内容");
        assertThat(document.getSections()).extracting(ResumeDocumentSectionDTO::getKind)
                .containsExactly("SUMMARY", "EDUCATION", "EXPERIENCE", "PROJECT", "SKILL",
                        "ACHIEVEMENT", "CERTIFICATE", "OTHER");

        ResumeDocumentSectionDTO experience = document.getSections().get(2);
        ResumeDocumentEntryDTO experienceEntry = experience.getEntries().get(0);
        assertThat(experienceEntry.getHeading()).isEqualTo("某公司 · Java 开发");
        assertThat(experienceEntry.getMeta()).isEqualTo("2020 - 至今");
        assertThat(experienceEntry.getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("负责订单服务开发", "主导重构");

        ResumeDocumentEntryDTO projectEntry = document.getSections().get(3).getEntries().get(0);
        assertThat(projectEntry.getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("技术栈：Java、Redis", "缓存设计");

        ResumeDocumentEntryDTO skillEntry = document.getSections().get(4).getEntries().get(0);
        assertThat(skillEntry.getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("后端：Java、Spring");

        assertIdsUniqueAndPresent(document);
    }

    @Test
    void fromParsedSnapshotFallsBackToStructuredDataWhenDisplayModelEmpty() {
        String snapshot = """
                {
                  "name": "李四",
                  "structuredData": {
                    "summary": "应届硕士",
                    "education": ["某大学 · 硕士 · 2023"],
                    "experiences": [
                      { "organization": "某实验室", "role": "研究助理",
                        "startDate": "2022.09", "endDate": "2023.06",
                        "description": "参与平台建设", "bullets": ["完成数据接入"] }
                    ],
                    "projects": [
                      { "name": "检索平台", "timeRange": "2023",
                        "responsibilities": ["负责检索模块"], "techStack": ["Python"] }
                    ],
                    "skills": { "keywords": ["Java", "Python"] },
                    "achievements": [ { "title": "奖学金", "level": "校级", "date": "2022" } ],
                    "certificates": ["CET-6"],
                    "others": ["开源社区贡献"]
                  }
                }
                """;

        ResumeDocumentDTO document = converter.fromParsedSnapshot(snapshot);

        assertThat(document.getSections()).extracting(ResumeDocumentSectionDTO::getTitle)
                .containsExactly("个人总结", "教育经历", "工作经历", "项目经历", "技能", "荣誉奖项", "证书", "其他内容");

        ResumeDocumentEntryDTO experienceEntry = document.getSections().get(2).getEntries().get(0);
        assertThat(experienceEntry.getHeading()).isEqualTo("某实验室 · 研究助理");
        assertThat(experienceEntry.getMeta()).isEqualTo("2022.09 - 2023.06");
        assertThat(experienceEntry.getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("参与平台建设", "完成数据接入");

        ResumeDocumentEntryDTO skillEntry = document.getSections().get(4).getEntries().get(0);
        assertThat(skillEntry.getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("Java", "Python");

        ResumeDocumentEntryDTO achievementEntry = document.getSections().get(5).getEntries().get(0);
        assertThat(achievementEntry.getHeading()).isEqualTo("奖学金");
        assertThat(achievementEntry.getMeta()).isEqualTo("校级 · 2022");
    }

    @Test
    void fromParsedSnapshotReturnsEmptySectionsWhenNoParseContent() {
        ResumeDocumentDTO document = converter.fromParsedSnapshot("""
                { "name": "王五", "phone": "13900000000" }
                """);

        assertThat(document.getBasics().getName()).isEqualTo("王五");
        assertThat(document.getSections()).isEmpty();
    }

    @Test
    void fromParsedSnapshotRejectsBlankOrInvalidSnapshot() {
        assertThatThrownBy(() -> converter.fromParsedSnapshot(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历内容尚未就绪");
        assertThatThrownBy(() -> converter.fromParsedSnapshot("{ not json"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不正确");
    }

    @Test
    void normalizeRejectsUnknownSchemaVersion() {
        ResumeDocumentDTO document = validDocumentBuilder().schemaVersion("SOMETHING_ELSE").build();

        assertThatThrownBy(() -> converter.normalize(document))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的简历内容格式");
    }

    @Test
    void normalizeAssignsMissingIdsWithoutChangingVisibleText() {
        ResumeDocumentDTO document = validDocumentBuilder()
                .sections(List.of(sectionBuilder()
                        .kind("CUSTOM")
                        .title("  工作经历  ")
                        .entries(List.of(entryBuilder()
                                .heading(" 某公司 ")
                                .bullets(List.of(ResumeDocumentBulletDTO.builder().text("  要点  ").build()))
                                .build()))
                        .build()))
                .build();

        ResumeDocumentDTO normalized = converter.normalize(document);

        assertThat(normalized.getSchemaVersion()).isEqualTo(ResumeDocumentDTO.SCHEMA_VERSION);
        assertThat(normalized.getBasics()).isNotNull();
        assertThat(normalized.getBasics().getContacts()).isEmpty();
        ResumeDocumentSectionDTO section = normalized.getSections().get(0);
        assertThat(section.getKind()).isEqualTo("CUSTOM");
        assertThat(section.getTitle()).isEqualTo("  工作经历  ");
        assertThat(section.getId()).isNotBlank();
        ResumeDocumentEntryDTO entry = section.getEntries().get(0);
        assertThat(entry.getHeading()).isEqualTo(" 某公司 ");
        assertThat(entry.getId()).isNotBlank();
        assertThat(entry.getBullets().get(0).getText()).isEqualTo("  要点  ");
        assertThat(entry.getBullets().get(0).getId()).isNotBlank();
    }

    @Test
    void normalizeRejectsDuplicateIdsInsteadOfSilentlyChangingIdentity() {
        ResumeDocumentDTO document = validDocumentBuilder()
                .sections(List.of(sectionBuilder()
                        .id("same-id")
                        .title("章节一")
                        .entries(List.of())
                        .build(),
                        sectionBuilder()
                                .id("same-id")
                                .title("章节二")
                                .entries(List.of())
                                .build()))
                .build();

        assertThatThrownBy(() -> converter.normalize(document))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ID 重复");
    }

    @Test
    void normalizeEnforcesEditingLimits() {
        List<ResumeDocumentSectionDTO> tooManySections = IntStream.range(0, 31)
                .mapToObj(index -> sectionBuilder()
                        .title("章节" + index)
                        .entries(List.of())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
        assertThatThrownBy(() -> converter.normalize(
                validDocumentBuilder().sections(tooManySections).build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("章节数量超出编辑上限");

        List<ResumeDocumentBulletDTO> tooManyBullets = IntStream.range(0, 101)
                .mapToObj(index -> ResumeDocumentBulletDTO.builder().text("要点" + index).build())
                .collect(Collectors.toCollection(ArrayList::new));
        assertThatThrownBy(() -> converter.normalize(validDocumentBuilder()
                .sections(List.of(sectionBuilder()
                        .title("工作经历")
                        .entries(List.of(entryBuilder()
                                .heading("某公司")
                                .bullets(tooManyBullets)
                                .build()))
                        .build()))
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("要点数量超出编辑上限");
    }

    @Test
    void normalizeRejectsOversizedBulletTextInsteadOfTruncating() {
        String oversized = "字".repeat(5000);

        assertThatThrownBy(() -> converter.normalize(validDocumentBuilder()
                .sections(List.of(sectionBuilder()
                        .title("工作经历")
                        .entries(List.of(entryBuilder()
                                .heading("某公司")
                                .bullets(List.of(ResumeDocumentBulletDTO.builder().text(oversized).build()))
                                .build()))
                        .build()))
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("要点内容超出编辑上限");
    }

    @Test
    void fromParsedSnapshotIsDeterministicAcrossInvocations() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String snapshot = """
                {
                  "name": "张三",
                  "displayModel": {
                    "workExperienceCards": [
                      { "company": "某公司", "position": "Java 开发", "timeRange": "2020 - 至今",
                        "responsibilities": ["负责订单服务开发"] }
                    ]
                  }
                }
                """;

        String first = objectMapper.writeValueAsString(converter.fromParsedSnapshot(snapshot));
        String second = objectMapper.writeValueAsString(converter.fromParsedSnapshot(snapshot));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void fromParsedSnapshotUsesTopSkillsWhenSkillGroupsAbsent() {
        ResumeDocumentDTO document = converter.fromParsedSnapshot("""
                {
                  "displayModel": {
                    "skillSummary": { "topSkills": ["Java", "Redis"], "groups": [] }
                  }
                }
                """);

        assertThat(document.getSections()).hasSize(1);
        ResumeDocumentSectionDTO skillSection = document.getSections().get(0);
        assertThat(skillSection.getKind()).isEqualTo("SKILL");
        assertThat(skillSection.getEntries().get(0).getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("Java", "Redis");
    }

    @Test
    void normalizeRejectsNullDocument() {
        assertThatThrownBy(() -> converter.normalize(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历内容不能为空");
    }

    @Test
    void normalizeEnforcesContactAndEntryLimits() {
        List<ResumeDocumentContactDTO> tooManyContacts = IntStream.range(0, 21)
                .mapToObj(index -> ResumeDocumentContactDTO.builder()
                        .label("字段" + index)
                        .value("值" + index)
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
        assertThatThrownBy(() -> converter.normalize(validDocumentBuilder()
                .basics(ResumeDocumentBasicsDTO.builder()
                        .contacts(tooManyContacts)
                        .build())
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("基础信息字段数量超出编辑上限");

        List<ResumeDocumentEntryDTO> tooManyEntries = IntStream.range(0, 101)
                .mapToObj(index -> entryBuilder()
                        .heading("条目" + index)
                        .bullets(List.of())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
        assertThatThrownBy(() -> converter.normalize(validDocumentBuilder()
                .sections(List.of(sectionBuilder()
                        .title("工作经历")
                        .entries(tooManyEntries)
                        .build()))
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("条目数量超出编辑上限");
    }

    @Test
    void normalizeRejectsOversizedNameAndHeadingInsteadOfTruncating() {
        assertThatThrownBy(() -> converter.normalize(validDocumentBuilder()
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("名".repeat(300))
                        .contacts(List.of())
                        .build())
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("姓名超出编辑上限");

        assertThatThrownBy(() -> converter.normalize(validDocumentBuilder()
                .sections(List.of(sectionBuilder()
                        .title("工作经历")
                        .entries(List.of(entryBuilder()
                                .heading("司".repeat(500))
                                .bullets(List.of())
                                .build()))
                        .build()))
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("条目标题超出编辑上限");
    }

    @Test
    void normalizePreservesBlankInProgressBulletsAndContacts() {
        ResumeDocumentDTO normalized = converter.normalize(validDocumentBuilder()
                .basics(ResumeDocumentBasicsDTO.builder()
                        .contacts(List.of(
                                ResumeDocumentContactDTO.builder().label("  ").value(" ").build(),
                                ResumeDocumentContactDTO.builder().label("电话").value("13800000000").build()))
                        .build())
                .sections(List.of(sectionBuilder()
                        .title("工作经历")
                        .entries(List.of(entryBuilder()
                                .heading("某公司")
                                .bullets(List.of(
                                        ResumeDocumentBulletDTO.builder().text("   ").build(),
                                        ResumeDocumentBulletDTO.builder().text("有效要点").build()))
                                .build()))
                        .build()))
                .build());

        assertThat(normalized.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getLabel)
                .containsExactly("  ", "电话");
        assertThat(normalized.getSections().get(0).getEntries().get(0).getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("   ", "有效要点");
    }

    @Test
    void fromParsedSnapshotRejectsBasicInfoBeyondContactLimitInsteadOfDroppingFields() {
        StringBuilder basicInfo = new StringBuilder("{");
        for (int index = 0; index < 25; index++) {
            if (index > 0) {
                basicInfo.append(",");
            }
            basicInfo.append("\"字段").append(index).append("\":\"值").append(index).append("\"");
        }
        basicInfo.append("}");
        String snapshot = "{ \"name\": \"张三\", \"phone\": \"13800000000\", \"email\": \"a@b.com\", \"basicInfo\": "
                + basicInfo + " }";

        assertThatThrownBy(() -> converter.fromParsedSnapshot(snapshot))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法安全转换");
    }

    @Test
    void fromParsedSnapshotPreservesExtendedBasicInfoFields() {
        ResumeDocumentDTO document = converter.fromParsedSnapshot("""
                {
                  "name": "张三",
                  "basicInfo": {
                    "github": "https://github.com/example",
                    "linkedin": "https://linkedin.com/in/example",
                    "城市": "上海"
                  }
                }
                """);

        assertThat(document.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getLabel, ResumeDocumentContactDTO::getValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("github", "https://github.com/example"),
                        org.assertj.core.groups.Tuple.tuple("linkedin", "https://linkedin.com/in/example"),
                        org.assertj.core.groups.Tuple.tuple("城市", "上海"));
    }

    @Test
    void fromParsedSnapshotUsesCompleteRawSectionsBeforeDerivedProjections() {
        ResumeDocumentDTO document = converter.fromParsedSnapshot("""
                {
                  "rawSections": [
                    {
                      "id": "section-001",
                      "originalTitle": "工作经历",
                      "normalizedSection": "WORK",
                      "originalOrder": 0,
                      "displayOrder": 0,
                      "blocks": [
                        { "index": 0, "text": "原始公司与岗位", "originalIndex": 0, "displayOrder": 0 },
                        { "index": 1, "text": "仅存在于原始章节的业务内容", "originalIndex": 1, "displayOrder": 1 }
                      ]
                    }
                  ],
                  "structuredData": {
                    "experiences": [ { "organization": "派生公司", "bullets": ["派生摘要"] } ]
                  },
                  "displayModel": {
                    "workExperienceCards": [ { "company": "展示公司", "responsibilities": ["展示摘要"] } ]
                  }
                }
                """);

        assertThat(document.getSections()).hasSize(1);
        assertThat(document.getSections().get(0).getEntries().get(0).getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("原始公司与岗位", "仅存在于原始章节的业务内容");
    }

    @Test
    void fromParsedSnapshotPreservesValidLegacyTopLevelBusinessFields() {
        ResumeDocumentDTO document = converter.fromParsedSnapshot("""
                {
                  "highestEducation": "本科",
                  "education": ["某大学 · 计算机"],
                  "skills": ["Java", "Redis"],
                  "projects": ["订单系统项目"],
                  "workExperiences": ["某公司后端开发"],
                  "internships": ["某科技公司实习"],
                  "campusExperiences": ["校园技术社团"],
                  "awards": ["优秀学生"],
                  "certificates": ["软件设计师"],
                  "summary": "后端开发者",
                  "others": ["开源贡献"]
                }
                """);

        assertThat(document.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getLabel, ResumeDocumentContactDTO::getValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("最高学历", "本科"));
        assertThat(document.getSections()).extracting(ResumeDocumentSectionDTO::getTitle)
                .containsExactly("个人总结", "教育经历", "技能", "工作经历", "实习经历", "校园经历",
                        "项目经历", "荣誉奖项", "证书", "其他内容");
        String serialized = new ObjectMapper().valueToTree(document).toString();
        assertThat(serialized)
                .contains("某大学 · 计算机", "Java", "Redis", "订单系统项目", "某公司后端开发", "某科技公司实习",
                        "校园技术社团", "优秀学生", "软件设计师", "后端开发者", "开源贡献");
    }

    @Test
    void fromParsedSnapshotMergesUniqueLegacyFieldsWithPartialStructuredData() {
        ResumeDocumentDTO document = converter.fromParsedSnapshot("""
                {
                  "structuredData": { "summary": "结构化总结" },
                  "education": ["旧版独有教育经历"],
                  "skills": ["旧版独有技能"],
                  "workExperiences": ["旧版独有工作经历"]
                }
                """);

        String serialized = new ObjectMapper().valueToTree(document).toString();
        assertThat(serialized)
                .contains("结构化总结", "旧版独有教育经历", "旧版独有技能", "旧版独有工作经历");
    }

    @Test
    void fromParsedSnapshotUsesRawTextBeforeLossyDerivedProjectionsWhenSectionsAreUnavailable() {
        ResumeDocumentDTO document = converter.fromParsedSnapshot("""
                {
                  "rawText": "姓名 张三\\n唯一原始内容",
                  "structuredData": { "summary": "派生摘要" },
                  "displayModel": { "summaryCard": { "content": "展示摘要" } }
                }
                """);

        assertThat(document.getSections()).hasSize(1);
        assertThat(document.getSections().get(0).getEntries().get(0).getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("姓名 张三", "唯一原始内容");
    }

    @Test
    void fromParsedSnapshotSupplementsRawTextLinesMissingFromRawSections() {
        ResumeDocumentDTO document = converter.fromParsedSnapshot("""
                {
                  "rawText": "已分段内容\\n包含已分段内容但更完整的原始业务内容",
                  "rawSections": [
                    {
                      "id": "section-001",
                      "originalTitle": "项目经历",
                      "normalizedSection": "PROJECTS",
                      "blocks": [ { "index": 0, "text": "已分段内容" } ]
                    }
                  ]
                }
                """);

        assertThat(document.getSections()).extracting(ResumeDocumentSectionDTO::getTitle)
                .containsExactly("项目经历", "其他原始内容");
        assertThat(document.getSections().get(1).getEntries().get(0).getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("包含已分段内容但更完整的原始业务内容");
    }

    @Test
    void fromParsedSnapshotRejectsUnknownOrMistypedParserFields() {
        assertThatThrownBy(() -> converter.fromParsedSnapshot("""
                { "name": "张三", "unexpectedBusinessField": "不能静默丢弃" }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不正确");

        assertThatThrownBy(() -> converter.fromParsedSnapshot("""
                { "structuredData": { "experiences": "not-an-array" } }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不正确");

        assertThatThrownBy(() -> converter.fromParsedSnapshot("""
                { "structuredData": { "summary": "有效总结" }, "education": ["学校", null] }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("空值");
    }

    @Test
    void fromParsedSnapshotValidatesLegacyCollectionsBeforeRawRepresentationShortCircuit() {
        assertThatThrownBy(() -> converter.fromParsedSnapshot("""
                {
                  "rawText": "完整原始内容",
                  "education": ["学校", null]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("空值");

        String tooManySkills = IntStream.range(0, 101)
                .mapToObj(index -> "\"技能" + index + "\"")
                .collect(Collectors.joining(","));
        assertThatThrownBy(() -> converter.fromParsedSnapshot(
                "{\"rawText\":\"完整原始内容\",\"skills\":[" + tooManySkills + "]}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出安全转换上限");
    }

    @Test
    void normalizeRejectsMalformedNullStructuralItems() {
        ResumeDocumentDTO malformed = validDocumentBuilder()
                .sections(List.of(sectionBuilder()
                        .entries(java.util.Collections.singletonList(null))
                        .build()))
                .build();

        assertThatThrownBy(() -> converter.normalize(malformed))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("条目格式不正确");
    }

    private ResumeDocumentDTO.ResumeDocumentDTOBuilder validDocumentBuilder() {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder().contacts(List.of()).build())
                .sections(List.of());
    }

    private ResumeDocumentSectionDTO.ResumeDocumentSectionDTOBuilder sectionBuilder() {
        return ResumeDocumentSectionDTO.builder()
                .kind("CUSTOM")
                .title("")
                .entries(List.of());
    }

    private ResumeDocumentEntryDTO.ResumeDocumentEntryDTOBuilder entryBuilder() {
        return ResumeDocumentEntryDTO.builder().bullets(List.of());
    }

    private void assertIdsUniqueAndPresent(ResumeDocumentDTO document) {
        List<String> ids = new ArrayList<>();
        document.getBasics().getContacts().forEach(contact -> ids.add(contact.getId()));
        for (ResumeDocumentSectionDTO section : document.getSections()) {
            ids.add(section.getId());
            for (ResumeDocumentEntryDTO entry : section.getEntries()) {
                ids.add(entry.getId());
                entry.getBullets().forEach(bullet -> ids.add(bullet.getId()));
            }
        }
        assertThat(ids).doesNotContainNull().doesNotHaveDuplicates();
    }
}
