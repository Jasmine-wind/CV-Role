package com.winter.airesumeoptimizer.module.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * RESUME_DOCUMENT_V1 语义文档归一化与 generic V1 历史文档只读升级的契约测试。
 * V1 解析快照 → canonical 文档的投影测试位于解析模块的 canonical 构建器测试。
 */
class ResumeDocumentConverterImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumeDocumentConverterImpl converter = new ResumeDocumentConverterImpl(objectMapper);

    @Test
    void normalizeShouldRejectNullOrWrongSchemaVersion() {
        assertThatThrownBy(() -> converter.normalize(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历内容不能为空");

        ResumeDocumentDTO legacy = validDocument();
        legacy.setSchemaVersion("RESUME_DOCUMENT_V9");
        assertThatThrownBy(() -> converter.normalize(legacy))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的简历内容格式");
    }

    @Test
    void normalizeShouldKeepGivenIdsAndFillMissingOnes() {
        ResumeDocumentDTO document = validDocument();
        document.getSections().get(0).getEntries().get(0).getBullets().get(0).setId(null);

        ResumeDocumentDTO normalized = converter.normalize(document);

        assertThat(normalized.getSections().get(0).getId()).isEqualTo("s-1");
        assertThat(normalized.getSections().get(0).getEntries().get(0).getId()).isEqualTo("s-1-e-1");
        assertThat(normalized.getSections().get(0).getEntries().get(0).getBullets().get(0).getId()).isNotBlank();
    }

    @Test
    void normalizeShouldCopyFrozenOccurrenceGeometryWithoutAliasingTheInput() {
        ResumeDocumentDTO document = validDocument();
        ResumeSourceRefDTO sourceRef = ResumeSourceRefDTO.builder()
                .text("原文")
                .sourceOccurrenceIds(List.of("occ-1"))
                .page(2).x(10.0).y(20.0).width(100.0).height(12.0)
                .build();
        document.setSourceOccurrenceRefs(Map.of("occ-1", sourceRef));

        ResumeDocumentDTO normalized = converter.normalize(document);
        sourceRef.setX(999.0);

        assertThat(normalized.getSourceOccurrenceRefs().get("occ-1").getX()).isEqualTo(10.0);
        assertThat(normalized.getSourceOccurrenceRefs().get("occ-1").getPage()).isEqualTo(2);
    }

    @Test
    void normalizeWorkspaceSaveShouldUseOnlyFrozenProvenance() {
        ResumeDocumentDTO frozen = documentWithProvenance();
        ResumeDocumentDTO current = converter.normalize(frozen);
        ResumeDocumentDTO submitted = converter.normalize(frozen);
        submitted.setSourceOccurrenceIds(List.of("forged-root"));
        submitted.setSourceOccurrenceTexts(Map.of("forged-root", "伪造原文"));
        ResumeDocumentBulletDTO bullet = submitted.getSections().get(0).getEntries().get(0).getBullets().get(0);
        bullet.setText("编辑后的职责");
        bullet.setSourceOccurrenceIds(List.of("forged-child"));
        bullet.setSourceRef(sourceRef("forged-child", "伪造原文"));

        ResumeDocumentDTO saved = converter.normalizeWorkspaceSave(submitted, current, frozen);

        assertThat(saved.getSourceOccurrenceIds()).containsExactly("occ-root", "occ-bullet");
        assertThat(saved.getSourceOccurrenceTexts()).containsEntry("occ-bullet", "负责订单服务开发");
        ResumeDocumentBulletDTO savedBullet = saved.getSections().get(0).getEntries().get(0).getBullets().get(0);
        assertThat(savedBullet.getText()).isEqualTo("编辑后的职责");
        assertThat(savedBullet.getSourceOccurrenceIds()).containsExactly("occ-bullet");
        assertThat(savedBullet.getSourceRef().getSourceOccurrenceIds()).containsExactly("occ-bullet");
    }

    @Test
    void normalizeWorkspaceSaveShouldLeaveNewNodesUnreferenced() {
        ResumeDocumentDTO frozen = documentWithProvenance();
        ResumeDocumentDTO submitted = converter.normalize(frozen);
        submitted.getSections().get(0).getEntries().get(0).getBullets().add(
                ResumeDocumentBulletDTO.builder()
                        .id("new-bullet")
                        .text("用户新增内容")
                        .sourceOccurrenceIds(List.of("occ-bullet"))
                        .sourceRef(sourceRef("occ-bullet", "负责订单服务开发"))
                        .build());

        ResumeDocumentDTO saved = converter.normalizeWorkspaceSave(submitted, frozen, frozen);

        ResumeDocumentBulletDTO added = saved.getSections().get(0).getEntries().get(0).getBullets().get(1);
        assertThat(added.getSourceOccurrenceIds()).isNullOrEmpty();
        assertThat(added.getSourceRef()).isNull();
    }

    @Test
    void normalizeWorkspaceSaveShouldRejectBulletReparenting() {
        ResumeDocumentDTO frozen = documentWithProvenance();
        ResumeDocumentEntryDTO second = ResumeDocumentEntryDTO.builder()
                .id("s-1-e-2").organization("另一家公司")
                .bullets(new ArrayList<>()).build();
        frozen.getSections().get(0).getEntries().add(second);
        ResumeDocumentDTO submitted = converter.normalize(frozen);
        ResumeDocumentBulletDTO moved = submitted.getSections().get(0).getEntries().get(0).getBullets().remove(0);
        submitted.getSections().get(0).getEntries().get(1).getBullets().add(moved);

        assertThatThrownBy(() -> converter.normalizeWorkspaceSave(submitted, frozen, frozen))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能改变原有归属");
    }

    @Test
    void normalizeWorkspaceSaveShouldRejectDeletedFrozenIdRecreatedUnderDifferentParent() {
        ResumeDocumentDTO frozen = documentWithProvenance();
        ResumeDocumentDTO current = converter.normalize(frozen);
        current.getSections().get(0).getEntries().get(0).getBullets().clear();
        current.getSections().get(0).getEntries().add(ResumeDocumentEntryDTO.builder()
                .id("new-entry")
                .organization("用户新增项目")
                .bullets(new ArrayList<>())
                .build());
        ResumeDocumentDTO submitted = converter.normalize(current);
        submitted.getSections().get(0).getEntries().get(1).getBullets().add(
                ResumeDocumentBulletDTO.builder()
                        .id("s-1-e-1-b-1")
                        .text("尝试复用已删除来源 ID")
                        .build());

        assertThatThrownBy(() -> converter.normalizeWorkspaceSave(submitted, current, frozen))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已删除的来源节点不能复用原 ID");
    }

    @Test
    void normalizeWorkspaceSaveShouldRejectDeletedFrozenIdRecreatedAsDifferentNodeType() {
        ResumeDocumentDTO frozen = documentWithProvenance();
        ResumeDocumentDTO current = converter.normalize(frozen);
        current.getSections().get(0).getEntries().get(0).getBullets().clear();
        ResumeDocumentDTO submitted = converter.normalize(current);
        submitted.getBasics().getContacts().add(ResumeDocumentContactDTO.builder()
                .id("s-1-e-1-b-1")
                .type("EMAIL")
                .label("邮箱")
                .value("new@example.test")
                .build());

        assertThatThrownBy(() -> converter.normalizeWorkspaceSave(submitted, current, frozen))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已删除的来源节点不能复用原 ID");
    }

    @Test
    void normalizeWorkspaceSaveShouldRejectWhitespaceDisguisedFrozenIdResurrection() {
        ResumeDocumentDTO frozen = documentWithProvenance();
        ResumeDocumentDTO current = converter.normalize(frozen);
        current.getSections().get(0).getEntries().get(0).getBullets().clear();
        ResumeDocumentDTO submitted = converter.normalize(current);
        submitted.getSections().get(0).getEntries().get(0).getBullets().add(
                ResumeDocumentBulletDTO.builder()
                        .id(" s-1-e-1-b-1 ")
                        .text("尝试用空白绕过冻结 ID 检查")
                        .build());

        assertThatThrownBy(() -> converter.normalizeWorkspaceSave(submitted, current, frozen))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("节点 ID 格式不正确");
    }

    @Test
    void normalizeWorkspaceSaveShouldKeepGenuineNewIdAcrossRepeatedSavesWithoutProvenance() {
        ResumeDocumentDTO frozen = documentWithProvenance();
        ResumeDocumentDTO firstSubmission = converter.normalize(frozen);
        firstSubmission.getSections().get(0).getEntries().get(0).getBullets().add(
                ResumeDocumentBulletDTO.builder()
                        .id("new-bullet")
                        .text("用户新增内容")
                        .sourceOccurrenceIds(List.of("occ-bullet"))
                        .sourceRef(sourceRef("occ-bullet", "伪造来源"))
                        .build());
        ResumeDocumentDTO firstSaved = converter.normalizeWorkspaceSave(firstSubmission, frozen, frozen);
        ResumeDocumentDTO secondSubmission = converter.normalize(firstSaved);
        ResumeDocumentBulletDTO newBullet = secondSubmission.getSections().get(0)
                .getEntries().get(0).getBullets().get(1);
        newBullet.setText("用户再次编辑新增内容");
        newBullet.setSourceOccurrenceIds(List.of("occ-bullet"));
        newBullet.setSourceRef(sourceRef("occ-bullet", "再次伪造来源"));

        ResumeDocumentDTO secondSaved = converter.normalizeWorkspaceSave(secondSubmission, firstSaved, frozen);

        ResumeDocumentBulletDTO repeatedlySaved = secondSaved.getSections().get(0)
                .getEntries().get(0).getBullets().get(1);
        assertThat(repeatedlySaved.getId()).isEqualTo("new-bullet");
        assertThat(repeatedlySaved.getText()).isEqualTo("用户再次编辑新增内容");
        assertThat(repeatedlySaved.getSourceOccurrenceIds()).isNullOrEmpty();
        assertThat(repeatedlySaved.getSourceRef()).isNull();
    }

    @Test
    void normalizeShouldRejectDuplicateIds() {
        ResumeDocumentDTO document = validDocument();
        document.getBasics().getContacts().get(0).setId("dup");
        document.getSections().get(0).setId("dup");

        assertThatThrownBy(() -> converter.normalize(document))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ID 重复");
    }

    @Test
    void normalizeShouldDeriveContactLabelAndTypeWhenMissing() {
        ResumeDocumentDTO document = validDocument();
        document.getBasics().getContacts().get(0).setType(null);
        document.getBasics().getContacts().get(0).setLabel(null);

        ResumeDocumentDTO normalized = converter.normalize(document);

        assertThat(normalized.getBasics().getContacts().get(0).getType()).isEqualTo("OTHER");
        assertThat(normalized.getBasics().getContacts().get(0).getLabel()).isEqualTo("其他");
    }

    @Test
    void normalizeShouldCollapseDuplicateContactRows() {
        ResumeDocumentDTO document = validDocument();
        document.getBasics().setContacts(new ArrayList<>(List.of(
                ResumeDocumentContactDTO.builder()
                        .id("c-1").type("PHONE").label("电话").value("13800000000").build(),
                ResumeDocumentContactDTO.builder()
                        .id("c-2").type("PHONE").label("手机号").value("13800000000").build(),
                ResumeDocumentContactDTO.builder()
                        .id("c-3").type("OTHER").label("其他").value("").build(),
                ResumeDocumentContactDTO.builder()
                        .id("c-4").type("OTHER").label("其他").value("  ").build())));

        ResumeDocumentDTO normalized = converter.normalize(document);

        assertThat(normalized.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getId)
                .containsExactly("c-1", "c-3");
    }

    @Test
    void normalizeShouldRejectUnknownContactType() {
        ResumeDocumentDTO document = validDocument();
        document.getBasics().getContacts().get(0).setType("FAX");

        assertThatThrownBy(() -> converter.normalize(document))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的联系方式类型");
    }

    @Test
    void normalizeShouldRejectUnknownSectionKindAndOverLimits() {
        ResumeDocumentDTO unknownKind = validDocument();
        unknownKind.getSections().get(0).setKind("MAGIC");
        assertThatThrownBy(() -> converter.normalize(unknownKind))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的简历章节类型");

        ResumeDocumentDTO overLimit = validDocument();
        overLimit.getSections().get(0).getEntries().get(0).getBullets().get(0).setText("x".repeat(4001));
        assertThatThrownBy(() -> converter.normalize(overLimit))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("要点内容超出编辑上限");
    }

    @Test
    void upgradeLegacyShouldRejectBlankOrUnknownSchema() {
        assertThatThrownBy(() -> converter.upgradeLegacyDocument(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历内容尚未就绪");

        assertThatThrownBy(() -> converter.upgradeLegacyDocument("{\"schemaVersion\":\"RESUME_DOCUMENT_V9\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的简历内容格式");
    }

    @Test
    void upgradeLegacyShouldPreferKnownContactLabelsOverNumericValueInference() {
        String legacy = """
                {
                  "schemaVersion": "RESUME_DOCUMENT_V1",
                  "basics": { "name": "张三", "contacts": [
                    { "label": "QQ", "value": "12345678" },
                    { "label": "微信", "value": "wxid_123456" },
                    { "label": "所在地", "value": "310000" }
                  ] },
                  "sections": []
                }
                """;

        ResumeDocumentDTO upgraded = converter.upgradeLegacyDocument(legacy);

        assertThat(upgraded.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getType)
                .containsExactly("QQ", "WECHAT", "LOCATION");
    }

    @Test
    void upgradeLegacyShouldTypeContactsAndExtractBasicsFields() {
        String legacy = """
                {
                  "schemaVersion": "RESUME_DOCUMENT_V1",
                  "basics": {
                    "name": "张三",
                    "contacts": [
                      { "id": "c-1", "label": "电话", "value": "13800000000" },
                      { "id": "c-2", "label": "邮箱", "value": "zhangsan@example.com" },
                      { "id": "c-3", "label": "求职意向", "value": "Java 后端" },
                      { "id": "c-4", "label": "最高学历", "value": "本科" },
                      { "id": "c-5", "label": "所在地", "value": "上海" },
                      { "id": "c-6", "label": "学历", "value": "13800000000" }
                    ]
                  },
                  "sections": []
                }
                """;

        ResumeDocumentDTO upgraded = converter.upgradeLegacyDocument(legacy);

        assertThat(upgraded.getSchemaVersion()).isEqualTo(ResumeDocumentDTO.SCHEMA_VERSION);
        assertThat(upgraded.getBasics().getJobIntention()).isEqualTo("Java 后端");
        assertThat(upgraded.getBasics().getHighestEducation()).isEqualTo("本科");
        assertThat(upgraded.getBasics().getContacts())
                .extracting(ResumeDocumentContactDTO::getType, ResumeDocumentContactDTO::getValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("PHONE", "13800000000"),
                        org.assertj.core.groups.Tuple.tuple("EMAIL", "zhangsan@example.com"),
                        org.assertj.core.groups.Tuple.tuple("LOCATION", "上海"),
                        org.assertj.core.groups.Tuple.tuple("OTHER", "13800000000"));
        assertThat(upgraded.getBasics().getHighestEducation()).isEqualTo("本科");
    }

    @Test
    void upgradeLegacyShouldDefaultHistoricalNullOptionalArrays() {
        String legacy = """
                {
                  "schemaVersion": "RESUME_DOCUMENT_V1",
                  "basics": { "name": "张三", "contacts": null },
                  "sections": [
                    { "kind": "PROJECT", "title": "项目经历", "entries": [
                      { "organization": "旧项目", "role": "负责人", "startDate": "2022", "endDate": "至今", "bullets": null }
                    ] }
                  ]
                }
                """;

        ResumeDocumentDTO upgraded = converter.upgradeLegacyDocument(legacy);

        assertThat(upgraded.getBasics().getContacts()).isEmpty();
        ResumeDocumentEntryDTO entry = upgraded.getSections().get(0).getEntries().get(0);
        assertThat(entry.getOrganization()).isEqualTo("旧项目");
        assertThat(entry.getRole()).isEqualTo("负责人");
        assertThat(entry.getStartDate()).isEqualTo("2022");
        assertThat(entry.getEndDate()).isEqualTo("至今");
        assertThat(entry.getBullets()).isEmpty();
    }

    @Test
    void upgradeLegacyShouldRejectNonTextualGenericScalarInsteadOfCoercingIt() {
        String legacy = """
                {
                  "schemaVersion": "RESUME_DOCUMENT_V1",
                  "basics": { "name": "张三", "contacts": [
                    { "label": "自定义", "value": 13800000000 }
                  ] },
                  "sections": null
                }
                """;

        assertThatThrownBy(() -> converter.upgradeLegacyDocument(legacy))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("格式不正确");
    }

    @Test
    void upgradeLegacyShouldRejectUnknownSectionKindInsteadOfRelabelingItCustom() {
        String legacy = "{\"schemaVersion\":\"RESUME_DOCUMENT_V1\",\"basics\":{\"name\":\"张三\",\"contacts\":[]},\"sections\":[{\"kind\":\"UNKNOWN\",\"title\":\"自定义内容\",\"entries\":[]}] }";

        assertThatThrownBy(() -> converter.upgradeLegacyDocument(legacy))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的简历章节类型");
    }

    @Test
    void upgradeLegacyShouldRejectNonArrayEntriesInsteadOfDroppingThem() {
        String legacy = "{\"schemaVersion\":\"RESUME_DOCUMENT_V1\",\"basics\":{\"name\":\"张三\",\"contacts\":[]},\"sections\":[{\"kind\":\"EXPERIENCE\",\"title\":\"工作经历\",\"entries\":{\"heading\":\"某公司\"}}]}";

        assertThatThrownBy(() -> converter.upgradeLegacyDocument(legacy))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("章节条目格式不正确");
    }

    @Test
    void upgradeLegacyShouldRejectDirtyShapeAndRequireReparse() {
        String dirty = "{\"schemaVersion\":\"RESUME_DOCUMENT_V1\",\"basics\":{\"name\":\"张三\",\"contacts\":[]},\"sections\":{}}";

        assertThatThrownBy(() -> converter.upgradeLegacyDocument(dirty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请重新解析");
    }

    @Test
    void upgradeLegacyShouldMapExperienceHeadingMetaAndSkillGroups() {
        String legacy = """
                {
                  "schemaVersion": "RESUME_DOCUMENT_V1",
                  "basics": { "name": "张三", "contacts": [] },
                  "sections": [
                    {
                      "id": "s-1", "kind": "EXPERIENCE", "title": "工作经历",
                      "entries": [
                        { "id": "s-1-e-1", "heading": "某科技有限公司", "meta": "2022.07 - 至今 · Java 后端",
                          "bullets": [ { "id": "s-1-e-1-b-1", "text": "负责订单服务" } ] }
                      ]
                    },
                    {
                      "id": "s-2", "kind": "SKILL", "title": "技能",
                      "entries": [
                        { "id": "s-2-e-1", "heading": null, "meta": null,
                          "bullets": [ { "id": "s-2-e-1-b-1", "text": "后端技术：Java、Spring Boot、MySQL" } ] }
                      ]
                    },
                    {
                      "id": "s-3", "kind": "EDUCATION", "title": "教育经历",
                      "entries": [
                        { "id": "s-3-e-1", "heading": "某大学", "meta": "2018.09 - 2022.06", "bullets": [] }
                      ]
                    }
                  ]
                }
                """;

        ResumeDocumentDTO upgraded = converter.upgradeLegacyDocument(legacy);

        ResumeDocumentEntryDTO experience = upgraded.getSections().get(0).getEntries().get(0);
        assertThat(experience.getOrganization()).isEqualTo("某科技有限公司");
        assertThat(experience.getStartDate()).isEqualTo("2022.07");
        assertThat(experience.getEndDate()).isEqualTo("至今");
        assertThat(experience.getRole()).isEqualTo("Java 后端");
        assertThat(experience.getBullets()).hasSize(1);

        ResumeDocumentEntryDTO skill = upgraded.getSections().get(1).getEntries().get(0);
        assertThat(skill.getGroup()).isEqualTo("后端技术");
        assertThat(skill.getSkillItems()).containsExactly("Java", "Spring Boot", "MySQL");
        assertThat(skill.getBullets()).isEmpty();

        ResumeDocumentEntryDTO education = upgraded.getSections().get(2).getEntries().get(0);
        assertThat(education.getSchool()).isEqualTo("某大学");
        assertThat(education.getStartDate()).isEqualTo("2018.09");
        assertThat(education.getEndDate()).isEqualTo("2022.06");
    }

    @Test
    void upgradeLegacyShouldProjectAchievementHeadingAndDateMeta() {
        String legacy = """
                {
                  "schemaVersion": "RESUME_DOCUMENT_V1",
                  "basics": { "name": "张三", "contacts": [] },
                  "sections": [
                    { "kind": "ACHIEVEMENT", "title": "荣誉奖项", "entries": [
                      { "heading": "校级一等奖", "meta": "2022.09 - 2022.12", "bullets": [] }
                    ] }
                  ]
                }
                """;

        ResumeDocumentEntryDTO upgraded = converter.upgradeLegacyDocument(legacy)
                .getSections().get(0).getEntries().get(0);

        assertThat(upgraded.getAwardTitle()).isEqualTo("校级一等奖");
        assertThat(upgraded.getAwardDate()).isEqualTo("2022.09 - 2022.12");
        assertThat(upgraded.getBullets()).isEmpty();
        assertThat(upgraded.getHeading()).isNull();
        assertThat(upgraded.getMeta()).isNull();
    }

    @Test
    void upgradeLegacyShouldPreserveUnprojectableEducationMetaOnlyOnce() {
        String legacy = """
                {
                  "schemaVersion": "RESUME_DOCUMENT_V1",
                  "basics": { "name": "张三", "contacts": [] },
                  "sections": [
                    { "kind": "EDUCATION", "title": "教育经历", "entries": [
                      { "heading": "某大学", "meta": "2018 - 2022 · 软件工程", "bullets": [] }
                    ] }
                  ]
                }
                """;

        ResumeDocumentEntryDTO upgraded = converter.upgradeLegacyDocument(legacy)
                .getSections().get(0).getEntries().get(0);

        assertThat(upgraded.getSchool()).isEqualTo("某大学");
        assertThat(upgraded.getStartDate()).isEqualTo("2018");
        assertThat(upgraded.getEndDate()).isEqualTo("2022");
        assertThat(upgraded.getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("2018 - 2022 · 软件工程");
    }

    @Test
    void upgradeLegacyShouldKeepHeadingOnlyEntriesAsBullets() {
        String legacy = """
                {
                  "schemaVersion": "RESUME_DOCUMENT_V1",
                  "basics": { "name": "张三", "contacts": [] },
                  "sections": [
                    {
                      "id": "s-1", "kind": "CERTIFICATE", "title": "证书",
                      "entries": [ { "id": "s-1-e-1", "heading": "CET-6", "meta": null, "bullets": [] } ]
                    }
                  ]
                }
                """;

        ResumeDocumentDTO upgraded = converter.upgradeLegacyDocument(legacy);

        assertThat(upgraded.getSections().get(0).getEntries().get(0).getBullets())
                .extracting(ResumeDocumentBulletDTO::getText)
                .containsExactly("CET-6");
    }

    @Test
    void upgradeLegacyShouldPassThroughValidV1SemanticDocument() throws Exception {
        ResumeDocumentDTO document = validDocument();
        String json = objectMapper.writeValueAsString(converter.normalize(document));

        ResumeDocumentDTO reread = converter.upgradeLegacyDocument(json);

        assertThat(reread).usingRecursiveComparison().isEqualTo(converter.normalize(document));
    }

    private ResumeDocumentDTO documentWithProvenance() {
        ResumeDocumentDTO document = validDocument();
        document.setSourceOccurrenceIds(List.of("occ-root", "occ-bullet"));
        document.setSourceOccurrenceTexts(Map.of(
                "occ-root", "工作经历",
                "occ-bullet", "负责订单服务开发"));
        document.setSourceOccurrencePrimaryIds(Map.of(
                "occ-root", "occ-root",
                "occ-bullet", "occ-bullet"));
        document.setSourceRef(sourceRef("occ-root", "工作经历"));
        ResumeDocumentSectionDTO section = document.getSections().get(0);
        section.setSourceOccurrenceIds(List.of("occ-root"));
        section.setSourceRef(sourceRef("occ-root", "工作经历"));
        ResumeDocumentEntryDTO entry = section.getEntries().get(0);
        entry.setSourceOccurrenceIds(List.of("occ-bullet"));
        entry.setSourceRef(sourceRef("occ-bullet", "负责订单服务开发"));
        ResumeDocumentBulletDTO bullet = entry.getBullets().get(0);
        bullet.setSourceOccurrenceIds(List.of("occ-bullet"));
        bullet.setSourceRef(sourceRef("occ-bullet", "负责订单服务开发"));
        return document;
    }

    private ResumeSourceRefDTO sourceRef(String occurrenceId, String text) {
        return ResumeSourceRefDTO.builder()
                .text(text)
                .sourceOccurrenceIds(List.of(occurrenceId))
                .build();
    }

    private ResumeDocumentDTO validDocument() {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("张三")
                        .contacts(new ArrayList<>(List.of(ResumeDocumentContactDTO.builder()
                                .id("c-1")
                                .type("PHONE")
                                .label("电话")
                                .value("13800000000")
                                .build())))
                        .build())
                .sections(new ArrayList<>(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1")
                        .kind("EXPERIENCE")
                        .title("工作经历")
                        .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                                .id("s-1-e-1")
                                .organization("某科技有限公司")
                                .role("Java 后端工程师")
                                .startDate("2022.07")
                                .endDate("至今")
                                .bullets(new ArrayList<>(List.of(ResumeDocumentBulletDTO.builder()
                                        .id("s-1-e-1-b-1")
                                        .text("负责订单服务开发")
                                        .build())))
                                .build())))
                        .build())))
                .build();
    }
}
