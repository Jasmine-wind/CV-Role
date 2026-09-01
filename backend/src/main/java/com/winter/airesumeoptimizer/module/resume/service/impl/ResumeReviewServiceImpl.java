package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeReviewResolveRequestDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.enums.ResumeQualityStatus;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDocumentQualityValidator;
import com.winter.airesumeoptimizer.module.resume.service.ResumeReviewService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeReviewVO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentContactType;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentSectionKind;
import com.winter.airesumeoptimizer.module.workspace.service.ResumeDocumentConverter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 确认服务实现：候选项处理全部走 canonical 文档的普通编辑语义。
 * 每次处理都重新归一化并重新裁决质量状态；未决项清零且无阻断项时回到 READY。
 */
@Service
public class ResumeReviewServiceImpl implements ResumeReviewService {

    private static final String FALLBACK_SECTION_TITLE = "补充内容";

    private final ResumeMapper resumeMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeDocumentConverter resumeDocumentConverter;
    private final ResumeDocumentQualityValidator resumeDocumentQualityValidator;
    private final ObjectMapper objectMapper;

    public ResumeReviewServiceImpl(
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            ResumeVersionMapper resumeVersionMapper,
            ResumeDocumentConverter resumeDocumentConverter,
            ResumeDocumentQualityValidator resumeDocumentQualityValidator,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.resumeDocumentConverter = resumeDocumentConverter;
        this.resumeDocumentQualityValidator = resumeDocumentQualityValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResumeReviewVO getReview(Long userId, Long resumeId) {
        ResumeParseResult parseResult = getOwnedParseResult(userId, resumeId);
        return toVO(userId, parseResult);
    }

    @Override
    @Transactional
    public ResumeReviewVO resolve(Long userId, Long resumeId, ResumeReviewResolveRequestDTO request) {
        if (request == null || request.getItemId() == null || request.getItemId().isBlank()) {
            throw new BusinessException(400, "缺少候选项 ID");
        }
        String action = request.getAction() == null ? "" : request.getAction().strip().toUpperCase();
        if (!ResumeReviewResolveRequestDTO.ACTION_ACCEPT.equals(action)
                && !ResumeReviewResolveRequestDTO.ACTION_DELETE.equals(action)) {
            throw new BusinessException(400, "不支持的处理动作");
        }
        ResumeParseResult parseResult = getOwnedParseResult(userId, resumeId);
        ResumeVersion canonicalSource = getCanonicalSource(userId, parseResult);
        if (canonicalSource == null || canonicalSource.getStructuredContent() == null
                || canonicalSource.getStructuredContent().isBlank()) {
            throw new BusinessException(409, "简历内容尚未就绪，请先完成解析");
        }
        String canonicalDocument = canonicalSource.getStructuredContent();

        ResumeDocumentDTO document = readDocument(canonicalDocument);
        List<ResumeUnresolvedItemDTO> items = readUnresolvedItems(parseResult.getUnresolvedItems());
        String expectedUnresolvedItems = parseResult.getUnresolvedItems();
        ResumeUnresolvedItemDTO item = items.stream()
                .filter(candidate -> request.getItemId().equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "待确认项不存在或已处理"));
        if (ResumeReviewResolveRequestDTO.ACTION_DELETE.equals(action)
                && ResumeUnresolvedItemDTO.KIND_NAME_CANDIDATE.equals(item.getKind())
                && (document.getBasics() == null
                || document.getBasics().getName() == null
                || document.getBasics().getName().isBlank())) {
            throw new BusinessException(400, "姓名是必填信息，请填写后接受");
        }
        if (ResumeReviewResolveRequestDTO.ACTION_DELETE.equals(action)
                && ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE.equals(item.getKind())) {
            throw new BusinessException(400, "至少需要补录一个电话或邮箱，不能删除该必填项");
        }
        if (ResumeReviewResolveRequestDTO.ACTION_DELETE.equals(action)
                && ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE.equals(item.getKind())
                && !hasReachableContact(document)
                && items.stream()
                .filter(candidate -> candidate != item)
                .noneMatch(candidate -> ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE.equals(candidate.getKind())
                        || ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE.equals(candidate.getKind()))) {
            throw new BusinessException(400, "请先保留或修改一个电话/邮箱候选，不能删除最后的联系方式候选");
        }

        if (ResumeReviewResolveRequestDTO.ACTION_ACCEPT.equals(action)) {
            applyNameEdit(document, request.getName());
            acceptItem(document, item, request);
        }
        items.remove(item);
        if (ResumeReviewResolveRequestDTO.ACTION_ACCEPT.equals(action)
                && ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE.equals(item.getKind())) {
            ensureRequiredContactCandidate(document, items);
        }

        ResumeDocumentDTO normalized = resumeDocumentConverter.normalize(document);
        ResumeDocumentQualityValidator.ValidationResult validation =
                resumeDocumentQualityValidator.validate(normalized, items);

        String serializedDocument = serialize(normalized);
        String serializedItems = serialize(items);
        String serializedIssues = serialize(validation.issues());
        UpdateWrapper<ResumeParseResult> update = new UpdateWrapper<ResumeParseResult>()
                .eq("id", parseResult.getId())
                .eq("resume_id", parseResult.getResumeId())
                .in("quality_status",
                        ResumeQualityStatus.QUALITY_NEEDS_REVIEW,
                        ResumeQualityStatus.QUALITY_READY);
        if (expectedUnresolvedItems == null) {
            update.isNull("unresolved_items");
        } else {
            update.eq("unresolved_items", expectedUnresolvedItems);
        }
        if (ResumeReviewResolveRequestDTO.ACTION_ACCEPT.equals(action)
                && !serializedDocument.equals(canonicalDocument)) {
            int sourceRows = resumeVersionMapper.update(null, new UpdateWrapper<ResumeVersion>()
                    .eq("id", canonicalSource.getId())
                    .eq("user_id", userId)
                    .eq("resume_id", resumeId)
                    .eq("version_type", "SOURCE")
                    .isNull("source_version_id")
                    .isNull("job_target_id")
                    .eq("content_status", "READY")
                    .eq("content_revision", 0L)
                    .eq("structured_content", canonicalDocument)
                    .set("structured_content", serializedDocument)
                    .set("updated_at", LocalDateTime.now()));
            if (sourceRows != 1) {
                throw new BusinessException(409, "简历内容正在更新，请刷新后重试");
            }
            canonicalSource.setStructuredContent(serializedDocument);
        }
        int rows = resumeParseResultMapper.update(null, update
                .set("unresolved_items", serializedItems)
                .set("quality_issues", serializedIssues)
                .set("quality_status", validation.qualityStatus())
                .set("updated_at", LocalDateTime.now()));
        if (rows != 1) {
            throw new BusinessException(409, "简历内容正在更新，请刷新后重试");
        }
        parseResult.setUnresolvedItems(serializedItems);
        parseResult.setQualityIssues(serializedIssues);
        parseResult.setQualityStatus(validation.qualityStatus());
        return toVO(userId, parseResult);
    }

    private void applyNameEdit(ResumeDocumentDTO document, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (document.getBasics() == null) {
            throw new BusinessException(500, "简历基础信息缺失");
        }
        document.getBasics().setName(name.strip());
    }

    private void acceptItem(
            ResumeDocumentDTO document, ResumeUnresolvedItemDTO item, ResumeReviewResolveRequestDTO request) {
        switch (item.getKind() == null ? "" : item.getKind()) {
            case ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE ->
                    acceptContact(document, item, request);
            case ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE ->
                    acceptRequiredContact(document, item, request);
            case ResumeUnresolvedItemDTO.KIND_NAME_CANDIDATE ->
                    acceptNameCandidate(document, item, request);
            case ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE ->
                    acceptEntry(document, item, request);
            case ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT ->
                    acceptFragment(document, item, request);
            default -> throw new BusinessException(400, "不支持的候选项类型");
        }
    }

    private void ensureRequiredContactCandidate(
            ResumeDocumentDTO document, List<ResumeUnresolvedItemDTO> items) {
        boolean hasContactCandidate = items.stream()
                .anyMatch(candidate -> ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE.equals(candidate.getKind())
                        || ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE.equals(candidate.getKind()));
        if (hasReachableContact(document) || hasContactCandidate) {
            return;
        }
        items.add(ResumeUnresolvedItemDTO.builder()
                .id(nextUnresolvedId(items))
                .kind(ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE)
                .canonicalDraft("{\"type\":\"PHONE\",\"label\":\"电话\",\"value\":\"\"}")
                .reason("缺少可用电话或邮箱，请补录后接受")
                .build());
    }

    private String nextUnresolvedId(List<ResumeUnresolvedItemDTO> items) {
        int max = 0;
        for (ResumeUnresolvedItemDTO candidate : items) {
            if (candidate == null || candidate.getId() == null || !candidate.getId().startsWith("u-")) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(candidate.getId().substring(2)));
            } catch (NumberFormatException ignored) {
                // Keep the generated ID deterministic even when an older sidecar used a custom ID.
            }
        }
        return "u-" + (max + 1);
    }

    private boolean hasReachableContact(ResumeDocumentDTO document) {
        if (document == null || document.getBasics() == null || document.getBasics().getContacts() == null) {
            return false;
        }
        for (ResumeDocumentContactDTO contact : document.getBasics().getContacts()) {
            if (contact == null || contact.getValue() == null || contact.getValue().isBlank()) {
                continue;
            }
            ResumeDocumentContactType type = ResumeDocumentContactType.fromValue(contact.getType());
            if ((type == ResumeDocumentContactType.PHONE
                    && ResumeDocumentQualityValidator.isValidPhone(contact.getValue()))
                    || (type == ResumeDocumentContactType.EMAIL
                    && ResumeDocumentQualityValidator.isValidEmail(contact.getValue()))) {
                return true;
            }
        }
        return false;
    }

    private void acceptRequiredContact(
            ResumeDocumentDTO document,
            ResumeUnresolvedItemDTO item,
            ResumeReviewResolveRequestDTO request) {
        String requestedType = request.getContactType();
        ResumeDocumentContactType type = ResumeDocumentContactType.fromValue(
                requestedType == null || requestedType.isBlank() ? "PHONE" : requestedType);
        if (type != ResumeDocumentContactType.PHONE && type != ResumeDocumentContactType.EMAIL) {
            throw new BusinessException(400, "必填联系方式只能是电话或邮箱");
        }
        acceptContact(document, item, request);
    }

    private void acceptContact(
            ResumeDocumentDTO document, ResumeUnresolvedItemDTO item, ResumeReviewResolveRequestDTO request) {
        ResumeDocumentContactDTO contact = readDraft(item, ResumeDocumentContactDTO.class);
        if (request.getContactType() != null && !request.getContactType().isBlank()) {
            contact.setType(ResumeDocumentContactType.fromValue(request.getContactType()).name());
        }
        if (request.getContactLabel() != null && !request.getContactLabel().isBlank()) {
            contact.setLabel(request.getContactLabel().strip());
        }
        if (request.getContactValue() != null && !request.getContactValue().isBlank()) {
            contact.setValue(request.getContactValue().strip());
        }
        if (contact.getType() == null) {
            contact.setType(ResumeDocumentContactType.OTHER.name());
        }
        if (contact.getLabel() == null || contact.getLabel().isBlank()) {
            contact.setLabel(ResumeDocumentContactType.fromValue(contact.getType()).getDefaultLabel());
        }
        if (contact.getValue() == null || contact.getValue().isBlank()) {
            throw new BusinessException(400, "联系方式内容不能为空");
        }
        ResumeDocumentContactType type = ResumeDocumentContactType.fromValue(contact.getType());
        if (type == ResumeDocumentContactType.PHONE && !ResumeDocumentQualityValidator.isValidPhone(contact.getValue())) {
            throw new BusinessException(400, "电话格式不正确，请修改后再接受");
        }
        if (type == ResumeDocumentContactType.EMAIL && !ResumeDocumentQualityValidator.isValidEmail(contact.getValue())) {
            throw new BusinessException(400, "邮箱格式不正确，请修改后再接受");
        }
        if (document.getBasics() == null || document.getBasics().getContacts() == null) {
            throw new BusinessException(500, "简历基础信息缺失");
        }
        contact.setId(null);
        document.getBasics().getContacts().add(contact);
    }

    private void acceptNameCandidate(
            ResumeDocumentDTO document, ResumeUnresolvedItemDTO item, ResumeReviewResolveRequestDTO request) {
        String name = request.getName();
        if (name == null || name.isBlank()) {
            name = readFragmentText(item);
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "姓名不能为空");
        }
        if (document.getBasics() == null) {
            throw new BusinessException(500, "简历基础信息缺失");
        }
        document.getBasics().setName(name.strip());
    }

    private void acceptEntry(
            ResumeDocumentDTO document, ResumeUnresolvedItemDTO item, ResumeReviewResolveRequestDTO request) {
        ResumeDocumentEntryDTO entry = readDraft(item, ResumeDocumentEntryDTO.class);
        applyEntryEdits(entry, request.getEntry());
        ResumeDocumentSectionKind kindHint = entryKindHint(item);
        validateEntryTitle(kindHint, entry);
        entry.setId(null);
        ResumeDocumentSectionDTO section = resolveTargetSection(
                document, request.getTargetSectionId(), kindHint, FALLBACK_SECTION_TITLE);
        section.getEntries().add(entry);
    }

    private void validateEntryTitle(ResumeDocumentSectionKind kind, ResumeDocumentEntryDTO entry) {
        if (entry.getBullets() != null
                && entry.getBullets().stream().anyMatch(bullet -> bullet == null
                || bullet.getText() == null
                || bullet.getText().isBlank())) {
            throw new BusinessException(400, "请删除空白要点后再接受该候选条目");
        }
        if (kind == ResumeDocumentSectionKind.EDUCATION
                && (entry.getSchool() == null || entry.getSchool().isBlank())) {
            throw new BusinessException(400, "请补充学校名后再接受该候选条目");
        }
        if ((kind == ResumeDocumentSectionKind.EXPERIENCE || kind == ResumeDocumentSectionKind.PROJECT)
                && (entry.getOrganization() == null || entry.getOrganization().isBlank())) {
            throw new BusinessException(400, "请补充公司或项目名后再接受该候选条目");
        }
    }

    private void applyEntryEdits(ResumeDocumentEntryDTO entry, ResumeDocumentEntryDTO edits) {
        if (edits == null) {
            return;
        }
        if (edits.getOrganization() != null) {
            entry.setOrganization(edits.getOrganization());
        }
        if (edits.getRole() != null) {
            entry.setRole(edits.getRole());
        }
        if (edits.getSchool() != null) {
            entry.setSchool(edits.getSchool());
        }
        if (edits.getDegree() != null) {
            entry.setDegree(edits.getDegree());
        }
        if (edits.getMajor() != null) {
            entry.setMajor(edits.getMajor());
        }
        if (edits.getStartDate() != null) {
            entry.setStartDate(edits.getStartDate());
        }
        if (edits.getEndDate() != null) {
            entry.setEndDate(edits.getEndDate());
        }
        if (edits.getLocation() != null) {
            entry.setLocation(edits.getLocation());
        }
        if (edits.getGroup() != null) {
            entry.setGroup(edits.getGroup());
        }
        if (edits.getSkillItems() != null) {
            entry.setSkillItems(new ArrayList<>(edits.getSkillItems()));
        }
        if (edits.getBullets() != null) {
            entry.setBullets(new ArrayList<>(edits.getBullets()));
        }
    }

    private void acceptFragment(
            ResumeDocumentDTO document, ResumeUnresolvedItemDTO item, ResumeReviewResolveRequestDTO request) {
        String text = request.getText();
        if (text == null || text.isBlank()) {
            text = readFragmentText(item);
        }
        if (text == null || text.isBlank()) {
            throw new BusinessException(400, "内容不能为空");
        }
        ResumeDocumentSectionDTO section = resolveTargetSection(
                document,
                request.getTargetSectionId(),
                ResumeDocumentSectionKind.OTHER,
                FALLBACK_SECTION_TITLE);
        ResumeDocumentEntryDTO entry = ResumeDocumentEntryDTO.builder()
                .bullets(new ArrayList<>(List.of(ResumeDocumentBulletDTO.builder().text(text.strip()).build())))
                .build();
        section.getEntries().add(entry);
    }

    private ResumeDocumentSectionKind entryKindHint(ResumeUnresolvedItemDTO item) {
        try {
            JsonNode draft = objectMapper.readTree(item.getCanonicalDraft() == null ? "{}" : item.getCanonicalDraft());
            String kind = draft.path("kind").asText("");
            ResumeDocumentSectionKind parsed = ResumeDocumentSectionKind.fromValue(kind);
            return parsed == ResumeDocumentSectionKind.CUSTOM ? ResumeDocumentSectionKind.OTHER : parsed;
        } catch (JsonProcessingException exception) {
            return ResumeDocumentSectionKind.OTHER;
        }
    }

    private String readFragmentText(ResumeUnresolvedItemDTO item) {
        try {
            JsonNode draft = objectMapper.readTree(item.getCanonicalDraft() == null ? "{}" : item.getCanonicalDraft());
            String text = draft.path("text").asText("");
            return text.isBlank() ? null : text;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "候选项内容格式不正确");
        }
    }

    /**
     * 归属章节解析：显式指定优先；缺省为游离内容维护一个「补充内容」章节。
     * 该章节由确认动作创建，不属于解析器兜底产物。
     */
    private ResumeDocumentSectionDTO resolveTargetSection(
            ResumeDocumentDTO document, String targetSectionId, ResumeDocumentSectionKind kindHint, String title) {
        if (document.getSections() == null) {
            document.setSections(new ArrayList<>());
        }
        if (targetSectionId != null && !targetSectionId.isBlank()) {
            ResumeDocumentSectionDTO target = document.getSections().stream()
                    .filter(section -> targetSectionId.equals(section.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(400, "归属章节不存在"));
            if (kindHint != null
                    && kindHint != ResumeDocumentSectionKind.OTHER
                    && kindHint != ResumeDocumentSectionKind.CUSTOM
                    && !kindHint.name().equals(target.getKind())) {
                throw new BusinessException(400, "候选条目不能归入不匹配的章节");
            }
            return target;
        }
        if (kindHint != null && kindHint != ResumeDocumentSectionKind.OTHER
                && kindHint != ResumeDocumentSectionKind.CUSTOM) {
            ResumeDocumentSectionDTO existing = document.getSections().stream()
                    .filter(section -> kindHint.name().equals(section.getKind()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return existing;
            }
            ResumeDocumentSectionDTO created = ResumeDocumentSectionDTO.builder()
                    .kind(kindHint.name())
                    .title(sectionTitle(kindHint))
                    .entries(new ArrayList<>())
                    .build();
            document.getSections().add(created);
            return created;
        }
        ResumeDocumentSectionDTO fallback = document.getSections().stream()
                .filter(section -> FALLBACK_SECTION_TITLE.equals(section.getTitle()))
                .findFirst()
                .orElse(null);
        if (fallback != null) {
            return fallback;
        }
        ResumeDocumentSectionDTO created = ResumeDocumentSectionDTO.builder()
                .kind(ResumeDocumentSectionKind.OTHER.name())
                .title(FALLBACK_SECTION_TITLE)
                .entries(new ArrayList<>())
                .build();
        document.getSections().add(created);
        return created;
    }

    private String sectionTitle(ResumeDocumentSectionKind kind) {
        return switch (kind) {
            case EDUCATION -> "教育经历";
            case EXPERIENCE -> "工作经历";
            case PROJECT -> "项目经历";
            case SKILL -> "技能";
            case SUMMARY -> "个人总结";
            case ACHIEVEMENT -> "荣誉奖项";
            case CERTIFICATE -> "证书";
            default -> FALLBACK_SECTION_TITLE;
        };
    }

    private <T> T readDraft(ResumeUnresolvedItemDTO item, Class<T> type) {
        if (item.getCanonicalDraft() == null || item.getCanonicalDraft().isBlank()) {
            throw new BusinessException(500, "候选项内容缺失");
        }
        try {
            JsonNode draft = objectMapper.readTree(item.getCanonicalDraft());
            if (type == ResumeDocumentEntryDTO.class && draft instanceof ObjectNode objectDraft) {
                // kind is routing metadata for entryKindHint, not a persisted entry field.
                objectDraft.remove("kind");
            }
            return objectMapper.treeToValue(draft, type);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "候选项内容格式不正确");
        }
    }

    private ResumeDocumentDTO readDocument(String canonicalDocument) {
        try {
            return objectMapper.readValue(canonicalDocument, ResumeDocumentDTO.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历内容格式不正确，请重新解析");
        }
    }

    private List<ResumeUnresolvedItemDTO> readUnresolvedItems(String unresolvedItemsJson) {
        if (unresolvedItemsJson == null || unresolvedItemsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<ResumeUnresolvedItemDTO> items = objectMapper.readValue(
                    unresolvedItemsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ResumeUnresolvedItemDTO.class));
            return items == null ? new ArrayList<>() : new ArrayList<>(items);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "待确认项格式不正确，请重新解析");
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历内容保存失败");
        }
    }

    private ResumeVersion getCanonicalSource(Long userId, ResumeParseResult parseResult) {
        if (parseResult == null || parseResult.getCanonicalSourceVersionId() == null) {
            return null;
        }
        LambdaQueryWrapper<ResumeVersion> query = new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, parseResult.getCanonicalSourceVersionId())
                .eq(ResumeVersion::getResumeId, parseResult.getResumeId())
                .eq(ResumeVersion::getVersionType, "SOURCE")
                .isNull(ResumeVersion::getSourceVersionId)
                .isNull(ResumeVersion::getJobTargetId);
        if (userId != null) {
            query.eq(ResumeVersion::getUserId, userId);
        }
        return resumeVersionMapper.selectOne(query);
    }

    private ResumeParseResult getOwnedParseResult(Long userId, Long resumeId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (resumeId == null || resumeId <= 0) {
            throw new BusinessException(400, "简历 ID 必须大于 0");
        }
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resume.getId()));
        if (parseResult == null) {
            throw new BusinessException(404, "简历尚未解析");
        }
        return parseResult;
    }

    private ResumeReviewVO toVO(Long userId, ResumeParseResult parseResult) {
        ResumeVersion source = getCanonicalSource(userId, parseResult);
        return ResumeReviewVO.builder()
                .resumeId(parseResult.getResumeId())
                .qualityStatus(parseResult.getQualityStatus())
                .qualityIssues(parseResult.getQualityIssues())
                .unresolvedItems(parseResult.getUnresolvedItems())
                .canonicalDocument(source == null ? null : source.getStructuredContent())
                .build();
    }
}
