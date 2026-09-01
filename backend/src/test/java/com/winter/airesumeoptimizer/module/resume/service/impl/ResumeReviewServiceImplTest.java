package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeReviewResolveRequestDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeReviewVO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.service.impl.ResumeDocumentConverterImpl;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 用户确认边界测试：确认对象是 canonical 字段与候选项；
 * 全部候选项处理完毕后质量状态回到 READY。
 */
class ResumeReviewServiceImplTest {

    static {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Resume.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeParseResult.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeVersion.class);
    }

    private static final Long USER_ID = 1L;
    private static final Long RESUME_ID = 10L;

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);
    private final ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumeReviewServiceImpl service = new ResumeReviewServiceImpl(
            resumeMapper,
            resumeParseResultMapper,
            resumeVersionMapper,
            new ResumeDocumentConverterImpl(objectMapper),
            new ResumeDocumentQualityValidatorImpl(),
            objectMapper);

    private Resume resume;
    private ResumeParseResult parseResult;
    private ResumeDocumentDTO document;
    private ResumeVersion source;

    @BeforeEach
    void setUp() throws Exception {
        resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);

        // canonical 文档缺少邮箱 → 存在阻断项；另有一个联系方式候选等待确认。
        document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO.builder()
                        .name("李华")
                        .contacts(new java.util.ArrayList<>())
                        .build())
                .sections(new java.util.ArrayList<>(List.of(
                        com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO.builder()
                                .id("s-1")
                                .kind("EXPERIENCE")
                                .title("工作经历")
                                .entries(new java.util.ArrayList<>(List.of(
                                        com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO.builder()
                                                .id("s-1-e-1")
                                                .organization("某科技有限公司")
                                                .bullets(new java.util.ArrayList<>(List.of(
                                                        com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO.builder()
                                                                .id("s-1-e-1-b-1")
                                                                .text("负责订单服务开发，表现良好")
                                                                .build())))
                                                .build())))
                                .build(),
                        com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO.builder()
                                .id("s-2")
                                .kind("EDUCATION")
                                .title("教育经历")
                                .entries(new java.util.ArrayList<>(List.of(
                                        com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO.builder()
                                                .id("s-2-e-1")
                                                .school("某大学")
                                                .bullets(new java.util.ArrayList<>())
                                                .build())))
                                .build(),
                        com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO.builder()
                                .id("s-3")
                                .kind("SKILL")
                                .title("技能")
                                .entries(new java.util.ArrayList<>(List.of(
                                        com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO.builder()
                                                .id("s-3-e-1")
                                                .skillItems(new java.util.ArrayList<>(List.of("Java")))
                                                .bullets(new java.util.ArrayList<>())
                                                .build())))
                                .build())))
                .build();

        List<ResumeUnresolvedItemDTO> unresolved = List.of(ResumeUnresolvedItemDTO.builder()
                .id("u-1")
                .kind(ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE)
                .canonicalDraft("{\"type\":\"EMAIL\",\"label\":\"邮箱\",\"value\":\"lihua@example.com\"}")
                .reason("该邮箱格式无法确认，请核对后接受或删除")
                .build());

        parseResult = new ResumeParseResult();
        parseResult.setId(5L);
        parseResult.setResumeId(RESUME_ID);
        parseResult.setParseStatus("SUCCESS");
        parseResult.setQualityStatus("NEEDS_REVIEW");
        parseResult.setCanonicalSourceVersionId(40L);
        parseResult.setUnresolvedItems(objectMapper.writeValueAsString(unresolved));
        parseResult.setQualityIssues("[]");

        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeParseResultMapper.selectOne(any())).thenReturn(parseResult);
        source = new ResumeVersion();
        source.setId(40L);
        source.setUserId(USER_ID);
        source.setResumeId(RESUME_ID);
        source.setVersionType("SOURCE");
        source.setContentStatus("READY");
        source.setStructuredContent(objectMapper.writeValueAsString(document));
        when(resumeVersionMapper.selectOne(any())).thenReturn(source);
        when(resumeVersionMapper.update(isNull(), any())).thenReturn(1);
        when(resumeParseResultMapper.update(isNull(), any())).thenReturn(1);
    }

    @Test
    void getReviewShouldExposeCanonicalLayerOnly() {
        ResumeReviewVO review = service.getReview(USER_ID, RESUME_ID);

        assertThat(review.getQualityStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(review.getCanonicalDocument()).contains("某科技有限公司");
        assertThat(review.getUnresolvedItems()).contains("u-1");
    }

    @Test
    void acceptContactCandidateShouldClearReviewWhenNoUnresolvedRemain() throws Exception {
        ResumeReviewVO review = service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-1")
                .action("ACCEPT")
                .build());

        assertThat(review.getQualityStatus()).isEqualTo("READY");
        assertThat(review.getUnresolvedItems())
                .isEqualTo(objectMapper.writeValueAsString(List.of()));
        ResumeDocumentDTO updated = objectMapper.readValue(review.getCanonicalDocument(), ResumeDocumentDTO.class);
        assertThat(updated.getBasics().getContacts())
                .extracting(
                        com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO::getType,
                        com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO::getValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EMAIL", "lihua@example.com"));
    }

    @Test
    void acceptCandidateWithEditedValueShouldUseTheEditedValue() throws Exception {
        ResumeReviewVO review = service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-1")
                .action("ACCEPT")
                .contactValue("lihua.fixed@example.com")
                .build());

        ResumeDocumentDTO updated = objectMapper.readValue(review.getCanonicalDocument(), ResumeDocumentDTO.class);
        assertThat(updated.getBasics().getContacts().get(0).getValue()).isEqualTo("lihua.fixed@example.com");
        assertThat(review.getQualityStatus()).isEqualTo("READY");
    }

    @Test
    void acceptingNonReachableContactCandidateKeepsARequiredFollowup() throws Exception {
        ResumeReviewVO review = service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-1")
                .action("ACCEPT")
                .contactType("LOCATION")
                .contactValue("上海")
                .build());

        assertThat(review.getQualityStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(review.getUnresolvedItems()).contains(ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE);
    }

    @Test
    void acceptEntryCandidateWithoutTitleIsRejectedAndRemainsActionable() throws Exception {
        parseResult.setUnresolvedItems(objectMapper.writeValueAsString(List.of(
                ResumeUnresolvedItemDTO.builder()
                        .id("u-entry")
                        .kind(ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE)
                        .canonicalDraft("{\"kind\":\"EXPERIENCE\",\"organization\":null,"
                                + "\"bullets\":[{\"text\":\"负责支付服务\"}]}")
                        .reason("缺少公司名")
                        .build())));
        source.setStructuredContent(objectMapper.writeValueAsString(document));

        assertThatThrownBy(() -> service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-entry")
                .action("ACCEPT")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("补充公司或项目名");
    }

    @Test
    void acceptEntryCandidateCanEditMissingTitleBeforeAddingToSemanticSection() throws Exception {
        ResumeUnresolvedItemDTO candidate = ResumeUnresolvedItemDTO.builder()
                .id("u-entry")
                .kind(ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE)
                .canonicalDraft("{\"kind\":\"EXPERIENCE\",\"organization\":null,\"role\":\"后端工程师\","
                        + "\"bullets\":[{\"text\":\"负责支付服务\"}]}")
                .reason("缺少公司名")
                .build();
        parseResult.setUnresolvedItems(objectMapper.writeValueAsString(List.of(candidate)));
        source.setStructuredContent(objectMapper.writeValueAsString(document));

        ResumeReviewVO review = service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-entry")
                .action("ACCEPT")
                .entry(ResumeDocumentEntryDTO.builder()
                        .organization("新公司")
                        .role("后端工程师")
                        .build())
                .build());

        ResumeDocumentDTO updated = objectMapper.readValue(review.getCanonicalDocument(), ResumeDocumentDTO.class);
        assertThat(updated.getSections().get(0).getEntries())
                .anySatisfy(entry -> assertThat(entry.getOrganization()).isEqualTo("新公司"));
    }

    @Test
    void acceptRequiredContactCandidateCanRepairMissingContact() throws Exception {
        parseResult.setUnresolvedItems(objectMapper.writeValueAsString(List.of(
                ResumeUnresolvedItemDTO.builder()
                        .id("u-contact")
                        .kind(ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE)
                        .canonicalDraft("{\"type\":\"PHONE\",\"label\":\"电话\",\"value\":\"\"}")
                        .reason("缺少可用电话或邮箱")
                        .build())));
        source.setStructuredContent(objectMapper.writeValueAsString(document));

        ResumeReviewVO review = service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-contact")
                .action("ACCEPT")
                .contactType("EMAIL")
                .contactValue("lihua@example.com")
                .build());

        ResumeDocumentDTO updated = objectMapper.readValue(review.getCanonicalDocument(), ResumeDocumentDTO.class);
        assertThat(updated.getBasics().getContacts())
                .anySatisfy(contact -> assertThat(contact.getValue()).isEqualTo("lihua@example.com"));
    }

    @Test
    void acceptNameCandidateCanRepairMissingName() throws Exception {
        document.getBasics().setName(null);
        source.setStructuredContent(objectMapper.writeValueAsString(document));
        ResumeUnresolvedItemDTO candidate = ResumeUnresolvedItemDTO.builder()
                .id("u-name")
                .kind(ResumeUnresolvedItemDTO.KIND_NAME_CANDIDATE)
                .canonicalDraft("{\"text\":\"\"}")
                .reason("请确认姓名")
                .build();
        parseResult.setUnresolvedItems(objectMapper.writeValueAsString(List.of(candidate)));

        ResumeReviewVO review = service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-name")
                .action("ACCEPT")
                .name("李明")
                .build());

        ResumeDocumentDTO updated = objectMapper.readValue(review.getCanonicalDocument(), ResumeDocumentDTO.class);
        assertThat(updated.getBasics().getName()).isEqualTo("李明");
    }

    @Test
    void deleteLastContactCandidateShouldKeepReviewActionable() {
        assertThatThrownBy(() -> service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-1")
                .action("DELETE")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除最后的联系方式候选");
    }

    @Test
    void resolveShouldRejectUnknownItemOrInvalidPayload() {
        assertThatThrownBy(() -> service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-404")
                .action("ACCEPT")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待确认项不存在");

        assertThatThrownBy(() -> service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-1")
                .action("ACCEPT")
                .contactType("EMAIL")
                .contactValue("not-an-email")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱格式不正确");

        assertThatThrownBy(() -> service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-1")
                .action("IGNORE")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的处理动作");
    }

    @Test
    void resolveShouldRejectStaleConcurrentReviewState() {
        when(resumeParseResultMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resolve(USER_ID, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-1")
                .action("ACCEPT")
                .build()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(409));
    }

    @Test
    void resolveShouldRejectCrossUserResume() {
        when(resumeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.resolve(2L, RESUME_ID, ResumeReviewResolveRequestDTO.builder()
                .itemId("u-1")
                .action("ACCEPT")
                .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历不存在");
    }
}
