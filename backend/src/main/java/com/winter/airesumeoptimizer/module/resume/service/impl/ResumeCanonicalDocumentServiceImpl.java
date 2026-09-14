package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeDisplayModelDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillEvidenceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillSetDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeCanonicalDocumentService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDocumentQualityValidator;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentContactType;
import com.winter.airesumeoptimizer.module.workspace.service.ResumeDocumentConverter;
import com.winter.airesumeoptimizer.module.workspace.service.impl.ResumeDocumentConverterImpl;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentSectionKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * canonical 文档构建实现（Slice A）。
 *
 * <p>输入只是候选解析：只有能通过结构白名单的内容才进入正式文档；
 * 无法可靠判定归属的内容显式成为未决候选项，由用户确认，绝不机械追加兜底章节。
 */
@Service
public class ResumeCanonicalDocumentServiceImpl implements ResumeCanonicalDocumentService {

    private static final int MAX_CONTACTS = 20;
    private static final int MAX_SECTIONS = 30;
    private static final int MAX_ENTRIES_PER_SECTION = 100;
    private static final int MAX_BULLETS_PER_ENTRY = 100;
    private static final int MAX_OTHERS_CANDIDATES = 20;
    private static final int MAX_UNRESOLVED_ITEMS = 60;
    private static final int MIN_COVERAGE_LINE_LENGTH = 1;
    private static final int MIN_TOKEN_LENGTH = 2;
    /** “Email: x@y.z”一类短标签前缀的最大残差长度（归一化后）。 */
    private static final int CONTACT_LABEL_RESIDUE_MAX_LENGTH = 8;
    /** 有意义 token：连续中文或连续字母数字；未表示行必须全部 token 命中才算覆盖。 */
    private static final Pattern MEANINGFUL_TOKEN_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z0-9]+(?:[+#.-][A-Za-z0-9]+)*");
    /** Source-backed 校验使用更严格的词边界，避免 Java 被 JavaScript 的前缀误证明。 */
    private static final Pattern SOURCE_TOKEN_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z0-9+#.-]+");

    /** 教育经历日期区间：原文字符串提取，不做语义解析。 */
    private static final Pattern EDUCATION_DATE_RANGE = Pattern.compile(
            "((?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?)"
                    + "\\s*(?:[-–—~～至到]+|[-–—~～])\\s*"
                    + "((?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?|至今|今|现在|present)");

    private static final Set<String> DEGREE_WORDS = Set.of(
            "本科", "硕士", "博士", "大专", "专科", "学士", "研究生", "高中", "中专", "博士后", "MBA");
    private static final Set<String> STRUCTURAL_HEADINGS = Set.of(
            "个人信息", "基本信息", "联系方式", "教育经历", "教育背景", "专业技能", "技术能力", "技能", "技能关键词",
            "技术栈", "工作经历", "工作经验", "职业经历", "实习经历", "项目经历", "项目经验", "校园经历", "在校经历",
            "获奖经历", "荣誉奖项", "证书", "自我评价", "个人总结", "个人概述", "个人优势", "自我介绍", "profile",
            "education", "skills", "experience", "projects", "summary");

    private static final Map<String, String> SKILL_GROUP_LABELS = Map.ofEntries(
            Map.entry("language", "编程语言"),
            Map.entry("framework", "框架"),
            Map.entry("database", "数据库"),
            Map.entry("frontend", "前端技术"),
            Map.entry("middleware", "中间件"),
            Map.entry("cv", "计算机视觉"),
            Map.entry("ai", "AI / 机器学习"),
            Map.entry("tool", "工具"),
            Map.entry("data", "数据分析"),
            Map.entry("other", "其他技能"));

    /** basicInfo 中与根字段重复的键，不重复进入文档。 */
    private static final Set<String> BASIC_INFO_EXCLUDED_KEYS = Set.of(
            "name", "phone", "email", "jobintention", "resumetype",
            "姓名", "名字", "电话", "手机", "手机号", "邮箱", "电子邮件", "求职意向");
    private static final Set<String> HISTORICAL_STRING_FIELDS = Set.of(
            "name", "phone", "email", "jobIntention", "highestEducation", "resumeType", "parseMode",
            "parserVersion", "summary", "rawText", "id", "text", "sourceText", "description", "skill",
            "organization", "role", "school", "degree", "major", "startDate", "endDate", "location",
            "environment", "mentor", "timeRange", "date", "title", "level", "competition", "ranking",
            "sourceSectionId", "sourceType", "originalTitle", "normalizedSection", "displayName", "iconType",
            "source", "fontName", "sectionType", "sourceSectionConfidence", "heading", "meta", "field", "status",
            "rejectReason", "aiSectionClassifyFallbackReason", "aiStructuredParseFallbackReason", "aiStatus",
            "aiSkippedReason", "aiFallbackReason", "aiCacheKeyDigest", "promptVersion", "cacheKey", "modelName",
            "type", "target", "targetRole", "highestDegree", "workYears", "generatedBy", "aiDisplayErrorMessage",
            "displayPromptVersion", "displayAdapterVersion", "canonicalDraft", "kind", "label", "value", "content",
            "company", "position");
    private static final Set<String> HISTORICAL_STRING_ARRAY_FIELDS = Set.of(
            "education", "skills", "projects", "workExperiences", "internships", "campusExperiences", "awards",
            "certificates", "others", "keywords", "descriptions", "evidence", "responsibilities", "techStack",
            "sourceBlockIds", "sourceOccurrenceIds", "qualityWarnings", "lines", "coreSkills", "topSkills",
            "certificateTags", "pendingItems");
    private static final Set<String> HISTORICAL_EDUCATION_OBJECT_FIELDS = Set.of(
            "id", "heading", "meta", "school", "degree", "major", "startDate", "endDate",
            "bullets", "description", "evidence", "sourceRef", "sourceOccurrenceIds", "confidence");

    private final ObjectMapper objectMapper;
    private final ObjectMapper strictObjectMapper;
    private final ResumeDocumentConverter historicalDocumentConverter;

    /** Convenience constructor retained for the deterministic unit-level callers. */
    public ResumeCanonicalDocumentServiceImpl(ObjectMapper objectMapper) {
        this(objectMapper, new ResumeDocumentConverterImpl(objectMapper));
    }

    @Autowired
    public ResumeCanonicalDocumentServiceImpl(
            ObjectMapper objectMapper, ResumeDocumentConverter historicalDocumentConverter) {
        this.objectMapper = objectMapper;
        this.historicalDocumentConverter = historicalDocumentConverter;
        this.strictObjectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    @Override
    public BuildResult build(ResumeStructuredContentDTO structuredContent) {
        List<ResumeUnresolvedItemDTO> unresolved = new ArrayList<>();
        if (structuredContent == null) {
            return new BuildResult(emptyDocument(), unresolved);
        }
        // 先构建章节，再基于已表示内容裁决基础信息，避免学校等字段重复成为未决项。
        SourceContext provenance = new SourceContext(structuredContent);
        // Historical/indexed-only callers may not carry rawText. Use the retained source
        // occurrences as the evidence text instead of silently disabling source-backed checks.
        String sourceText = provenance.sourceText(structuredContent.getRawText());
        List<ResumeDocumentSectionDTO> sections = buildSections(
                structuredContent, unresolved, sourceText, provenance);
        String representedText = collectSectionText(sections);
        ResumeDocumentBasicsDTO basics = buildBasics(
                structuredContent, unresolved, representedText, sourceText, provenance);
        appendUnrepresentedLines(structuredContent, basics, sections, unresolved, provenance);
        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .sourceRef(provenance.reference(provenance.occurrences))
                .sourceOccurrenceIds(provenance.occurrenceIds(provenance.occurrences))
                .sourceOccurrenceTexts(provenance.occurrenceTexts())
                .sourceOccurrencePrimaryIds(provenance.occurrencePrimaryIds())
                .sourceOccurrenceRefs(provenance.occurrenceRefs())
                .basics(basics)
                .sections(sections)
                .build();
        assignDeterministicIds(document, unresolved);
        return new BuildResult(document, unresolved);
    }

    @Override
    public BuildResult buildFromStructuredJson(String structuredJson) {
        if (structuredJson == null || structuredJson.isBlank()) {
            throw new BusinessException(500, "简历内容尚未就绪，请先完成简历解析");
        }
        try {
            JsonNode historicalRoot = objectMapper.readTree(structuredJson);
            // Jackson's generic Map<String, String> binding may otherwise coerce numbers and
            // booleans. Validate the historical payload shape before using any compatibility
            // projection so malformed scalar values cannot disappear silently.
            validateHistoricalStringShapes(historicalRoot);
            if (looksLikeEmbeddedHistoricalDocument(historicalRoot)) {
                // A few pre-Slice-A task snapshots embedded the V1 document itself in the
                // structured-json column. Reuse the single persisted-document upgrader instead
                // of binding that shape to ResumeStructuredContentDTO (which intentionally has
                // no entry-level heading/meta fields).
                String canonicalJson = historicalRoot.path("schemaVersion").isTextual()
                        ? structuredJson
                        : withCanonicalSchemaVersion(historicalRoot);
                return new BuildResult(
                        historicalDocumentConverter.upgradeLegacyDocument(canonicalJson), List.of());
            }
            // Older snapshots occasionally retained generic heading/meta pairs inside the
            // semantic arrays. Those fields are intentionally not part of the current typed
            // parser DTOs, so project them on the raw tree before strict binding. The projection
            // is deterministic and section-aware; it never creates source references.
            HistoricalTreeProjection projection = projectHistoricalGenericFields(historicalRoot);
            ResumeStructuredContentDTO content =
                    strictObjectMapper.treeToValue(projection.root(), ResumeStructuredContentDTO.class);
            if (content == null) {
                throw new BusinessException(500, "简历结构化内容格式不正确");
            }
            // Decide this before enrichment. Enrichment can reconstruct rawSections from old
            // display sections, but those reconstructed rows are not raw source evidence.
            boolean sourceMaterialPresent = hasSourceMaterial(content);
            // A source-only historical snapshot may have retained indexed/raw section rows but
            // no nested semantic projection. Copy those rows into the legacy compatibility
            // fields before enrichment; this is a lossless section hint, not a new fact.
            if (sourceMaterialPresent) {
                // A source view is authoritative evidence even when a newer snapshot also has
                // an empty/partial structuredData object. Fill only absent compatibility fields;
                // this prevents a partial nested projection from masking retained occurrences.
                populateCompatibilityFieldsFromSource(content);
            }
            // Historical task snapshots may contain only the legacy top-level collections.
            // Rebuild their compatibility structured projection before reading it; this is
            // read-only and never becomes the new parse/AI Source of Truth.
            if (content.getStructuredData() == null && sourceMaterialPresent) {
                // Enrichment is safe only when the snapshot already carries source material.
                // For a legacy candidate containing only structured fields it would manufacture
                // source-line IDs and make those fields appear source-backed.
                List<ResumeRawSectionDTO> retainedRawSections = content.getRawSections();
                ResumeStructuredResultAssembler.enrich(content);
                if (retainedRawSections != null && !retainedRawSections.isEmpty()) {
                    // Enrichment rebuilds rawSections from the older text-section view and would
                    // otherwise erase a raw-block-only snapshot. Keep the retained source view;
                    // SourceContext merges it with any indexed/text-section view later.
                    content.setRawSections(retainedRawSections);
                }
            }
            if (sourceMaterialPresent && content.getStructuredData() != null) {
                // Mixed snapshots can contain a newer structuredData object alongside legacy
                // top-level fields. Fill only missing/residual compatibility values; never let
                // an empty/partial nested object silently hide a source-backed legacy row.
                mergeCompatibilityStructuredData(content);
            }
            // A snapshot without rawText/indexed lines/raw blocks has no evidence boundary.
            // Project its candidate display fields directly and deliberately leave every
            // sourceRef/sourceOccurrenceIds field empty. Never turn candidate values into a
            // synthetic source corpus merely to make build(...) accept them.
            BuildResult result = sourceMaterialPresent
                    ? build(content)
                    : buildHistoricalCompatibilityProjection(
                            content, projection.educationEntries(), projection.educationProjectionPresent());
            if (sourceMaterialPresent && !projection.genericEducationEntries().isEmpty()) {
                appendHistoricalEducationEntriesPreservingRows(
                        result.document().getSections(),
                        result.unresolvedItems(),
                        projection.genericEducationEntries(),
                        new SourceContext(content));
                stripHistoricalProvenance(projection.genericEducationEntries());
                assignDeterministicIds(result.document(), result.unresolvedItems());
            }
            if (sourceMaterialPresent) {
                // Source-backed semantic data is authoritative for provenance, but an old
                // snapshot may also contain presentation-only cards that were not copied into
                // structuredData. Retain those cards as an explicitly unreferenced compatibility
                // projection instead of silently dropping them or pretending they are source
                // evidence.
                ResumeDisplayModelDTO display = historicalDisplayModel(content);
                if (display != null) {
                    mergeHistoricalBasics(result.document().getBasics(), historicalBasics(content));
                    appendHistoricalDisplayFallbackSections(
                            result.document().getSections(), content, content.getStructuredData(), display);
                    assignDeterministicIds(result.document(), result.unresolvedItems());
                }
            }
            if ((result.document().getSections() == null || result.document().getSections().isEmpty())
                    && !hasHistoricalBasics(content)) {
                throw new BusinessException(500, "历史简历内容无法形成可编辑章节，请重新解析");
            }
            return result;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(500, "简历结构化内容格式不正确");
        }
    }

    private void validateHistoricalStringShapes(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new BusinessException(500, "简历结构化内容格式不正确");
        }
        validateHistoricalNode(root, null, false);
    }

    private void validateHistoricalNode(JsonNode node, String fieldName, boolean debugSubtree) {
        if (node == null || node.isNull()) {
            return;
        }
        // Debug payloads may contain arbitrary diagnostic scalars, but known historical field
        // names still need the same shape checks inside the subtree. Do not skip recursion: a
        // malformed nested text value must not be hidden behind `debug`.
        if ("sourceRef".equals(fieldName)) {
            validateHistoricalSourceReference(node);
            return;
        }
        if ("sourceOccurrenceRefs".equals(fieldName)) {
            if (!node.isObject()) {
                throw new BusinessException(500, "简历来源 occurrence 坐标清单格式不正确");
            }
            node.fields().forEachRemaining(entry -> {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new BusinessException(500, "简历来源 occurrence 坐标清单格式不正确");
                }
                validateHistoricalSourceReference(entry.getValue());
            });
            return;
        }
        if ("sourceOccurrenceTexts".equals(fieldName)
                || "sourceOccurrencePrimaryIds".equals(fieldName)) {
            if (!node.isObject()) {
                throw new BusinessException(500, "简历来源 occurrence 清单格式不正确");
            }
            node.fields().forEachRemaining(entry -> {
                if (entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getValue() == null || !entry.getValue().isTextual()
                        || entry.getValue().textValue().isBlank()) {
                    throw new BusinessException(500, "简历来源 occurrence 清单格式不正确");
                }
            });
            return;
        }
        if (fieldName != null && HISTORICAL_STRING_FIELDS.contains(fieldName)) {
            if (!node.isTextual()) {
                throw new BusinessException(500, "简历结构化内容中的文本字段格式不正确");
            }
            return;
        }
        if ("confidence".equals(fieldName)) {
            if (!node.isNumber()) {
                throw new BusinessException(500, "简历结构化内容中的置信度格式不正确");
            }
            return;
        }
        if (fieldName != null && HISTORICAL_STRING_ARRAY_FIELDS.contains(fieldName)) {
            // `projects` is a flat legacy String[] at the root but an object[] under
            // structuredData; accept only that known dual shape and still validate object fields.
            if ("skills".equals(fieldName) && node.isObject()) {
                node.fields().forEachRemaining(entry -> validateHistoricalNode(
                        entry.getValue(), entry.getKey(), false));
                return;
            }
            if (!node.isArray()) {
                throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
            }
            for (JsonNode child : node) {
                if (child == null || child.isNull() || child.isTextual()) {
                    continue;
                }
                if (("projects".equals(fieldName) || "evidence".equals(fieldName)
                        || "education".equals(fieldName)) && child.isObject()) {
                    // `projects` may be either the flat legacy String[] or semantic DTOs;
                    // skill evidence is likewise an object[] under structuredData.skills. Some
                    // old snapshots also encoded one education row as a generic heading/meta
                    // object. The raw-tree compatibility projection converts that row to text
                    // before the strict DTO binding below.
                    validateHistoricalNode(child, null, false);
                    continue;
                }
                throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
            }
            return;
        }
        if ("basicInfo".equals(fieldName)) {
            if (!node.isObject()) {
                throw new BusinessException(500, "简历基础信息格式不正确");
            }
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue() != null && !entry.getValue().isNull()
                        && !entry.getValue().isTextual()) {
                    throw new BusinessException(500, "简历基础信息中的文本字段格式不正确");
                }
            });
            return;
        }
        if ("basicInfoDebug".equals(fieldName)) {
            if (!node.isObject()) {
                throw new BusinessException(500, "简历基础信息调试格式不正确");
            }
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    return;
                }
                if (!value.isObject()) {
                    throw new BusinessException(500, "简历基础信息调试格式不正确");
                }
                value.fields().forEachRemaining(detail -> {
                    String key = detail.getKey();
                    JsonNode detailValue = detail.getValue();
                    if (detailValue == null || detailValue.isNull()) {
                        return;
                    }
                    if ("confidence".equals(key)) {
                        if (!detailValue.isNumber()) {
                            throw new BusinessException(500, "简历基础信息调试格式不正确");
                        }
                    } else if (Set.of("value", "source", "evidence", "status", "rejectReason").contains(key)
                            && !detailValue.isTextual()) {
                        throw new BusinessException(500, "简历基础信息调试格式不正确");
                    }
                });
            });
            return;
        }
        if ("groups".equals(fieldName)) {
            if (node.isArray()) {
                // structuredData.skills.groups is a map, while displayModel.skillSummary.groups
                // is a typed card array. Let strict DTO binding distinguish those two known
                // containers, but recursively validate all generic heading/meta values first.
                for (JsonNode value : node) {
                    validateHistoricalNode(value, null, false);
                }
                return;
            }
            if (!node.isObject()) {
                throw new BusinessException(500, "简历技能分组格式不正确");
            }
            node.fields().forEachRemaining(entry -> {
                JsonNode values = entry.getValue();
                if (values == null || values.isNull()) {
                    return;
                }
                if (!values.isArray()) {
                    throw new BusinessException(500, "简历技能分组格式不正确");
                }
                for (JsonNode value : values) {
                    if (value == null || value.isNull() || !value.isTextual()) {
                        throw new BusinessException(500, "简历技能分组格式不正确");
                    }
                }
            });
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> validateHistoricalNode(
                    entry.getValue(), entry.getKey(), "debug".equals(entry.getKey())));
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                validateHistoricalNode(child, null, false);
            }
        }
    }

    private boolean looksLikeEmbeddedHistoricalDocument(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        JsonNode schemaVersion = root.get("schemaVersion");
        if (schemaVersion != null && schemaVersion.isTextual()
                && ResumeDocumentDTO.SCHEMA_VERSION.equals(schemaVersion.textValue().strip())) {
            return true;
        }
        JsonNode sections = root.get("sections");
        if (sections == null || !sections.isArray()) {
            return false;
        }
        for (JsonNode section : sections) {
            if (section != null && section.isObject() && section.has("entries")) {
                return true;
            }
        }
        return false;
    }

    private String withCanonicalSchemaVersion(JsonNode root) {
        if (!(root instanceof ObjectNode copy)) {
            throw new BusinessException(500, "简历结构化内容格式不正确");
        }
        ObjectNode canonical = copy.deepCopy();
        canonical.put("schemaVersion", ResumeDocumentDTO.SCHEMA_VERSION);
        try {
            return objectMapper.writeValueAsString(canonical);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历结构化内容格式不正确");
        }
    }

    /**
     * Project the one historical shape that the current typed candidate DTO deliberately does
     * not expose: generic {@code heading}/{@code meta} pairs embedded in semantic arrays.
     *
     * <p>This operates on a copy of the parsed JSON tree. The fields are removed before strict
     * DTO binding, while their text is either placed in a section-compatible semantic slot or
     * retained in the closest generic text collection. No source reference is created for the
     * projection; source-backed snapshots still have to pass the normal SourceContext checks.</p>
     */
    private HistoricalTreeProjection projectHistoricalGenericFields(JsonNode root) {
        if (!(root instanceof ObjectNode copy)) {
            return new HistoricalTreeProjection(root, List.of(), List.of(), false);
        }
        projectHistoricalGenericDisplay(copy.get("displayModel"));
        projectHistoricalGenericDisplay(copy.get("aiDisplayModel"));
        projectHistoricalGenericDisplay(copy.get("ruleDisplayModel"));
        ObjectNode structuredData = objectOrNull(copy.get("structuredData"));
        HistoricalEducationProjection nestedEducation = HistoricalEducationProjection.empty();
        if (structuredData != null) {
            projectHistoricalGenericSkills(structuredData);
            projectHistoricalGenericEntries(structuredData.get("experiences"), "EXPERIENCE");
            projectHistoricalGenericEntries(structuredData.get("projects"), "PROJECT");
            projectHistoricalGenericEntries(structuredData.get("achievements"), "ACHIEVEMENT");
            nestedEducation = projectHistoricalGenericEducation(structuredData);
        }
        // The assembler historically wrote the same education view both at the root and under
        // structuredData. Normalize either location and merge the two views count-aware, so a
        // genuine repeated row survives while a mirrored row is not duplicated.
        HistoricalEducationProjection rootEducation = projectHistoricalGenericEducation(copy);
        return new HistoricalTreeProjection(
                copy,
                mergeHistoricalEducationViews(nestedEducation.entries(), rootEducation.entries()),
                mergeHistoricalEducationViews(nestedEducation.genericEntries(), rootEducation.genericEntries()),
                nestedEducation.present() || rootEducation.present());
    }

    /**
     * A few pre-Slice-A display snapshots used the same generic heading/meta vocabulary as V1
     * entries. Normalize those fields to the display card's compatible slots before binding. The
     * display model remains a fallback only, so its source references are intentionally not
     * promoted by the later historical projection.
     */
    private void projectHistoricalGenericDisplay(JsonNode displayNode) {
        if (!(displayNode instanceof ObjectNode display)) {
            return;
        }
        projectHistoricalGenericDisplaySkillSummary(display.get("skillSummary"));
        projectHistoricalDisplayCards(display.get("educationCards"), "EDUCATION");
        projectHistoricalDisplayCards(display.get("workExperienceCards"), "EXPERIENCE");
        projectHistoricalDisplayCards(display.get("internshipCards"), "EXPERIENCE");
        projectHistoricalDisplayCards(display.get("campusExperienceCards"), "EXPERIENCE");
        projectHistoricalDisplayCards(display.get("projectCards"), "PROJECT");
        projectHistoricalDisplayCards(display.get("achievementCards"), "ACHIEVEMENT");
    }

    private void projectHistoricalGenericSkills(ObjectNode structuredData) {
        JsonNode skillsNode = structuredData.get("skills");
        if (!(skillsNode instanceof ObjectNode skills)) {
            return;
        }
        String heading = historicalTextNode(skills.get("heading"));
        String meta = historicalTextNode(skills.get("meta"));
        if (heading == null && meta == null) {
            return;
        }
        skills.remove(List.of("heading", "meta"));
        appendHistoricalStringNodeIfMissing(skills, "descriptions", heading);
        appendHistoricalStringNodeIfMissing(skills, "descriptions", meta);
    }

    private void projectHistoricalGenericDisplaySkillSummary(JsonNode summaryNode) {
        if (!(summaryNode instanceof ObjectNode summary)) {
            return;
        }
        String heading = historicalTextNode(summary.get("heading"));
        String meta = historicalTextNode(summary.get("meta"));
        summary.remove(List.of("heading", "meta"));
        appendHistoricalStringNodeIfMissing(summary, "descriptions", heading);
        appendHistoricalStringNodeIfMissing(summary, "descriptions", meta);
        JsonNode groupsNode = summary.get("groups");
        if (!(groupsNode instanceof ArrayNode groups)) {
            return;
        }
        for (JsonNode groupNode : groups) {
            if (!(groupNode instanceof ObjectNode group)) {
                continue;
            }
            String groupHeading = historicalTextNode(group.get("heading"));
            String groupMeta = historicalTextNode(group.get("meta"));
            group.remove(List.of("heading", "meta"));
            String name = historicalTextNode(group.get("name"));
            if (groupHeading != null) {
                if (name == null) {
                    group.put("name", groupHeading);
                } else if (!sameHistoricalText(name, groupHeading)) {
                    appendHistoricalStringNodeIfMissing(group, "descriptions", groupHeading);
                }
            }
            appendHistoricalStringNodeIfMissing(group, "descriptions", groupMeta);
        }
    }

    private void projectHistoricalDisplayCards(JsonNode cardsNode, String section) {
        if (cardsNode == null || cardsNode.isMissingNode() || cardsNode.isNull()) {
            return;
        }
        if (!cardsNode.isArray()) {
            return; // strict DTO binding reports the original shape error.
        }
        for (JsonNode cardNode : cardsNode) {
            if (!(cardNode instanceof ObjectNode card)) {
                continue;
            }
            String heading = historicalTextNode(card.get("heading"));
            String meta = historicalTextNode(card.get("meta"));
            card.remove("heading");
            // AchievementCard.meta is an existing, section-specific field. Other display card
            // DTOs have no generic meta slot, so remove it after projecting it below; otherwise
            // strict binding would reject the otherwise recoverable historical row.
            if (!"ACHIEVEMENT".equals(section)) {
                card.remove("meta");
            }
            if (heading == null && meta == null) {
                continue;
            }
            switch (section) {
                case "EDUCATION" -> {
                    putOrAppendHistoricalDisplayField(card, "school", heading);
                    if (meta != null) {
                        if (historicalIsStandaloneDate(meta) || historicalLooksLikeDate(meta)) {
                            putOrAppendHistoricalDisplayField(card, "timeRange", meta);
                        } else {
                            appendHistoricalTextField(card, "summary", meta);
                        }
                    }
                }
                case "EXPERIENCE" -> {
                    putOrAppendHistoricalDisplayField(card, "company", heading);
                    if (meta != null) {
                        Matcher range = EDUCATION_DATE_RANGE.matcher(meta);
                        boolean hasRange = range.find();
                        String remainder = hasRange
                                ? removeHistoricalDateRangeRemainder(meta, range) : "";
                        if (hasRange) {
                            putOrAppendHistoricalDisplayField(card, "timeRange", meta);
                            if (!remainder.isEmpty()) {
                                putOrAppendHistoricalDisplayField(card, "position", remainder);
                            }
                        } else if (!historicalLooksLikeDate(meta)) {
                            putOrAppendHistoricalDisplayField(card, "position", meta);
                        } else {
                            putOrAppendHistoricalDisplayField(card, "timeRange", meta);
                        }
                    }
                }
                case "PROJECT" -> {
                    putOrAppendHistoricalDisplayField(card, "name", heading);
                    appendHistoricalTextField(card, "summary", meta);
                }
                case "ACHIEVEMENT" -> {
                    String existingTitle = historicalTextNode(card.get("title"));
                    if (heading != null && existingTitle == null) {
                        card.put("title", heading);
                    } else if (heading != null && !sameHistoricalText(existingTitle, heading)) {
                        // AchievementCard has no summary/bullet slot. Keep both title values in
                        // the existing title field; placing the conflict in meta could turn a
                        // valid date into an untyped bullet and lose its date projection.
                        card.put("title", existingTitle + "\n" + heading);
                    }
                }
                default -> { }
            }
        }
    }

    private void putOrAppendHistoricalDisplayField(ObjectNode card, String field, String value) {
        String text = trimToNull(value);
        if (text == null) {
            return;
        }
        String existing = historicalTextNode(card.get(field));
        if (existing == null) {
            card.put(field, text);
        } else if (!sameHistoricalText(existing, text)) {
            appendHistoricalTextField(card, "summary", text);
        }
    }

    private void appendHistoricalTextField(ObjectNode card, String field, String value) {
        String text = trimToNull(value);
        if (text == null) {
            return;
        }
        String existing = historicalTextNode(card.get(field));
        if (existing == null) {
            card.put(field, text);
        } else if (!sameHistoricalText(existing, text)) {
            card.put(field, existing + "\n" + text);
        }
    }

    private void projectHistoricalGenericEntries(JsonNode entriesNode, String section) {
        if (entriesNode == null || entriesNode.isMissingNode() || entriesNode.isNull()) {
            return;
        }
        if (!entriesNode.isArray()) {
            return; // strict DTO binding reports the original shape error.
        }
        for (JsonNode entryNode : entriesNode) {
            if (entryNode instanceof ObjectNode entry) {
                String heading = historicalTextNode(entry.get("heading"));
                String meta = historicalTextNode(entry.get("meta"));
                entry.remove(List.of("heading", "meta"));
                if (heading == null && meta == null) {
                    continue;
                }
                switch (section) {
                    case "EXPERIENCE" -> projectHistoricalExperience(entry, heading, meta);
                    case "PROJECT" -> projectHistoricalProject(entry, heading, meta);
                    case "ACHIEVEMENT" -> projectHistoricalAchievement(entry, heading, meta);
                    default -> { }
                }
            }
        }
    }

    private void projectHistoricalExperience(ObjectNode entry, String heading, String meta) {
        String organization = historicalTextNode(entry.get("organization"));
        if (heading != null) {
            if (organization == null) {
                entry.put("organization", heading);
            } else if (!sameHistoricalText(organization, heading)) {
                appendHistoricalBulletNode(entry, heading);
            }
        }
        projectHistoricalExperienceMeta(entry, meta);
    }

    private void projectHistoricalExperienceMeta(ObjectNode entry, String meta) {
        if (meta == null) {
            return;
        }
        String role = historicalTextNode(entry.get("role"));
        String startDate = historicalTextNode(entry.get("startDate"));
        String endDate = historicalTextNode(entry.get("endDate"));
        Matcher range = EDUCATION_DATE_RANGE.matcher(meta);
        boolean consumed = false;
        if (range.find()) {
            String rangeStart = range.group(1).strip();
            String rangeEnd = range.group(2).strip();
            boolean datesCompatible = (startDate == null || sameHistoricalText(startDate, rangeStart))
                    && (endDate == null || sameHistoricalText(endDate, rangeEnd));
            String remainder = removeHistoricalDateRangeRemainder(meta, range);
            if (datesCompatible) {
                if (startDate == null) {
                    entry.put("startDate", rangeStart);
                }
                if (endDate == null) {
                    entry.put("endDate", rangeEnd);
                }
                if (remainder.isEmpty()) {
                    consumed = true;
                } else if (role == null) {
                    entry.put("role", remainder);
                    consumed = true;
                } else if (sameHistoricalText(role, remainder)) {
                    consumed = true;
                }
            }
        } else if (role == null && !historicalLooksLikeDate(meta)) {
            entry.put("role", meta);
            consumed = true;
        } else if (startDate == null && historicalLooksLikeDate(meta)) {
            entry.put("startDate", meta);
            consumed = true;
        } else if (sameHistoricalText(role, meta) || sameHistoricalText(startDate, meta)
                || sameHistoricalText(endDate, meta)) {
            consumed = true;
        }
        if (!consumed) {
            appendHistoricalBulletNode(entry, meta);
        }
    }

    private void projectHistoricalProject(ObjectNode entry, String heading, String meta) {
        String name = historicalTextNode(entry.get("name"));
        if (heading != null) {
            if (name == null) {
                entry.put("name", heading);
            } else if (!sameHistoricalText(name, heading)) {
                appendHistoricalStringNode(entry, "responsibilities", heading);
            }
        }
        if (meta == null) {
            return;
        }
        String role = historicalTextNode(entry.get("role"));
        String startDate = historicalTextNode(entry.get("startDate"));
        String endDate = historicalTextNode(entry.get("endDate"));
        Matcher range = EDUCATION_DATE_RANGE.matcher(meta);
        boolean consumed = false;
        if (range.find()) {
            String rangeStart = range.group(1).strip();
            String rangeEnd = range.group(2).strip();
            boolean datesCompatible = (startDate == null || sameHistoricalText(startDate, rangeStart))
                    && (endDate == null || sameHistoricalText(endDate, rangeEnd));
            String remainder = removeHistoricalDateRangeRemainder(meta, range);
            if (datesCompatible) {
                if (startDate == null) {
                    startDate = rangeStart;
                    entry.put("startDate", rangeStart);
                }
                if (endDate == null) {
                    endDate = rangeEnd;
                    entry.put("endDate", rangeEnd);
                }
                if (remainder.isEmpty()) {
                    consumed = true;
                } else if (role == null) {
                    role = remainder;
                    entry.put("role", remainder);
                    consumed = true;
                } else if (sameHistoricalText(role, remainder)) {
                    consumed = true;
                }
            }
        } else if (role == null && !historicalLooksLikeDate(meta)) {
            entry.put("role", meta);
            consumed = true;
        } else if (startDate == null && historicalLooksLikeDate(meta)) {
            entry.put("startDate", meta);
            consumed = true;
        } else if (sameHistoricalText(role, meta) || sameHistoricalText(startDate, meta)
                || sameHistoricalText(endDate, meta)) {
            consumed = true;
        }
        if (!consumed) {
            appendHistoricalStringNode(entry, "responsibilities", meta);
        }
    }

    private void projectHistoricalAchievement(ObjectNode entry, String heading, String meta) {
        String title = historicalTextNode(entry.get("title"));
        if (heading != null) {
            if (title == null) {
                entry.put("title", heading);
            } else if (!sameHistoricalText(title, heading)) {
                appendHistoricalStringNode(entry, "evidence", heading);
            }
        }
        if (meta == null) {
            return;
        }
        String date = historicalTextNode(entry.get("date"));
        if (date == null && historicalIsStandaloneDate(meta)) {
            entry.put("date", meta);
        } else if (!sameHistoricalText(date, meta)) {
            appendHistoricalStringNode(entry, "evidence", meta);
        }
    }

    /** The current candidate DTO is string-only for education rows, but old JSON can contain a
     * generic object with heading/meta. Extract that row as an unreferenced compatibility entry,
     * projecting only explicit school/date/degree fields and retaining ambiguous metadata as a
     * bullet. */
    private HistoricalEducationProjection projectHistoricalGenericEducation(ObjectNode structuredData) {
        JsonNode educationNode = structuredData.get("education");
        if (educationNode == null || educationNode.isMissingNode() || educationNode.isNull()) {
            return HistoricalEducationProjection.empty();
        }
        if (!educationNode.isArray()) {
            return new HistoricalEducationProjection(List.of(), List.of(), true);
        }
        ArrayNode replacement = objectMapper.createArrayNode();
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        List<ResumeDocumentEntryDTO> genericEntries = new ArrayList<>();
        for (JsonNode value : educationNode) {
            if (value == null || value.isNull()) {
                replacement.add(value);
                continue;
            }
            if (value.isTextual()) {
                replacement.add(value);
                String text = trimToNull(value.textValue());
                if (text != null) {
                    entries.add(historicalEducationEntry(text));
                }
                continue;
            }
            if (!(value instanceof ObjectNode entry)) {
                // Keep the malformed value for strict validation to reject.
                replacement.add(value);
                continue;
            }
            List<String> unsupported = new ArrayList<>();
            entry.fieldNames().forEachRemaining(field -> {
                if (!HISTORICAL_EDUCATION_OBJECT_FIELDS.contains(field)) {
                    unsupported.add(field);
                }
            });
            if (!unsupported.isEmpty()) {
                throw new BusinessException(500, "简历教育经历格式不正确");
            }
            String heading = historicalTextNode(entry.get("heading"));
            String meta = historicalTextNode(entry.get("meta"));
            if (heading == null && meta == null && !hasHistoricalEducationObjectContent(entry)) {
                // An empty/provenance-only object is not a valid string row. Keep it for strict
                // DTO binding to reject instead of manufacturing an empty editable entry.
                replacement.add(value);
                continue;
            }
            ResumeDocumentEntryDTO projected = historicalGenericEducationEntry(entry, heading, meta);
            entries.add(projected);
            // Every object row must be retained separately because the typed candidate DTO can
            // only bind the textual replacement array. These rows stay unreferenced in a
            // source-backed snapshot unless the normal SourceContext can prove a separate copy.
            genericEntries.add(projected);
        }
        structuredData.set("education", replacement);
        return new HistoricalEducationProjection(
                List.copyOf(entries), List.copyOf(genericEntries), true);
    }

    private boolean hasHistoricalEducationObjectContent(ObjectNode entry) {
        if (Stream.of(entry.get("school"), entry.get("degree"), entry.get("major"),
                        entry.get("startDate"), entry.get("endDate"), entry.get("description"))
                .map(this::historicalTextNode)
                .anyMatch(java.util.Objects::nonNull)) {
            return true;
        }
        ResumeSourceRefDTO sourceRef = historicalSourceReference(entry.get("sourceRef"));
        return (sourceRef != null && hasText(sourceRef.getText()))
                || hasHistoricalTextArrayContent(entry.get("bullets"))
                || hasHistoricalTextArrayContent(entry.get("evidence"));
    }

    private boolean hasHistoricalTextArrayContent(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (!node.isArray()) {
            throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
        }
        return node.elements().hasNext();
    }

    private ResumeDocumentEntryDTO historicalGenericEducationEntry(
            ObjectNode entry, String heading, String meta) {
        String school = historicalTextNode(entry.get("school"));
        String degree = historicalTextNode(entry.get("degree"));
        String major = historicalTextNode(entry.get("major"));
        String startDate = historicalTextNode(entry.get("startDate"));
        String endDate = historicalTextNode(entry.get("endDate"));
        List<String> sourceOccurrenceIds = historicalStringList(entry.get("sourceOccurrenceIds"));
        ResumeSourceRefDTO sourceRef = historicalSourceReference(entry.get("sourceRef"));
        if (sourceRef != null) {
            if (sourceOccurrenceIds == null) {
                sourceOccurrenceIds = new ArrayList<>();
            }
            for (String occurrenceId : nonBlankIds(sourceRef.getSourceOccurrenceIds())) {
                if (!sourceOccurrenceIds.contains(occurrenceId)) {
                    sourceOccurrenceIds.add(occurrenceId);
                }
            }
        }
        List<String> bullets = new ArrayList<>();
        if (heading != null) {
            if (school == null) {
                school = heading;
            } else if (!sameHistoricalText(school, heading)) {
                appendHistoricalValue(bullets, heading);
            }
        }
        if (meta != null) {
            Matcher range = EDUCATION_DATE_RANGE.matcher(meta);
            boolean consumed = false;
            if (range.find()) {
                String candidateStart = range.group(1).strip();
                String candidateEnd = range.group(2).strip();
                boolean compatible = (startDate == null || sameHistoricalText(startDate, candidateStart))
                        && (endDate == null || sameHistoricalText(endDate, candidateEnd));
                if (compatible) {
                    if (startDate == null) {
                        startDate = candidateStart;
                    }
                    if (endDate == null) {
                        endDate = candidateEnd;
                    }
                }
                consumed = compatible && removeHistoricalDateRangeRemainder(meta, range).isEmpty();
            } else if (startDate == null && historicalLooksLikeDate(meta)) {
                startDate = meta;
                consumed = true;
            } else if (sameHistoricalText(startDate, meta) || sameHistoricalText(endDate, meta)) {
                consumed = true;
            }
            if (!consumed) {
                appendHistoricalValue(bullets, meta);
            }
        }
        appendHistoricalValue(bullets, historicalTextNode(entry.get("description")));
        appendHistoricalTextNodes(bullets, entry.get("bullets"));
        appendHistoricalTextNodes(bullets, entry.get("evidence"));
        if (bullets.isEmpty() && sourceRef != null) {
            appendHistoricalValue(bullets, sourceRef.getText());
        }
        if (school == null && degree == null && major == null && startDate == null && endDate == null
                && bullets.isEmpty()) {
            return historicalGenericEntry(List.of());
        }
        return ResumeDocumentEntryDTO.builder()
                .id(historicalTextNode(entry.get("id")))
                .sourceRef(sourceRef)
                .sourceOccurrenceIds(sourceOccurrenceIds)
                .school(school)
                .degree(degree)
                .major(major)
                .startDate(startDate)
                .endDate(endDate)
                .bullets(historicalBullets(bullets))
                .build();
    }

    private ObjectNode objectOrNull(JsonNode node) {
        return node instanceof ObjectNode object ? object : null;
    }

    private String historicalTextNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new BusinessException(500, "简历结构化内容中的文本字段格式不正确");
        }
        return trimToNull(node.textValue());
    }

    private List<String> historicalStringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (value == null || value.isNull()) {
                values.add(null);
            } else if (!value.isTextual()) {
                throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
            } else {
                values.add(value.textValue());
            }
        }
        return values;
    }

    private ResumeSourceRefDTO historicalSourceReference(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return strictObjectMapper.treeToValue(node, ResumeSourceRefDTO.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历来源引用格式不正确");
        }
    }

    private void appendHistoricalTextNodes(List<String> values, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
        }
        for (JsonNode child : node) {
            if (child == null || child.isNull()) {
                continue;
            }
            if (child.isTextual()) {
                appendHistoricalValue(values, child.textValue());
            } else if (child instanceof ObjectNode bullet) {
                validateHistoricalBulletObject(bullet);
                String text = historicalTextNode(bullet.get("text"));
                if (text == null) {
                    throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
                }
                appendHistoricalValue(values, text);
            } else {
                throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
            }
        }
    }

    private void validateHistoricalBulletObject(ObjectNode bullet) {
        Set<String> allowed = Set.of("text", "id", "sourceRef", "sourceOccurrenceIds");
        bullet.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
            }
        });
        historicalTextNode(bullet.get("text"));
        historicalTextNode(bullet.get("id"));
        validateHistoricalSourceReference(bullet.get("sourceRef"));
        validateHistoricalStringArray(bullet.get("sourceOccurrenceIds"),
                "简历结构化内容中的文本列表格式不正确");
    }

    private void validateHistoricalSourceReference(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        try {
            strictObjectMapper.treeToValue(node, ResumeSourceRefDTO.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历来源引用格式不正确");
        }
    }

    private void validateHistoricalStringArray(JsonNode node, String message) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            throw new BusinessException(500, message);
        }
        for (JsonNode value : node) {
            if (value != null && !value.isNull() && !value.isTextual()) {
                throw new BusinessException(500, message);
            }
        }
    }

    private void appendHistoricalBulletNode(ObjectNode entry, String value) {
        String text = trimToNull(value);
        if (text == null) {
            return;
        }
        JsonNode node = entry.get("bullets");
        ArrayNode bullets;
        if (node == null || node.isNull()) {
            bullets = objectMapper.createArrayNode();
            entry.set("bullets", bullets);
        } else if (node instanceof ArrayNode array) {
            bullets = array;
        } else {
            throw new BusinessException(500, "简历要点格式不正确");
        }
        ObjectNode bullet = objectMapper.createObjectNode();
        bullet.put("text", text);
        bullets.add(bullet);
    }

    private void appendHistoricalStringNode(ObjectNode entry, String field, String value) {
        String text = trimToNull(value);
        if (text == null) {
            return;
        }
        JsonNode node = entry.get(field);
        ArrayNode values;
        if (node == null || node.isNull()) {
            values = objectMapper.createArrayNode();
            entry.set(field, values);
        } else if (node instanceof ArrayNode array) {
            values = array;
        } else {
            throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
        }
        values.add(text);
    }

    private void appendHistoricalStringNodeIfMissing(ObjectNode entry, String field, String value) {
        String text = trimToNull(value);
        if (text == null) {
            return;
        }
        JsonNode node = entry.get(field);
        if (node instanceof ArrayNode values) {
            for (JsonNode existing : values) {
                String existingText = historicalTextNode(existing);
                if (sameHistoricalText(existingText, text)) {
                    return;
                }
            }
        } else if (node != null && !node.isNull()) {
            throw new BusinessException(500, "简历结构化内容中的文本列表格式不正确");
        }
        appendHistoricalStringNode(entry, field, text);
    }

    private String removeHistoricalDateRangeRemainder(String value, Matcher range) {
        return (value.substring(0, range.start()) + " " + value.substring(range.end()))
                .replaceAll("[\\s·•\\-–—~～]+", " ")
                .strip();
    }

    private boolean historicalLooksLikeDate(String value) {
        return value != null && (value.matches(".*(?:19|20)\\d{2}.*")
                || value.matches("(?i)^(?:至今|今|现在|present)$"));
    }

    private boolean historicalIsStandaloneDate(String value) {
        return value != null && (EDUCATION_DATE_RANGE.matcher(value.strip()).matches()
                || value.strip().matches("(?i)^(?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?|至今|今|现在|present$"));
    }

    private boolean hasSourceMaterial(ResumeStructuredContentDTO content) {
        if (content == null) {
            return false;
        }
        if (content.getRawText() != null && !content.getRawText().isBlank()) {
            return true;
        }
        if (content.getIndexedLines() != null && content.getIndexedLines().stream()
                .anyMatch(line -> line != null && line.getText() != null && !line.getText().isBlank())) {
            return true;
        }
        if (content.getRawSections() != null && content.getRawSections().stream()
                .filter(section -> section != null && section.getBlocks() != null)
                .flatMap(section -> section.getBlocks().stream())
                .anyMatch(block -> block != null && block.getText() != null && !block.getText().isBlank())) {
            return true;
        }
        // The old ResumeTextSectionDTO is also used as a display-only historical projection.
        // Only a block carrying an explicit source identity is evidence; plain lines/blocks must
        // stay on the source-free compatibility path so their IDs are never fabricated.
        return content.getSections() != null && content.getSections().stream()
                .filter(section -> section != null && section.getBlocks() != null)
                .flatMap(section -> section.getBlocks().stream())
                .anyMatch(this::hasExplicitHistoricalSourceIdentity);
    }

    private boolean hasExplicitHistoricalSourceIdentity(ResumeBlockDTO block) {
        // A historical text-section block ID is only a logical/display identity. It cannot
        // establish an evidence boundary by itself; require an explicit occurrence marker so
        // source-free compatibility snapshots never gain synthetic provenance merely because a
        // UI row happened to have an ID.
        return block != null && hasText(block.getText())
                && !nonBlankIds(block.getSourceOccurrenceIds()).isEmpty();
    }

    /**
     * Recover only the old flat compatibility fields from retained source rows. The source rows
     * remain the evidence boundary and are still validated by SourceContext during build().
     */
    private void populateCompatibilityFieldsFromSource(ResumeStructuredContentDTO content) {
        SourceContext source = new SourceContext(content);
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("EDUCATION", new ArrayList<>());
        values.put("SKILLS", new ArrayList<>());
        values.put("PROJECTS", new ArrayList<>());
        values.put("WORK_EXPERIENCES", new ArrayList<>());
        values.put("INTERNSHIPS", new ArrayList<>());
        values.put("CAMPUS_EXPERIENCES", new ArrayList<>());
        values.put("AWARDS", new ArrayList<>());
        values.put("CERTIFICATES", new ArrayList<>());
        values.put("SUMMARY", new ArrayList<>());
        values.put("OTHERS", new ArrayList<>());
        for (ResumeSourceEvidenceMatcher.Occurrence occurrence : source.occurrences) {
            String text = trimToNull(occurrence.text());
            if (text == null) {
                continue;
            }
            String section = ResumeSourceEvidenceMatcher.normalizeSection(occurrence.section());
            // GENERAL/UNKNOWN is an evidence location, not a semantic "other" section. Do not
            // turn every raw-text line into an explicit `others` candidate: the canonical build
            // and its coverage pass must decide whether an unclassified row is represented.
            if (!values.containsKey(section)
                    || "GENERAL".equals(section)
                    || "UNKNOWN".equals(section)) {
                continue;
            }
            values.get(section).add(text);
        }
        setIfEmpty(content, "EDUCATION", values.get("EDUCATION"));
        setIfEmpty(content, "SKILLS", values.get("SKILLS"));
        setIfEmpty(content, "PROJECTS", values.get("PROJECTS"));
        setIfEmpty(content, "WORK_EXPERIENCES", values.get("WORK_EXPERIENCES"));
        setIfEmpty(content, "INTERNSHIPS", values.get("INTERNSHIPS"));
        setIfEmpty(content, "CAMPUS_EXPERIENCES", values.get("CAMPUS_EXPERIENCES"));
        setIfEmpty(content, "AWARDS", values.get("AWARDS"));
        setIfEmpty(content, "CERTIFICATES", values.get("CERTIFICATES"));
        setIfEmpty(content, "OTHERS", values.get("OTHERS"));
        if (trimToNull(content.getSummary()) == null && !values.get("SUMMARY").isEmpty()) {
            content.setSummary(String.join(" ", values.get("SUMMARY")));
        }
    }

    private void setIfEmpty(ResumeStructuredContentDTO content, String field, List<String> values) {
        if (content == null || values == null || values.isEmpty()) {
            return;
        }
        List<String> copy = new ArrayList<>(values);
        switch (field) {
            case "EDUCATION" -> { if (content.getEducation() == null || content.getEducation().isEmpty()) content.setEducation(copy); }
            case "SKILLS" -> { if (content.getSkills() == null || content.getSkills().isEmpty()) content.setSkills(copy); }
            case "PROJECTS" -> { if (content.getProjects() == null || content.getProjects().isEmpty()) content.setProjects(copy); }
            case "WORK_EXPERIENCES" -> { if (content.getWorkExperiences() == null || content.getWorkExperiences().isEmpty()) content.setWorkExperiences(copy); }
            case "INTERNSHIPS" -> { if (content.getInternships() == null || content.getInternships().isEmpty()) content.setInternships(copy); }
            case "CAMPUS_EXPERIENCES" -> { if (content.getCampusExperiences() == null || content.getCampusExperiences().isEmpty()) content.setCampusExperiences(copy); }
            case "AWARDS" -> { if (content.getAwards() == null || content.getAwards().isEmpty()) content.setAwards(copy); }
            case "CERTIFICATES" -> { if (content.getCertificates() == null || content.getCertificates().isEmpty()) content.setCertificates(copy); }
            case "OTHERS" -> { if (content.getOthers() == null || content.getOthers().isEmpty()) content.setOthers(copy); }
            default -> { }
        }
    }

    /** Merge legacy top-level compatibility fields into a partial structured projection. */
    private void mergeCompatibilityStructuredData(ResumeStructuredContentDTO content) {
        ResumeStructuredDataDTO data = content == null ? null : content.getStructuredData();
        if (content == null || data == null) {
            return;
        }

        List<ResumeExperienceDTO> experiences = new ArrayList<>(safeList(data.getExperiences()));
        appendCompatibilityExperiences(
                experiences, historicalExperienceValueCounts(experiences, "WORK"),
                content.getWorkExperiences(), "WORK");
        appendCompatibilityExperiences(
                experiences, historicalExperienceValueCounts(experiences, "INTERNSHIP"),
                content.getInternships(), "INTERNSHIP");
        appendCompatibilityExperiences(
                experiences, historicalExperienceValueCounts(experiences, "CAMPUS"),
                content.getCampusExperiences(), "CAMPUS");

        List<ResumeProjectDTO> projects = new ArrayList<>(safeList(data.getProjects()));
        for (String value : historicalProjectCompatibilityValues(content.getProjects(), projects)) {
            projects.add(ResumeProjectDTO.builder().name(value).description(value).build());
        }
        List<ResumeAchievementDTO> achievements = new ArrayList<>(safeList(data.getAchievements()));
        for (String value : historicalAchievementCompatibilityValues(content.getAwards(), achievements)) {
            achievements.add(ResumeAchievementDTO.builder().title(value).build());
        }

        ResumeSkillSetDTO skills = mergeCompatibilitySkills(data.getSkills(), content.getSkills());
        content.setStructuredData(ResumeStructuredDataDTO.builder()
                .education(historicalValues(data.getEducation(), content.getEducation()))
                .educationSourceRefs(data.getEducationSourceRefs())
                .skills(skills)
                .experiences(experiences)
                .projects(projects)
                .achievements(achievements)
                .certificates(historicalValues(data.getCertificates(), content.getCertificates()))
                .summary(firstHistoricalValue(data.getSummary(), content.getSummary()))
                .summarySourceRef(data.getSummarySourceRef())
                .others(historicalValues(data.getOthers(), content.getOthers()))
                .build());
    }

    private void appendCompatibilityExperiences(
            List<ResumeExperienceDTO> target,
            Map<String, Integer> represented,
            List<String> values,
            String type) {
        for (String value : historicalValues(values, null)) {
            String key = historicalKey(value);
            int count = represented.getOrDefault(key, 0);
            if (count > 0) {
                represented.put(key, count - 1);
                continue;
            }
            target.add(ResumeExperienceDTO.builder()
                    .type(type)
                    .description(value)
                    .bullets(List.of(value))
                    .evidence(List.of(value))
                    .build());
        }
    }

    private ResumeSkillSetDTO mergeCompatibilitySkills(
            ResumeSkillSetDTO semantic,
            List<String> compatibilityKeywords) {
        if (semantic == null) {
            return compatibilityKeywords == null
                    ? null
                    : ResumeSkillSetDTO.builder().keywords(historicalValues(null, compatibilityKeywords)).build();
        }
        List<String> keywords = historicalValues(semantic.getKeywords(), compatibilityKeywords);
        if (semantic.getGroups() == null) {
            return ResumeSkillSetDTO.builder()
                    .keywords(keywords)
                    .groups(null)
                    .descriptions(semantic.getDescriptions())
                    .evidence(semantic.getEvidence())
                    .build();
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, Integer> represented = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> group : semantic.getGroups().entrySet()) {
            List<String> values = new ArrayList<>(historicalValues(group.getValue(), null));
            groups.put(group.getKey(), values);
            for (String value : values) {
                represented.merge(historicalKey(value), 1, Integer::sum);
            }
        }
        List<String> residual = new ArrayList<>();
        for (String keyword : keywords) {
            String key = historicalKey(keyword);
            int count = represented.getOrDefault(key, 0);
            if (count > 0) {
                represented.put(key, count - 1);
            } else {
                residual.add(keyword);
            }
        }
        if (!residual.isEmpty()) {
            groups.computeIfAbsent("other", ignored -> new ArrayList<>()).addAll(residual);
        }
        return ResumeSkillSetDTO.builder()
                .keywords(keywords)
                .groups(groups)
                .descriptions(semantic.getDescriptions())
                .evidence(semantic.getEvidence())
                .build();
    }

    /**
     * Project a historical structured snapshot without manufacturing a source corpus.
     *
     * <p>Older task snapshots can contain useful structured values but no raw text, indexed
     * lines, or raw blocks. They remain readable as a compatibility projection, but none of the
     * values can be re-proven as source evidence here. Consequently this method copies values
     * directly, leaves provenance fields empty, and keeps ambiguous values in generic semantic
     * fields/bullets rather than guessing or silently dropping them.</p>
     */
    private BuildResult buildHistoricalCompatibilityProjection(
            ResumeStructuredContentDTO content,
            List<ResumeDocumentEntryDTO> educationEntries,
            boolean educationProjectionPresent) {
        List<ResumeUnresolvedItemDTO> unresolved = new ArrayList<>();
        List<ResumeDocumentSectionDTO> sections = new ArrayList<>();
        ResumeStructuredDataDTO data = content == null ? null : content.getStructuredData();
        ResumeDisplayModelDTO displayModel = historicalDisplayModel(content);

        String summary = firstHistoricalValue(
                data == null ? null : data.getSummary(), content == null ? null : content.getSummary());
        if (summary != null) {
            sections.add(historicalSection(
                    ResumeDocumentSectionKind.SUMMARY, "个人总结",
                    List.of(historicalGenericEntry(List.of(summary)))));
        }

        List<ResumeExperienceDTO> experiences = data == null
                ? List.of() : safeList(data.getExperiences());
        appendHistoricalExperienceSections(sections, experiences);
        appendHistoricalExperienceCompatibility(sections, content, experiences);

        List<ResumeProjectDTO> projects = data == null ? List.of() : safeList(data.getProjects());
        List<ResumeDocumentEntryDTO> projectEntries = new ArrayList<>();
        for (ResumeProjectDTO project : projects) {
            if (hasHistoricalProjectContent(project)) {
                projectEntries.add(historicalProjectEntry(project));
            }
        }
        if (content != null) {
            if (projects.isEmpty()) {
                projectEntries.addAll(historicalProjects(content.getProjects()).stream()
                        .map(this::historicalProjectEntry)
                        .toList());
            } else {
                for (String value : historicalProjectCompatibilityValues(content.getProjects(), projects)) {
                    projectEntries.add(historicalProjectEntry(
                            ResumeProjectDTO.builder().name(value).description(value).build()));
                }
            }
        }
        if (!projectEntries.isEmpty()) {
            sections.add(historicalSection(
                    ResumeDocumentSectionKind.PROJECT, "项目经历", projectEntries));
        }

        List<String> education = historicalValues(
                data == null ? null : data.getEducation(), content == null ? null : content.getEducation());
        List<ResumeDocumentEntryDTO> projectedEducation = educationProjectionPresent
                ? new ArrayList<>(safeList(educationEntries))
                : new ArrayList<>(education.stream().map(this::historicalEducationEntry).toList());
        if (educationProjectionPresent) {
            stripHistoricalProvenance(projectedEducation);
        }
        if (!projectedEducation.isEmpty()) {
            sections.add(historicalSection(
                    ResumeDocumentSectionKind.EDUCATION, "教育经历", projectedEducation));
        }

        ResumeSkillSetDTO skills = data == null ? null : data.getSkills();
        appendHistoricalSkillSection(sections, skills, content == null ? null : content.getSkills());

        List<ResumeAchievementDTO> achievements = data == null
                ? List.of() : safeList(data.getAchievements());
        List<ResumeDocumentEntryDTO> achievementEntries = new ArrayList<>();
        for (ResumeAchievementDTO achievement : achievements) {
            if (hasHistoricalAchievementContent(achievement)) {
                achievementEntries.add(historicalAchievementEntry(achievement));
            }
        }
        if (content != null) {
            if (achievements.isEmpty()) {
                achievementEntries.addAll(historicalAchievements(content.getAwards()).stream()
                        .map(this::historicalAchievementEntry)
                        .toList());
            } else {
                for (String value : historicalAchievementCompatibilityValues(content.getAwards(), achievements)) {
                    achievementEntries.add(historicalAchievementEntry(
                            ResumeAchievementDTO.builder().title(value).build()));
                }
            }
        }
        if (!achievementEntries.isEmpty()) {
            sections.add(historicalSection(ResumeDocumentSectionKind.ACHIEVEMENT, "荣誉奖项", achievementEntries));
        }

        List<String> certificates = historicalValues(
                data == null ? null : data.getCertificates(), content == null ? null : content.getCertificates());
        if (!certificates.isEmpty()) {
            sections.add(historicalSection(
                    ResumeDocumentSectionKind.CERTIFICATE, "证书",
                    certificates.stream().map(value -> historicalGenericEntry(List.of(value))).toList()));
        }

        List<String> others = historicalValues(
                data == null ? null : data.getOthers(), content == null ? null : content.getOthers());
        if (!others.isEmpty()) {
            sections.add(historicalSection(
                    ResumeDocumentSectionKind.OTHER, "其他",
                    others.stream().map(value -> historicalGenericEntry(List.of(value))).toList()));
        }

        // displayModel is a presentation snapshot, not a new evidence boundary. It is used only
        // as a read-only fallback for old rows that lack their semantic projection; all generated
        // entries deliberately carry no source references.
        appendHistoricalDisplayFallbackSections(sections, content, data, displayModel);

        ResumeDocumentBasicsDTO basics = historicalBasics(content);
        // Some legacy snapshots have only the old ResumeTextSectionDTO projection. Preserve rows
        // that are not already represented by semantic fields, but do not count a compatibility
        // projection as a second canonical claim.
        appendHistoricalTextSections(
                sections, content == null ? null : content.getSections(), basics);

        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(basics)
                .sections(sections)
                .build();
        assignDeterministicIds(document, unresolved);
        return new BuildResult(document, unresolved);
    }

    private ResumeDisplayModelDTO historicalDisplayModel(ResumeStructuredContentDTO content) {
        List<ResumeDisplayModelDTO> candidates = historicalDisplayModels(content);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        // Historical rows can contain ruleDisplayModel and aiDisplayModel side by side. Merge
        // views by field instead of letting an overview-only first model hide later cards. The
        // fallback projection below performs occurrence-safe content deduplication.
        ResumeDisplayModelDTO first = candidates.get(0);
        return ResumeDisplayModelDTO.builder()
                .overview(mergeHistoricalOverviews(candidates))
                .skillSummary(mergeHistoricalSkillSummaries(candidates))
                .educationCards(concatHistoricalLists(candidates, ResumeDisplayModelDTO::getEducationCards))
                .workExperienceCards(concatHistoricalLists(candidates, ResumeDisplayModelDTO::getWorkExperienceCards))
                .internshipCards(concatHistoricalLists(candidates, ResumeDisplayModelDTO::getInternshipCards))
                .campusExperienceCards(concatHistoricalLists(candidates, ResumeDisplayModelDTO::getCampusExperienceCards))
                .projectCards(concatHistoricalLists(candidates, ResumeDisplayModelDTO::getProjectCards))
                .achievementCards(concatHistoricalLists(candidates, ResumeDisplayModelDTO::getAchievementCards))
                .certificateTags(concatHistoricalLists(candidates, ResumeDisplayModelDTO::getCertificateTags))
                .summaryCard(mergeHistoricalSummaryCards(candidates))
                .pendingItems(concatHistoricalLists(candidates, ResumeDisplayModelDTO::getPendingItems))
                .displayMeta(first.getDisplayMeta())
                .build();
    }

    private List<ResumeDisplayModelDTO> historicalDisplayModels(ResumeStructuredContentDTO content) {
        if (content == null) {
            return List.of();
        }
        return java.util.stream.Stream.of(
                        content.getDisplayModel(), content.getRuleDisplayModel(), content.getAiDisplayModel())
                .filter(this::hasDisplayModelContent)
                .toList();
    }

    private boolean hasDisplayModelContent(ResumeDisplayModelDTO model) {
        if (model == null) {
            return false;
        }
        return model.getOverview() != null
                || model.getSkillSummary() != null
                || !safeList(model.getEducationCards()).isEmpty()
                || !safeList(model.getWorkExperienceCards()).isEmpty()
                || !safeList(model.getInternshipCards()).isEmpty()
                || !safeList(model.getCampusExperienceCards()).isEmpty()
                || !safeList(model.getProjectCards()).isEmpty()
                || !safeList(model.getAchievementCards()).isEmpty()
                || !safeList(model.getCertificateTags()).isEmpty()
                || model.getSummaryCard() != null
                || !safeList(model.getPendingItems()).isEmpty();
    }

    private void mergeHistoricalBasics(
            ResumeDocumentBasicsDTO target,
            ResumeDocumentBasicsDTO fallback) {
        if (target == null || fallback == null) {
            return;
        }
        if (hasText(fallback.getName())) {
            target.setName(mergeHistoricalScalarValues(target.getName(), fallback.getName()));
        }
        if (hasText(fallback.getJobIntention())) {
            target.setJobIntention(mergeHistoricalScalarValues(
                    target.getJobIntention(), fallback.getJobIntention()));
        }
        if (hasText(fallback.getHighestEducation())) {
            target.setHighestEducation(mergeHistoricalScalarValues(
                    target.getHighestEducation(), fallback.getHighestEducation()));
        }
        if (target.getContacts() == null) {
            target.setContacts(new ArrayList<>());
        }
        for (ResumeDocumentContactDTO candidate : safeList(fallback.getContacts())) {
            if (candidate == null || !hasText(candidate.getValue())) {
                continue;
            }
            boolean alreadyPresent = target.getContacts().stream()
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(existing -> java.util.Objects.equals(existing.getType(), candidate.getType())
                            && sameHistoricalText(existing.getValue(), candidate.getValue()));
            if (!alreadyPresent) {
                target.getContacts().add(candidate);
            }
        }
    }

    private void appendHistoricalDisplayFallbackSections(
            List<ResumeDocumentSectionDTO> sections,
            ResumeStructuredContentDTO content,
            ResumeStructuredDataDTO data,
            ResumeDisplayModelDTO display) {
        if (display == null) {
            return;
        }
        // Do not gate an entire category on one semantic row. A mixed historical snapshot may
        // contain semantic A and display-only B; append only display entries not already present.
        if (display.getSummaryCard() != null && hasText(display.getSummaryCard().getContent())) {
            ResumeDocumentEntryDTO summaryEntry = ResumeDocumentEntryDTO.builder()
                    .sourceOccurrenceIds(historicalDisplayOccurrenceIds(display.getSummaryCard().getSourceRef()))
                    .bullets(historicalBullets(List.of(display.getSummaryCard().getContent())))
                    .build();
            appendHistoricalSectionEntries(
                    sections, ResumeDocumentSectionKind.SUMMARY, "个人总结", List.of(summaryEntry));
        }
        addHistoricalDisplayExperienceSection(sections, "工作经历", display.getWorkExperienceCards());
        addHistoricalDisplayExperienceSection(sections, "实习经历", display.getInternshipCards());
        addHistoricalDisplayExperienceSection(sections, "校园经历", display.getCampusExperienceCards());
        appendHistoricalSectionEntries(sections, ResumeDocumentSectionKind.PROJECT, "项目经历",
                historicalDisplayProjects(display.getProjectCards()));
        appendHistoricalSectionEntries(sections, ResumeDocumentSectionKind.EDUCATION, "教育经历",
                historicalDisplayEducation(display.getEducationCards()));
        appendHistoricalSectionEntries(sections, ResumeDocumentSectionKind.SKILL, "技能",
                historicalDisplaySkills(display));
        appendHistoricalSectionEntries(sections, ResumeDocumentSectionKind.ACHIEVEMENT, "荣誉奖项",
                historicalDisplayAchievements(display.getAchievementCards()));
        appendHistoricalSectionEntries(sections, ResumeDocumentSectionKind.CERTIFICATE, "证书",
                historicalDisplayCertificates(display.getCertificateTags()));
        appendHistoricalSectionEntries(sections, ResumeDocumentSectionKind.OTHER, "其他",
                historicalValues(display.getPendingItems(), null).stream()
                        .map(value -> historicalGenericEntry(List.of(value)))
                        .toList());
    }

    private void appendHistoricalEducationEntriesPreservingRows(
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeUnresolvedItemDTO> unresolved,
            List<ResumeDocumentEntryDTO> candidates,
            SourceContext provenance) {
        ResumeDocumentSectionDTO target = sections.stream()
                .filter(section -> section != null
                        && ResumeDocumentSectionKind.EDUCATION.name().equals(section.getKind())
                        && java.util.Objects.equals(normalizeHistoricalTitle(section.getTitle()), "教育经历"))
                .findFirst()
                .orElse(null);
        List<ResumeDocumentEntryDTO> existing = target == null || target.getEntries() == null
                ? new ArrayList<>() : target.getEntries();
        Set<String> occupiedOccurrences = new LinkedHashSet<>();
        existing.stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(entry -> historicalEntryOccurrenceIds(entry).stream())
                .forEach(occupiedOccurrences::add);

        List<ResumeDocumentEntryDTO> valid = new ArrayList<>();
        for (ResumeDocumentEntryDTO candidate : safeList(candidates)) {
            if (candidate == null || !hasHistoricalEntryContent(candidate)) {
                continue;
            }
            Set<String> candidateOccurrences = historicalEntryOccurrenceIds(candidate);
            SourceContext.EducationProof proof = provenance == null
                    ? new SourceContext.EducationProof(Set.of())
                    : provenance.proveHistoricalEducation(
                            historicalEducationClaims(candidate),
                            candidate.getSourceRef(),
                            candidateOccurrences,
                            candidateOccurrences.isEmpty() ? occupiedOccurrences : Set.of());
            if (proof == null) {
                addHistoricalEducationCandidate(unresolved, candidate);
                continue;
            }
            boolean mirrored = !proof.occurrenceIds().isEmpty()
                    && (!java.util.Collections.disjoint(occupiedOccurrences, proof.occurrenceIds())
                    || existing.stream().filter(java.util.Objects::nonNull).anyMatch(entry ->
                    !java.util.Collections.disjoint(
                            historicalEntryOccurrenceIds(entry), proof.occurrenceIds())));
            occupiedOccurrences.addAll(proof.occurrenceIds());
            if (mirrored) {
                // A source-backed semantic row already owns this source occurrence. Suppress only
                // this proven mirror; a later identical candidate can still consume another
                // occurrence and therefore remains independently representable.
                continue;
            }
            valid.add(candidate);
        }
        if (valid.isEmpty()) {
            return;
        }
        if (target == null) {
            sections.add(historicalSection(
                    ResumeDocumentSectionKind.EDUCATION, "教育经历", new ArrayList<>(valid)));
            return;
        }
        existing.addAll(valid);
        target.setEntries(existing);
    }

    private List<String> historicalEducationClaims(ResumeDocumentEntryDTO candidate) {
        List<String> claims = new ArrayList<>();
        if (candidate == null) {
            return claims;
        }
        for (String value : Stream.of(
                candidate.getSchool(), candidate.getDegree(), candidate.getMajor(),
                candidate.getStartDate(), candidate.getEndDate()).toList()) {
            String text = trimToNull(value);
            if (text != null) {
                claims.add(text);
            }
        }
        for (ResumeDocumentBulletDTO bullet : safeList(candidate.getBullets())) {
            if (bullet != null) {
                String text = trimToNull(bullet.getText());
                if (text != null) {
                    claims.add(text);
                }
            }
        }
        return claims;
    }

    private void addHistoricalEducationCandidate(
            List<ResumeUnresolvedItemDTO> unresolved, ResumeDocumentEntryDTO candidate) {
        if (unresolved == null || unresolved.size() >= MAX_UNRESOLVED_ITEMS) {
            throw new BusinessException(500, "未表示内容超过审查上限，请重新整理或重新解析简历");
        }
        String sourceText = candidate.getSourceRef() == null
                ? null : trimToNull(candidate.getSourceRef().getText());
        // Historical references are only a validation hint. Do not expose them in the review
        // draft where an explicit accept could otherwise promote them into TARGET provenance.
        stripHistoricalProvenance(List.of(candidate));
        try {
            ObjectNode draft = objectMapper.valueToTree(candidate);
            draft.put("kind", ResumeDocumentSectionKind.EDUCATION.name());
            unresolved.add(ResumeUnresolvedItemDTO.builder()
                    .kind(ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE)
                    .canonicalDraft(objectMapper.writeValueAsString(draft))
                    .sourceRef(sourceText)
                    .reason("历史教育候选无法由当前来源确认，请接受或删除")
                    .build());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(500, "候选条目序列化失败");
        }
    }

    private void appendHistoricalSectionEntries(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            List<ResumeDocumentEntryDTO> candidates) {
        List<ResumeDocumentEntryDTO> valid = safeList(candidates).stream()
                .filter(java.util.Objects::nonNull)
                .filter(this::hasHistoricalEntryContent)
                .toList();
        if (valid.isEmpty()) {
            return;
        }
        ResumeDocumentSectionDTO target = sections.stream()
                .filter(section -> section != null && kind.name().equals(section.getKind())
                        && java.util.Objects.equals(normalizeHistoricalTitle(section.getTitle()),
                        normalizeHistoricalTitle(title)))
                .findFirst()
                .orElse(null);
        if (target == null) {
            List<ResumeDocumentEntryDTO> added = new ArrayList<>(valid);
            stripHistoricalProvenance(added);
            sections.add(historicalSection(kind, title, added));
            return;
        }
        List<ResumeDocumentEntryDTO> existing = target.getEntries() == null
                ? new ArrayList<>() : target.getEntries();
        for (ResumeDocumentEntryDTO candidate : valid) {
            if (existing.stream().noneMatch(entry -> historicalFallbackEntriesEquivalent(entry, candidate))) {
                existing.add(candidate);
            }
        }
        // Display cards are a compatibility view. They may use occurrence IDs while deciding
        // whether two historical views mirror one another, but those IDs are never promoted to
        // canonical provenance by this fallback path.
        stripHistoricalProvenance(valid);
        target.setEntries(existing);
    }

    private boolean historicalFallbackEntriesEquivalent(
            ResumeDocumentEntryDTO existing, ResumeDocumentEntryDTO candidate) {
        if (historicalEntriesEquivalent(existing, candidate)) {
            return true;
        }
        // A display-only card without an explicit occurrence identity can mirror a source-backed
        // semantic row. Its content key may suppress that mirror; equal text with two explicit
        // occurrence IDs remains separate.
        return historicalEntryOccurrenceIds(candidate).isEmpty()
                && existing != null && candidate != null
                && historicalEntryKey(existing).equals(historicalEntryKey(candidate));
    }

    private void stripHistoricalProvenance(List<ResumeDocumentEntryDTO> entries) {
        for (ResumeDocumentEntryDTO entry : safeList(entries)) {
            if (entry == null) {
                continue;
            }
            entry.setSourceRef(null);
            entry.setSourceOccurrenceIds(null);
            entry.setFieldSourceRefs(null);
            entry.setSkillItemSourceRefs(null);
            entry.setSkillDescriptionSourceRefs(null);
            entry.setTechStackSourceRefs(null);
        }
    }

    private boolean historicalEntriesEquivalent(
            ResumeDocumentEntryDTO left, ResumeDocumentEntryDTO right) {
        if (left == null || right == null) {
            return false;
        }
        Set<String> leftOccurrences = historicalEntryOccurrenceIds(left);
        Set<String> rightOccurrences = historicalEntryOccurrenceIds(right);
        if (!leftOccurrences.isEmpty() || !rightOccurrences.isEmpty()) {
            // Explicit occurrence identity is the only authority for mirrored historical views;
            // equal text with different occurrence IDs is a repeated fact, not a duplicate view.
            return !leftOccurrences.isEmpty() && leftOccurrences.equals(rightOccurrences);
        }
        return historicalEntryKey(left).equals(historicalEntryKey(right));
    }

    private Set<String> historicalEntryOccurrenceIds(ResumeDocumentEntryDTO entry) {
        Set<String> ids = new LinkedHashSet<>();
        if (entry == null) {
            return ids;
        }
        ids.addAll(nonBlankIds(entry.getSourceOccurrenceIds()));
        if (entry.getSourceRef() != null) {
            ids.addAll(nonBlankIds(entry.getSourceRef().getSourceOccurrenceIds()));
        }
        return ids;
    }

    private List<ResumeDocumentEntryDTO> mergeHistoricalEducationViews(
            List<ResumeDocumentEntryDTO> primary, List<ResumeDocumentEntryDTO> compatibility) {
        List<ResumeDocumentEntryDTO> result = new ArrayList<>(safeList(primary));
        int primarySize = result.size();
        boolean[] consumed = new boolean[primarySize];
        for (ResumeDocumentEntryDTO candidate : safeList(compatibility)) {
            int match = -1;
            for (int index = 0; index < primarySize; index++) {
                if (!consumed[index]
                        && historicalEducationEntriesEquivalent(result.get(index), candidate)) {
                    match = index;
                    break;
                }
            }
            if (match >= 0) {
                consumed[match] = true;
            } else {
                result.add(candidate);
            }
        }
        return result;
    }

    private boolean historicalEducationEntriesEquivalent(
            ResumeDocumentEntryDTO left, ResumeDocumentEntryDTO right) {
        if (historicalEntriesEquivalent(left, right)) {
            return true;
        }
        if (left == null || right == null
                || !historicalEntryOccurrenceIds(left).isEmpty()
                || !historicalEntryOccurrenceIds(right).isEmpty()
                || !sameHistoricalText(left.getSchool(), right.getSchool())) {
            return false;
        }
        if (!compatibleHistoricalField(left.getDegree(), right.getDegree())
                || !compatibleHistoricalField(left.getMajor(), right.getMajor())
                || !compatibleHistoricalField(left.getStartDate(), right.getStartDate())
                || !compatibleHistoricalField(left.getEndDate(), right.getEndDate())) {
            return false;
        }
        return historicalEducationIsSchoolOnly(left) || historicalEducationIsSchoolOnly(right);
    }

    private boolean compatibleHistoricalField(String left, String right) {
        return !hasText(left) || !hasText(right) || sameHistoricalText(left, right);
    }

    private boolean historicalEducationIsSchoolOnly(ResumeDocumentEntryDTO entry) {
        return entry != null && hasText(entry.getSchool())
                && !hasText(entry.getDegree()) && !hasText(entry.getMajor())
                && !hasText(entry.getStartDate()) && !hasText(entry.getEndDate())
                && safeList(entry.getBullets()).isEmpty();
    }

    private String historicalEntryKey(ResumeDocumentEntryDTO entry) {
        if (entry == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add(historicalKey(entry.getHeading()));
        parts.add(historicalKey(entry.getMeta()));
        parts.add(historicalKey(entry.getOrganization()));
        parts.add(historicalKey(entry.getRole()));
        parts.add(historicalKey(entry.getSchool()));
        parts.add(historicalKey(entry.getDegree()));
        parts.add(historicalKey(entry.getMajor()));
        parts.add(historicalKey(entry.getStartDate()));
        parts.add(historicalKey(entry.getEndDate()));
        parts.add(historicalKey(entry.getLocation()));
        parts.add(historicalKey(entry.getEnvironment()));
        parts.add(historicalKey(entry.getMentor()));
        parts.add(historicalKey(entry.getGroup()));
        parts.add(historicalKey(entry.getAwardTitle()));
        parts.add(historicalKey(entry.getAwardLevel()));
        parts.add(historicalKey(entry.getAwardCompetition()));
        parts.add(historicalKey(entry.getAwardRanking()));
        parts.add(historicalKey(entry.getAwardDate()));
        parts.add(String.join("\u001f", historicalValues(entry.getTechStack(), null).stream()
                .map(this::historicalKey).toList()));
        parts.add(String.join("\u001f", historicalValues(entry.getSkillItems(), null).stream()
                .map(this::historicalKey).toList()));
        parts.add(String.join("\u001f", historicalValues(entry.getSkillDescriptions(), null).stream()
                .map(this::historicalKey).toList()));
        parts.add(String.join("\u001f", safeList(entry.getBullets()).stream()
                .filter(java.util.Objects::nonNull)
                .map(ResumeDocumentBulletDTO::getText)
                .map(this::historicalKey)
                .toList()));
        return String.join("\u001e", parts);
    }

    private String normalizeHistoricalTitle(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private ResumeDisplayModelDTO.Overview mergeHistoricalOverviews(
            List<ResumeDisplayModelDTO> models) {
        return ResumeDisplayModelDTO.Overview.builder()
                .name(mergeHistoricalTextValues(models,
                        model -> model.getOverview() == null ? null : model.getOverview().getName()))
                .targetRole(mergeHistoricalTextValues(models,
                        model -> model.getOverview() == null ? null : model.getOverview().getTargetRole()))
                .resumeType(mergeHistoricalTextValues(models,
                        model -> model.getOverview() == null ? null : model.getOverview().getResumeType()))
                .highestDegree(mergeHistoricalTextValues(models,
                        model -> model.getOverview() == null ? null : model.getOverview().getHighestDegree()))
                .workYears(mergeHistoricalTextValues(models,
                        model -> model.getOverview() == null ? null : model.getOverview().getWorkYears()))
                .coreSkills(mergeHistoricalStringLists(models, model -> model.getOverview() == null
                        ? null : model.getOverview().getCoreSkills()))
                .build();
    }

    private ResumeDisplayModelDTO.SkillSummary mergeHistoricalSkillSummaries(
            List<ResumeDisplayModelDTO> models) {
        List<ResumeDisplayModelDTO.SkillSummary> summaries = models.stream()
                .map(ResumeDisplayModelDTO::getSkillSummary)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (summaries.isEmpty()) {
            return null;
        }
        return ResumeDisplayModelDTO.SkillSummary.builder()
                .topSkills(summaries.stream().flatMap(summary -> historicalValues(summary.getTopSkills(), null).stream()).toList())
                .groups(summaries.stream().flatMap(summary -> safeList(summary.getGroups()).stream()).toList())
                .descriptions(summaries.stream()
                        .flatMap(summary -> historicalValues(summary.getDescriptions(), null).stream()).toList())
                .build();
    }

    private ResumeDisplayModelDTO.SummaryCard mergeHistoricalSummaryCards(
            List<ResumeDisplayModelDTO> models) {
        List<ResumeDisplayModelDTO.SummaryCard> cards = models.stream()
                .map(ResumeDisplayModelDTO::getSummaryCard)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (cards.isEmpty()) {
            return null;
        }
        ResumeDisplayModelDTO.SummaryCard first = cards.get(0);
        return ResumeDisplayModelDTO.SummaryCard.builder()
                .content(mergeHistoricalTextValues(cards, ResumeDisplayModelDTO.SummaryCard::getContent))
                .collapsed(firstBoolean(cards, ResumeDisplayModelDTO.SummaryCard::getCollapsed))
                .sourceRef(firstNonNull(cards, ResumeDisplayModelDTO.SummaryCard::getSourceRef))
                .build();
    }

    private <T, R> R firstNonNull(List<T> values, java.util.function.Function<T, R> getter) {
        return values.stream().map(getter).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private <T> String mergeHistoricalTextValues(
            List<T> values, java.util.function.Function<T, String> getter) {
        List<String> merged = new ArrayList<>();
        for (T value : safeList(values)) {
            String text = trimToNull(getter.apply(value));
            if (text != null && merged.stream().noneMatch(existing -> sameHistoricalText(existing, text))) {
                merged.add(text);
            }
        }
        return merged.isEmpty() ? null : String.join("\n", merged);
    }

    private Boolean firstBoolean(List<ResumeDisplayModelDTO.SummaryCard> cards,
            java.util.function.Function<ResumeDisplayModelDTO.SummaryCard, Boolean> getter) {
        return cards.stream().map(getter).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private <T> List<T> concatHistoricalLists(List<ResumeDisplayModelDTO> models,
            java.util.function.Function<ResumeDisplayModelDTO, List<T>> getter) {
        return models.stream().flatMap(model -> safeList(getter.apply(model)).stream()).toList();
    }

    private List<String> mergeHistoricalStringLists(
            List<ResumeDisplayModelDTO> models,
            java.util.function.Function<ResumeDisplayModelDTO, List<String>> getter) {
        List<String> merged = new ArrayList<>();
        for (ResumeDisplayModelDTO model : safeList(models)) {
            for (String value : safeList(getter.apply(model))) {
                String text = trimToNull(value);
                if (text != null && merged.stream().noneMatch(existing -> sameHistoricalText(existing, text))) {
                    merged.add(text);
                }
            }
        }
        return merged;
    }

    private boolean hasHistoricalSummaryProjection(
            ResumeStructuredContentDTO content, ResumeStructuredDataDTO data) {
        return hasText(data == null ? null : data.getSummary()) || hasText(content == null ? null : content.getSummary());
    }

    private boolean hasHistoricalExperienceProjection(
            ResumeStructuredContentDTO content, ResumeStructuredDataDTO data) {
        return safeList(data == null ? null : data.getExperiences()).stream()
                .anyMatch(this::hasHistoricalExperienceContent)
                || nonBlankPreserve(content == null ? null : content.getWorkExperiences()).size() > 0
                || nonBlankPreserve(content == null ? null : content.getInternships()).size() > 0
                || nonBlankPreserve(content == null ? null : content.getCampusExperiences()).size() > 0;
    }

    private boolean hasHistoricalProjectProjection(
            ResumeStructuredContentDTO content, ResumeStructuredDataDTO data) {
        return safeList(data == null ? null : data.getProjects()).stream()
                .anyMatch(this::hasHistoricalProjectContent)
                || !nonBlankPreserve(content == null ? null : content.getProjects()).isEmpty();
    }

    private boolean hasHistoricalEducationProjection(
            ResumeStructuredContentDTO content, ResumeStructuredDataDTO data) {
        return !historicalValues(data == null ? null : data.getEducation(),
                content == null ? null : content.getEducation()).isEmpty();
    }

    private boolean hasHistoricalSkillProjection(
            ResumeStructuredContentDTO content, ResumeStructuredDataDTO data) {
        ResumeSkillSetDTO skills = data == null ? null : data.getSkills();
        return skills != null && ( !historicalValues(skills.getKeywords(), null).isEmpty()
                || skills.getGroups() != null && skills.getGroups().values().stream()
                .anyMatch(values -> !historicalValues(values, null).isEmpty())
                || !historicalSkillEvidenceItems(skills).isEmpty()
                || !historicalSkillDescriptions(skills).isEmpty())
                || !nonBlankPreserve(content == null ? null : content.getSkills()).isEmpty();
    }

    private boolean hasHistoricalAchievementProjection(
            ResumeStructuredContentDTO content, ResumeStructuredDataDTO data) {
        return safeList(data == null ? null : data.getAchievements()).stream()
                .anyMatch(this::hasHistoricalAchievementContent)
                || !nonBlankPreserve(content == null ? null : content.getAwards()).isEmpty();
    }

    private boolean hasHistoricalCertificateProjection(
            ResumeStructuredContentDTO content, ResumeStructuredDataDTO data) {
        return !historicalValues(data == null ? null : data.getCertificates(),
                content == null ? null : content.getCertificates()).isEmpty();
    }

    private boolean hasHistoricalOtherProjection(
            ResumeStructuredContentDTO content, ResumeStructuredDataDTO data) {
        return !historicalValues(data == null ? null : data.getOthers(),
                content == null ? null : content.getOthers()).isEmpty();
    }

    private void addHistoricalDisplayExperienceSection(
            List<ResumeDocumentSectionDTO> sections,
            String title,
            List<ResumeDisplayModelDTO.ExperienceCard> cards) {
        appendHistoricalSectionEntries(
                sections, ResumeDocumentSectionKind.EXPERIENCE, title, historicalDisplayExperiences(cards));
    }

    private List<String> historicalDisplayOccurrenceIds(ResumeSourceRefDTO sourceRef) {
        if (sourceRef == null) {
            return null;
        }
        List<String> occurrenceIds = nonBlankIds(sourceRef.getSourceOccurrenceIds());
        return occurrenceIds.isEmpty() ? null : occurrenceIds;
    }

    private List<ResumeDocumentEntryDTO> historicalDisplayExperiences(
            List<ResumeDisplayModelDTO.ExperienceCard> cards) {
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (ResumeDisplayModelDTO.ExperienceCard card : safeList(cards)) {
            if (card == null) {
                continue;
            }
            List<String> bullets = new ArrayList<>();
            appendHistoricalValue(bullets, card.getSummary());
            appendHistoricalValues(bullets, card.getResponsibilities());
            String role = trimToNull(card.getPosition());
            String startDate = null;
            String endDate = null;
            String timeRange = trimToNull(card.getTimeRange());
            if (timeRange != null) {
                Matcher range = EDUCATION_DATE_RANGE.matcher(timeRange);
                if (range.find()) {
                    String remainder = removeHistoricalDateRangeRemainder(timeRange, range);
                    startDate = range.group(1).strip();
                    endDate = range.group(2).strip();
                    if (!remainder.isEmpty()) {
                        if (role == null) {
                            role = remainder;
                        } else if (!sameHistoricalText(role, remainder)) {
                            appendHistoricalValue(bullets, timeRange);
                        }
                    }
                } else if (role == null && historicalIsStandaloneDate(timeRange)) {
                    startDate = timeRange;
                } else {
                    appendHistoricalValue(bullets, timeRange);
                }
            }
            if (hasText(card.getCompany()) || hasText(role) || hasText(startDate) || hasText(endDate)
                    || !bullets.isEmpty()) {
                entries.add(ResumeDocumentEntryDTO.builder()
                        .sourceOccurrenceIds(historicalDisplayOccurrenceIds(card.getSourceRef()))
                        .organization(trimToNull(card.getCompany()))
                        .role(role)
                        .startDate(startDate)
                        .endDate(endDate)
                        .bullets(historicalBullets(bullets))
                        .build());
            }
        }
        return entries;
    }

    private List<ResumeDocumentEntryDTO> historicalDisplayProjects(
            List<ResumeDisplayModelDTO.ProjectCard> cards) {
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (ResumeDisplayModelDTO.ProjectCard card : safeList(cards)) {
            if (card == null) {
                continue;
            }
            List<String> bullets = new ArrayList<>();
            appendHistoricalValue(bullets, card.getSummary());
            appendHistoricalValues(bullets, card.getResponsibilities());
            if (hasText(card.getName()) || !bullets.isEmpty() || !safeList(card.getTechStack()).isEmpty()) {
                entries.add(ResumeDocumentEntryDTO.builder()
                        .sourceOccurrenceIds(historicalDisplayOccurrenceIds(card.getSourceRef()))
                        .organization(trimToNull(card.getName()))
                        .techStack(historicalValues(card.getTechStack(), null))
                        .bullets(historicalBullets(bullets))
                        .build());
            }
        }
        return entries;
    }

    private List<ResumeDocumentEntryDTO> historicalDisplayEducation(
            List<ResumeDisplayModelDTO.EducationCard> cards) {
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (ResumeDisplayModelDTO.EducationCard card : safeList(cards)) {
            if (card == null) {
                continue;
            }
            List<String> bullets = new ArrayList<>();
            appendHistoricalValue(bullets, card.getSummary());
            String startDate = null;
            String endDate = null;
            String timeRange = trimToNull(card.getTimeRange());
            if (timeRange != null) {
                Matcher range = EDUCATION_DATE_RANGE.matcher(timeRange);
                if (range.find()) {
                    startDate = range.group(1).strip();
                    endDate = range.group(2).strip();
                    // An attached remainder may contain a major or other historical metadata;
                    // retain the original value once because EducationCard has no safe generic
                    // slot for that remainder.
                    if (!removeHistoricalDateRangeRemainder(timeRange, range).isEmpty()) {
                        appendHistoricalValue(bullets, timeRange);
                    }
                } else if (historicalIsStandaloneDate(timeRange)) {
                    startDate = timeRange;
                } else {
                    appendHistoricalValue(bullets, timeRange);
                }
            }
            if (hasText(card.getSchool()) || hasText(card.getDegree()) || hasText(card.getMajor())
                    || hasText(startDate) || hasText(endDate) || !bullets.isEmpty()) {
                entries.add(ResumeDocumentEntryDTO.builder()
                        .sourceOccurrenceIds(historicalDisplayOccurrenceIds(card.getSourceRef()))
                        .school(trimToNull(card.getSchool()))
                        .degree(trimToNull(card.getDegree()))
                        .major(trimToNull(card.getMajor()))
                        .startDate(startDate)
                        .endDate(endDate)
                        .bullets(historicalBullets(bullets))
                        .build());
            }
        }
        return entries;
    }

    private List<ResumeDocumentEntryDTO> historicalDisplaySkills(ResumeDisplayModelDTO display) {
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        ResumeDisplayModelDTO.SkillSummary summary = display.getSkillSummary();
        Map<String, Integer> represented = new LinkedHashMap<>();
        List<String> summaryDescriptions = summary == null
                ? List.of() : historicalValues(summary.getDescriptions(), null);
        boolean summaryDescriptionsAttached = false;
        if (summary != null) {
            for (ResumeDisplayModelDTO.SkillGroup group : safeList(summary.getGroups())) {
                if (group == null) {
                    continue;
                }
                List<String> items = historicalValues(group.getSkills(), null);
                List<String> ownDescriptions = historicalValues(group.getDescriptions(), null);
                List<String> descriptions = summaryDescriptionsAttached
                        ? ownDescriptions
                        : historicalValues(ownDescriptions, summaryDescriptions);
                if (items.isEmpty() && descriptions.isEmpty()) {
                    continue;
                }
                items.forEach(item -> represented.merge(historicalKey(item), 1, Integer::sum));
                entries.add(ResumeDocumentEntryDTO.builder()
                        .group(trimToNull(group.getName()))
                        .skillItems(items)
                        .skillDescriptions(descriptions)
                        .bullets(new ArrayList<>())
                        .build());
                summaryDescriptionsAttached = summaryDescriptionsAttached || !summaryDescriptions.isEmpty();
            }
        }
        List<String> topSkills = summary == null ? List.of() : historicalValues(summary.getTopSkills(), null);
        ResumeDisplayModelDTO.Overview overview = display.getOverview();
        if (topSkills.isEmpty() && overview != null) {
            topSkills = historicalValues(overview.getCoreSkills(), null);
        }
        List<String> residual = new ArrayList<>();
        for (String skill : topSkills) {
            String key = historicalKey(skill);
            int count = represented.getOrDefault(key, 0);
            if (count > 0) {
                represented.put(key, count - 1);
            } else {
                residual.add(skill);
            }
        }
        List<String> residualDescriptions = summaryDescriptionsAttached ? List.of() : summaryDescriptions;
        if (!residual.isEmpty() || (entries.isEmpty() && !residualDescriptions.isEmpty())) {
            entries.add(ResumeDocumentEntryDTO.builder()
                    .group("技能")
                    .skillItems(residual)
                    .skillDescriptions(residualDescriptions)
                    .bullets(new ArrayList<>())
                    .build());
        }
        return entries;
    }

    private List<ResumeDocumentEntryDTO> historicalDisplayAchievements(
            List<ResumeDisplayModelDTO.AchievementCard> cards) {
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (ResumeDisplayModelDTO.AchievementCard card : safeList(cards)) {
            if (card == null) {
                continue;
            }
            String meta = trimToNull(card.getMeta());
            List<String> bullets = new ArrayList<>();
            if (meta != null && !historicalIsStandaloneDate(meta)) {
                bullets.add(meta);
            }
            if (hasText(card.getTitle()) || meta != null) {
                entries.add(ResumeDocumentEntryDTO.builder()
                        .sourceOccurrenceIds(historicalDisplayOccurrenceIds(card.getSourceRef()))
                        .awardTitle(trimToNull(card.getTitle()))
                        .awardDate(meta != null && historicalIsStandaloneDate(meta) ? meta : null)
                        .bullets(historicalBullets(bullets))
                        .build());
            }
        }
        return entries;
    }

    private List<ResumeDocumentEntryDTO> historicalDisplayCertificates(List<String> values) {
        return historicalValues(values, null).stream()
                .map(value -> historicalGenericEntry(List.of(value)))
                .toList();
    }

    private ResumeDocumentBasicsDTO historicalBasics(ResumeStructuredContentDTO content) {
        List<ResumeDocumentContactDTO> contacts = new ArrayList<>();
        if (content == null) {
            return ResumeDocumentBasicsDTO.builder().contacts(contacts).build();
        }
        Map<String, String> basicInfo = content.getBasicInfo();
        ResumeDisplayModelDTO display = historicalDisplayModel(content);
        ResumeDisplayModelDTO.Overview overview = display == null ? null : display.getOverview();
        String name = mergeHistoricalScalarValues(
                content.getName(),
                historicalBasicValue(basicInfo, "name", "姓名", "名字"),
                overview == null ? null : overview.getName());
        String phone = firstHistoricalValue(content.getPhone(), historicalBasicValue(
                basicInfo, "phone", "mobile", "telephone", "电话", "手机", "手机号"));
        String email = firstHistoricalValue(content.getEmail(), historicalBasicValue(
                basicInfo, "email", "邮箱", "电子邮件"));
        String jobIntention = mergeHistoricalScalarValues(
                content.getJobIntention(),
                historicalBasicValue(basicInfo, "jobintention", "jobintent", "求职意向", "求职目标", "目标岗位", "应聘岗位"),
                overview == null ? null : overview.getTargetRole());
        String degreeCandidate = historicalBasicValue(
                basicInfo, "degree", "highesteducation", "最高学历", "学历", "教育程度");
        String highestEducation = mergeHistoricalScalarValues(
                content.getHighestEducation(), overview == null ? null : overview.getHighestDegree());
        if (highestEducation == null && isHistoricalDegreeValue(degreeCandidate)) {
            highestEducation = degreeCandidate;
        }

        addHistoricalContact(contacts, ResumeDocumentContactType.PHONE, phone, "电话");
        addHistoricalContact(contacts, ResumeDocumentContactType.EMAIL, email, "邮箱");
        if (basicInfo != null) {
            for (Map.Entry<String, String> entry : basicInfo.entrySet()) {
                String key = trimToNull(entry.getKey());
                String value = trimToNull(entry.getValue());
                if (key == null || value == null) {
                    continue;
                }
                String normalizedKey = normalizeHistoricalBasicKey(key);
                if (isHistoricalNameKey(normalizedKey)) {
                    if (!historicalContainsText(name, value)) {
                        addHistoricalContact(contacts, ResumeDocumentContactType.OTHER, value, key);
                    }
                    continue;
                }
                if (isHistoricalPhoneKey(normalizedKey)) {
                    if (!historicalContainsText(phone, value)) {
                        addHistoricalContact(contacts, ResumeDocumentContactType.PHONE, value, key);
                    }
                    continue;
                }
                if (isHistoricalEmailKey(normalizedKey)) {
                    if (!historicalContainsText(email, value)) {
                        addHistoricalContact(contacts, ResumeDocumentContactType.EMAIL, value, key);
                    }
                    continue;
                }
                if (isHistoricalJobKey(normalizedKey)) {
                    if (!historicalContainsText(jobIntention, value)) {
                        addHistoricalContact(contacts, ResumeDocumentContactType.OTHER, value, key);
                    }
                    continue;
                }
                if (isHistoricalDegreeKey(normalizedKey)) {
                    // A safely classifiable degree is promoted; an ambiguous value (for example
                    // a phone number under 学历) remains visible as an OTHER contact.
                    if (!isHistoricalDegreeValue(value)
                            || !historicalContainsText(highestEducation, value)) {
                        addHistoricalContact(contacts, ResumeDocumentContactType.OTHER, value, key);
                    }
                    continue;
                }
                addHistoricalContact(contacts, historicalContactType(key), value, key);
            }
        }
        return ResumeDocumentBasicsDTO.builder()
                .name(name)
                .jobIntention(jobIntention)
                .highestEducation(highestEducation)
                .contacts(contacts)
                .build();
    }

    private String historicalBasicValue(Map<String, String> basicInfo, String... keys) {
        if (basicInfo == null || keys == null) {
            return null;
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String key : keys) {
            wanted.add(normalizeHistoricalBasicKey(key));
        }
        for (Map.Entry<String, String> entry : basicInfo.entrySet()) {
            if (wanted.contains(normalizeHistoricalBasicKey(entry.getKey()))) {
                String value = trimToNull(entry.getValue());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String normalizeHistoricalBasicKey(String key) {
        return key == null ? "" : key.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-]", "");
    }

    private boolean isHistoricalNameKey(String key) {
        return Set.of("name", "姓名", "名字").contains(key);
    }

    private boolean isHistoricalPhoneKey(String key) {
        return Set.of("phone", "mobile", "telephone", "电话", "手机", "手机号").contains(key);
    }

    private boolean isHistoricalEmailKey(String key) {
        return Set.of("email", "邮箱", "电子邮件").contains(key);
    }

    private boolean isHistoricalJobKey(String key) {
        return Set.of("jobintention", "jobintent", "求职意向", "求职目标", "目标岗位", "应聘岗位").contains(key);
    }

    private boolean isHistoricalDegreeKey(String key) {
        return Set.of("degree", "highesteducation", "最高学历", "学历", "教育程度").contains(key);
    }

    private boolean isHistoricalDegreeValue(String value) {
        String normalized = trimToNull(value);
        return normalized != null && DEGREE_WORDS.stream().anyMatch(normalized::contains);
    }

    private ResumeDocumentContactType historicalContactType(String key) {
        String normalized = key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
        if (normalized.contains("phone") || normalized.contains("mobile")
                || key.contains("电话") || key.contains("手机")) {
            return ResumeDocumentContactType.PHONE;
        }
        if (normalized.contains("email") || key.contains("邮箱")) {
            return ResumeDocumentContactType.EMAIL;
        }
        return switch (normalized) {
            case "github" -> ResumeDocumentContactType.GITHUB;
            case "linkedin" -> ResumeDocumentContactType.LINKEDIN;
            case "wechat" -> ResumeDocumentContactType.WECHAT;
            case "qq" -> ResumeDocumentContactType.QQ;
            case "website" -> ResumeDocumentContactType.WEBSITE;
            case "location" -> ResumeDocumentContactType.LOCATION;
            default -> ResumeDocumentContactType.OTHER;
        };
    }

    private void addHistoricalContact(
            List<ResumeDocumentContactDTO> contacts,
            ResumeDocumentContactType type,
            String value,
            String label) {
        String safeValue = trimToNull(value);
        if (safeValue == null) {
            return;
        }
        // Historical maps can contain two distinct labels with identical values. Text equality
        // is not source identity, so preserve both rows; explicit known aliases are filtered by
        // historicalBasics before this method is called.
        contacts.add(ResumeDocumentContactDTO.builder()
                .type(type.name())
                .label(label == null || label.isBlank() ? type.getDefaultLabel() : label)
                .value(safeValue)
                .build());
    }

    private void appendHistoricalExperienceSections(
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeExperienceDTO> experiences) {
        Map<String, List<ResumeDocumentEntryDTO>> grouped = new LinkedHashMap<>();
        for (ResumeExperienceDTO experience : safeList(experiences)) {
            if (!hasHistoricalExperienceContent(experience)) {
                continue;
            }
            String type = experienceTypeOf(
                    experience.getType() == null ? "WORK" : experience.getType().toUpperCase(Locale.ROOT));
            grouped.computeIfAbsent(type, ignored -> new ArrayList<>())
                    .add(historicalExperienceEntry(experience));
        }
        for (Map.Entry<String, List<ResumeDocumentEntryDTO>> group : grouped.entrySet()) {
            sections.add(historicalSection(
                    ResumeDocumentSectionKind.EXPERIENCE, experienceTitle(group.getKey()), group.getValue()));
        }
    }

    private void appendHistoricalExperienceCompatibility(
            List<ResumeDocumentSectionDTO> sections,
            ResumeStructuredContentDTO content,
            List<ResumeExperienceDTO> semanticExperiences) {
        if (content == null) {
            return;
        }
        Map<String, List<String>> valuesByType = new LinkedHashMap<>();
        valuesByType.put("WORK", content.getWorkExperiences());
        valuesByType.put("INTERNSHIP", content.getInternships());
        valuesByType.put("CAMPUS", content.getCampusExperiences());
        for (Map.Entry<String, List<String>> group : valuesByType.entrySet()) {
            List<String> compatibility = historicalValues(group.getValue(), null);
            if (compatibility.isEmpty()) {
                continue;
            }
            Map<String, Integer> represented = historicalExperienceValueCounts(
                    semanticExperiences, group.getKey());
            List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
            for (String value : compatibility) {
                String key = historicalKey(value);
                int count = represented.getOrDefault(key, 0);
                if (count > 0) {
                    represented.put(key, count - 1);
                } else {
                    entries.add(historicalGenericEntry(List.of(value)));
                }
            }
            if (!entries.isEmpty()) {
                sections.add(historicalSection(
                        ResumeDocumentSectionKind.EXPERIENCE, experienceTitle(group.getKey()), entries));
            }
        }
    }

    private Map<String, Integer> historicalExperienceValueCounts(List<ResumeExperienceDTO> experiences) {
        return historicalExperienceValueCounts(experiences, null);
    }

    private Map<String, Integer> historicalExperienceValueCounts(
            List<ResumeExperienceDTO> experiences, String requestedType) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String normalizedType = requestedType == null ? null : experienceTypeOf(requestedType.toUpperCase(Locale.ROOT));
        for (ResumeExperienceDTO experience : safeList(experiences)) {
            if (experience == null || normalizedType != null
                    && !normalizedType.equals(experienceTypeOf(experience.getType() == null
                            ? null : experience.getType().toUpperCase(Locale.ROOT)))) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>();
            addHistoricalKey(values, experience.getOrganization());
            addHistoricalKey(values, experience.getRole());
            addHistoricalKey(values, experience.getStartDate());
            addHistoricalKey(values, experience.getEndDate());
            addHistoricalKey(values, experience.getDescription());
            values.addAll(historicalKeys(experience.getBullets()));
            values.addAll(historicalKeys(experience.getEvidence()));
            for (String value : values) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return counts;
    }

    private boolean historicalExperienceAlreadyPresent(
            List<ResumeExperienceDTO> experiences, List<String> values) {
        return values.stream().allMatch(value -> historicalExperienceValuePresent(experiences, value));
    }

    private boolean historicalExperienceValuePresent(List<ResumeExperienceDTO> experiences, String value) {
        String target = historicalKey(value);
        return safeList(experiences).stream().filter(java.util.Objects::nonNull).anyMatch(experience ->
                historicalKey(experience.getDescription()).equals(target)
                        || safeList(experience.getBullets()).stream()
                        .anyMatch(bullet -> historicalKey(bullet).equals(target))
                        || historicalKey(experience.getOrganization()).equals(target));
    }

    private boolean hasHistoricalExperienceContent(ResumeExperienceDTO experience) {
        return experience != null
                && (hasText(experience.getOrganization())
                || hasText(experience.getRole())
                || hasText(experience.getStartDate())
                || hasText(experience.getEndDate())
                || hasText(experience.getDescription())
                || hasText(experience.getSourceRef() == null ? null : experience.getSourceRef().getText())
                || safeList(experience.getBullets()).stream().anyMatch(this::hasText)
                || safeList(experience.getEvidence()).stream().anyMatch(this::hasText));
    }

    private boolean hasHistoricalProjectContent(ResumeProjectDTO project) {
        return project != null
                && (hasText(project.getName())
                || hasText(project.getDescription())
                || hasText(project.getRole())
                || hasText(project.getMentor())
                || hasText(project.getTimeRange())
                || hasText(project.getEnvironment())
                || hasText(project.getStartDate())
                || hasText(project.getEndDate())
                || hasText(project.getSourceRef() == null ? null : project.getSourceRef().getText())
                || safeList(project.getTechStack()).stream().anyMatch(this::hasText)
                || safeList(project.getResponsibilities()).stream().anyMatch(this::hasText)
                || safeList(project.getEvidence()).stream().anyMatch(this::hasText));
    }

    private boolean hasHistoricalAchievementContent(ResumeAchievementDTO achievement) {
        return achievement != null
                && (hasText(achievement.getTitle())
                || hasText(achievement.getLevel())
                || hasText(achievement.getCompetition())
                || hasText(achievement.getRanking())
                || hasText(achievement.getTimeRange())
                || hasText(achievement.getDate())
                || hasText(achievement.getSourceRef() == null ? null : achievement.getSourceRef().getText())
                || safeList(achievement.getEvidence()).stream().anyMatch(this::hasText));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String mergeHistoricalScalarValues(String... values) {
        List<String> merged = new ArrayList<>();
        for (String value : values == null ? new String[0] : values) {
            for (String line : value == null ? new String[0] : value.split("\\R")) {
                String text = trimToNull(line);
                if (text != null && merged.stream().noneMatch(existing -> sameHistoricalText(existing, text))) {
                    merged.add(text);
                }
            }
        }
        return merged.isEmpty() ? null : String.join("\n", merged);
    }

    private boolean historicalContainsText(String aggregate, String candidate) {
        String text = trimToNull(candidate);
        if (text == null || aggregate == null) {
            return false;
        }
        return sameHistoricalText(aggregate, text)
                || java.util.Arrays.stream(aggregate.split("\\R"))
                .anyMatch(line -> sameHistoricalText(line, text));
    }

    private ResumeDocumentEntryDTO historicalExperienceEntry(ResumeExperienceDTO experience) {
        List<String> bullets = new ArrayList<>();
        appendHistoricalValue(bullets, experience.getDescription());
        appendHistoricalValues(bullets, experience.getBullets());
        appendHistoricalValues(bullets, experience.getEvidence());
        if (bullets.isEmpty() && experience.getSourceRef() != null) {
            appendHistoricalValue(bullets, experience.getSourceRef().getText());
        }
        return ResumeDocumentEntryDTO.builder()
                .organization(trimToNull(experience.getOrganization()))
                .role(trimToNull(experience.getRole()))
                .startDate(trimToNull(experience.getStartDate()))
                .endDate(trimToNull(experience.getEndDate()))
                .bullets(historicalBullets(bullets))
                .build();
    }

    private ResumeDocumentEntryDTO historicalProjectEntry(ResumeProjectDTO project) {
        List<String> bullets = new ArrayList<>();
        appendHistoricalValue(bullets, project.getDescription());
        appendHistoricalValues(bullets, project.getResponsibilities());
        appendHistoricalValues(bullets, project.getEvidence());
        appendHistoricalValue(bullets, project.getTimeRange());
        if (bullets.isEmpty() && project.getSourceRef() != null) {
            appendHistoricalValue(bullets, project.getSourceRef().getText());
        }
        return ResumeDocumentEntryDTO.builder()
                .organization(trimToNull(project.getName()))
                .role(trimToNull(project.getRole()))
                .startDate(trimToNull(project.getStartDate()))
                .endDate(trimToNull(project.getEndDate()))
                .environment(trimToNull(project.getEnvironment()))
                .mentor(trimToNull(project.getMentor()))
                .techStack(historicalValues(project.getTechStack(), null))
                .bullets(historicalBullets(bullets))
                .build();
    }

    private ResumeDocumentEntryDTO historicalAchievementEntry(ResumeAchievementDTO achievement) {
        List<String> bullets = new ArrayList<>();
        appendHistoricalValues(bullets, achievement.getEvidence());
        appendHistoricalValue(bullets, achievement.getTimeRange());
        if (bullets.isEmpty() && achievement.getSourceRef() != null) {
            appendHistoricalValue(bullets, achievement.getSourceRef().getText());
        }
        return ResumeDocumentEntryDTO.builder()
                .awardTitle(trimToNull(achievement.getTitle()))
                .awardLevel(trimToNull(achievement.getLevel()))
                .awardCompetition(trimToNull(achievement.getCompetition()))
                .awardRanking(trimToNull(achievement.getRanking()))
                .awardDate(firstHistoricalValue(achievement.getTimeRange(), achievement.getDate()))
                .bullets(historicalBullets(bullets))
                .build();
    }

    private void appendHistoricalSkillSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeSkillSetDTO semantic,
            List<String> compatibilityKeywords) {
        if (semantic == null && (compatibilityKeywords == null || compatibilityKeywords.isEmpty())) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        Map<String, Integer> represented = new LinkedHashMap<>();
        List<String> descriptions = historicalSkillDescriptions(semantic);
        boolean descriptionsAttached = false;
        if (semantic != null && semantic.getGroups() != null) {
            for (Map.Entry<String, List<String>> group : semantic.getGroups().entrySet()) {
                List<String> items = historicalValues(group.getValue(), null);
                if (items.isEmpty()) {
                    continue;
                }
                for (String item : items) {
                    represented.merge(historicalKey(item), 1, Integer::sum);
                }
                entries.add(ResumeDocumentEntryDTO.builder()
                        .group(SKILL_GROUP_LABELS.getOrDefault(group.getKey(), group.getKey()))
                        .skillItems(items)
                        .skillDescriptions(descriptionsAttached ? List.of() : descriptions)
                        .bullets(new ArrayList<>())
                        .build());
                descriptionsAttached = descriptionsAttached || !descriptions.isEmpty();
            }
        }

        // Older snapshots may contain only skills.evidence. Promote its preserved labels and
        // source text to the compatibility projection, but never attach those values as fresh
        // source evidence. A second equal evidence row remains a second item.
        List<String> evidenceItems = historicalSkillEvidenceItems(semantic);
        List<String> keywords = historicalValues(
                semantic == null ? null : semantic.getKeywords(), compatibilityKeywords);
        appendHistoricalEvidenceResidual(keywords, evidenceItems);
        Map<String, Integer> representedCounts = new LinkedHashMap<>(represented);
        List<String> residual = new ArrayList<>();
        for (String value : keywords) {
            String key = historicalKey(value);
            int count = representedCounts.getOrDefault(key, 0);
            if (count > 0) {
                representedCounts.put(key, count - 1);
            } else {
                residual.add(value);
            }
        }
        if (!residual.isEmpty()) {
            entries.add(ResumeDocumentEntryDTO.builder()
                    .group("技能")
                    .skillItems(residual)
                    .skillDescriptions(descriptionsAttached ? List.of() : descriptions)
                    .bullets(new ArrayList<>())
                    .build());
            descriptionsAttached = descriptionsAttached || !descriptions.isEmpty();
        }
        if (entries.isEmpty() && (!descriptions.isEmpty() || !evidenceItems.isEmpty())) {
            entries.add(ResumeDocumentEntryDTO.builder()
                    .group("技能")
                    .skillItems(evidenceItems)
                    .skillDescriptions(descriptions)
                    .bullets(new ArrayList<>())
                    .build());
        }
        if (!entries.isEmpty()) {
            sections.add(historicalSection(ResumeDocumentSectionKind.SKILL, "技能", entries));
        }
    }

    private List<String> historicalSkillEvidenceItems(ResumeSkillSetDTO semantic) {
        List<String> items = new ArrayList<>();
        if (semantic == null) {
            return items;
        }
        for (ResumeSkillEvidenceDTO evidence : safeList(semantic.getEvidence())) {
            if (evidence == null) {
                continue;
            }
            String skill = trimToNull(evidence.getSkill());
            appendHistoricalValue(items, skill);
            for (String keyword : safeList(evidence.getKeywords())) {
                if (!sameHistoricalText(skill, keyword)) {
                    appendHistoricalValue(items, keyword);
                }
            }
        }
        return items;
    }

    private void appendHistoricalEvidenceResidual(List<String> keywords, List<String> evidenceItems) {
        if (keywords == null || evidenceItems == null || evidenceItems.isEmpty()) {
            return;
        }
        Map<String, Integer> represented = new LinkedHashMap<>();
        for (String keyword : keywords) {
            represented.merge(historicalKey(keyword), 1, Integer::sum);
        }
        for (String item : evidenceItems) {
            String key = historicalKey(item);
            int count = represented.getOrDefault(key, 0);
            if (count > 0) {
                represented.put(key, count - 1);
            } else {
                keywords.add(item);
            }
        }
    }

    private List<String> historicalSkillDescriptions(ResumeSkillSetDTO semantic) {
        if (semantic == null) {
            return List.of();
        }
        List<String> evidenceDescriptions = new ArrayList<>();
        for (ResumeSkillEvidenceDTO evidence : safeList(semantic.getEvidence())) {
            if (evidence == null) {
                continue;
            }
            // description and sourceText on one evidence object are an echo. Equal text on
            // separate evidence objects remains a separate historical occurrence.
            String description = trimToNull(evidence.getDescription());
            String sourceText = trimToNull(evidence.getSourceText());
            appendHistoricalValue(evidenceDescriptions, description);
            if (sourceText != null && !sameHistoricalText(description, sourceText)) {
                appendHistoricalValue(evidenceDescriptions, sourceText);
            }
        }
        return historicalValues(semantic.getDescriptions(), evidenceDescriptions);
    }

    private void appendHistoricalTextSections(
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeTextSectionDTO> textSections,
            ResumeDocumentBasicsDTO basics) {
        Map<String, Integer> displayed = historicalDisplayedCounts(sections, basics);
        for (ResumeTextSectionDTO source : safeList(textSections)) {
            if (source == null) {
                continue;
            }
            ResumeDocumentSectionKind kind = historicalSectionKind(source.getSectionType());
            String bucket = historicalSectionBucket(source.getSectionType(), source.getHeading());
            List<String> residual = new ArrayList<>();
            for (String value : historicalTextSectionValues(source)) {
                String key = historicalDisplayedKey(bucket, value);
                int count = displayed.getOrDefault(key, 0);
                if (count > 0) {
                    displayed.put(key, count - 1);
                } else {
                    residual.add(value);
                }
            }
            if (residual.isEmpty()) {
                continue;
            }
            String title = firstHistoricalValue(source.getHeading(), kind.name());
            sections.add(historicalSection(kind, title,
                    residual.stream().map(value -> historicalGenericEntry(List.of(value))).toList()));
        }
    }

    /**
     * Return all retained text-section rows without treating the block view as a reason to drop
     * the legacy line view. Blocks are preferred for equal rows, while additional line
     * occurrences remain visible. Text equality alone never removes a second occurrence.
     */
    private List<String> historicalTextSectionValues(ResumeTextSectionDTO source) {
        List<String> values = new ArrayList<>();
        if (source == null) {
            return values;
        }
        for (ResumeBlockDTO block : safeList(source.getBlocks())) {
            if (block != null) {
                appendHistoricalValue(values, block.getText());
            }
        }
        Map<String, Integer> blockCounts = new LinkedHashMap<>();
        for (String value : values) {
            blockCounts.merge(historicalKey(value), 1, Integer::sum);
        }
        for (String line : safeList(source.getLines())) {
            String value = trimToNull(line);
            if (value == null) {
                continue;
            }
            String key = historicalKey(value);
            int count = blockCounts.getOrDefault(key, 0);
            if (count > 0) {
                blockCounts.put(key, count - 1);
            } else {
                values.add(value);
            }
        }
        return values;
    }

    /** Count semantic display values so old top-level/text projections are not emitted twice. */
    private Map<String, Integer> historicalDisplayedCounts(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentBasicsDTO basics) {
        Map<String, Integer> displayed = new LinkedHashMap<>();
        if (basics != null) {
            addHistoricalDisplayed(displayed, "BASICS", basics.getName());
            addHistoricalDisplayed(displayed, "BASICS", basics.getJobIntention());
            addHistoricalDisplayed(displayed, "BASICS", basics.getHighestEducation());
            for (ResumeDocumentContactDTO contact : safeList(basics.getContacts())) {
                if (contact != null) {
                    addHistoricalDisplayed(displayed, "BASICS", contact.getValue());
                }
            }
        }
        for (ResumeDocumentSectionDTO section : safeList(sections)) {
            if (section == null) {
                continue;
            }
            String bucket = historicalSectionBucket(section.getKind(), section.getTitle());
            for (ResumeDocumentEntryDTO entry : safeList(section.getEntries())) {
                if (entry == null) {
                    continue;
                }
                addHistoricalDisplayed(displayed, bucket, entry.getOrganization());
                addHistoricalDisplayed(displayed, bucket, entry.getRole());
                addHistoricalDisplayed(displayed, bucket, entry.getSchool());
                addHistoricalDisplayed(displayed, bucket, entry.getDegree());
                addHistoricalDisplayed(displayed, bucket, entry.getMajor());
                addHistoricalDisplayed(displayed, bucket, entry.getStartDate());
                addHistoricalDisplayed(displayed, bucket, entry.getEndDate());
                addHistoricalDisplayed(displayed, bucket, entry.getLocation());
                addHistoricalDisplayed(displayed, bucket, entry.getEnvironment());
                addHistoricalDisplayed(displayed, bucket, entry.getMentor());
                addHistoricalDisplayed(displayed, bucket, entry.getGroup());
                addHistoricalDisplayed(displayed, bucket, entry.getAwardTitle());
                addHistoricalDisplayed(displayed, bucket, entry.getAwardLevel());
                addHistoricalDisplayed(displayed, bucket, entry.getAwardCompetition());
                addHistoricalDisplayed(displayed, bucket, entry.getAwardRanking());
                addHistoricalDisplayed(displayed, bucket, entry.getAwardDate());
                for (String value : safeList(entry.getTechStack())) {
                    addHistoricalDisplayed(displayed, bucket, value);
                }
                for (String value : safeList(entry.getSkillItems())) {
                    addHistoricalDisplayed(displayed, bucket, value);
                }
                for (String value : safeList(entry.getSkillDescriptions())) {
                    addHistoricalDisplayed(displayed, bucket, value);
                }
                for (ResumeDocumentBulletDTO bullet : safeList(entry.getBullets())) {
                    if (bullet != null) {
                        addHistoricalDisplayed(displayed, bucket, bullet.getText());
                    }
                }
            }
        }
        return displayed;
    }

    private void addHistoricalDisplayed(Map<String, Integer> displayed, String bucket, String value) {
        String key = trimToNull(value);
        if (key != null) {
            displayed.merge(historicalDisplayedKey(bucket, key), 1, Integer::sum);
        }
    }

    private String historicalDisplayedKey(String bucket, String value) {
        return (bucket == null ? "OTHER" : bucket) + "\u0000" + historicalKey(value);
    }

    private String historicalSectionBucket(String kind, String title) {
        String normalizedKind = kind == null ? "" : kind.strip().toUpperCase(Locale.ROOT);
        String normalizedTitle = title == null ? "" : title.strip().toLowerCase(Locale.ROOT);
        if (normalizedKind.contains("BASIC") || normalizedTitle.contains("个人信息")
                || normalizedTitle.contains("基本信息") || normalizedTitle.contains("联系方式")) {
            return "BASICS";
        }
        if (normalizedKind.contains("EXPERIENCE") || normalizedKind.equals("WORK")
                || normalizedKind.equals("INTERNSHIP") || normalizedKind.equals("CAMPUS")) {
            if (normalizedKind.contains("INTERN") || normalizedTitle.contains("实习")) {
                return "INTERNSHIPS";
            }
            if (normalizedKind.contains("CAMPUS") || normalizedTitle.contains("校园")
                    || normalizedTitle.contains("在校")) {
                return "CAMPUS_EXPERIENCES";
            }
            return "WORK_EXPERIENCES";
        }
        if (normalizedKind.contains("PROJECT")) return "PROJECTS";
        if (normalizedKind.contains("EDUCATION")) return "EDUCATION";
        if (normalizedKind.contains("SKILL")) return "SKILLS";
        if (normalizedKind.contains("AWARD") || normalizedKind.contains("ACHIEVEMENT")) return "AWARDS";
        if (normalizedKind.contains("CERTIFICATE")) return "CERTIFICATES";
        if (normalizedKind.contains("SUMMARY")) return "SUMMARY";
        if (normalizedKind.contains("OTHER")) return "OTHERS";
        return "OTHER";
    }

    private ResumeDocumentSectionKind historicalSectionKind(String sectionType) {
        String normalized = sectionType == null ? "" : sectionType.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WORK", "WORK_EXPERIENCES", "EXPERIENCE", "INTERNSHIP", "INTERNSHIPS", "CAMPUS",
                    "CAMPUS_EXPERIENCES" -> ResumeDocumentSectionKind.EXPERIENCE;
            case "PROJECT", "PROJECTS" -> ResumeDocumentSectionKind.PROJECT;
            case "EDUCATION" -> ResumeDocumentSectionKind.EDUCATION;
            case "SKILL", "SKILLS" -> ResumeDocumentSectionKind.SKILL;
            case "AWARD", "AWARDS", "ACHIEVEMENT" -> ResumeDocumentSectionKind.ACHIEVEMENT;
            case "CERTIFICATE", "CERTIFICATES" -> ResumeDocumentSectionKind.CERTIFICATE;
            case "SUMMARY" -> ResumeDocumentSectionKind.SUMMARY;
            default -> ResumeDocumentSectionKind.OTHER;
        };
    }

    private ResumeDocumentSectionDTO historicalSection(
            ResumeDocumentSectionKind kind, String title, List<ResumeDocumentEntryDTO> entries) {
        return ResumeDocumentSectionDTO.builder()
                .kind(kind.name())
                .title(title == null || title.isBlank() ? kind.name() : title)
                .entries(entries == null ? new ArrayList<>() : new ArrayList<>(entries))
                .build();
    }

    private boolean hasHistoricalEntryContent(ResumeDocumentEntryDTO entry) {
        if (entry == null) {
            return false;
        }
        return Stream.of(entry.getOrganization(), entry.getRole(), entry.getSchool(), entry.getDegree(),
                        entry.getMajor(), entry.getStartDate(), entry.getEndDate(), entry.getLocation(),
                        entry.getEnvironment(), entry.getMentor(), entry.getGroup(), entry.getAwardTitle(),
                        entry.getAwardLevel(), entry.getAwardCompetition(), entry.getAwardRanking(),
                        entry.getAwardDate())
                .anyMatch(this::hasText)
                || !historicalValues(entry.getTechStack(), null).isEmpty()
                || !historicalValues(entry.getSkillItems(), null).isEmpty()
                || !historicalValues(entry.getSkillDescriptions(), null).isEmpty()
                || !safeList(entry.getBullets()).isEmpty();
    }

    private ResumeDocumentEntryDTO historicalGenericEntry(List<String> values) {
        return ResumeDocumentEntryDTO.builder()
                .bullets(historicalBullets(values))
                .build();
    }

    /**
     * Project a compact historical education row only when its school token is explicit. A
     * date-like or otherwise ambiguous row remains one bullet rather than being promoted to a
     * guessed school/degree field.
     */
    private ResumeDocumentEntryDTO historicalEducationEntry(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return historicalGenericEntry(List.of());
        }
        String startDate = null;
        String endDate = null;
        String remainder = text;
        Matcher range = EDUCATION_DATE_RANGE.matcher(text);
        if (range.find()) {
            startDate = range.group(1).strip();
            endDate = range.group(2).strip();
            remainder = (text.substring(0, range.start()) + " " + text.substring(range.end()))
                    .replaceAll("[\\s|｜·、,，:：\\-–—~～]+", " ")
                    .strip();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : remainder.split("[\\s|｜·、,，]+")) {
            String normalized = trimToNull(token);
            if (normalized != null) {
                tokens.add(normalized);
            }
        }
        String school = null;
        String degree = null;
        List<String> majorParts = new ArrayList<>();
        for (String token : tokens) {
            if (school == null && (token.contains("大学") || token.contains("学院") || token.contains("学校"))) {
                school = token;
            } else if (degree == null && DEGREE_WORDS.contains(token)) {
                degree = token;
            } else {
                majorParts.add(token);
            }
        }
        if (school == null) {
            return historicalGenericEntry(List.of(text));
        }
        return ResumeDocumentEntryDTO.builder()
                .school(school)
                .degree(degree)
                .major(majorParts.isEmpty() ? null : String.join(" ", majorParts))
                .startDate(startDate)
                .endDate(endDate)
                .bullets(new ArrayList<>())
                .build();
    }

    private List<ResumeDocumentBulletDTO> historicalBullets(List<String> values) {
        List<ResumeDocumentBulletDTO> bullets = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            String text = trimToNull(value);
            if (text == null) {
                continue;
            }
            bullets.add(ResumeDocumentBulletDTO.builder().text(text).build());
        }
        return bullets;
    }

    private List<ResumeProjectDTO> historicalProjects(List<String> values) {
        return historicalValues(values, null).stream()
                .map(value -> ResumeProjectDTO.builder().name(value).description(value).build())
                .toList();
    }

    private List<ResumeAchievementDTO> historicalAchievements(List<String> values) {
        return historicalValues(values, null).stream()
                .map(value -> ResumeAchievementDTO.builder().title(value).build())
                .toList();
    }

    private List<String> historicalProjectCompatibilityValues(
            List<String> compatibility, List<ResumeProjectDTO> semantic) {
        List<String> residual = new ArrayList<>();
        Map<String, Integer> represented = historicalProjectValueCounts(semantic);
        for (String value : compatibility == null ? List.<String>of() : compatibility) {
            String candidate = trimToNull(value);
            if (candidate == null) {
                continue;
            }
            String key = historicalKey(candidate);
            int count = represented.getOrDefault(key, 0);
            if (count > 0) {
                represented.put(key, count - 1);
            } else {
                residual.add(candidate);
            }
        }
        return residual;
    }

    private Map<String, Integer> historicalProjectValueCounts(List<ResumeProjectDTO> projects) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ResumeProjectDTO project : safeList(projects)) {
            if (project == null) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>();
            addHistoricalKey(values, project.getName());
            addHistoricalKey(values, project.getRole());
            addHistoricalKey(values, project.getStartDate());
            addHistoricalKey(values, project.getEndDate());
            addHistoricalKey(values, project.getTimeRange());
            addHistoricalKey(values, project.getEnvironment());
            addHistoricalKey(values, project.getMentor());
            addHistoricalKey(values, project.getDescription());
            values.addAll(historicalKeys(project.getTechStack()));
            values.addAll(historicalKeys(project.getResponsibilities()));
            values.addAll(historicalKeys(project.getEvidence()));
            for (String value : values) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return counts;
    }

    private Set<String> historicalKeys(List<String> values) {
        Set<String> keys = new LinkedHashSet<>();
        for (String value : safeList(values)) {
            addHistoricalKey(keys, value);
        }
        return keys;
    }

    private void addHistoricalKey(Set<String> target, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            target.add(historicalKey(normalized));
        }
    }

    private boolean historicalProjectValuePresent(List<ResumeProjectDTO> projects, String value) {
        String target = historicalKey(value);
        return safeList(projects).stream().filter(java.util.Objects::nonNull).anyMatch(project ->
                sameHistoricalText(project.getName(), target)
                        || sameHistoricalText(project.getDescription(), target)
                        || sameHistoricalText(project.getRole(), target)
                        || sameHistoricalText(project.getTimeRange(), target)
                        || sameHistoricalText(project.getEnvironment(), target)
                        || sameHistoricalText(project.getStartDate(), target)
                        || sameHistoricalText(project.getEndDate(), target)
                        || safeList(project.getTechStack()).stream().anyMatch(item -> sameHistoricalText(item, target))
                        || safeList(project.getResponsibilities()).stream().anyMatch(item -> sameHistoricalText(item, target))
                        || safeList(project.getEvidence()).stream().anyMatch(item -> sameHistoricalText(item, target)));
    }

    private List<String> historicalAchievementCompatibilityValues(
            List<String> compatibility, List<ResumeAchievementDTO> semantic) {
        List<String> residual = new ArrayList<>();
        Map<String, Integer> represented = historicalAchievementValueCounts(semantic);
        for (String value : compatibility == null ? List.<String>of() : compatibility) {
            String candidate = trimToNull(value);
            if (candidate == null) {
                continue;
            }
            String key = historicalKey(candidate);
            int count = represented.getOrDefault(key, 0);
            if (count > 0) {
                represented.put(key, count - 1);
            } else {
                residual.add(candidate);
            }
        }
        return residual;
    }

    private Map<String, Integer> historicalAchievementValueCounts(
            List<ResumeAchievementDTO> achievements) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ResumeAchievementDTO achievement : safeList(achievements)) {
            if (achievement == null) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>();
            addHistoricalKey(values, achievement.getTitle());
            addHistoricalKey(values, achievement.getLevel());
            addHistoricalKey(values, achievement.getCompetition());
            addHistoricalKey(values, achievement.getRanking());
            addHistoricalKey(values, achievement.getTimeRange());
            addHistoricalKey(values, achievement.getDate());
            values.addAll(historicalKeys(achievement.getEvidence()));
            for (String value : values) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return counts;
    }

    private boolean historicalAchievementValuePresent(
            List<ResumeAchievementDTO> achievements, String value) {
        String target = historicalKey(value);
        return safeList(achievements).stream().filter(java.util.Objects::nonNull).anyMatch(achievement ->
                sameHistoricalText(achievement.getTitle(), target)
                        || sameHistoricalText(achievement.getLevel(), target)
                        || sameHistoricalText(achievement.getCompetition(), target)
                        || sameHistoricalText(achievement.getRanking(), target)
                        || sameHistoricalText(achievement.getTimeRange(), target)
                        || sameHistoricalText(achievement.getDate(), target)
                        || safeList(achievement.getEvidence()).stream()
                        .anyMatch(item -> sameHistoricalText(item, target)));
    }

    private List<String> historicalValues(List<String> primary, List<String> compatibility) {
        List<String> result = new ArrayList<>();
        appendHistoricalValues(result, primary);
        Map<String, Integer> primaryCounts = new LinkedHashMap<>();
        for (String value : result) {
            primaryCounts.merge(historicalKey(value), 1, Integer::sum);
        }
        for (String value : compatibility == null ? List.<String>of() : compatibility) {
            String normalized = trimToNull(value);
            if (normalized == null) {
                continue;
            }
            String key = historicalKey(normalized);
            int count = primaryCounts.getOrDefault(key, 0);
            if (count > 0) {
                primaryCounts.put(key, count - 1);
            } else {
                result.add(normalized);
            }
        }
        return result;
    }

    private void appendHistoricalValues(List<String> target, List<String> values) {
        for (String value : values == null ? List.<String>of() : values) {
            appendHistoricalValue(target, value);
        }
    }

    private void appendHistoricalValue(List<String> target, String value) {
        String safe = trimToNull(value);
        if (safe != null) {
            target.add(safe);
        }
    }

    private String firstHistoricalValue(String first, String second) {
        String left = trimToNull(first);
        return left == null ? trimToNull(second) : left;
    }

    private boolean sameHistoricalText(String left, String right) {
        return left != null && right != null && historicalKey(left).equals(historicalKey(right));
    }

    private String historicalKey(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private boolean hasHistoricalBasics(ResumeStructuredContentDTO content) {
        if (content == null) {
            return false;
        }
        ResumeDisplayModelDTO display = historicalDisplayModel(content);
        ResumeDisplayModelDTO.Overview overview = display == null ? null : display.getOverview();
        return trimToNull(content.getName()) != null
                || trimToNull(content.getPhone()) != null
                || trimToNull(content.getEmail()) != null
                || trimToNull(content.getJobIntention()) != null
                || trimToNull(content.getHighestEducation()) != null
                || overview != null && (trimToNull(overview.getName()) != null
                || trimToNull(overview.getTargetRole()) != null
                || trimToNull(overview.getHighestDegree()) != null
                || !historicalValues(overview.getCoreSkills(), null).isEmpty())
                || content.getBasicInfo() != null && content.getBasicInfo().entrySet().stream()
                .anyMatch(entry -> trimToNull(entry.getValue()) != null);
    }

    /**
     * Kept only for source-backed legacy paths that still need a text representation. It is not
     * used by buildFromStructuredJson when no source material exists.
     */
    private String legacySourceText(ResumeStructuredContentDTO content) {
        List<String> lines = new ArrayList<>();
        if (content == null) {
            return "";
        }
        appendLegacyValue(lines, content.getName());
        appendLegacyValue(lines, content.getPhone());
        appendLegacyValue(lines, content.getEmail());
        appendLegacyValue(lines, content.getJobIntention());
        appendLegacyValue(lines, content.getHighestEducation());
        if (content.getBasicInfo() != null) {
            content.getBasicInfo().forEach((key, value) -> appendLegacyValue(lines, value));
        }
        ResumeStructuredDataDTO data = content.getStructuredData();
        if (data == null) {
            return String.join("\n", lines);
        }
        appendLegacyValues(lines, data.getEducation());
        ResumeSkillSetDTO skills = data.getSkills();
        if (skills != null) {
            appendLegacyValues(lines, skills.getKeywords());
            if (skills.getGroups() != null) {
                skills.getGroups().values().forEach(values -> appendLegacyValues(lines, values));
            }
            appendLegacyValues(lines, skills.getDescriptions());
            if (skills.getEvidence() != null) {
                for (ResumeSkillEvidenceDTO evidence : skills.getEvidence()) {
                    if (evidence == null) {
                        continue;
                    }
                    appendLegacyValue(lines, evidence.getSourceText());
                    appendLegacyValue(lines, evidence.getDescription());
                    appendLegacyValues(lines, evidence.getKeywords());
                    appendLegacyValue(lines, evidence.getSourceRef() == null
                            ? null : evidence.getSourceRef().getText());
                }
            }
        }
        if (data.getExperiences() != null) {
            for (ResumeExperienceDTO experience : data.getExperiences()) {
                if (experience == null) {
                    continue;
                }
                appendLegacyValue(lines, experience.getOrganization());
                appendLegacyValue(lines, experience.getRole());
                appendLegacyValue(lines, experience.getStartDate());
                appendLegacyValue(lines, experience.getEndDate());
                appendLegacyValue(lines, experience.getDescription());
                appendLegacyValues(lines, experience.getBullets());
                appendLegacyValues(lines, experience.getEvidence());
                appendLegacyValue(lines, experience.getSourceRef() == null
                        ? null : experience.getSourceRef().getText());
            }
        }
        if (data.getProjects() != null) {
            for (ResumeProjectDTO project : data.getProjects()) {
                if (project == null) {
                    continue;
                }
                appendLegacyValue(lines, project.getName());
                appendLegacyValue(lines, project.getDescription());
                appendLegacyValue(lines, project.getRole());
                appendLegacyValue(lines, project.getMentor());
                appendLegacyValue(lines, project.getTimeRange());
                appendLegacyValue(lines, project.getEnvironment());
                appendLegacyValues(lines, project.getTechStack());
                appendLegacyValues(lines, project.getResponsibilities());
                appendLegacyValue(lines, project.getStartDate());
                appendLegacyValue(lines, project.getEndDate());
                appendLegacyValues(lines, project.getEvidence());
                appendLegacyValue(lines, project.getSourceRef() == null
                        ? null : project.getSourceRef().getText());
            }
        }
        if (data.getAchievements() != null) {
            for (ResumeAchievementDTO achievement : data.getAchievements()) {
                if (achievement == null) {
                    continue;
                }
                appendLegacyValue(lines, achievement.getTitle());
                appendLegacyValue(lines, achievement.getLevel());
                appendLegacyValue(lines, achievement.getCompetition());
                appendLegacyValue(lines, achievement.getRanking());
                appendLegacyValue(lines, achievement.getTimeRange());
                appendLegacyValue(lines, achievement.getDate());
                appendLegacyValues(lines, achievement.getEvidence());
                appendLegacyValue(lines, achievement.getSourceRef() == null
                        ? null : achievement.getSourceRef().getText());
            }
        }
        appendLegacyValues(lines, data.getCertificates());
        appendLegacyValue(lines, data.getSummary());
        appendLegacyValue(lines, data.getSummarySourceRef() == null
                ? null : data.getSummarySourceRef().getText());
        appendLegacyValues(lines, data.getOthers());
        return String.join("\n", lines);
    }

    private void appendLegacyValues(List<String> lines, List<String> values) {
        if (values != null) {
            values.forEach(value -> appendLegacyValue(lines, value));
        }
    }

    private void appendLegacyValue(List<String> lines, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            lines.add(trimmed);
        }
    }

    private ResumeDocumentBasicsDTO buildBasics(
            ResumeStructuredContentDTO content,
            List<ResumeUnresolvedItemDTO> unresolved,
            String representedText,
            String sourceText,
            SourceContext provenance) {
        List<ResumeDocumentContactDTO> contacts = new ArrayList<>();
        Map<String, Set<String>> allocatedBasicValues = new LinkedHashMap<>();
        List<ResumeSourceRefDTO> basicReferences = new ArrayList<>();
        SourceMatch phoneMatch = matchDistinctBasicValue(
                content.getPhone(), null, null, provenance, allocatedBasicValues);
        SourceMatch emailMatch = matchDistinctBasicValue(
                content.getEmail(), null, null, provenance, allocatedBasicValues);
        addTypedContact(contacts, unresolved, ResumeDocumentContactType.PHONE,
                content.getPhone(), phoneMatch);
        addTypedContact(contacts, unresolved, ResumeDocumentContactType.EMAIL,
                content.getEmail(), emailMatch);
        addReferenceIfPresent(basicReferences, phoneMatch);
        addReferenceIfPresent(basicReferences, emailMatch);

        SourceMatch jobIntentionMatch = matchDistinctBasicValue(
                content.getJobIntention(), null, null, provenance, allocatedBasicValues);
        SourceMatch highestEducationMatch = matchDistinctBasicValue(
                content.getHighestEducation(), null, null, provenance, allocatedBasicValues);
        String jobIntention = jobIntentionMatch == null ? null : trimToNull(content.getJobIntention());
        String highestEducation = highestEducationMatch == null ? null : trimToNull(content.getHighestEducation());
        Map<String, ResumeSourceRefDTO> fieldSourceRefs = new LinkedHashMap<>();
        putFieldRef(fieldSourceRefs, "jobIntention", jobIntentionMatch);
        putFieldRef(fieldSourceRefs, "highestEducation", highestEducationMatch);

        Map<String, String> basicInfo = content.getBasicInfo();
        if (basicInfo != null) {
            for (Map.Entry<String, String> field : basicInfo.entrySet()) {
                String key = trimToNull(field.getKey());
                String value = trimToNull(field.getValue());
                if (key == null || value == null) {
                    continue;
                }
                String normalizedKey = key.toLowerCase(Locale.ROOT);
                if (BASIC_INFO_EXCLUDED_KEYS.contains(key) || BASIC_INFO_EXCLUDED_KEYS.contains(normalizedKey)) {
                    continue;
                }
                if (normalizedKey.equals("location") || key.equals("所在地") || key.equals("城市")) {
                    SourceMatch match = matchDistinctBasicValue(
                            value, null, null, provenance, allocatedBasicValues);
                    if (match != null) {
                        addContact(contacts, ResumeDocumentContactType.LOCATION, value, match.reference());
                        addReferenceIfPresent(basicReferences, match);
                    }
                } else if (normalizedKey.equals("github") || normalizedKey.equals("linkedin")
                        || normalizedKey.equals("wechat") || normalizedKey.equals("qq")
                        || normalizedKey.equals("website")) {
                    SourceMatch match = matchDistinctBasicValue(
                            value, null, null, provenance, allocatedBasicValues);
                    if (match != null) {
                        addContact(contacts, contactTypeForKey(normalizedKey), value, match.reference());
                        addReferenceIfPresent(basicReferences, match);
                    }
                } else if (normalizedKey.equals("degree") || normalizedKey.equals("highesteducation")
                        || key.equals("学历") || key.equals("最高学历")) {
                    if (highestEducation == null) {
                        SourceMatch match = matchDistinctBasicValue(
                                value, null, null, provenance, allocatedBasicValues);
                        if (match != null) {
                            highestEducation = value;
                            putFieldRef(fieldSourceRefs, "highestEducation", match);
                            addReferenceIfPresent(basicReferences, match);
                        }
                    }
                } else if (normalizedKey.equals("jobintention") || key.equals("求职意向")) {
                    if (jobIntention == null) {
                        SourceMatch match = matchDistinctBasicValue(
                                value, null, null, provenance, allocatedBasicValues);
                        if (match != null) {
                            jobIntention = value;
                            putFieldRef(fieldSourceRefs, "jobIntention", match);
                            addReferenceIfPresent(basicReferences, match);
                        }
                    }
                } else if ((normalizedKey.equals("school") || normalizedKey.equals("university")
                        || key.equals("学校") || key.equals("院校"))
                        && represented(representedText, value)) {
                    // 学校已出现在教育经历等正式章节中，不重复进入未决候选。
                    continue;
                } else if (sourceBacked(key + "：" + value, sourceText)) {
                    // 性别/年龄/工作年限等非投递必需字段不自动进入正式文档，交由用户确认。
                    SourceMatch match = matchDistinctBasicValue(
                            value, null, null, provenance, allocatedBasicValues);
                    addFragment(unresolved, key + "：" + value, "无法安全归类的基础信息，请确认是否保留",
                            primaryOccurrenceId(match == null ? null : match.reference()));
                    addReferenceIfPresent(basicReferences, match);
                }
            }
        }

        if (!hasReachableContact(contacts) && !hasContactCandidate(unresolved)) {
            addRequiredContactCandidate(unresolved);
        }
        SourceMatch nameMatch = matchDistinctBasicValue(
                content.getName(), null, null, provenance, allocatedBasicValues);
        String name = nameMatch == null ? null : trimToNull(content.getName());
        if (name == null) {
            String nameCandidate = findNameCandidate(sourceText);
            SourceMatch nameCandidateMatch = provenance.match(nameCandidate, null, null);
            addNameCandidate(unresolved, nameCandidate,
                    primaryOccurrenceId(nameCandidateMatch == null
                            ? null : nameCandidateMatch.reference()));
        }
        putFieldRef(fieldSourceRefs, "name", nameMatch);
        addReferenceIfPresent(basicReferences, nameMatch);
        addReferenceIfPresent(basicReferences, jobIntentionMatch);
        addReferenceIfPresent(basicReferences, highestEducationMatch);
        ResumeSourceRefDTO basicsReference = provenance.mergeReferences(basicReferences);
        return ResumeDocumentBasicsDTO.builder()
                .sourceRef(basicsReference)
                .sourceOccurrenceIds(occurrenceIdsFromReferences(basicReferences).isEmpty()
                        ? null : new ArrayList<>(occurrenceIdsFromReferences(basicReferences)))
                .fieldSourceRefs(fieldSourceRefs)
                .name(name)
                .jobIntention(jobIntention)
                .highestEducation(highestEducation)
                .contacts(contacts)
                .build();
    }

    private void putFieldRef(
            Map<String, ResumeSourceRefDTO> fieldSourceRefs,
            String field,
            SourceMatch match) {
        if (fieldSourceRefs != null && field != null && match != null && match.reference() != null) {
            fieldSourceRefs.put(field, match.reference());
        }
    }

    private SourceMatch matchDistinctBasicValue(
            String value,
            String section,
            ResumeSourceRefDTO preferred,
            SourceContext provenance,
            Map<String, Set<String>> allocatedValues) {
        String trimmed = trimToNull(value);
        if (trimmed == null || provenance == null) {
            return null;
        }
        // Basics are scalar claims, not a source-ordered sibling list. Repeated explicit text
        // without a preferred occurrence boundary is ambiguous and must not consume "the next"
        // matching row merely because it appears first.
        return provenance.match(trimmed, section, preferred);
    }

    private void addReferenceIfPresent(List<ResumeSourceRefDTO> target, SourceMatch match) {
        if (target != null && match != null && match.reference() != null) {
            target.add(match.reference());
        }
    }

    private boolean hasReachableContact(List<ResumeDocumentContactDTO> contacts) {
        if (contacts == null) {
            return false;
        }
        for (ResumeDocumentContactDTO contact : contacts) {
            if (contact == null || contact.getValue() == null) {
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

    private boolean hasContactCandidate(List<ResumeUnresolvedItemDTO> unresolved) {
        return unresolved != null && unresolved.stream()
                .anyMatch(item -> item != null
                        && (ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE.equals(item.getKind())
                        || ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE.equals(item.getKind())));
    }

    private void addRequiredContactCandidate(List<ResumeUnresolvedItemDTO> unresolved) {
        if (unresolved.size() >= MAX_UNRESOLVED_ITEMS) {
            throw new BusinessException(500, "未表示内容超过审查上限，请重新整理或重新解析简历");
        }
        unresolved.add(ResumeUnresolvedItemDTO.builder()
                .kind(ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE)
                .canonicalDraft("{\"type\":\"PHONE\",\"label\":\"电话\",\"value\":\"\"}")
                .reason("缺少可用电话或邮箱，请补录后接受")
                .build());
    }

    private void addNameCandidate(List<ResumeUnresolvedItemDTO> unresolved, String sourceLine) {
        addNameCandidate(unresolved, sourceLine, null);
    }

    private void addNameCandidate(
            List<ResumeUnresolvedItemDTO> unresolved, String sourceLine, String sourceOccurrenceId) {
        String candidate = trimToNull(sourceLine);
        if (unresolved.size() >= MAX_UNRESOLVED_ITEMS) {
            throw new BusinessException(500, "未表示内容超过审查上限，请重新整理或重新解析简历");
        }
        unresolved.add(ResumeUnresolvedItemDTO.builder()
                .kind(ResumeUnresolvedItemDTO.KIND_NAME_CANDIDATE)
                .canonicalDraft("{\"text\":" + jsonString(candidate == null ? "" : candidate) + "}")
                .sourceRef(firstNonBlank(sourceOccurrenceId, candidate))
                .reason(candidate == null
                        ? "未能识别姓名，请手动填写并接受或删除"
                        : "未能安全确认姓名，请核对后编辑并接受或删除")
                .build());
    }

    private String findNameCandidate(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return null;
        }
        for (String line : sourceText.split("\\R")) {
            String candidate = trimToNull(line);
            if (candidate == null || candidate.length() > 40 || isStructuralHeading(candidate)
                    || ResumeDocumentQualityValidator.isValidEmail(candidate)
                    || ResumeDocumentQualityValidator.isValidPhone(candidate)
                    || candidate.matches(".*(?:19|20)\\d{2}.*")) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private void addTypedContact(
            List<ResumeDocumentContactDTO> contacts,
            List<ResumeUnresolvedItemDTO> unresolved,
            ResumeDocumentContactType type,
            String value,
            SourceMatch match) {
        String trimmed = trimToNull(value);
        if (trimmed == null || match == null) {
            // AI 候选中不在原文出现的联系方式不是用户事实，直接丢弃，不进入审查候选。
            return;
        }
        boolean valid = type == ResumeDocumentContactType.PHONE
                ? ResumeDocumentQualityValidator.isValidPhone(trimmed)
                : ResumeDocumentQualityValidator.isValidEmail(trimmed);
        if (valid) {
            addContact(contacts, type, trimmed, match.reference());
        } else {
            addContactCandidate(unresolved, type, trimmed, primaryOccurrenceId(match.reference()));
        }
    }

    private void addContact(List<ResumeDocumentContactDTO> contacts, ResumeDocumentContactType type, String value) {
        addContact(contacts, type, value, null);
    }

    private void addContact(
            List<ResumeDocumentContactDTO> contacts,
            ResumeDocumentContactType type,
            String value,
            ResumeSourceRefDTO sourceRef) {
        boolean duplicate = contacts.stream()
                .anyMatch(contact -> type.name().equals(contact.getType()) && value.equals(contact.getValue()));
        if (duplicate && (sourceRef == null || sourceRef.getSourceOccurrenceIds() == null
                || sourceRef.getSourceOccurrenceIds().isEmpty())) {
            // Unreferenced legacy compatibility projections may be normalized to one contact,
            // but two source-backed occurrences remain two independently traceable facts.
            return;
        }
        if (contacts.size() >= MAX_CONTACTS) {
            throw new BusinessException(500, "简历联系方式超出编辑上限，无法安全转换");
        }
        contacts.add(ResumeDocumentContactDTO.builder()
                .type(type.name())
                .label(type.getDefaultLabel())
                .value(value)
                .sourceRef(sourceRef)
                .sourceOccurrenceIds(sourceRef == null ? null : sourceRef.getSourceOccurrenceIds())
                .build());
    }

    private List<ResumeDocumentSectionDTO> buildSections(
            ResumeStructuredContentDTO content,
            List<ResumeUnresolvedItemDTO> unresolved,
            String sourceText,
            SourceContext provenance) {
        ResumeStructuredDataDTO data = content.getStructuredData();
        List<ResumeDocumentSectionDTO> sections = new ArrayList<>();
        if (data == null) {
            return sections;
        }

        String summary = trimToNull(data.getSummary());
        SourceMatch summaryMatch = provenance.match(summary, "SUMMARY", data.getSummarySourceRef());
        if (summaryMatch == null && summary != null && sourceBackedSummary(summary, content)) {
            // Wrapped summary recovery is scoped to the recognized SUMMARY section. The
            // provenance resolver may return a contiguous span, but never a cross-section union.
            summaryMatch = provenance.match(summary, "SUMMARY", data.getSummarySourceRef());
        }
        if (summary != null && summaryMatch != null) {
            ResumeDocumentEntryDTO entry = genericEntry(
                    List.of(summary), List.of(summaryMatch.reference()));
            addSection(sections, ResumeDocumentSectionKind.SUMMARY, "个人总结",
                    List.of(entry), summaryMatch.reference());
        }

        // New canonical projections use the recruiter reading order. The V1 section list
        // remains the persisted display-order contract after this point; templates never
        // silently reorder an explicitly edited TARGET document.
        addExperienceSections(sections, data.getExperiences(), sourceText, unresolved, provenance);
        addProjectSection(sections, data.getProjects(), sourceText, unresolved, provenance);
        addEducationSection(
                sections, data.getEducation(), data.getEducationSourceRefs(), sourceText, unresolved, provenance);
        addSkillSection(sections, data.getSkills(), sourceText, provenance);
        addAchievementSection(sections, data.getAchievements(), sourceText, provenance);
        addCertificateSection(sections, data.getCertificates(), sourceText, provenance);

        if (data.getOthers() != null) {
            int added = 0;
            List<ResumeSourceRefDTO> otherRefs = provenance.referencesForValues(
                    data.getOthers(), null, null);
            for (int index = 0; index < data.getOthers().size(); index++) {
                String text = trimToNull(data.getOthers().get(index));
                if (text == null || !sourceBacked(text, sourceText)) {
                    // structured parser/LLM 可能输出原文之外的候选；它不是用户事实，不进入 sidecar。
                    continue;
                }
                if (added >= MAX_OTHERS_CANDIDATES) {
                    break;
                }
                ResumeSourceRefDTO otherRef = index < otherRefs.size() ? otherRefs.get(index) : null;
                addFragment(unresolved, text, "未归类内容，请确认归属章节或删除",
                        primaryOccurrenceId(otherRef));
                added++;
            }
        }
        return sections;
    }

    private void addEducationSection(
            List<ResumeDocumentSectionDTO> sections,
            List<String> educationLines,
            List<ResumeSourceRefDTO> educationSourceRefs,
            String sourceText,
            List<ResumeUnresolvedItemDTO> unresolved,
            SourceContext provenance) {
        if (educationLines == null || educationLines.isEmpty()) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        Set<String> consumedOccurrences = new LinkedHashSet<>();
        int sourceRefIndex = 0;
        for (String line : educationLines) {
            String text = trimToNull(line);
            if (text == null) {
                continue;
            }
            ResumeSourceRefDTO preferred = educationSourceRefs != null && sourceRefIndex < educationSourceRefs.size()
                    ? educationSourceRefs.get(sourceRefIndex)
                    : null;
            sourceRefIndex++;
            SourceMatch lineMatch = provenance.matchNext(
                    text, "EDUCATION", preferred, consumedOccurrences);
            if (lineMatch == null) {
                continue;
            }
            provenance.consume(consumedOccurrences, lineMatch);
            ResumeDocumentEntryDTO entry = parseEducationEntry(text, lineMatch, provenance);
            if (entry.getSchool() == null || entry.getSchool().isBlank()) {
                addEntryCandidate(unresolved, ResumeDocumentSectionKind.EDUCATION, entry,
                        primaryOccurrenceId(lineMatch.reference()));
            } else {
                entries.add(entry);
            }
        }
        addSection(sections, ResumeDocumentSectionKind.EDUCATION, "教育经历", entries);
    }

    /**
     * 教育行的确定性拆分：先提取原文日期区间，再识别学校/学历/专业。
     * 无法可靠识别学校时不猜字段，整行保留为要点，内容不丢。
     */
    private ResumeDocumentEntryDTO parseEducationEntry(
            String line, SourceMatch lineMatch, SourceContext provenance) {
        String startDate = null;
        String endDate = null;
        String remainder = line;
        Matcher range = EDUCATION_DATE_RANGE.matcher(line);
        if (range.find()) {
            startDate = range.group(1).strip();
            endDate = range.group(2).strip();
            remainder = (line.substring(0, range.start()) + " " + line.substring(range.end()))
                    .replaceAll("[\\s|｜·、,，:：\\-–—~～]+", " ")
                    .strip();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : remainder.split("[\\s|｜·、,，]+")) {
            String trimmed = trimToNull(token);
            if (trimmed != null) {
                tokens.add(trimmed);
            }
        }
        String school = null;
        String degree = null;
        List<String> majorParts = new ArrayList<>();
        for (String token : tokens) {
            if (school == null && (token.contains("大学") || token.contains("学院") || token.contains("学校"))) {
                school = token;
            } else if (degree == null && DEGREE_WORDS.contains(token)) {
                degree = token;
            } else {
                majorParts.add(token);
            }
        }
        if (school == null) {
            return genericEntry(List.of(line), List.of(lineMatch.reference()));
        }
        String major = majorParts.isEmpty() ? null : String.join(" ", majorParts);
        Map<String, ResumeSourceRefDTO> fieldRefs = new LinkedHashMap<>();
        putFieldRef(fieldRefs, "school", provenance.match(school, "EDUCATION", lineMatch.reference()));
        putFieldRef(fieldRefs, "degree", provenance.match(degree, "EDUCATION", lineMatch.reference()));
        putFieldRef(fieldRefs, "major", provenance.match(major, "EDUCATION", lineMatch.reference()));
        putFieldRef(fieldRefs, "startDate", provenance.match(startDate, "EDUCATION", lineMatch.reference()));
        putFieldRef(fieldRefs, "endDate", provenance.match(endDate, "EDUCATION", lineMatch.reference()));
        return ResumeDocumentEntryDTO.builder()
                .sourceRef(lineMatch.reference())
                .sourceOccurrenceIds(lineMatch.reference().getSourceOccurrenceIds())
                .fieldSourceRefs(fieldRefs)
                .school(school)
                .degree(degree)
                .major(major)
                .startDate(startDate)
                .endDate(endDate)
                .bullets(new ArrayList<>())
                .build();
    }

    private void addExperienceSections(
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeExperienceDTO> experiences,
            String sourceText,
            List<ResumeUnresolvedItemDTO> unresolved,
            SourceContext provenance) {
        if (experiences == null || experiences.isEmpty()) {
            return;
        }
        Map<String, List<ResumeExperienceDTO>> grouped = new LinkedHashMap<>();
        for (ResumeExperienceDTO experience : experiences) {
            if (experience == null) {
                continue;
            }
            grouped.computeIfAbsent(experienceTypeOf(experience.getType()), key -> new ArrayList<>())
                    .add(experience);
        }
        for (Map.Entry<String, List<ResumeExperienceDTO>> group : grouped.entrySet()) {
            List<ExperienceBucket> buckets = groupExperiences(group.getValue());
            List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
            Set<String> consumedBucketOccurrences = new LinkedHashSet<>();
            for (ExperienceBucket bucket : buckets) {
                String sourceSection = group.getKey();
                boolean hasExplicitReferences = !bucket.sourceRefs.isEmpty();
                ResumeSourceRefDTO bucketRef = provenance.mergeReferences(bucket.sourceRefs);
                Set<String> bucketOccurrences = new LinkedHashSet<>();
                boolean boundaryAllocated = true;
                if (hasExplicitReferences) {
                    // mergeReferences authenticates both the IDs and the one continuous span.
                    // Do this before consuming anything so a non-contiguous explicit boundary
                    // cannot partially claim rows and then continue as a normal entry.
                    if (bucketRef == null) {
                        boundaryAllocated = false;
                    } else {
                        boundaryAllocated = provenance.consumeReferences(
                                consumedBucketOccurrences, bucket.sourceRefs, sourceSection);
                    }
                    if (boundaryAllocated) {
                        for (ResumeSourceRefDTO sourceRef : bucket.sourceRefs) {
                            bucketOccurrences.addAll(nonBlankIds(sourceRef.getSourceOccurrenceIds()));
                        }
                    }
                    if (!boundaryAllocated) {
                        // An explicit boundary is authoritative. A stale or already-owned
                        // reference must never be reinterpreted as the first equal-text row in
                        // another entry; keep the candidate reviewable but unproven.
                        addEntryCandidate(unresolved, ResumeDocumentSectionKind.EXPERIENCE,
                                bucket.reviewCandidate(), null);
                        continue;
                    }
                } else {
                    List<ResumeSourceRefDTO> inferredRefs = new ArrayList<>();
                    Set<String> trialConsumed = new LinkedHashSet<>(consumedBucketOccurrences);
                    for (String sourceLine : bucket.sourceLines) {
                        SourceMatch lineMatch = provenance.matchNext(
                                sourceLine, sourceSection, null, trialConsumed);
                        if (lineMatch == null) {
                            continue;
                        }
                        provenance.consume(trialConsumed, lineMatch);
                        bucketOccurrences.addAll(provenance.occurrenceIds(lineMatch.occurrences()));
                        inferredRefs.add(lineMatch.reference());
                    }
                    bucketRef = provenance.mergeReferences(inferredRefs);
                    if (bucketRef == null && !inferredRefs.isEmpty()) {
                        // An inferred entry may span several physical rows, but those rows must
                        // be one continuous source span. Keep a non-contiguous candidate in the
                        // review sidecar instead of combining fields from unrelated entries.
                        addEntryCandidate(unresolved, ResumeDocumentSectionKind.EXPERIENCE,
                                bucket.reviewCandidate(), null);
                        continue;
                    }
                    consumedBucketOccurrences.clear();
                    consumedBucketOccurrences.addAll(trialConsumed);
                }
                if (bucketOccurrences.isEmpty() && bucketRef != null) {
                    bucketOccurrences.addAll(nonBlankIds(bucketRef.getSourceOccurrenceIds()));
                }
                SourceBackedValues backedBullets = sourceBackedValues(
                        bucket.bullets, provenance, sourceSection, bucketOccurrences);
                List<String> bullets = backedBullets.values();
                SourceMatch organizationMatch = provenance.matchWithin(
                        bucket.organization, sourceSection, bucketOccurrences);
                SourceMatch roleMatch = provenance.matchWithin(bucket.role, sourceSection, bucketOccurrences);
                SourceMatch startDateMatch = provenance.matchWithin(bucket.startDate, sourceSection, bucketOccurrences);
                SourceMatch endDateMatch = provenance.matchWithin(bucket.endDate, sourceSection, bucketOccurrences);
                String organization = organizationMatch == null ? null : trimToNull(bucket.organization);
                String role = roleMatch == null ? null : trimToNull(bucket.role);
                String startDate = startDateMatch == null ? null : trimToNull(bucket.startDate);
                String endDate = endDateMatch == null ? null : trimToNull(bucket.endDate);
                if (isBlank(organization) && isBlank(role) && bullets.isEmpty()) {
                    if (!bucketOccurrences.isEmpty()) {
                        addEntryCandidate(unresolved, ResumeDocumentSectionKind.EXPERIENCE,
                                bucket.reviewCandidate(), firstOccurrenceId(bucketOccurrences));
                    }
                    continue;
                }
                Map<String, ResumeSourceRefDTO> fieldRefs = new LinkedHashMap<>();
                putFieldRef(fieldRefs, "organization", organizationMatch);
                putFieldRef(fieldRefs, "role", roleMatch);
                putFieldRef(fieldRefs, "startDate", startDateMatch);
                putFieldRef(fieldRefs, "endDate", endDateMatch);
                List<ResumeSourceRefDTO> allRefs = new ArrayList<>();
                allRefs.add(organizationMatch == null ? null : organizationMatch.reference());
                allRefs.add(roleMatch == null ? null : roleMatch.reference());
                allRefs.add(startDateMatch == null ? null : startDateMatch.reference());
                allRefs.add(endDateMatch == null ? null : endDateMatch.reference());
                allRefs.addAll(backedBullets.references());
                ResumeSourceRefDTO entryRef = bucketRef != null
                        ? bucketRef : firstReference(allRefs);
                Set<String> entryOccurrenceIds = new LinkedHashSet<>(bucketOccurrences);
                entryOccurrenceIds.addAll(occurrenceIdsFromReferences(allRefs));
                ResumeDocumentEntryDTO entry = ResumeDocumentEntryDTO.builder()
                        .sourceRef(entryRef)
                        .sourceOccurrenceIds(entryOccurrenceIds.isEmpty() ? null : new ArrayList<>(entryOccurrenceIds))
                        .fieldSourceRefs(fieldRefs)
                        .organization(organization)
                        .role(role)
                        .startDate(startDate)
                        .endDate(endDate)
                        .bullets(toBullets(bullets, backedBullets.references()))
                        .build();
                if (isBlank(organization)) {
                    addEntryCandidate(unresolved, ResumeDocumentSectionKind.EXPERIENCE, entry,
                            firstOccurrenceId(entryOccurrenceIds));
                } else {
                    entries.add(entry);
                }
            }
            addSection(sections, ResumeDocumentSectionKind.EXPERIENCE, experienceTitle(group.getKey()), entries);
        }
    }

    /**
     * 规则解析的旧兼容模型可能一行一个 experience：只有带组织/日期的行是新条目，后续行并入前一条。
     * 这里不根据语气猜新公司；没有可识别标题的首条仍保留为无标题条目，由质量门阻止 READY。
     */
    private List<ExperienceBucket> groupExperiences(List<ResumeExperienceDTO> candidates) {
        List<ExperienceBucket> result = new ArrayList<>();
        for (ResumeExperienceDTO candidate : candidates == null ? List.<ResumeExperienceDTO>of() : candidates) {
            if (candidate == null) {
                continue;
            }
            boolean header = isExperienceHeader(candidate);
            if (header || result.isEmpty()) {
                result.add(new ExperienceBucket(candidate, header));
            } else {
                result.get(result.size() - 1).addContinuation(candidate);
            }
        }
        return result;
    }

    private boolean isExperienceHeader(ResumeExperienceDTO candidate) {
        return !isBlank(candidate.getOrganization())
                || !isBlank(candidate.getStartDate())
                || !isBlank(candidate.getEndDate());
    }

    /**
     * A project name is an entry boundary even when dates are absent. Only a nameless candidate
     * can be treated as a continuation of the preceding project.
     */
    private List<ProjectBucket> groupProjects(List<ResumeProjectDTO> candidates) {
        List<ProjectBucket> result = new ArrayList<>();
        for (ResumeProjectDTO candidate : candidates == null ? List.<ResumeProjectDTO>of() : candidates) {
            if (candidate == null) {
                continue;
            }
            boolean header = result.isEmpty() || isProjectHeader(candidate);
            if (header) {
                result.add(new ProjectBucket(candidate, isProjectHeader(candidate)));
            } else {
                result.get(result.size() - 1).addContinuation(candidate);
            }
        }
        return result;
    }

    private boolean isProjectHeader(ResumeProjectDTO candidate) {
        return !isBlank(candidate.getName())
                || !isBlank(candidate.getStartDate())
                || !isBlank(candidate.getEndDate())
                || (candidate.getTimeRange() != null && candidate.getTimeRange().matches(".*(?:19|20)\\d{2}.*"));
    }

    private ResumeDocumentContactType contactTypeForKey(String key) {
        return switch (key) {
            case "github" -> ResumeDocumentContactType.GITHUB;
            case "linkedin" -> ResumeDocumentContactType.LINKEDIN;
            case "wechat" -> ResumeDocumentContactType.WECHAT;
            case "qq" -> ResumeDocumentContactType.QQ;
            case "website" -> ResumeDocumentContactType.WEBSITE;
            default -> ResumeDocumentContactType.OTHER;
        };
    }

    private String sourceBackedOrNull(String value, String sourceText) {
        String trimmed = trimToNull(value);
        return trimmed == null || !sourceBacked(trimmed, sourceText) ? null : trimmed;
    }

    private void appendBacked(List<String> values, String value, SourceMatch match) {
        if (values != null && match != null) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                values.add(trimmed);
            }
        }
    }

    private String backedValue(String value, SourceMatch match) {
        return match == null ? null : trimToNull(value);
    }

    private List<String> nonNullValues(String... values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private List<ResumeSourceRefDTO> nonNullRefs(ResumeSourceRefDTO... refs) {
        List<ResumeSourceRefDTO> result = new ArrayList<>();
        if (refs != null) {
            for (ResumeSourceRefDTO ref : refs) {
                if (ref != null) {
                    result.add(ref);
                }
            }
        }
        return result;
    }

    private List<String> sourceBackedDistinct(List<String> values, String sourceText) {
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null && sourceBacked(trimmed, sourceText)) {
                // Do not text-dedupe here: two identical source occurrences are legitimate and
                // their occurrence-level provenance is carried by the upstream entry refs.
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<String> sourceBackedDistinct(
            List<String> values,
            String sourceText,
            SourceContext provenance,
            String section,
            ResumeSourceRefDTO preferred) {
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null && provenance.hasCandidate(trimmed, section, preferred)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** Source-backed means one source line/span, never tokens scattered across the document. */
    private boolean sourceBacked(String value, String sourceText) {
        if (value == null || value.isBlank() || sourceText == null || sourceText.isBlank()) {
            return false;
        }
        String normalizedSourceText = normalizeSourceSeparators(sourceText);
        for (String line : normalizedSourceText.split("\\R", -1)) {
            if (sourceBackedInOccurrence(value, line)) {
                return true;
            }
        }
        return false;
    }

    private boolean sourceBackedInOccurrence(String value, String sourceLine) {
        if (value == null || value.isBlank() || sourceLine == null || sourceLine.isBlank()) {
            return false;
        }
        List<String> expectedTokens = sourceTokens(value);
        List<String> availableTokens = sourceTokens(sourceLine);
        if (expectedTokens.isEmpty() || availableTokens.isEmpty()) {
            return false;
        }
        // 允许 “Spring Boot” 与原文 “SpringBoot” 这类确定性空格差异，
        // 但不接受更长 token 的前缀（Java 不得从 JavaScript 推出）。
        String compactExpected = compactSourceToken(expectedTokens);
        if (availableTokens.stream().anyMatch(token -> compactSourceToken(List.of(token)).equals(compactExpected))) {
            return true;
        }
        for (String expectedToken : expectedTokens) {
            if (availableTokens.contains(expectedToken)
                    || availableTokens.stream().anyMatch(token -> compactSourceToken(List.of(token))
                    .equals(compactSourceToken(List.of(expectedToken))))) {
                continue;
            }
            // Dates and labels can be attached to a Chinese fact in the same source line (for
            // example “2024年创新实践奖”); never join separate source rows.
            if (isChineseToken(expectedToken)
                    && (expectedToken.length() >= 2 || expectedToken.matches("[年月日]"))
                    && availableTokens.stream().anyMatch(token -> token.contains(expectedToken))) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean isChineseToken(String token) {
        return token != null && token.matches("[\\u4e00-\\u9fa5]+");
    }

    private boolean isChineseTokenBackedAcrossLineBreak(String expectedToken, String sourceText) {
        String[] lines = sourceText == null ? new String[0] : sourceText.split("\\R", -1);
        for (int index = 0; index + 1 < lines.length; index++) {
            String left = normalize(lines[index]);
            String right = normalize(lines[index + 1]);
            if (left.isEmpty() || right.isEmpty()) {
                continue;
            }
            String joined = left + right;
            int boundary = left.length();
            int searchFrom = 0;
            while (searchFrom < joined.length()) {
                int start = joined.indexOf(expectedToken, searchFrom);
                if (start < 0) {
                    break;
                }
                int end = start + expectedToken.length();
                if (start < boundary && end > boundary
                        && Math.min(boundary - start, end - boundary) <= 1) {
                    return true;
                }
                searchFrom = start + 1;
            }
        }
        return false;
    }

    private String normalizeSourceSeparators(String value) {
        return value == null ? null : value.replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }

    private List<String> sourceTokens(String value) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = SOURCE_TOKEN_PATTERN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (isChineseToken(token) && token.length() > 1 && token.matches("[年月日].+")) {
                tokens.add(token.substring(0, 1));
                tokens.add(token.substring(1));
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String compactSourceToken(List<String> tokens) {
        return String.join("", tokens).replaceAll("[^a-z0-9+#\\u4e00-\\u9fa5]", "");
    }

    private String experienceTypeOf(String type) {
        return "INTERNSHIP".equals(type) ? "INTERNSHIP" : "CAMPUS".equals(type) ? "CAMPUS" : "WORK";
    }

    private String experienceTitle(String type) {
        return switch (type) {
            case "INTERNSHIP" -> "实习经历";
            case "CAMPUS" -> "校园经历";
            default -> "工作经历";
        };
    }

    private void addProjectSection(
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeProjectDTO> projects,
            String sourceText,
            List<ResumeUnresolvedItemDTO> unresolved,
            SourceContext provenance) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        List<ProjectBucket> buckets = groupProjects(projects);
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        Set<String> consumedProjectOccurrences = new LinkedHashSet<>();
        for (ProjectBucket bucket : buckets) {
            ProjectSourceAllocation allocation = allocateProjectBucketReference(
                    bucket, provenance, consumedProjectOccurrences);
            ResumeSourceRefDTO bucketRef = allocation.reference();
            Set<String> bucketOccurrences = allocation.occurrenceIds();
            if (allocation.reviewRequired()) {
                ResumeDocumentEntryDTO reviewCandidate = bucket.reviewCandidate();
                if (!allocation.occurrenceIds().isEmpty()) {
                    reviewCandidate.setSourceOccurrenceIds(new ArrayList<>(allocation.occurrenceIds()));
                }
                // A non-contiguous allocation has no single source span. Preserve the
                // occurrence set on the review draft, but deliberately leave sourceRef null
                // rather than fabricating a continuous range.
                addEntryCandidate(unresolved, ResumeDocumentSectionKind.PROJECT,
                        reviewCandidate, null);
                continue;
            }
            if (bucketOccurrences.isEmpty()) {
                continue;
            }
            List<ResumeSourceRefDTO> bulletRefs = new ArrayList<>();
            List<String> bullets = sourceBackedProjectValues(
                    bucket.bullets, provenance, bucketOccurrences, bulletRefs);
            List<ResumeSourceRefDTO> skillsRefs = new ArrayList<>();
            List<String> skills = sourceBackedProjectValues(
                    bucket.techStack, provenance, bucketOccurrences, skillsRefs);
            if (!skills.isEmpty()) {
                appendUnique(bullets, "技术栈：" + String.join("、", skills));
                bulletRefs.add(null);
            }
            SourceMatch environmentMatch = provenance.matchWithin(
                    bucket.environment, "PROJECT", bucketOccurrences);
            SourceMatch mentorMatch = provenance.matchWithin(bucket.mentor, "PROJECT", bucketOccurrences);
            if (environmentMatch != null) {
                appendLabeled(bullets, "开发环境", bucket.environment);
                bulletRefs.add(environmentMatch.reference());
            }
            if (mentorMatch != null) {
                appendLabeled(bullets, "导师", bucket.mentor);
                bulletRefs.add(mentorMatch.reference());
            }
            SourceMatch organizationMatch = provenance.matchWithin(bucket.name, "PROJECT", bucketOccurrences);
            SourceMatch roleMatch = provenance.matchWithin(bucket.role, "PROJECT", bucketOccurrences);
            SourceMatch startDateMatch = provenance.matchWithin(bucket.startDate, "PROJECT", bucketOccurrences);
            SourceMatch endDateMatch = provenance.matchWithin(bucket.endDate, "PROJECT", bucketOccurrences);
            String organization = organizationMatch == null ? null : trimToNull(bucket.name);
            String role = roleMatch == null ? null : trimToNull(bucket.role);
            String startDate = startDateMatch == null ? null : trimToNull(bucket.startDate);
            String endDate = endDateMatch == null ? null : trimToNull(bucket.endDate);
            if (isBlank(organization) && isBlank(role) && bullets.isEmpty()) {
                continue;
            }
            Map<String, ResumeSourceRefDTO> fieldRefs = new LinkedHashMap<>();
            putFieldRef(fieldRefs, "organization", organizationMatch);
            putFieldRef(fieldRefs, "role", roleMatch);
            putFieldRef(fieldRefs, "startDate", startDateMatch);
            putFieldRef(fieldRefs, "endDate", endDateMatch);
            putFieldRef(fieldRefs, "environment", environmentMatch);
            putFieldRef(fieldRefs, "mentor", mentorMatch);
            List<ResumeSourceRefDTO> techStackRefs = skillsRefs;
            List<ResumeSourceRefDTO> allEntryRefs = nonNullRefs(
                    organizationMatch == null ? null : organizationMatch.reference(),
                    roleMatch == null ? null : roleMatch.reference(),
                    startDateMatch == null ? null : startDateMatch.reference(),
                    endDateMatch == null ? null : endDateMatch.reference(),
                    environmentMatch == null ? null : environmentMatch.reference(),
                    mentorMatch == null ? null : mentorMatch.reference());
            allEntryRefs.addAll(techStackRefs);
            allEntryRefs.addAll(bulletRefs);
            ResumeSourceRefDTO entryRef = bucketRef != null
                    ? bucketRef : firstReference(allEntryRefs);
            Set<String> entryOccurrenceIds = new LinkedHashSet<>(bucketOccurrences);
            entryOccurrenceIds.addAll(occurrenceIdsFromReferences(allEntryRefs));
            ResumeDocumentEntryDTO entry = ResumeDocumentEntryDTO.builder()
                    .sourceRef(entryRef)
                    .sourceOccurrenceIds(entryOccurrenceIds.isEmpty()
                            ? null : new ArrayList<>(entryOccurrenceIds))
                    .fieldSourceRefs(fieldRefs)
                    .organization(organization)
                    .role(role)
                    .startDate(startDate)
                    .endDate(endDate)
                    .environment(environmentMatch == null ? null : trimToNull(bucket.environment))
                    .mentor(mentorMatch == null ? null : trimToNull(bucket.mentor))
                    .techStack(skills)
                    .techStackSourceRefs(techStackRefs)
                    .bullets(toBullets(bullets, bulletRefs))
                    .build();
            if (isBlank(organization)) {
                addEntryCandidate(unresolved, ResumeDocumentSectionKind.PROJECT, entry,
                        firstOccurrenceId(entryOccurrenceIds));
            } else {
                entries.add(entry);
            }
        }
        addSection(sections, ResumeDocumentSectionKind.PROJECT, "项目经历", entries);
    }

    private ProjectSourceAllocation allocateProjectBucketReference(
            ProjectBucket bucket,
            SourceContext provenance,
            Set<String> consumedOccurrences) {
        if (!bucket.sourceRefs.isEmpty()) {
            ResumeSourceRefDTO explicit = provenance.mergeReferences(bucket.sourceRefs);
            Set<String> occurrenceIds = new LinkedHashSet<>();
            for (ResumeSourceRefDTO sourceRef : bucket.sourceRefs) {
                occurrenceIds.addAll(nonBlankIds(sourceRef.getSourceOccurrenceIds()));
            }
            // An explicit reference is a hard boundary. A second bucket cannot silently reuse it.
            if (explicit == null
                    || !provenance.consumeReferences(consumedOccurrences, bucket.sourceRefs, "PROJECT")) {
                return ProjectSourceAllocation.review(occurrenceIds);
            }
            return ProjectSourceAllocation.accept(explicit, occurrenceIds);
        }
        List<ResumeSourceRefDTO> matches = new ArrayList<>();
        Set<String> occurrenceIds = new LinkedHashSet<>();
        Set<String> trialConsumed = new LinkedHashSet<>(consumedOccurrences);
        for (String value : bucket.sourceLines) {
            SourceMatch match = provenance.matchNext(value, "PROJECT", null, trialConsumed);
            if (match == null) {
                continue;
            }
            matches.add(match.reference());
            occurrenceIds.addAll(provenance.occurrenceIds(match.occurrences()));
            provenance.consume(trialConsumed, match);
        }
        if (occurrenceIds.isEmpty()) {
            return ProjectSourceAllocation.empty();
        }
        ResumeSourceRefDTO merged = provenance.mergeReferences(matches);
        if (merged == null) {
            // Multiple inferred rows must describe one continuous project span. Do not return a
            // partial anchor that would allow fields from separate projects to be mixed. Keep the
            // full trial occurrence set on a review draft so no candidate/source identity is lost.
            return ProjectSourceAllocation.review(occurrenceIds);
        }
        consumedOccurrences.clear();
        consumedOccurrences.addAll(trialConsumed);
        return ProjectSourceAllocation.accept(merged, occurrenceIds);
    }

    private List<String> sourceBackedProjectValues(
            List<String> values,
            SourceContext provenance,
            Set<String> allowedOccurrences,
            List<ResumeSourceRefDTO> references) {
        List<String> result = new ArrayList<>();
        Map<String, Set<String>> consumedByValue = new LinkedHashMap<>();
        for (String value : values == null ? List.<String>of() : values) {
            String trimmed = trimToNull(value);
            Set<String> consumed = consumedByValue.computeIfAbsent(normalize(trimmed),
                    ignored -> new LinkedHashSet<>());
            SourceMatch match = provenance.matchWithinSpan(
                    trimmed, "PROJECT", allowedOccurrences, consumed);
            if (trimmed != null && match != null) {
                result.add(trimmed);
                if (references != null) {
                    references.add(match.reference());
                }
                provenance.consume(consumed, match);
            }
        }
        return result;
    }

    private SourceBackedValues sourceBackedValues(
            List<String> values,
            SourceContext provenance,
            String section,
            Set<String> allowedOccurrences) {
        List<String> result = new ArrayList<>();
        List<ResumeSourceRefDTO> references = new ArrayList<>();
        Map<String, Set<String>> consumedByValue = new LinkedHashMap<>();
        for (String value : values == null ? List.<String>of() : values) {
            String trimmed = trimToNull(value);
            Set<String> consumed = consumedByValue.computeIfAbsent(normalize(trimmed),
                    ignored -> new LinkedHashSet<>());
            SourceMatch match = provenance.matchWithinSpan(
                    trimmed, section, allowedOccurrences, consumed);
            if (trimmed != null && match != null) {
                result.add(trimmed);
                references.add(match.reference());
                provenance.consume(consumed, match);
            }
        }
        return new SourceBackedValues(result, references);
    }

    private Set<String> occurrenceIdsFromReferences(List<ResumeSourceRefDTO> references) {
        Set<String> result = new LinkedHashSet<>();
        for (ResumeSourceRefDTO reference : references == null
                ? List.<ResumeSourceRefDTO>of() : references) {
            if (reference != null) {
                result.addAll(nonBlankIds(reference.getSourceOccurrenceIds()));
            }
        }
        return result;
    }

    private ResumeSourceRefDTO firstReference(List<ResumeSourceRefDTO> references) {
        return references == null ? null : references.stream()
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String firstOccurrenceId(Set<String> occurrenceIds) {
        return occurrenceIds == null ? null : occurrenceIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void addSkillSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeSkillSetDTO skills,
            String sourceText,
            SourceContext provenance) {
        if (skills == null) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        List<String> assignedDescriptions = new ArrayList<>();
        Set<String> assignedDescriptionOccurrences = new LinkedHashSet<>();
        Set<String> assignedDescriptionReferenceOccurrences = new LinkedHashSet<>();
        if (skills.getGroups() != null) {
            for (Map.Entry<String, List<String>> group : skills.getGroups().entrySet()) {
                List<String> items = sourceBackedDistinct(nonBlankPreserve(group.getValue()), sourceText,
                        provenance, "SKILL_EVIDENCE", null);
                List<String> descriptions = skillDescriptions(
                        skills, items, sourceText, provenance, assignedDescriptionOccurrences);
                if (items.isEmpty()) {
                    continue;
                }
                assignedDescriptions.addAll(descriptions);
                List<ResumeSourceRefDTO> itemRefs = skillItemRefs(skills, items, provenance);
                List<ResumeSourceRefDTO> descriptionRefs = skillDescriptionRefs(
                        skills, descriptions, items, provenance, assignedDescriptionReferenceOccurrences);
                ResumeSourceRefDTO entryRef = provenance.mergeReferences(nonNullRefs(
                        provenance.mergeReferences(itemRefs), provenance.mergeReferences(descriptionRefs)));
                entries.add(ResumeDocumentEntryDTO.builder()
                        .sourceRef(entryRef)
                        .sourceOccurrenceIds(entryRef == null ? null : entryRef.getSourceOccurrenceIds())
                        .skillItemSourceRefs(itemRefs)
                        .skillDescriptionSourceRefs(descriptionRefs)
                        .group(SKILL_GROUP_LABELS.getOrDefault(group.getKey(), group.getKey()))
                        .skillItems(items)
                        .skillDescriptions(descriptions)
                        .bullets(new ArrayList<>())
                        .build());
            }
        }
        if (entries.isEmpty()) {
            List<String> keywords = sourceBackedDistinct(nonBlankPreserve(skills.getKeywords()), sourceText,
                    provenance, "SKILL_EVIDENCE", null);
            if (!keywords.isEmpty()) {
                List<String> descriptions = skillDescriptions(
                        skills, keywords, sourceText, provenance, assignedDescriptionOccurrences);
                assignedDescriptions.addAll(descriptions);
                List<ResumeSourceRefDTO> itemRefs = skillItemRefs(skills, keywords, provenance);
                List<ResumeSourceRefDTO> descriptionRefs = skillDescriptionRefs(
                        skills, descriptions, keywords, provenance, assignedDescriptionReferenceOccurrences);
                ResumeSourceRefDTO entryRef = provenance.mergeReferences(nonNullRefs(
                        provenance.mergeReferences(itemRefs), provenance.mergeReferences(descriptionRefs)));
                entries.add(ResumeDocumentEntryDTO.builder()
                        .sourceRef(entryRef)
                        .sourceOccurrenceIds(entryRef == null ? null : entryRef.getSourceOccurrenceIds())
                        .skillItemSourceRefs(itemRefs)
                        .skillDescriptionSourceRefs(descriptionRefs)
                        .skillItems(keywords)
                        .skillDescriptions(descriptions)
                        .bullets(new ArrayList<>())
                        .build());
            }
        }
        Map<String, Integer> assignedDescriptionCounts = new LinkedHashMap<>();
        for (String description : assignedDescriptions) {
            assignedDescriptionCounts.merge(historicalKey(description), 1, Integer::sum);
        }
        List<String> remainingDescriptions = new ArrayList<>();
        for (String description : sourceBackedDistinct(
                nonBlankPreserve(skills.getDescriptions()), sourceText, provenance, "SKILL_EVIDENCE", null)) {
            String key = historicalKey(description);
            int assigned = assignedDescriptionCounts.getOrDefault(key, 0);
            if (assigned > 0) {
                assignedDescriptionCounts.put(key, assigned - 1);
            } else {
                remainingDescriptions.add(description);
            }
        }
        if (!remainingDescriptions.isEmpty()) {
            List<ResumeSourceRefDTO> descriptionRefs = skillDescriptionRefs(
                    skills, remainingDescriptions, List.of(), provenance,
                    assignedDescriptionReferenceOccurrences);
            ResumeSourceRefDTO entryRef = provenance.mergeReferences(descriptionRefs);
            entries.add(ResumeDocumentEntryDTO.builder()
                    .sourceRef(entryRef)
                    .sourceOccurrenceIds(entryRef == null ? null : entryRef.getSourceOccurrenceIds())
                    .skillDescriptionSourceRefs(descriptionRefs)
                    .skillItems(new ArrayList<>())
                    .skillDescriptions(remainingDescriptions)
                    .bullets(new ArrayList<>())
                    .build());
        }
        addSection(sections, ResumeDocumentSectionKind.SKILL, "技能",
                coalesceSkillEntriesByOccurrence(entries));
    }

    /** One source row may yield several detected skill groups. Keep it as one main entry so the
     * row is not counted as duplicated ownership while retaining every item/ref pair. */
    private List<ResumeDocumentEntryDTO> coalesceSkillEntriesByOccurrence(
            List<ResumeDocumentEntryDTO> entries) {
        List<ResumeDocumentEntryDTO> result = new ArrayList<>();
        for (ResumeDocumentEntryDTO entry : entries == null ? List.<ResumeDocumentEntryDTO>of() : entries) {
            if (entry == null) {
                continue;
            }
            Set<String> ids = new LinkedHashSet<>(nonBlankIds(entry.getSourceOccurrenceIds()));
            ids.addAll(occurrenceIdsFromReferences(entry.getSourceRef() == null
                    ? List.of() : List.of(entry.getSourceRef())));
            int existingIndex = -1;
            if (!ids.isEmpty()) {
                for (int index = 0; index < result.size(); index++) {
                    Set<String> existingIds = new LinkedHashSet<>(
                            nonBlankIds(result.get(index).getSourceOccurrenceIds()));
                    existingIds.addAll(occurrenceIdsFromReferences(result.get(index).getSourceRef() == null
                            ? List.of() : List.of(result.get(index).getSourceRef())));
                    if (!java.util.Collections.disjoint(ids, existingIds)) {
                        existingIndex = index;
                        break;
                    }
                }
            }
            if (existingIndex < 0) {
                result.add(entry);
            } else {
                mergeSkillEntry(result.get(existingIndex), entry);
            }
        }
        return result;
    }

    private void mergeSkillEntry(ResumeDocumentEntryDTO target, ResumeDocumentEntryDTO source) {
        if (target == null || source == null) {
            return;
        }
        target.setGroup(joinDistinctGroups(target.getGroup(), source.getGroup()));
        target.setSkillItems(concatPreservingOccurrences(target.getSkillItems(), source.getSkillItems()));
        target.setSkillDescriptions(concatPreservingOccurrences(
                target.getSkillDescriptions(), source.getSkillDescriptions()));
        target.setSkillItemSourceRefs(concatRefs(
                target.getSkillItemSourceRefs(), source.getSkillItemSourceRefs()));
        target.setSkillDescriptionSourceRefs(concatRefs(
                target.getSkillDescriptionSourceRefs(), source.getSkillDescriptionSourceRefs()));
        Set<String> ids = new LinkedHashSet<>(nonBlankIds(target.getSourceOccurrenceIds()));
        ids.addAll(nonBlankIds(source.getSourceOccurrenceIds()));
        ids.addAll(occurrenceIdsFromReferences(target.getSourceRef() == null
                ? List.of() : List.of(target.getSourceRef())));
        ids.addAll(occurrenceIdsFromReferences(source.getSourceRef() == null
                ? List.of() : List.of(source.getSourceRef())));
        target.setSourceOccurrenceIds(ids.isEmpty() ? null : new ArrayList<>(ids));
        if (target.getSourceRef() == null) {
            target.setSourceRef(source.getSourceRef());
        }
    }

    private String joinDistinctGroups(String first, String second) {
        String left = trimToNull(first);
        String right = trimToNull(second);
        if (left == null) {
            return right;
        }
        if (right == null || left.equals(right) || left.contains(" / " + right)) {
            return left;
        }
        return left + " / " + right;
    }

    private <T> List<T> concatPreservingOccurrences(List<T> first, List<T> second) {
        List<T> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        if (second != null) {
            result.addAll(second);
        }
        return result.isEmpty() ? null : result;
    }

    private List<ResumeSourceRefDTO> concatRefs(
            List<ResumeSourceRefDTO> first, List<ResumeSourceRefDTO> second) {
        List<ResumeSourceRefDTO> result = concatPreservingOccurrences(first, second);
        return result;
    }

    private List<ResumeSourceRefDTO> skillItemRefs(
            ResumeSkillSetDTO skills,
            List<String> items,
            SourceContext provenance) {
        List<ResumeSourceRefDTO> refs = new ArrayList<>();
        // Different skill facts can legitimately be printed on one source row ("Java, Python").
        // Only repeated claims of the same value need a fresh occurrence; otherwise a global
        // consumed set would silently remove provenance from every item after the first one.
        Map<String, Set<String>> consumedByValue = new LinkedHashMap<>();
        for (String item : items == null ? List.<String>of() : items) {
            String itemKey = normalize(item);
            Set<String> consumed = consumedByValue.computeIfAbsent(itemKey,
                    ignored -> new LinkedHashSet<>());
            SourceMatch selected = null;
            for (ResumeSkillEvidenceDTO evidence : skills == null || skills.getEvidence() == null
                    ? List.<ResumeSkillEvidenceDTO>of() : skills.getEvidence()) {
                if (evidence == null || !skillEvidenceContains(evidence, item)) {
                    continue;
                }
                SourceMatch candidate = provenance.match(item, "SKILL_EVIDENCE", evidence.getSourceRef());
                if (candidate != null && candidate.occurrences().stream()
                        .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                        .noneMatch(consumed::contains)) {
                    selected = candidate;
                    break;
                }
            }
            if (selected == null) {
                selected = nextUnusedSkillMatch(item, provenance, consumed);
            }
            if (selected == null) {
                refs.add(null);
                continue;
            }
            refs.add(selected.reference());
            selected.occurrences().stream()
                    .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                    .filter(java.util.Objects::nonNull)
                    .forEach(consumed::add);
        }
        return refs;
    }

    private boolean skillEvidenceContains(ResumeSkillEvidenceDTO evidence, String item) {
        if (evidence == null || item == null || item.isBlank()) {
            return false;
        }
        return item.equalsIgnoreCase(evidence.getSkill())
                || (evidence.getKeywords() != null
                && evidence.getKeywords().stream().anyMatch(keyword -> item.equalsIgnoreCase(keyword)));
    }

    private SourceMatch nextUnusedSkillMatch(
            String item,
            SourceContext provenance,
            Set<String> consumed) {
        if (item == null || item.isBlank() || provenance == null) {
            return null;
        }
        return provenance.matchNext(item, "SKILL_EVIDENCE", null, consumed);
    }

    private List<ResumeSourceRefDTO> skillDescriptionRefs(
            ResumeSkillSetDTO skills,
            List<String> descriptions,
            List<String> keywords,
            SourceContext provenance,
            Set<String> consumedDescriptionOccurrences) {
        List<ResumeSourceRefDTO> refs = new ArrayList<>();
        for (String description : descriptions == null ? List.<String>of() : descriptions) {
            SourceMatch selected = null;
            boolean hasExplicitMatchingEvidence = false;
            if (skills != null && skills.getEvidence() != null) {
                for (ResumeSkillEvidenceDTO evidence : skills.getEvidence()) {
                    if (evidence == null) {
                        continue;
                    }
                    String evidenceText = trimToNull(evidence.getDescription());
                    if (evidenceText == null
                            && (evidence.getKeywords() == null || evidence.getKeywords().isEmpty())) {
                        evidenceText = trimToNull(evidence.getSourceText());
                    }
                    if (!sameText(description, evidenceText)) {
                        continue;
                    }
                    hasExplicitMatchingEvidence = hasExplicitMatchingEvidence || evidence.getSourceRef() != null;
                    selected = nextUnusedSkillDescriptionMatch(
                            description, evidence.getSourceRef(), provenance, consumedDescriptionOccurrences);
                    if (selected != null) {
                        break;
                    }
                }
            }
            if (selected == null && !hasExplicitMatchingEvidence) {
                selected = nextUnusedSkillDescriptionMatch(
                        description, null, provenance, consumedDescriptionOccurrences);
            }
            if (selected != null) {
                selected.occurrences().stream()
                        .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                        .filter(java.util.Objects::nonNull)
                        .forEach(consumedDescriptionOccurrences::add);
            }
            refs.add(selected == null ? null : selected.reference());
        }
        return refs;
    }

    private List<String> skillDescriptions(
            ResumeSkillSetDTO skills,
            List<String> keywords,
            String sourceText,
            SourceContext provenance,
            Set<String> assignedDescriptionOccurrences) {
        if (skills == null || skills.getEvidence() == null) {
            return List.of();
        }
        List<String> descriptions = new ArrayList<>();
        for (var evidence : skills.getEvidence()) {
            if (evidence == null) {
                continue;
            }
            String description = trimToNull(evidence.getDescription());
            if (description == null && (evidence.getKeywords() == null || evidence.getKeywords().isEmpty())) {
                description = trimToNull(evidence.getSourceText());
            }
            if (description == null) {
                continue;
            }
            String evidenceDescription = description;
            boolean matchesKeyword = keywords == null || keywords.isEmpty()
                    || keywords.stream().anyMatch(keyword -> keyword != null
                    && (evidenceDescription.contains(keyword)
                    || (evidence.getKeywords() != null && evidence.getKeywords().contains(keyword))));
            boolean unmatchedDescriptionBelongsToGroup = (evidence.getKeywords() == null || evidence.getKeywords().isEmpty())
                    && nearestTaggedEvidenceBelongsToGroup(skills.getEvidence(), skills.getEvidence().indexOf(evidence), keywords);
            if (matchesKeyword || unmatchedDescriptionBelongsToGroup) {
                SourceMatch descriptionMatch = nextUnusedSkillDescriptionMatch(
                        description, evidence.getSourceRef(), provenance, assignedDescriptionOccurrences);
                if (descriptionMatch == null) {
                    continue;
                }
                descriptionMatch.occurrences().stream()
                        .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                        .filter(java.util.Objects::nonNull)
                        .forEach(assignedDescriptionOccurrences::add);
                descriptions.add(description);
            }
        }
        return descriptions;
    }

    private SourceMatch nextUnusedSkillDescriptionMatch(
            String description,
            ResumeSourceRefDTO preferred,
            SourceContext provenance,
            Set<String> consumedOccurrences) {
        if (description == null || description.isBlank() || provenance == null) {
            return null;
        }
        if (preferred != null) {
            SourceMatch selected = provenance.match(description, "SKILL_EVIDENCE", preferred);
            if (selected == null || selected.occurrences().stream()
                    .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                    .anyMatch(consumedOccurrences::contains)) {
                return null;
            }
            return selected;
        }
        return provenance.matchNext(description, "SKILL_EVIDENCE", null, consumedOccurrences);
    }

    private boolean nearestTaggedEvidenceBelongsToGroup(
            List<ResumeSkillEvidenceDTO> evidence,
            int index,
            List<String> keywords) {
        if (evidence == null || index < 0 || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (int cursor = index - 1; cursor >= 0; cursor--) {
            ResumeSkillEvidenceDTO candidate = evidence.get(cursor);
            if (candidate != null && candidate.getKeywords() != null && !candidate.getKeywords().isEmpty()) {
                return candidate.getKeywords().stream().anyMatch(keywords::contains);
            }
        }
        for (int cursor = index + 1; cursor < evidence.size(); cursor++) {
            ResumeSkillEvidenceDTO candidate = evidence.get(cursor);
            if (candidate != null && candidate.getKeywords() != null && !candidate.getKeywords().isEmpty()) {
                return candidate.getKeywords().stream().anyMatch(keywords::contains);
            }
        }
        return false;
    }

    private void addAchievementSection(
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeAchievementDTO> achievements,
            String sourceText,
            SourceContext provenance) {
        if (achievements == null || achievements.isEmpty()) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        Set<String> consumedOccurrences = new LinkedHashSet<>();
        for (ResumeAchievementDTO achievement : achievements) {
            if (achievement == null) {
                continue;
            }
            String awardDate = firstNonBlank(achievement.getTimeRange(), achievement.getDate());
            String anchor = firstNonBlank(
                    achievement.getTitle(), achievement.getLevel(), achievement.getCompetition(),
                    achievement.getRanking(), awardDate,
                    achievement.getEvidence() == null || achievement.getEvidence().isEmpty()
                            ? null : achievement.getEvidence().get(0));
            if (anchor == null) {
                continue;
            }
            SourceMatch anchorMatch = provenance.matchNext(
                    anchor, "ACHIEVEMENT", achievement.getSourceRef(), consumedOccurrences);
            if (anchorMatch == null) {
                continue;
            }
            provenance.consume(consumedOccurrences, anchorMatch);
            ResumeSourceRefDTO entryBoundary = anchorMatch.reference();
            SourceMatch titleMatch = provenance.match(
                    achievement.getTitle(), "ACHIEVEMENT", entryBoundary);
            SourceMatch levelMatch = provenance.match(
                    achievement.getLevel(), "ACHIEVEMENT", entryBoundary);
            SourceMatch competitionMatch = provenance.match(
                    achievement.getCompetition(), "ACHIEVEMENT", entryBoundary);
            SourceMatch rankingMatch = provenance.match(
                    achievement.getRanking(), "ACHIEVEMENT", entryBoundary);
            SourceMatch dateMatch = provenance.match(awardDate, "ACHIEVEMENT", entryBoundary);
            List<String> backedFields = new ArrayList<>();
            appendBacked(backedFields, achievement.getTitle(), titleMatch);
            appendBacked(backedFields, achievement.getLevel(), levelMatch);
            appendBacked(backedFields, achievement.getCompetition(), competitionMatch);
            appendBacked(backedFields, achievement.getRanking(), rankingMatch);
            appendBacked(backedFields, awardDate, dateMatch);
            if (backedFields.isEmpty()) {
                continue;
            }
            Map<String, ResumeSourceRefDTO> fieldRefs = new LinkedHashMap<>();
            putFieldRef(fieldRefs, "awardTitle", titleMatch);
            putFieldRef(fieldRefs, "awardLevel", levelMatch);
            putFieldRef(fieldRefs, "awardCompetition", competitionMatch);
            putFieldRef(fieldRefs, "awardRanking", rankingMatch);
            putFieldRef(fieldRefs, "awardDate", dateMatch);
            List<ResumeSourceRefDTO> achievementRefs = nonNullRefs(
                    titleMatch == null ? null : titleMatch.reference(),
                    levelMatch == null ? null : levelMatch.reference(),
                    competitionMatch == null ? null : competitionMatch.reference(),
                    rankingMatch == null ? null : rankingMatch.reference(),
                    dateMatch == null ? null : dateMatch.reference());
            ResumeSourceRefDTO mergedEntryRef = provenance.mergeReferences(achievementRefs);
            ResumeSourceRefDTO entryRef = mergedEntryRef == null
                    ? anchorMatch.reference() : mergedEntryRef;
            Set<String> entryOccurrenceIds = occurrenceIdsFromReferences(achievementRefs);
            entryOccurrenceIds.addAll(provenance.occurrenceIds(anchorMatch.occurrences()));
            ResumeSourceRefDTO summaryRef = mergedEntryRef;
            entries.add(ResumeDocumentEntryDTO.builder()
                    .sourceRef(entryRef)
                    .sourceOccurrenceIds(entryOccurrenceIds.isEmpty()
                            ? null : new ArrayList<>(entryOccurrenceIds))
                    .fieldSourceRefs(fieldRefs)
                    .awardTitle(backedValue(achievement.getTitle(), titleMatch))
                    .awardLevel(backedValue(achievement.getLevel(), levelMatch))
                    .awardCompetition(backedValue(achievement.getCompetition(), competitionMatch))
                    .awardRanking(backedValue(achievement.getRanking(), rankingMatch))
                    .awardDate(backedValue(awardDate, dateMatch))
                    .bullets(toBullets(List.of(String.join(" · ", backedFields)),
                            List.of(summaryRef)))
                    .build());
        }
        addSection(sections, ResumeDocumentSectionKind.ACHIEVEMENT, "荣誉奖项", entries);
    }

    private boolean allAwardFieldsSourceBacked(
            ResumeAchievementDTO achievement, String sourceText) {
        return optionalSourceBacked(achievement.getTitle(), sourceText)
                && optionalSourceBacked(achievement.getLevel(), sourceText)
                && optionalSourceBacked(achievement.getCompetition(), sourceText)
                && optionalSourceBacked(achievement.getRanking(), sourceText)
                && optionalSourceBacked(firstNonBlank(achievement.getTimeRange(), achievement.getDate()), sourceText);
    }

    private boolean optionalSourceBacked(String value, String sourceText) {
        return value == null || value.isBlank() || sourceBacked(value, sourceText);
    }

    private void addCertificateSection(
            List<ResumeDocumentSectionDTO> sections,
            List<String> certificates,
            String sourceText,
            SourceContext provenance) {
        List<String> values = sourceBackedDistinct(nonBlankPreserve(certificates), sourceText,
                provenance, "CERTIFICATE", null);
        if (values.isEmpty()) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        Set<String> consumedOccurrences = new LinkedHashSet<>();
        for (String certificate : values) {
            SourceMatch match = provenance.matchNext(
                    certificate, "CERTIFICATE", null, consumedOccurrences);
            if (match == null) {
                // The value was text-matched, but every matching occurrence was already
                // allocated to a sibling certificate. Leave the source row for the coverage
                // pass rather than creating an unproven canonical entry.
                continue;
            }
            provenance.consume(consumedOccurrences, match);
            entries.add(ResumeDocumentEntryDTO.builder()
                    .sourceRef(match.reference())
                    .sourceOccurrenceIds(match.reference().getSourceOccurrenceIds())
                    .bullets(toBullets(List.of(certificate), List.of(match.reference())))
                    .build());
        }
        addSection(sections, ResumeDocumentSectionKind.CERTIFICATE, "证书", entries);
    }

    /**
     * 覆盖检查：原材料中未被正式文档表示的行进入未决候选项。
     * 表示判定支持整行包含与逐 token 包含（姓名+电话、学校+专业+日期等被拆字段分别存在时不重复报警）。
     */
    private void appendUnrepresentedLines(
            ResumeStructuredContentDTO content,
            ResumeDocumentBasicsDTO basics,
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeUnresolvedItemDTO> unresolved,
            SourceContext provenance) {
        if (content == null || provenance == null || provenance.occurrences.isEmpty()) {
            return;
        }
        String documentText = collectDocumentText(basics, sections);
        List<String> contactValues = basics == null || basics.getContacts() == null
                ? List.of()
                : basics.getContacts().stream()
                        .map(ResumeDocumentContactDTO::getValue)
                        .filter(value -> value != null && !value.isBlank())
                        .toList();
        Set<String> representedOccurrences = representedOccurrenceIds(basics, sections);
        Set<String> unresolvedOccurrences = unresolvedOccurrenceIds(unresolved, provenance);
        int overflow = 0;
        // Iterate retained occurrences rather than distinct text. Two identical source rows are
        // two review slots; text equality must never make the second occurrence disappear.
        for (ResumeSourceEvidenceMatcher.Occurrence occurrence : provenance.occurrences) {
            if (occurrence == null || occurrence.text() == null) {
                continue;
            }
            String text = trimToNull(occurrence.text());
            if (text == null || text.length() < MIN_COVERAGE_LINE_LENGTH
                    || isStructuralHeading(text)) {
                continue;
            }
            boolean represented = occurrenceRepresented(occurrence, representedOccurrences);
            if (occurrenceRepresented(occurrence, unresolvedOccurrences)
                    || occurrence.sourceOccurrenceIds().isEmpty()
                    && unresolvedLineRepresented(unresolved, text)) {
                continue;
            }
            // A source reference may cover a whole row while the semantic field only contains a
            // strict subset of that row. Re-run the lossless line check even for a referenced
            // occurrence so an attached extra fact is still surfaced for review.
            if (represented && lineRepresented(documentText, contactValues, text)) {
                continue;
            }
            // A source occurrence with no usable identity is not expected from SourceContext,
            // but keep the old textual fallback for direct compatibility inputs.
            if (occurrence.sourceOccurrenceIds().isEmpty() && lineRepresented(documentText, contactValues, text)) {
                continue;
            }
            if (unresolved.size() >= MAX_UNRESOLVED_ITEMS) {
                overflow++;
                continue;
            }
            addFragment(unresolved, text, "该内容未被正式文档包含，请确认归属章节或删除",
                    occurrence.primaryId());
            unresolvedOccurrences.add(occurrence.primaryId());
        }
        if (overflow > 0) {
            // A generated count is not user material and must never be accepted into
            // a formal section. Once the review sidecar cannot represent every line,
            // fail closed instead of presenting an incomplete review as exhaustive.
            throw new BusinessException(500, "未表示内容超过审查上限，请重新整理或重新解析简历");
        }
    }

    private Set<String> representedOccurrenceIds(
            ResumeDocumentBasicsDTO basics,
            List<ResumeDocumentSectionDTO> sections) {
        Set<String> ids = new LinkedHashSet<>();
        if (basics != null) {
            addReferenceIds(ids, basics.getSourceRef());
            ids.addAll(nonBlankIds(basics.getSourceOccurrenceIds()));
            if (basics.getFieldSourceRefs() != null) {
                basics.getFieldSourceRefs().values().forEach(reference -> addReferenceIds(ids, reference));
            }
            for (ResumeDocumentContactDTO contact : safeList(basics.getContacts())) {
                if (contact == null) {
                    continue;
                }
                addReferenceIds(ids, contact.getSourceRef());
                ids.addAll(nonBlankIds(contact.getSourceOccurrenceIds()));
            }
        }
        for (ResumeDocumentSectionDTO section : safeList(sections)) {
            if (section == null) {
                continue;
            }
            addReferenceIds(ids, section.getSourceRef());
            ids.addAll(nonBlankIds(section.getSourceOccurrenceIds()));
            for (ResumeDocumentEntryDTO entry : safeList(section.getEntries())) {
                if (entry == null) {
                    continue;
                }
                addReferenceIds(ids, entry.getSourceRef());
                ids.addAll(nonBlankIds(entry.getSourceOccurrenceIds()));
                if (entry.getFieldSourceRefs() != null) {
                    entry.getFieldSourceRefs().values().forEach(reference -> addReferenceIds(ids, reference));
                }
                addReferenceIds(ids, entry.getSkillItemSourceRefs());
                addReferenceIds(ids, entry.getSkillDescriptionSourceRefs());
                for (ResumeDocumentBulletDTO bullet : safeList(entry.getBullets())) {
                    if (bullet == null) {
                        continue;
                    }
                    addReferenceIds(ids, bullet.getSourceRef());
                    ids.addAll(nonBlankIds(bullet.getSourceOccurrenceIds()));
                }
            }
        }
        return ids;
    }

    private void addReferenceIds(Set<String> target, ResumeSourceRefDTO reference) {
        if (target == null || reference == null) {
            return;
        }
        target.addAll(nonBlankIds(reference.getSourceOccurrenceIds()));
    }

    private void addReferenceIds(Set<String> target, List<ResumeSourceRefDTO> references) {
        for (ResumeSourceRefDTO reference : references == null ? List.<ResumeSourceRefDTO>of() : references) {
            addReferenceIds(target, reference);
        }
    }

    private Set<String> unresolvedOccurrenceIds(
            List<ResumeUnresolvedItemDTO> unresolved,
            SourceContext provenance) {
        Set<String> knownIds = provenance == null ? Set.of() : provenance.occurrences.stream()
                .flatMap(occurrence -> occurrence.sourceOccurrenceIds().stream())
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> ids = new LinkedHashSet<>();
        for (ResumeUnresolvedItemDTO item : safeList(unresolved)) {
            if (item != null && item.getSourceRef() != null
                    && knownIds.contains(item.getSourceRef().strip())) {
                // Source-aware fragments and entry/name candidates use the exact occurrence
                // identity. Human-readable legacy source text is deliberately not treated as an
                // ID, otherwise two equal unresolved rows would collapse into one.
                ids.add(item.getSourceRef().strip());
            }
        }
        return ids;
    }

    private boolean occurrenceRepresented(
            ResumeSourceEvidenceMatcher.Occurrence occurrence,
            Set<String> representedIds) {
        if (occurrence == null || representedIds == null || representedIds.isEmpty()) {
            return false;
        }
        // `ids` also contains logical/visual block IDs for non-authoritative tracing. Only the
        // occurrence namespace may mark a source row as represented; a colliding block ID must
        // never hide a distinct repeated occurrence.
        return occurrence.sourceOccurrenceIds().stream().anyMatch(representedIds::contains)
                || occurrence.primaryId() != null && representedIds.contains(occurrence.primaryId());
    }

    private boolean isStructuralHeading(String text) {
        return STRUCTURAL_HEADINGS.contains(normalize(text));
    }

    private boolean lineRepresented(
            String documentText,
            List<String> contactValues,
            String line) {
        String normalizedLine = normalize(line);
        if (normalizedLine.isEmpty()) {
            return true;
        }
        if (documentText == null || documentText.isBlank()) {
            return false;
        }
        if (sourceBacked(line, documentText)) {
            return true;
        }
        // 联系方式带短标签前缀（如 “Email: x@y.z”）：值已入文档且剩余残差很短时视为已覆盖。
        for (String contactValue : contactValues) {
            String normalizedValue = normalize(contactValue);
            if (normalizedValue.isEmpty() || !normalizedLine.contains(normalizedValue)) {
                continue;
            }
            int residueLength = normalizedLine.length() - normalizedValue.length();
            if (residueLength <= CONTACT_LABEL_RESIDUE_MAX_LENGTH) {
                return true;
            }
        }
        // 结构化字段常会把“技能组：”等标签替换为规范标签；标签后所有事实 token
        // 都必须被表示，不能用比例掩盖遗漏。
        int fullWidthColon = line.indexOf('：');
        int asciiColon = line.indexOf(':');
        int colon = fullWidthColon < 0
                ? asciiColon
                : asciiColon < 0 ? fullWidthColon : Math.min(fullWidthColon, asciiColon);
        if (colon > 0 && colon < line.length() - 1
                && allTokensRepresented(documentText, line.substring(colon + 1))) {
            return true;
        }
        // 被拆到不同字段或被重建的行（如“学校 专业 日期”、技术栈枚举）：
        // 只有行内所有有意义 token 都已出现在文档中，才视为内容已表示；缺一个也进入未决。
        List<String> tokens = meaningfulTokens(line);
        return !tokens.isEmpty() && allTokensRepresented(documentText, String.join(" ", tokens));
    }

    private boolean allTokensRepresented(String documentText, String value) {
        List<String> tokens = meaningfulTokens(value);
        return !tokens.isEmpty() && tokens.stream().allMatch(token -> sourceBacked(token, documentText));
    }

    /**
     * A wrapped summary is one contiguous source span even when PDF text extraction inserts a
     * line break in the middle of a sentence. Keep this recovery narrow to the SUMMARY section;
     * never allow arbitrary lines from different sections to prove a canonical claim.
     */
    private boolean sourceBackedSummary(String value, ResumeStructuredContentDTO content) {
        if (value == null || value.isBlank() || content == null || content.getSections() == null) {
            return false;
        }
        String expected = normalize(value);
        if (expected.isEmpty()) {
            return false;
        }
        for (ResumeTextSectionDTO section : content.getSections()) {
            if (section == null || !isSummarySection(section.getSectionType())) {
                continue;
            }
            List<String> lines = section.getLines() == null ? List.of() : section.getLines();
            for (int start = 0; start < lines.size(); start++) {
                StringBuilder span = new StringBuilder();
                for (int end = start; end < lines.size(); end++) {
                    String line = trimToNull(lines.get(end));
                    if (line == null) {
                        continue;
                    }
                    span.append(line);
                    String normalizedSpan = normalize(span.toString());
                    if (expected.equals(normalizedSpan)) {
                        return true;
                    }
                    if (!expected.startsWith(normalizedSpan) && !normalizedSpan.startsWith(expected)) {
                        break;
                    }
                }
            }
        }
        return false;
    }

    private boolean isSummarySection(String sectionType) {
        if (sectionType == null || sectionType.isBlank()) {
            return false;
        }
        String normalized = normalize(sectionType);
        return "summary".equals(normalized)
                || "个人总结".equals(normalized)
                || "自我评价".equals(normalized)
                || "个人概述".equals(normalized)
                || "个人优势".equals(normalized)
                || "自我介绍".equals(normalized)
                || "profile".equals(normalized);
    }

    /** 提取行内有意义的内容片段：连续中文或连续字母数字。分隔符/标点不作为 token。 */
    private List<String> meaningfulTokens(String line) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = MEANINGFUL_TOKEN_PATTERN.matcher(line);
        while (matcher.find()) {
            String token = normalize(matcher.group());
            if (token.length() >= MIN_TOKEN_LENGTH) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String collectDocumentText(ResumeDocumentBasicsDTO basics, List<ResumeDocumentSectionDTO> sections) {
        return collectBasicsText(basics) + collectSectionText(sections);
    }

    /** 基础信息表示文本：求职意向/最高学历携带标签紧邻值，保证带标签的原始行可被覆盖判定。 */
    private String collectBasicsText(ResumeDocumentBasicsDTO basics) {
        StringBuilder text = new StringBuilder();
        if (basics == null) {
            return "";
        }
        appendIfPresent(text, basics.getName());
        if (basics.getContacts() != null) {
            basics.getContacts().forEach(contact -> appendIfPresent(text, contact.getValue()));
        }
        if (basics.getJobIntention() != null) {
            text.append("求职意向").append(basics.getJobIntention()).append('\n');
        }
        if (basics.getHighestEducation() != null) {
            text.append("最高学历").append(basics.getHighestEducation()).append('\n');
        }
        return text.toString();
    }

    private String collectSectionText(List<ResumeDocumentSectionDTO> sections) {
        StringBuilder text = new StringBuilder();
        if (sections == null) {
            return "";
        }
        for (ResumeDocumentSectionDTO section : sections) {
            appendIfPresent(text, section.getTitle());
            if (section.getEntries() == null) {
                continue;
            }
            for (ResumeDocumentEntryDTO entry : section.getEntries()) {
                appendIfPresent(text, entry.getOrganization());
                appendIfPresent(text, entry.getRole());
                appendIfPresent(text, entry.getSchool());
                appendIfPresent(text, entry.getDegree());
                appendIfPresent(text, entry.getMajor());
                appendIfPresent(text, entry.getStartDate());
                appendIfPresent(text, entry.getEndDate());
                appendIfPresent(text, entry.getLocation());
                appendIfPresent(text, entry.getEnvironment());
                appendIfPresent(text, entry.getMentor());
                if (entry.getTechStack() != null) {
                    entry.getTechStack().forEach(item -> appendIfPresent(text, item));
                }
                appendIfPresent(text, entry.getGroup());
                appendIfPresent(text, entry.getAwardTitle());
                appendIfPresent(text, entry.getAwardLevel());
                appendIfPresent(text, entry.getAwardCompetition());
                appendIfPresent(text, entry.getAwardRanking());
                appendIfPresent(text, entry.getAwardDate());
                if (entry.getSkillItems() != null) {
                    entry.getSkillItems().forEach(item -> appendIfPresent(text, item));
                }
                if (entry.getSkillDescriptions() != null) {
                    entry.getSkillDescriptions().forEach(description -> appendIfPresent(text, description));
                }
                if (entry.getBullets() != null) {
                    entry.getBullets().forEach(bullet -> appendIfPresent(text, bullet.getText()));
                }
            }
        }
        return text.toString();
    }

    private boolean represented(String representedText, String value) {
        String normalizedValue = normalize(value);
        return !normalizedValue.isEmpty() && normalize(representedText).contains(normalizedValue);
    }

    private void addFragment(List<ResumeUnresolvedItemDTO> unresolved, String text, String reason) {
        addFragment(unresolved, text, reason, null);
    }

    private String primaryOccurrenceId(ResumeSourceRefDTO reference) {
        if (reference == null || reference.getSourceOccurrenceIds() == null) {
            return null;
        }
        return reference.getSourceOccurrenceIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::strip)
                .filter(id -> !"null".equalsIgnoreCase(id) && !"undefined".equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    private void addFragment(
            List<ResumeUnresolvedItemDTO> unresolved,
            String text,
            String reason,
            String sourceRef) {
        String draft = "{\"text\":" + jsonString(text) + "}";
        String normalizedSourceRef = trimToNull(sourceRef);
        boolean duplicate = unresolved.stream()
                .anyMatch(item -> ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT.equals(item.getKind())
                        && draft.equals(item.getCanonicalDraft())
                        && java.util.Objects.equals(normalizedSourceRef, trimToNull(item.getSourceRef())));
        if (duplicate) {
            return;
        }
        unresolved.add(ResumeUnresolvedItemDTO.builder()
                .kind(ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT)
                .canonicalDraft(draft)
                .sourceRef(normalizedSourceRef)
                .reason(reason)
                .build());
    }

    private boolean unresolvedLineRepresented(List<ResumeUnresolvedItemDTO> unresolved, String line) {
        String normalizedLine = normalize(line);
        if (normalizedLine.isEmpty() || unresolved == null) {
            return false;
        }
        for (ResumeUnresolvedItemDTO item : unresolved) {
            if (item == null || item.getSourceRef() == null) {
                continue;
            }
            for (String sourceLine : item.getSourceRef().split("\\R")) {
                if (normalizedLine.equals(normalize(sourceLine))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addEntryCandidate(
            List<ResumeUnresolvedItemDTO> unresolved,
            ResumeDocumentSectionKind kind,
            ResumeDocumentEntryDTO entry,
            String sourceRef) {
        if (unresolved.size() >= MAX_UNRESOLVED_ITEMS) {
            throw new BusinessException(500, "未表示内容超过审查上限，请重新整理或重新解析简历");
        }
        try {
            ObjectNode draft = objectMapper.valueToTree(entry);
            draft.put("kind", kind.name());
            unresolved.add(ResumeUnresolvedItemDTO.builder()
                    .kind(ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE)
                    .canonicalDraft(objectMapper.writeValueAsString(draft))
                    .sourceRef(trimToNull(sourceRef))
                    .reason(missingEntryTitleReason(kind))
                    .build());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(500, "候选条目序列化失败");
        }
    }

    private String missingEntryTitleReason(ResumeDocumentSectionKind kind) {
        return switch (kind) {
            case EDUCATION -> "教育经历缺少可确认的学校名，请补充后接受或删除";
            case PROJECT -> "项目经历缺少可确认的项目名，请补充后接受或删除";
            default -> "工作经历缺少可确认的公司名，请补充后接受或删除";
        };
    }

    private void addContactCandidate(
            List<ResumeUnresolvedItemDTO> unresolved, ResumeDocumentContactType type, String value) {
        addContactCandidate(unresolved, type, value, null);
    }

    private void addContactCandidate(
            List<ResumeUnresolvedItemDTO> unresolved,
            ResumeDocumentContactType type,
            String value,
            String sourceOccurrenceId) {
        unresolved.add(ResumeUnresolvedItemDTO.builder()
                .kind(ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE)
                .canonicalDraft("{\"type\":\"" + type.name() + "\",\"label\":\"" + type.getDefaultLabel()
                        + "\",\"value\":" + jsonString(value) + "}")
                .sourceRef(trimToNull(sourceOccurrenceId))
                .reason("该" + type.getDefaultLabel() + "格式无法确认，请核对后接受或删除")
                .build());
    }

    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "候选内容序列化失败");
        }
    }

    private void addSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            List<ResumeDocumentEntryDTO> entries) {
        addSection(sections, kind, title, entries, null);
    }

    private void addSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            List<ResumeDocumentEntryDTO> entries,
            ResumeSourceRefDTO sourceRef) {
        if (entries.isEmpty()) {
            return;
        }
        if (sections.size() >= MAX_SECTIONS) {
            throw new BusinessException(500, "简历章节数量超出编辑上限，无法安全转换");
        }
        if (entries.size() > MAX_ENTRIES_PER_SECTION) {
            throw new BusinessException(500, "单个章节的条目数量超出编辑上限，无法安全转换");
        }
        ResumeSourceRefDTO effectiveSourceRef = sourceRef == null
                ? mergeSourceRefs(entries.stream()
                        .map(ResumeDocumentEntryDTO::getSourceRef)
                        .toList())
                : sourceRef;
        Set<String> sectionOccurrenceIds = new LinkedHashSet<>();
        for (ResumeDocumentEntryDTO entry : entries) {
            if (entry != null) {
                sectionOccurrenceIds.addAll(nonBlankIds(entry.getSourceOccurrenceIds()));
            }
        }
        if (effectiveSourceRef != null) {
            sectionOccurrenceIds.addAll(nonBlankIds(effectiveSourceRef.getSourceOccurrenceIds()));
        }
        sections.add(ResumeDocumentSectionDTO.builder()
                .sourceRef(effectiveSourceRef)
                .sourceOccurrenceIds(sectionOccurrenceIds.isEmpty()
                        ? null : new ArrayList<>(sectionOccurrenceIds))
                .kind(kind.name())
                .title(title)
                .entries(entries)
                .build());
    }

    private ResumeDocumentEntryDTO genericEntry(List<String> bulletTexts) {
        return ResumeDocumentEntryDTO.builder()
                .bullets(toBullets(bulletTexts))
                .build();
    }

    private ResumeDocumentEntryDTO genericEntry(
            List<String> bulletTexts, List<ResumeSourceRefDTO> sourceRefs) {
        List<ResumeDocumentBulletDTO> bullets = new ArrayList<>();
        List<String> texts = bulletTexts == null ? List.of() : bulletTexts;
        for (int index = 0; index < texts.size(); index++) {
            ResumeSourceRefDTO sourceRef = sourceRefs != null && index < sourceRefs.size()
                    ? sourceRefs.get(index) : null;
            bullets.add(ResumeDocumentBulletDTO.builder()
                    .text(texts.get(index))
                    .sourceRef(sourceRef)
                    .sourceOccurrenceIds(sourceRef == null ? null : sourceRef.getSourceOccurrenceIds())
                    .build());
        }
        ResumeSourceRefDTO entryRef = mergeSourceRefs(sourceRefs);
        Set<String> entryOccurrenceIds = occurrenceIdsFromReferences(sourceRefs);
        return ResumeDocumentEntryDTO.builder()
                .sourceRef(entryRef)
                .sourceOccurrenceIds(entryOccurrenceIds.isEmpty()
                        ? null : new ArrayList<>(entryOccurrenceIds))
                .bullets(bullets)
                .build();
    }

    private ResumeSourceRefDTO mergeSourceRefs(List<ResumeSourceRefDTO> references) {
        List<ResumeSourceRefDTO> refs = references == null ? List.of() : references.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        if (refs.isEmpty()) {
            return null;
        }
        if (!referenceRangesAreContiguous(refs)) {
            // A sourceRef represents one source span, not an arbitrary set of rows. Keep the
            // occurrence IDs on the owning entry/section, but do not fabricate a line range for
            // non-contiguous fields.
            return null;
        }
        ResumeSourceRefDTO first = refs.get(0);
        LinkedHashSet<String> blockIds = new LinkedHashSet<>();
        LinkedHashSet<String> occurrenceIds = new LinkedHashSet<>();
        List<String> texts = new ArrayList<>();
        Set<String> emittedOccurrenceIds = new LinkedHashSet<>();
        Set<String> emittedTextKeys = new LinkedHashSet<>();
        Integer startLine = null;
        Integer endLine = null;
        Integer page = first.getPage();
        boolean samePage = true;
        for (ResumeSourceRefDTO ref : refs) {
            if (ref.getSourceBlockIds() != null) {
                blockIds.addAll(ref.getSourceBlockIds());
            }
            if (ref.getSourceOccurrenceIds() != null) {
                occurrenceIds.addAll(ref.getSourceOccurrenceIds());
            }
            Set<String> refOccurrenceIds = new LinkedHashSet<>(nonBlankIds(ref.getSourceOccurrenceIds()));
            boolean newOccurrence = refOccurrenceIds.isEmpty()
                    ? emittedTextKeys.add(normalize(ref.getText()))
                    : refOccurrenceIds.stream().anyMatch(id -> !emittedOccurrenceIds.contains(id));
            if (newOccurrence && ref.getText() != null && !ref.getText().isBlank()) {
                texts.add(ref.getText());
            }
            emittedOccurrenceIds.addAll(refOccurrenceIds);
            if (ref.getStartLine() != null) {
                startLine = startLine == null ? ref.getStartLine() : Math.min(startLine, ref.getStartLine());
            }
            if (ref.getEndLine() != null) {
                endLine = endLine == null ? ref.getEndLine() : Math.max(endLine, ref.getEndLine());
            }
            if (page == null ? ref.getPage() != null : !page.equals(ref.getPage())) {
                samePage = false;
            }
        }
        return ResumeSourceRefDTO.builder()
                .startLine(startLine)
                .endLine(endLine)
                .text(String.join("\n", texts))
                .sourceBlockIds(blockIds.isEmpty() ? null : new ArrayList<>(blockIds))
                .sourceOccurrenceIds(occurrenceIds.isEmpty() ? null : new ArrayList<>(occurrenceIds))
                .page(samePage ? page : null)
                .role(first.getRole())
                .sourceType(first.getSourceType())
                .build();
    }

    private boolean referenceRangesAreContiguous(List<ResumeSourceRefDTO> references) {
        if (references == null || references.size() <= 1) {
            return true;
        }
        List<ResumeSourceRefDTO> ordered = references.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ResumeSourceRefDTO::getStartLine,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        if (ordered.stream().anyMatch(ref -> ref.getStartLine() == null || ref.getEndLine() == null)) {
            return false;
        }
        int end = ordered.get(0).getEndLine();
        for (int index = 1; index < ordered.size(); index++) {
            ResumeSourceRefDTO next = ordered.get(index);
            if (next.getStartLine() > end + 1) {
                return false;
            }
            end = Math.max(end, next.getEndLine());
        }
        return true;
    }

    private List<ResumeDocumentBulletDTO> toBullets(List<String> texts) {
        return toBullets(texts, null);
    }

    private List<ResumeDocumentBulletDTO> toBullets(
            List<String> texts, List<ResumeSourceRefDTO> sourceRefs) {
        if (texts.size() > MAX_BULLETS_PER_ENTRY) {
            throw new BusinessException(500, "单个条目的要点数量超出编辑上限，无法安全转换");
        }
        List<ResumeDocumentBulletDTO> bullets = new ArrayList<>();
        for (int index = 0; index < texts.size(); index++) {
            ResumeSourceRefDTO sourceRef = sourceRefs != null && index < sourceRefs.size()
                    ? sourceRefs.get(index) : null;
            bullets.add(ResumeDocumentBulletDTO.builder()
                    .text(texts.get(index))
                    .sourceRef(sourceRef)
                    .sourceOccurrenceIds(sourceRef == null ? null : sourceRef.getSourceOccurrenceIds())
                    .build());
        }
        return bullets;
    }

    private List<ResumeDocumentBulletDTO> toBullets(
            List<String> texts,
            SourceContext provenance,
            String section,
            ResumeSourceRefDTO preferred) {
        List<ResumeSourceRefDTO> refs = provenance.referencesForValues(texts, section, preferred);
        return toBullets(texts, refs);
    }

    /**
     * 构建结果使用位置派生的稳定 ID（c-1 / s-1 / s-1-e-1 / u-1），
     * 保证同一候选解析重复构建（含恢复优化前版本）得到完全一致的文档。
     */
    private void assignDeterministicIds(ResumeDocumentDTO document, List<ResumeUnresolvedItemDTO> unresolved) {
        if (document.getBasics() != null && document.getBasics().getContacts() != null) {
            List<ResumeDocumentContactDTO> contacts = document.getBasics().getContacts();
            for (int index = 0; index < contacts.size(); index++) {
                contacts.get(index).setId("c-" + (index + 1));
            }
        }
        if (document.getSections() != null) {
            List<ResumeDocumentSectionDTO> sections = document.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                ResumeDocumentSectionDTO section = sections.get(sectionIndex);
                String sectionId = "s-" + (sectionIndex + 1);
                section.setId(sectionId);
                if (section.getEntries() == null) {
                    continue;
                }
                List<ResumeDocumentEntryDTO> entries = section.getEntries();
                for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                    ResumeDocumentEntryDTO entry = entries.get(entryIndex);
                    String entryId = sectionId + "-e-" + (entryIndex + 1);
                    entry.setId(entryId);
                    if (entry.getBullets() != null) {
                        List<ResumeDocumentBulletDTO> bullets = entry.getBullets();
                        for (int bulletIndex = 0; bulletIndex < bullets.size(); bulletIndex++) {
                            bullets.get(bulletIndex).setId(entryId + "-b-" + (bulletIndex + 1));
                        }
                    }
                }
            }
        }
        for (int index = 0; index < unresolved.size(); index++) {
            unresolved.get(index).setId("u-" + (index + 1));
        }
    }

    /**
     * Occurrence-level provenance adapter for canonical output. A candidate value is accepted
     * only when one indexed/raw occurrence contains it; values from separate rows are never
     * concatenated to prove a field. Synthetic IDs are used only for direct legacy/raw-text
     * callers that have no upstream block identity.
     */
    private static final class SourceContext {

        private final List<ResumeSourceEvidenceMatcher.Occurrence> occurrences;
        private final boolean allowUnscopedSections;

        private SourceContext(ResumeStructuredContentDTO content) {
            List<ResumeBlockDTO> indexed = indexedBlocks(content);
            List<ResumeBlockDTO> rawSections = rawSectionBlocks(content);
            List<ResumeBlockDTO> textSections = textSectionBlocks(content);
            List<ResumeBlockDTO> blocks = mergeSourceViews(indexed, rawSections, textSections);
            if (blocks.isEmpty() && content != null && content.getRawText() != null) {
                blocks = rawTextBlocks(content.getRawText());
            }
            blocks = addRawTextGaps(content, blocks);
            this.occurrences = ResumeSourceEvidenceMatcher.index(blocks);
            this.allowUnscopedSections = this.occurrences.stream()
                    .allMatch(occurrence -> isUnscopedSection(occurrence.section()));
        }

        private List<ResumeBlockDTO> indexedBlocks(ResumeStructuredContentDTO content) {
            List<ResumeBlockDTO> blocks = new ArrayList<>();
            int fallbackIndex = 0;
            for (ResumeIndexedLineDTO line : content == null || content.getIndexedLines() == null
                    ? List.<ResumeIndexedLineDTO>of() : content.getIndexedLines()) {
                if (line == null || line.getText() == null || line.getText().isBlank()) {
                    continue;
                }
                int lineIndex = line.getLineId() == null ? fallbackIndex : Math.max(0, line.getLineId() - 1);
                List<String> occurrenceIds = validOccurrenceIds(line.getSourceOccurrenceIds());
                String occurrenceId = firstNonBlank(
                        firstNonBlankValue(occurrenceIds),
                        "indexed-line-" + (lineIndex + 1));
                if (occurrenceIds.isEmpty()) {
                    occurrenceIds = List.of(occurrenceId);
                }
                blocks.add(ResumeBlockDTO.builder()
                        .id(firstNonBlank(line.getSourceBlockId(), firstNonBlankValue(line.getSourceBlockIds())))
                        .index(lineIndex)
                        .originalIndex(line.getOriginalIndex())
                        .text(line.getText())
                        .page(line.getPage())
                        .x(line.getX())
                        .y(line.getY())
                        .width(line.getWidth())
                        .height(line.getHeight())
                        .fontSize(line.getFontSize())
                        .fontName(line.getFontName())
                        .boldHint(line.getBoldHint())
                        .indent(line.getIndent())
                        .bulletHint(line.getBulletHint())
                        .role(line.getRole())
                        .sourceBlockIds(line.getSourceBlockIds())
                        .sourceOccurrenceIds(occurrenceIds)
                        .sourceSection(firstNonBlank(line.getSectionHint(), line.getRawSectionId()))
                        .sourceType(line.getSourceType())
                        .build());
                fallbackIndex++;
            }
            return blocks;
        }

        private List<ResumeBlockDTO> rawSectionBlocks(ResumeStructuredContentDTO content) {
            List<ResumeBlockDTO> blocks = new ArrayList<>();
            int blockIndex = 0;
            for (ResumeRawSectionDTO section : content == null || content.getRawSections() == null
                    ? List.<ResumeRawSectionDTO>of() : content.getRawSections()) {
                if (section == null || section.getBlocks() == null) {
                    continue;
                }
                for (ResumeRawSectionBlockDTO block : section.getBlocks()) {
                    if (block == null || block.getText() == null || block.getText().isBlank()) {
                        continue;
                    }
                    blockIndex++;
                    List<String> occurrenceIds = validOccurrenceIds(block.getSourceOccurrenceIds());
                    String occurrenceId = firstNonBlank(
                            firstNonBlankValue(occurrenceIds),
                            "raw-occurrence-" + blockIndex);
                    if (occurrenceIds.isEmpty()) {
                        occurrenceIds = List.of(occurrenceId);
                    }
                    blocks.add(ResumeBlockDTO.builder()
                            .id(block.getId())
                            .index(block.getOriginalIndex() == null ? blockIndex - 1 : block.getOriginalIndex())
                            .originalIndex(block.getOriginalIndex())
                            .text(block.getText())
                            .page(block.getPage())
                            .x(block.getX())
                            .y(block.getY())
                            .width(block.getWidth())
                            .height(block.getHeight())
                            .fontSize(block.getFontSize())
                            .fontName(block.getFontName())
                            .boldHint(block.getBoldHint())
                            .indent(block.getIndent())
                            .bulletHint(block.getBulletHint())
                            .role(block.getRole())
                            .sourceBlockIds(block.getSourceBlockIds())
                            .sourceOccurrenceIds(occurrenceIds)
                            .sourceSection(firstNonBlank(section.getNormalizedSection(), section.getOriginalTitle()))
                            .sourceType(block.getSourceType())
                            .build());
                }
            }
            return blocks;
        }

        private List<ResumeBlockDTO> textSectionBlocks(ResumeStructuredContentDTO content) {
            List<ResumeBlockDTO> blocks = new ArrayList<>();
            int blockIndex = 0;
            int nextFallbackOriginalIndex = 0;
            for (ResumeTextSectionDTO section : content == null || content.getSections() == null
                    ? List.<ResumeTextSectionDTO>of() : content.getSections()) {
                if (section == null) {
                    continue;
                }
                List<ResumeBlockDTO> sectionBlocks = section.getBlocks() == null
                        ? List.of() : section.getBlocks();
                for (ResumeBlockDTO block : sectionBlocks) {
                    if (block != null && block.getText() != null && !block.getText().isBlank()) {
                        // Historical text-section blocks often carry geometry/IDs but omit the
                        // section because it lived on the enclosing DTO. Restore that context.
                        if (block.getSourceSection() == null || block.getSourceSection().isBlank()) {
                            block.setSourceSection(section.getSectionType());
                        }
                        blocks.add(block);
                    }
                }
                List<String> lines = section.getLines() == null ? List.of() : section.getLines();
                boolean hasBlocks = !sectionBlocks.isEmpty();
                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                    String line = lines.get(lineIndex);
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    // A line and a block are the same occurrence only when their position and
                    // text agree. If blocks are partial or reordered, retain the line as an
                    // independent occurrence rather than silently assuming text equality.
                    boolean representedByBlock = lineIndex < sectionBlocks.size()
                            && sectionBlocks.get(lineIndex) != null
                            && sectionBlocks.get(lineIndex).getText() != null
                            && normalize(sectionBlocks.get(lineIndex).getText()).equals(normalize(line))
                            && blockPositionMatches(sectionBlocks.get(lineIndex), lineIndex,
                            sectionBlocks.size() == lines.size());
                    if (representedByBlock) {
                        continue;
                    }
                    blockIndex++;
                    int originalIndex = hasBlocks ? lineIndex : nextFallbackOriginalIndex + lineIndex;
                    String id = hasBlocks ? "section-line-" + blockIndex : "source-line-" + originalIndex;
                    blocks.add(ResumeBlockDTO.builder()
                            .id(id)
                            .index(blockIndex - 1)
                            .originalIndex(originalIndex)
                            .text(line.strip())
                            .sourceBlockIds(List.of(id))
                            // Keep the compatibility text-section projection on the same
                            // occurrence namespace as the raw section view. The two views are
                            // mirrors of one source row; deriving the occurrence from the block
                            // ID would make every historical line look like a new occurrence.
                            .sourceOccurrenceIds(List.of("source-occurrence-" + originalIndex))
                            .sourceSection(section.getSectionType())
                            .sourceType("historical-section-line")
                            .build());
                }
                int maxOriginalIndex = sectionBlocks.stream()
                        .filter(block -> block != null && block.getText() != null && !block.getText().isBlank())
                        .map(this::sourceOrder)
                        .filter(java.util.Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(nextFallbackOriginalIndex - 1);
                nextFallbackOriginalIndex = Math.max(
                        nextFallbackOriginalIndex + lines.size(), maxOriginalIndex + 1);
            }
            return blocks;
        }

        private boolean blockPositionMatches(ResumeBlockDTO block, int lineIndex, boolean equalSized) {
            Integer position = sourceOrder(block);
            return equalSized || position != null && position == lineIndex;
        }

        private List<ResumeBlockDTO> rawTextBlocks(String rawText) {
            List<ResumeBlockDTO> blocks = new ArrayList<>();
            int lineIndex = 0;
            for (String line : rawText.replace("\r\n", "\n").split("\\R", -1)) {
                lineIndex++;
                if (line == null || line.isBlank()) {
                    continue;
                }
                String id = "raw-line-" + lineIndex;
                blocks.add(ResumeBlockDTO.builder()
                        .id(id)
                        .index(lineIndex - 1)
                        .originalIndex(lineIndex - 1)
                        .text(line.strip())
                        .sourceBlockIds(List.of(id))
                        .sourceOccurrenceIds(List.of(id + "-occurrence"))
                        .sourceSection("GENERAL")
                        .build());
            }
            return blocks;
        }

        private List<ResumeBlockDTO> mergeSourceViews(List<ResumeBlockDTO>... views) {
            List<ResumeBlockDTO> merged = new ArrayList<>();
            if (views != null) {
                for (List<ResumeBlockDTO> view : views) {
                    // The same occurrence can appear once in indexed, raw-section, and legacy
                    // text views, but a repeated ID inside one view is a collision, not a mirror.
                    // Limit mirror suppression to blocks already accepted from an earlier view so
                    // duplicate rows survive long enough for the matcher to namespace them.
                    int priorViewSize = merged.size();
                    Map<String, Integer> mirrorBudget = occurrenceMirrorBudget(
                            merged.subList(0, priorViewSize));
                    for (ResumeBlockDTO block : view == null ? List.<ResumeBlockDTO>of() : view) {
                        if (block == null || block.getText() == null || block.getText().isBlank()) {
                            continue;
                        }
                        ResumeBlockDTO residual = removeMirroredOccurrences(block, mirrorBudget);
                        if (residual != null) {
                            merged.add(residual);
                        }
                    }
                }
            }
            merged.sort(java.util.Comparator.comparing(
                    this::sourceOrder, java.util.Comparator.nullsLast(Integer::compareTo)));
            return merged;
        }

        private ResumeBlockDTO removeMirroredOccurrences(
                ResumeBlockDTO candidate,
                Map<String, Integer> mirrorBudget) {
            List<String> candidateOccurrences = validOccurrenceIds(
                    candidate == null ? null : candidate.getSourceOccurrenceIds());
            if (candidateOccurrences.isEmpty()) {
                // A block ID groups visual lines but is not an occurrence identity. Without an
                // occurrence ID, equal text must remain independent across source views.
                return candidate;
            }
            String candidateText = normalize(candidate.getText());
            List<String> retained = new ArrayList<>();
            for (String occurrenceId : candidateOccurrences) {
                String key = mirrorKey(occurrenceId, candidateText);
                int remaining = mirrorBudget.getOrDefault(key, 0);
                if (remaining > 0) {
                    if (remaining == 1) {
                        mirrorBudget.remove(key);
                    } else {
                        mirrorBudget.put(key, remaining - 1);
                    }
                } else {
                    retained.add(occurrenceId);
                }
            }
            if (retained.isEmpty()) {
                return null;
            }
            if (retained.size() == candidateOccurrences.size()) {
                return candidate;
            }
            // A view may carry a union of mirrored and newly observed occurrences. Drop only
            // the already accepted mirror IDs; dropping the whole block would lose the new rows.
            return copyWithSourceOccurrenceIds(candidate, retained);
        }

        private ResumeBlockDTO copyWithSourceOccurrenceIds(
                ResumeBlockDTO source,
                List<String> sourceOccurrenceIds) {
            return ResumeBlockDTO.builder()
                    .id(source.getId())
                    .index(source.getIndex())
                    .originalIndex(source.getOriginalIndex())
                    .displayOrder(source.getDisplayOrder())
                    .text(source.getText())
                    .page(source.getPage())
                    .x(source.getX())
                    .y(source.getY())
                    .width(source.getWidth())
                    .height(source.getHeight())
                    .fontSize(source.getFontSize())
                    .fontName(source.getFontName())
                    .boldHint(source.getBoldHint())
                    .indent(source.getIndent())
                    .bulletHint(source.getBulletHint())
                    .role(source.getRole())
                    .sourceBlockIds(source.getSourceBlockIds())
                    .sourceOccurrenceIds(sourceOccurrenceIds)
                    .prevText(source.getPrevText())
                    .nextText(source.getNextText())
                    .sourceType(source.getSourceType())
                    .iconType(source.getIconType())
                    .sourceSection(source.getSourceSection())
                    .ruleSection(source.getRuleSection())
                    .ruleConfidence(source.getRuleConfidence())
                    .sourceSectionConfidence(source.getSourceSectionConfidence())
                    .lockedLevel(source.getLockedLevel())
                    .resumeTypeHint(source.getResumeTypeHint())
                    .parseMode(source.getParseMode())
                    .finalSectionSource(source.getFinalSectionSource())
                    .sectionLocked(source.getSectionLocked())
                    .build();
        }

        private Map<String, Integer> occurrenceMirrorBudget(List<ResumeBlockDTO> existing) {
            Map<String, Integer> budget = new LinkedHashMap<>();
            for (ResumeBlockDTO block : existing == null ? List.<ResumeBlockDTO>of() : existing) {
                if (block == null) {
                    continue;
                }
                String text = normalize(block.getText());
                // Keep multiplicity: two equal rows in an accepted view authorize exactly two
                // mirror suppressions in a later view, never an unbounded text-based collapse.
                for (String occurrenceId : validOccurrenceIds(block.getSourceOccurrenceIds())) {
                    budget.merge(mirrorKey(occurrenceId, text), 1, Integer::sum);
                }
            }
            return budget;
        }

        private String mirrorKey(String occurrenceId, String text) {
            return occurrenceId + "\\u0000" + text;
        }

        private Integer sourceOrder(ResumeBlockDTO block) {
            if (block == null) {
                return null;
            }
            return block.getOriginalIndex() == null ? block.getIndex() : block.getOriginalIndex();
        }

        private Integer alignmentOrder(ResumeBlockDTO block) {
            if (block == null) {
                return null;
            }
            return block.getIndex() == null ? sourceOrder(block) : block.getIndex();
        }

        /**
         * Indexed/raw-section views are sometimes partial historical snapshots. When rawText is
         * present, retain every line that is not represented by the richer view as a synthetic
         * source occurrence instead of letting the partial index hide it.
         */
        private List<ResumeBlockDTO> addRawTextGaps(
                ResumeStructuredContentDTO content,
                List<ResumeBlockDTO> existing) {
            String rawText = content == null ? null : content.getRawText();
            if (rawText == null || rawText.isBlank() || existing == null || existing.isEmpty()) {
                return existing == null ? List.of() : existing;
            }
            List<ResumeBlockDTO> result = new ArrayList<>();
            Set<String> usedIds = new LinkedHashSet<>();
            existing.stream()
                    .filter(block -> block != null)
                    .flatMap(block -> validOccurrenceIds(block.getSourceOccurrenceIds()).stream())
                    .forEach(usedIds::add);
            int cursor = 0;
            int lineNumber = 0;
            int sourceLineNumber = 0;
            String[] rawLines = rawText.replace("\r\n", "\n").split("\\R", -1);
            boolean sourceOrderIsPhysical = sourceOrderMatchesPhysicalLines(existing, rawLines);
            Map<String, Integer> rawTextCounts = new LinkedHashMap<>();
            for (String rawLine : rawLines) {
                String line = rawLine == null ? "" : rawLine.strip();
                if (!line.isBlank()) {
                    rawTextCounts.merge(normalize(line), 1, Integer::sum);
                }
            }
            for (String rawLine : rawLines) {
                String line = rawLine == null ? "" : rawLine.strip();
                if (isStructuralRawLine(line)) {
                    // Section headings are intentionally not copied into source section blocks.
                    // Do not let their physical positions offset the generated section-line
                    // view, and do not create review fragments for headings.
                    lineNumber++;
                    continue;
                }
                int sourcePosition = sourceOrderIsPhysical ? lineNumber : sourceLineNumber;
                int matchingIndex = findMatchingBlock(existing, cursor, line, sourcePosition, rawTextCounts);
                if (matchingIndex >= 0) {
                    while (cursor < matchingIndex) {
                        result.add(existing.get(cursor++));
                    }
                    result.add(existing.get(cursor++));
                } else if (!line.isBlank()) {
                    String baseId = "raw-line-" + (lineNumber + 1);
                    String id = uniqueId(baseId, usedIds);
                    String occurrenceId = uniqueId(id + "-occurrence", usedIds);
                    result.add(ResumeBlockDTO.builder()
                            .id(id)
                            .index(result.size())
                            .originalIndex(lineNumber)
                            .displayOrder(result.size())
                            .text(line)
                            .sourceBlockIds(List.of(id))
                            .sourceOccurrenceIds(List.of(occurrenceId))
                            .sourceSection("GENERAL")
                            .sourceType("raw-text-fallback")
                            .role(ResumeSourceBlockRole.UNKNOWN)
                            .build());
                    usedIds.add(occurrenceId);
                }
                if (!line.isBlank()) {
                    sourceLineNumber++;
                }
                lineNumber++;
            }
            while (cursor < existing.size()) {
                result.add(existing.get(cursor++));
            }
            return List.copyOf(result);
        }

        private boolean sourceOrderMatchesPhysicalLines(
                List<ResumeBlockDTO> blocks,
                String[] rawLines) {
            int lastPhysicalLine = -1;
            for (int index = 0; rawLines != null && index < rawLines.length; index++) {
                if (rawLines[index] != null && !rawLines[index].strip().isBlank()) {
                    lastPhysicalLine = index;
                }
            }
            int maxSourceOrder = blocks == null ? -1 : blocks.stream()
                    .filter(block -> block != null)
                    .map(this::alignmentOrder)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(-1);
            // Source-aware extraction keeps section headings in the physical source order even
            // though the section view omits them. Generated compatibility blocks do not, so a
            // compact non-heading cursor is the only safe way to align their rows with rawText.
            return lastPhysicalLine >= 0 && maxSourceOrder >= lastPhysicalLine;
        }

        private boolean isStructuralRawLine(String line) {
            String normalized = normalize(line);
            return !normalized.isBlank() && STRUCTURAL_HEADINGS.stream()
                    .anyMatch(heading -> heading.equalsIgnoreCase(normalized));
        }

        private int findMatchingBlock(
                List<ResumeBlockDTO> blocks,
                int start,
                String line,
                int rawLineNumber,
                Map<String, Integer> rawTextCounts) {
            if (line == null || line.isBlank()) {
                return -1;
            }
            String normalized = normalize(line);
            boolean hasPositionForRawLine = false;
            for (int index = Math.max(0, start); index < blocks.size(); index++) {
                ResumeBlockDTO block = blocks.get(index);
                if (block != null && alignmentOrder(block) != null
                        && alignmentOrder(block) == rawLineNumber) {
                    hasPositionForRawLine = true;
                    if (normalized.equals(normalize(block.getText()))) {
                        return index;
                    }
                }
            }
            if (hasPositionForRawLine) {
                // A reliable position exists but its text differs. Do not bind this raw line to
                // an unrelated equal-text row later in the document.
                return -1;
            }
            // Equal text is not enough to identify one of several repeated raw rows when no
            // position survives. Keep the raw occurrence as a gap rather than pointing it at the
            // first visually similar block.
            if (rawTextCounts != null && rawTextCounts.getOrDefault(normalized, 0) > 1) {
                return -1;
            }
            for (int index = Math.max(0, start); index < blocks.size(); index++) {
                ResumeBlockDTO block = blocks.get(index);
                if (block != null && normalized.equals(normalize(block.getText()))) {
                    return index;
                }
            }
            return -1;
        }

        private String uniqueId(String base, Set<String> used) {
            String candidate = base;
            int suffix = 2;
            while (used.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            return candidate;
        }

        private String normalize(String value) {
            return value == null ? "" : value.replaceAll("\\s+", " ").strip();
        }

        private boolean isUnscopedSection(String section) {
            return section == null || section.isBlank()
                    || "GENERAL".equalsIgnoreCase(section)
                    || "UNKNOWN".equalsIgnoreCase(section);
        }

        private String sourceText(String preferred) {
            String explicit = preferred == null ? null : preferred.strip();
            if (explicit != null && !explicit.isBlank()) {
                return explicit;
            }
            return occurrences.stream()
                    .map(ResumeSourceEvidenceMatcher.Occurrence::text)
                    .filter(text -> text != null && !text.isBlank())
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
        }

        private boolean hasCandidate(String value, String section, ResumeSourceRefDTO preferred) {
            if (value == null || value.isBlank()) {
                return false;
            }
            Set<String> allowed = section == null || section.isBlank() ? Set.of() : Set.of(section);
            if (preferred != null) {
                return ResumeSourceEvidenceMatcher.isValidReference(
                        preferred, occurrences, allowed, allowUnscopedSections)
                        && ResumeSourceEvidenceMatcher.referenceSupportsValue(
                        value, preferred, occurrences, allowed);
            }
            return !ResumeSourceEvidenceMatcher.matchingOccurrences(
                    value, occurrences, allowed, allowUnscopedSections).isEmpty()
                    || "SUMMARY".equalsIgnoreCase(section)
                    && !exactContiguousSpan(value, occurrences, allowed).isEmpty();
        }

        private SourceMatch match(String value, String section, ResumeSourceRefDTO preferred) {
            if (value == null || value.isBlank()) {
                return null;
            }
            Set<String> allowed = section == null || section.isBlank() ? Set.of() : Set.of(section);
            boolean hasPreferredReference = preferred != null;
            boolean validPreferred = hasPreferredReference
                    && ResumeSourceEvidenceMatcher.isValidReference(
                    preferred, occurrences, allowed, allowUnscopedSections);
            if (hasPreferredReference && !validPreferred) {
                // An explicit source reference is a hard provenance boundary. Never fall back to
                // the first equal-text occurrence when that reference is stale or malformed.
                return null;
            }
            if (validPreferred) {
                List<String> preferredIds = preferred.getSourceOccurrenceIds();
                List<ResumeSourceEvidenceMatcher.Occurrence> preferredOccurrences = occurrences.stream()
                        .filter(occurrence -> preferredIds != null && preferredIds.stream().anyMatch(occurrence.sourceOccurrenceIds()::contains))
                        .filter(occurrence -> ResumeSourceEvidenceMatcher.sectionAllowed(
                                occurrence.section(), allowed, allowUnscopedSections))
                        .toList();
                List<ResumeSourceEvidenceMatcher.Occurrence> direct = preferredOccurrences.stream()
                        .filter(occurrence -> ResumeSourceEvidenceMatcher.matchesOccurrence(value, occurrence.text()))
                        .toList();
                SourceMatch directMatch = boundedMatch(value, direct);
                if (directMatch != null) {
                    return directMatch;
                }
                if (distinctOccurrenceCount(direct) > 1) {
                    // A broad preferred boundary containing several matching rows does not
                    // identify which occurrence owns this field.
                    return null;
                }
                List<ResumeSourceEvidenceMatcher.Occurrence> span = exactContiguousSpan(
                        value, preferredOccurrences, allowed);
                if (!span.isEmpty()) {
                    return new SourceMatch(reference(span), span);
                }
                // A valid preferred reference is a hard provenance boundary. Falling through
                // would silently splice a field from another identical-looking source row.
                return null;
            }
            List<ResumeSourceEvidenceMatcher.Occurrence> matches =
                    ResumeSourceEvidenceMatcher.matchingOccurrences(
                            value, occurrences, allowed, allowUnscopedSections);
            SourceMatch unique = unscopedMatch(value, matches);
            if (unique != null) {
                return unique;
            }
            if (hasRepeatedExactText(value, matches)) {
                return null;
            }
            if ("SUMMARY".equalsIgnoreCase(section)) {
                List<ResumeSourceEvidenceMatcher.Occurrence> span = exactContiguousSpan(value, occurrences, allowed);
                if (!span.isEmpty()) {
                    return new SourceMatch(reference(span), span);
                }
            }
            Matcher range = EDUCATION_DATE_RANGE.matcher(value);
            if (range.find()) {
                List<ResumeSourceEvidenceMatcher.Occurrence> rangeMatches = occurrences.stream()
                        .filter(occurrence -> ResumeSourceEvidenceMatcher.sectionAllowed(
                                occurrence.section(), allowed, allowUnscopedSections))
                        .filter(occurrence -> ResumeSourceEvidenceMatcher.matchesOccurrence(
                                range.group(1), occurrence.text()))
                        .filter(occurrence -> ResumeSourceEvidenceMatcher.matchesOccurrence(
                                range.group(2), occurrence.text()))
                        .toList();
                SourceMatch rangeMatch = unscopedMatch(value, rangeMatches);
                if (rangeMatch != null) {
                    return rangeMatch;
                }
            }
            return null;
        }

        /**
         * Resolve one value to an occurrence that has not already been allocated to a sibling
         * claim. This is used only where a list represents separate source rows; it never joins
         * equal text occurrences merely because they have the same value.
         */
        private EducationProof proveHistoricalEducation(
                List<String> claims,
                ResumeSourceRefDTO preferred,
                Set<String> occurrenceIds,
                Set<String> occupiedOccurrences) {
            if (claims == null || claims.isEmpty()) {
                return null;
            }
            Set<String> allowed = Set.of("EDUCATION");
            if (preferred != null) {
                if (!ResumeSourceEvidenceMatcher.isValidReference(
                        preferred, occurrences, allowed, allowUnscopedSections)) {
                    return null;
                }
                boolean supported = claims.stream().allMatch(claim ->
                        ResumeSourceEvidenceMatcher.referenceSupportsValue(
                                claim, preferred, occurrences, allowed));
                if (!supported) {
                    return null;
                }
                return new EducationProof(new LinkedHashSet<>(preferred.getSourceOccurrenceIds()));
            }
            if (occurrenceIds != null && !occurrenceIds.isEmpty()) {
                List<String> ids = occurrenceIds.stream()
                        .filter(ResumeSourceEvidenceMatcher::hasText)
                        .map(String::strip)
                        .distinct()
                        .toList();
                if (ids.size() != occurrenceIds.size()) {
                    return null;
                }
                Map<String, List<ResumeSourceEvidenceMatcher.Occurrence>> byId =
                        ResumeSourceEvidenceMatcher.byId(occurrences);
                List<ResumeSourceEvidenceMatcher.Occurrence> selected = new ArrayList<>();
                for (String id : ids) {
                    List<ResumeSourceEvidenceMatcher.Occurrence> matches = byId.get(id);
                    if (matches == null || matches.size() != 1
                            || !ResumeSourceEvidenceMatcher.sectionAllowed(
                            matches.get(0).section(), allowed, allowUnscopedSections)) {
                        return null;
                    }
                    selected.add(matches.get(0));
                }
                selected.sort(Comparator.comparing(
                        ResumeSourceEvidenceMatcher.Occurrence::order,
                        Comparator.nullsLast(Integer::compareTo)));
                String selectedText = selected.stream()
                        .map(ResumeSourceEvidenceMatcher.Occurrence::text)
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("");
                ResumeSourceRefDTO probe = ResumeSourceRefDTO.builder()
                        .sourceOccurrenceIds(ids)
                        .text(selectedText)
                        .build();
                if (!ResumeSourceEvidenceMatcher.isValidReference(
                        probe, occurrences, allowed, allowUnscopedSections)
                        || claims.stream().anyMatch(claim ->
                        !ResumeSourceEvidenceMatcher.matchesCombined(claim, selectedText))) {
                    return null;
                }
                return new EducationProof(selected.stream()
                        .flatMap(selectedOccurrence -> selectedOccurrence.sourceOccurrenceIds().stream())
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
            }
            // Without an explicit historical boundary, accept only a row that proves every
            // projected field itself. Never join adjacent education rows merely because they are
            // close in source order. Skip rows already represented by a semantic source entry so
            // a generic historical view cannot duplicate that same occurrence.
            EducationProof occupiedMatch = null;
            for (ResumeSourceEvidenceMatcher.Occurrence occurrence : occurrences) {
                Set<String> ids = new LinkedHashSet<>(occurrence.sourceOccurrenceIds());
                if (!ResumeSourceEvidenceMatcher.sectionAllowed(
                        occurrence.section(), allowed, allowUnscopedSections)
                        || !claims.stream().allMatch(claim ->
                        ResumeSourceEvidenceMatcher.matchesOccurrence(claim, occurrence.text()))) {
                    continue;
                }
                if (java.util.Collections.disjoint(ids, occupiedOccurrences)) {
                    return new EducationProof(ids);
                }
                // A text-only generic row can mirror a source-backed semantic row. Keep the
                // occupied match as a mirror signal rather than turning it into a false review
                // candidate; it still cannot allocate a second source occurrence.
                if (occupiedMatch == null) {
                    occupiedMatch = new EducationProof(ids);
                }
            }
            return occupiedMatch;
        }

        private record EducationProof(Set<String> occurrenceIds) {
        }

        private SourceMatch matchWithin(
                String value,
                String section,
                Set<String> allowedOccurrences) {
            return matchWithin(value, section, allowedOccurrences, Set.of());
        }

        private SourceMatch matchWithin(
                String value,
                String section,
                Set<String> allowedOccurrences,
                Set<String> blockedOccurrences) {
            if (value == null || value.isBlank() || allowedOccurrences == null
                    || allowedOccurrences.isEmpty()) {
                return null;
            }
            Set<String> allowed = new LinkedHashSet<>(allowedOccurrences);
            Set<String> blocked = blockedOccurrences == null ? Set.of() : blockedOccurrences;
            List<ResumeSourceEvidenceMatcher.Occurrence> matches =
                    ResumeSourceEvidenceMatcher.matchingOccurrences(value, occurrences,
                            section == null || section.isBlank() ? Set.of() : Set.of(section),
                            allowUnscopedSections).stream()
                            .filter(occurrence -> occurrence.sourceOccurrenceIds().stream()
                                    .anyMatch(allowed::contains))
                            .filter(occurrence -> !blocked.contains(occurrence.primaryId()))
                            .toList();
            return boundedMatch(value, matches);
        }

        /** Match a field inside an entry boundary, including one contiguous wrapped source span. */
        private SourceMatch matchWithinSpan(
                String value,
                String section,
                Set<String> allowedOccurrences) {
            return matchWithinSpan(value, section, allowedOccurrences, Set.of());
        }

        private SourceMatch matchWithinSpan(
                String value,
                String section,
                Set<String> allowedOccurrences,
                Set<String> blockedOccurrences) {
            SourceMatch direct = matchWithin(value, section, allowedOccurrences, blockedOccurrences);
            if (direct != null || value == null || value.isBlank()
                    || allowedOccurrences == null || allowedOccurrences.isEmpty()) {
                return direct;
            }
            Set<String> blocked = blockedOccurrences == null ? Set.of() : blockedOccurrences;
            List<ResumeSourceEvidenceMatcher.Occurrence> candidates = occurrences.stream()
                    .filter(occurrence -> occurrence.sourceOccurrenceIds().stream()
                            .anyMatch(allowedOccurrences::contains))
                    .filter(occurrence -> !blocked.contains(occurrence.primaryId()))
                    .toList();
            List<ResumeSourceEvidenceMatcher.Occurrence> span = exactContiguousSpan(
                    value,
                    candidates,
                    section == null || section.isBlank() ? Set.of() : Set.of(section));
            return span.isEmpty() ? null : new SourceMatch(reference(span), span);
        }

        private SourceMatch matchNext(
                String value,
                String section,
                ResumeSourceRefDTO preferred,
                Set<String> consumed) {
            if (value == null || value.isBlank()) {
                return null;
            }
            Set<String> allowed = section == null || section.isBlank() ? Set.of() : Set.of(section);
            if (preferred != null) {
                if (!ResumeSourceEvidenceMatcher.isValidReference(
                        preferred, occurrences, allowed, allowUnscopedSections)) {
                    return null;
                }
                List<String> preferredIds = preferred.getSourceOccurrenceIds();
                List<ResumeSourceEvidenceMatcher.Occurrence> preferredOccurrences = occurrences.stream()
                        .filter(occurrence -> preferredIds != null
                                && preferredIds.stream().anyMatch(occurrence.sourceOccurrenceIds()::contains))
                        .filter(occurrence -> ResumeSourceEvidenceMatcher.sectionAllowed(
                                occurrence.section(), allowed, allowUnscopedSections))
                        .sorted(java.util.Comparator.comparing(
                                ResumeSourceEvidenceMatcher.Occurrence::order,
                                java.util.Comparator.nullsLast(Integer::compareTo)))
                        .toList();
                List<ResumeSourceEvidenceMatcher.Occurrence> direct = preferredOccurrences.stream()
                        .filter(occurrence -> ResumeSourceEvidenceMatcher.matchesOccurrence(value, occurrence.text()))
                        .filter(occurrence -> consumed == null || !consumed.contains(occurrence.primaryId()))
                        .toList();
                SourceMatch directMatch = boundedMatch(value, direct);
                if (directMatch != null) {
                    return directMatch;
                }
                if (distinctOccurrenceCount(direct) > 1) {
                    return null;
                }
                List<ResumeSourceEvidenceMatcher.Occurrence> freshPreferred = preferredOccurrences.stream()
                        .filter(occurrence -> consumed == null
                                || !consumed.contains(occurrence.primaryId()))
                        .toList();
                List<ResumeSourceEvidenceMatcher.Occurrence> span = exactContiguousSpan(
                        value, freshPreferred, allowed);
                if (!span.isEmpty()) {
                    return new SourceMatch(reference(span), span);
                }
                // An explicit reference either proves this value in its own fresh occurrence or
                // fails closed; do not reinterpret it as a request for the first equal-text row.
                return null;
            }
            List<ResumeSourceEvidenceMatcher.Occurrence> matches =
                    ResumeSourceEvidenceMatcher.matchingOccurrences(
                            value, occurrences, allowed, allowUnscopedSections).stream()
                            .sorted(java.util.Comparator.comparing(
                                    ResumeSourceEvidenceMatcher.Occurrence::order,
                                    java.util.Comparator.nullsLast(Integer::compareTo)))
                            .filter(occurrence -> consumed == null
                                    || !consumed.contains(occurrence.primaryId()))
                            .toList();
            if (matches.isEmpty()) {
                return null;
            }
            ResumeSourceEvidenceMatcher.Occurrence selected = matches.get(0);
            return new SourceMatch(reference(List.of(selected)), List.of(selected));
        }

        private SourceMatch unscopedMatch(
                String value, List<ResumeSourceEvidenceMatcher.Occurrence> candidates) {
            List<ResumeSourceEvidenceMatcher.Occurrence> ordered = distinctOrdered(candidates);
            if (ordered.isEmpty()) {
                return null;
            }
            List<ResumeSourceEvidenceMatcher.Occurrence> exact = exactTextMatches(value, ordered);
            if (exact.size() > 1 && exact.stream()
                    .anyMatch(occurrence -> !occurrence.syntheticId())) {
                return null;
            }
            ResumeSourceEvidenceMatcher.Occurrence selected = exact.size() == 1
                    ? exact.get(0) : ordered.get(0);
            return new SourceMatch(reference(List.of(selected)), List.of(selected));
        }

        private SourceMatch boundedMatch(
                String value, List<ResumeSourceEvidenceMatcher.Occurrence> candidates) {
            List<ResumeSourceEvidenceMatcher.Occurrence> ordered = distinctOrdered(candidates);
            List<ResumeSourceEvidenceMatcher.Occurrence> exact = exactTextMatches(value, ordered);
            if (exact.size() == 1) {
                ResumeSourceEvidenceMatcher.Occurrence selected = exact.get(0);
                return new SourceMatch(reference(List.of(selected)), List.of(selected));
            }
            return exact.size() > 1 ? null : uniqueMatch(ordered);
        }

        private boolean hasRepeatedExactText(
                String value, List<ResumeSourceEvidenceMatcher.Occurrence> candidates) {
            List<ResumeSourceEvidenceMatcher.Occurrence> exact = exactTextMatches(value, distinctOrdered(candidates));
            return exact.size() > 1 && exact.stream().anyMatch(occurrence -> !occurrence.syntheticId());
        }

        private List<ResumeSourceEvidenceMatcher.Occurrence> exactTextMatches(
                String value, List<ResumeSourceEvidenceMatcher.Occurrence> candidates) {
            String expected = normalize(value);
            return candidates.stream()
                    .filter(occurrence -> expected.equalsIgnoreCase(normalize(occurrence.text())))
                    .toList();
        }

        private List<ResumeSourceEvidenceMatcher.Occurrence> distinctOrdered(
                List<ResumeSourceEvidenceMatcher.Occurrence> candidates) {
            return (candidates == null ? List.<ResumeSourceEvidenceMatcher.Occurrence>of() : candidates).stream()
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(
                            ResumeSourceEvidenceMatcher.Occurrence::primaryId,
                            java.util.function.Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new))
                    .values().stream()
                    .sorted(java.util.Comparator.comparing(
                            ResumeSourceEvidenceMatcher.Occurrence::order,
                            java.util.Comparator.nullsLast(Integer::compareTo)))
                    .toList();
        }

        private SourceMatch uniqueMatch(List<ResumeSourceEvidenceMatcher.Occurrence> candidates) {
            List<ResumeSourceEvidenceMatcher.Occurrence> unique = (candidates == null
                    ? List.<ResumeSourceEvidenceMatcher.Occurrence>of() : candidates).stream()
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(
                            ResumeSourceEvidenceMatcher.Occurrence::primaryId,
                            java.util.function.Function.identity(),
                            (left, right) -> left,
                            LinkedHashMap::new))
                    .values().stream().toList();
            if (unique.size() != 1) {
                return null;
            }
            ResumeSourceEvidenceMatcher.Occurrence selected = unique.get(0);
            return new SourceMatch(reference(List.of(selected)), List.of(selected));
        }

        private long distinctOccurrenceCount(List<ResumeSourceEvidenceMatcher.Occurrence> candidates) {
            return (candidates == null ? List.<ResumeSourceEvidenceMatcher.Occurrence>of() : candidates).stream()
                    .filter(java.util.Objects::nonNull)
                    .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                    .distinct()
                    .count();
        }

        private void consume(
                Set<String> consumed,
                SourceMatch match) {
            if (consumed == null || match == null || match.occurrences() == null) {
                return;
            }
            match.occurrences().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                    .filter(java.util.Objects::nonNull)
                    .forEach(consumed::add);
        }

        private boolean consumeReference(
                Set<String> consumed,
                ResumeSourceRefDTO reference,
                String section) {
            if (reference == null || reference.getSourceOccurrenceIds() == null
                    || reference.getSourceOccurrenceIds().isEmpty()) {
                return false;
            }
            Set<String> allowed = section == null || section.isBlank() ? Set.of() : Set.of(section);
            if (!ResumeSourceEvidenceMatcher.isValidReference(
                    reference, occurrences, allowed, allowUnscopedSections)) {
                return false;
            }
            Map<String, List<ResumeSourceEvidenceMatcher.Occurrence>> byId =
                    ResumeSourceEvidenceMatcher.byId(occurrences);
            List<ResumeSourceEvidenceMatcher.Occurrence> selected = new ArrayList<>();
            for (String id : reference.getSourceOccurrenceIds()) {
                List<ResumeSourceEvidenceMatcher.Occurrence> matches = byId.get(id);
                if (matches == null || matches.size() != 1) {
                    return false;
                }
                selected.add(matches.get(0));
            }
            boolean fresh = selected.stream().allMatch(occurrence -> consumed == null
                    || !consumed.contains(occurrence.primaryId()));
            if (!fresh) {
                return false;
            }
            if (consumed != null) {
                selected.stream()
                        .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                        .filter(java.util.Objects::nonNull)
                        .forEach(consumed::add);
            }
            return true;
        }

        private boolean consumeReferences(
                Set<String> consumed,
                List<ResumeSourceRefDTO> references,
                String section) {
            if (references == null || references.isEmpty()) {
                return false;
            }
            Set<String> trial = consumed == null
                    ? new LinkedHashSet<>() : new LinkedHashSet<>(consumed);
            for (ResumeSourceRefDTO reference : references) {
                if (!consumeReference(trial, reference, section)) {
                    return false;
                }
            }
            if (consumed != null) {
                consumed.clear();
                consumed.addAll(trial);
            }
            return true;
        }

        private List<ResumeSourceEvidenceMatcher.Occurrence> exactContiguousSpan(
                String value,
                List<ResumeSourceEvidenceMatcher.Occurrence> candidates,
                Set<String> allowed) {
            String expected = normalize(value);
            if (expected.isEmpty()) {
                return List.of();
            }
            List<ResumeSourceEvidenceMatcher.Occurrence> ordered = (candidates == null
                    ? List.<ResumeSourceEvidenceMatcher.Occurrence>of()
                    : candidates.stream()
                            .filter(occurrence -> ResumeSourceEvidenceMatcher.sectionAllowed(
                                    occurrence.section(), allowed, allowUnscopedSections))
                            .sorted(java.util.Comparator.comparing(
                                    ResumeSourceEvidenceMatcher.Occurrence::order,
                                    java.util.Comparator.nullsLast(Integer::compareTo)))
                            .toList());
            Map<String, List<ResumeSourceEvidenceMatcher.Occurrence>> matchingSpans = new LinkedHashMap<>();
            for (int start = 0; start < ordered.size(); start++) {
                StringBuilder joined = new StringBuilder();
                List<ResumeSourceEvidenceMatcher.Occurrence> span = new ArrayList<>();
                for (int end = start; end < ordered.size(); end++) {
                    ResumeSourceEvidenceMatcher.Occurrence occurrence = ordered.get(end);
                    if (occurrence.text() == null || occurrence.text().isBlank()) {
                        continue;
                    }
                    if (!span.isEmpty()) {
                        Integer previousOrder = span.get(span.size() - 1).order();
                        if (previousOrder == null || occurrence.order() == null
                                || occurrence.order() != previousOrder + 1) {
                            break;
                        }
                    }
                    joined.append(occurrence.text());
                    span.add(occurrence);
                    String normalized = normalize(joined.toString());
                    if (expected.equals(normalized)) {
                        String key = span.stream()
                                .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                                .collect(java.util.stream.Collectors.joining("\u0000"));
                        matchingSpans.putIfAbsent(key, List.copyOf(span));
                        break;
                    }
                    if (!expected.startsWith(normalized)) {
                        break;
                    }
                }
            }
            return matchingSpans.size() == 1
                    ? matchingSpans.values().iterator().next() : List.of();
        }

        private ResumeSourceRefDTO reference(List<ResumeSourceEvidenceMatcher.Occurrence> selected) {
            List<ResumeSourceEvidenceMatcher.Occurrence> ordered = selected == null ? List.of() : selected.stream()
                    .filter(java.util.Objects::nonNull)
                    .sorted(ResumeSourceEvidenceMatcher.physicalOrderComparator())
                    .toList();
            if (ordered.isEmpty()) {
                return null;
            }
            List<String> blockIds = ordered.stream()
                    .flatMap(occurrence -> occurrence.sourceBlockIds().stream())
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
            List<String> occurrenceIds = ordered.stream()
                    .flatMap(occurrence -> occurrence.sourceOccurrenceIds().stream())
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
            // Block identity is optional metadata. Never use an occurrence ID as a block ID
            // fallback, and never synthesize an occurrence from a block/primary ID.
            ResumeSourceEvidenceMatcher.Occurrence first = ordered.get(0);
            Integer start = ordered.stream().map(this::referenceStartOrder)
                    .filter(java.util.Objects::nonNull).min(Integer::compareTo).map(value -> value + 1).orElse(null);
            Integer end = ordered.stream().map(this::referenceEndOrder)
                    .filter(java.util.Objects::nonNull).max(Integer::compareTo).map(value -> value + 1).orElse(null);
            Integer page = ordered.stream().map(ResumeSourceEvidenceMatcher.Occurrence::page)
                    .filter(java.util.Objects::nonNull).distinct().count() == 1 ? first.page() : null;
            boolean hasSinglePageGeometry = page != null && ordered.stream()
                    .allMatch(occurrence -> page.equals(occurrence.page())
                            && occurrence.x() != null && occurrence.y() != null
                            && occurrence.width() != null && occurrence.height() != null);
            Double x = hasSinglePageGeometry ? ordered.stream()
                    .map(ResumeSourceEvidenceMatcher.Occurrence::x).min(Double::compareTo).orElse(null) : null;
            Double y = hasSinglePageGeometry ? ordered.stream()
                    .map(ResumeSourceEvidenceMatcher.Occurrence::y).min(Double::compareTo).orElse(null) : null;
            Double rightEdge = hasSinglePageGeometry ? ordered.stream()
                    .mapToDouble(occurrence -> occurrence.x() + occurrence.width()).max().orElse(Double.NaN) : null;
            Double bottom = hasSinglePageGeometry ? ordered.stream()
                    .mapToDouble(occurrence -> occurrence.y() + occurrence.height()).max().orElse(Double.NaN) : null;
            return ResumeSourceRefDTO.builder()
                    .startLine(start)
                    .endLine(end)
                    .text(ordered.stream().map(ResumeSourceEvidenceMatcher.Occurrence::text)
                            .reduce((left, right) -> left + "\n" + right).orElse(""))
                    .sourceBlockIds(blockIds)
                    .sourceOccurrenceIds(occurrenceIds)
                    .page(page)
                    .x(x)
                    .y(y)
                    .width(x == null || rightEdge == null ? null : rightEdge - x)
                    .height(y == null || bottom == null ? null : bottom - y)
                    .fontSize(first.fontSize())
                    .fontName(first.fontName())
                    .boldHint(first.boldHint())
                    .indent(first.indent())
                    .bulletHint(first.bulletHint())
                    .role(first.role())
                    .sourceType(first.sourceType())
                    .build();
        }

        private Integer referenceStartOrder(ResumeSourceEvidenceMatcher.Occurrence occurrence) {
            return occurrence == null ? null
                    : occurrence.lineOrder() == null ? occurrence.order() : occurrence.lineOrder();
        }

        private Integer referenceEndOrder(ResumeSourceEvidenceMatcher.Occurrence occurrence) {
            return occurrence == null ? null
                    : occurrence.endLineOrder() == null
                    ? (occurrence.endOrder() == null ? referenceStartOrder(occurrence) : occurrence.endOrder())
                    : occurrence.endLineOrder();
        }

        private ResumeSourceRefDTO referenceForValues(
                List<String> values, String section, ResumeSourceRefDTO preferred) {
            return reference(matchesForValues(values, section, preferred));
        }

        private List<ResumeSourceEvidenceMatcher.Occurrence> matchesForValues(
                List<String> values, String section, ResumeSourceRefDTO preferred) {
            List<ResumeSourceEvidenceMatcher.Occurrence> matches = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String value : values == null ? List.<String>of() : values) {
                SourceMatch match = match(value, section, preferred);
                if (match == null) {
                    continue;
                }
                for (ResumeSourceEvidenceMatcher.Occurrence occurrence : match.occurrences()) {
                    if (seen.add(occurrence.primaryId())) {
                        matches.add(occurrence);
                    }
                }
            }
            return matches;
        }

        private List<ResumeSourceRefDTO> referencesForValues(
                List<String> values, String section, ResumeSourceRefDTO preferred) {
            List<ResumeSourceRefDTO> refs = new ArrayList<>();
            Map<String, Set<String>> consumedByValue = new LinkedHashMap<>();
            for (String value : values == null ? List.<String>of() : values) {
                Set<String> consumed = consumedByValue.computeIfAbsent(normalize(value),
                        ignored -> new LinkedHashSet<>());
                SourceMatch selected = matchNext(value, section, preferred, consumed);
                if (selected == null) {
                    // Preserve the value but do not attach the same source occurrence twice for
                    // a repeated claim. Distinct facts printed on one row may share the row.
                    refs.add(null);
                } else {
                    refs.add(selected.reference());
                    selected.occurrences().stream()
                            .map(ResumeSourceEvidenceMatcher.Occurrence::primaryId)
                            .filter(java.util.Objects::nonNull)
                            .forEach(consumed::add);
                }
            }
            return refs;
        }

        private ResumeSourceRefDTO mergeReferences(List<ResumeSourceRefDTO> references) {
            if (references == null || references.isEmpty()) {
                return null;
            }
            Set<String> ids = new LinkedHashSet<>();
            List<ResumeSourceEvidenceMatcher.Occurrence> selected = new ArrayList<>();
            boolean sawReference = false;
            for (ResumeSourceRefDTO sourceReference : references) {
                if (sourceReference == null) {
                    continue;
                }
                sawReference = true;
                // A stale explicit reference must not be reduced to whichever equal-text row
                // happens to remain in the current source view. Treat the whole merged boundary
                // as invalid when any component is invalid.
                if (!ResumeSourceEvidenceMatcher.isValidReference(sourceReference, occurrences, Set.of())) {
                    return null;
                }
                for (String id : sourceReference.getSourceOccurrenceIds()) {
                    if (id == null || !ids.add(id)) {
                        continue;
                    }
                    List<ResumeSourceEvidenceMatcher.Occurrence> matches = occurrences.stream()
                            .filter(occurrence -> occurrence.sourceOccurrenceIds().contains(id))
                            .toList();
                    if (matches.size() != 1) {
                        return null;
                    }
                    selected.add(matches.get(0));
                }
            }
            if (!sawReference || selected.isEmpty()) {
                return null;
            }
            ResumeSourceRefDTO merged = reference(selected);
            return ResumeSourceEvidenceMatcher.isValidReference(merged, occurrences, Set.of())
                    ? merged : null;
        }

        private List<String> occurrenceIds(List<ResumeSourceEvidenceMatcher.Occurrence> selected) {
            return (selected == null ? List.<ResumeSourceEvidenceMatcher.Occurrence>of() : selected).stream()
                    .flatMap(occurrence -> occurrence.sourceOccurrenceIds().stream())
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
        }

        private Map<String, String> occurrenceTexts() {
            Map<String, String> result = new LinkedHashMap<>();
            for (ResumeSourceEvidenceMatcher.Occurrence occurrence : occurrences) {
                if (occurrence == null || occurrence.text() == null || occurrence.text().isBlank()) {
                    continue;
                }
                for (String id : occurrence.sourceOccurrenceIds()) {
                    if (id != null && !id.isBlank()
                            && !"null".equalsIgnoreCase(id.strip())
                            && !"undefined".equalsIgnoreCase(id.strip())) {
                        result.putIfAbsent(id.strip(), occurrence.text());
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }

        private Map<String, ResumeSourceRefDTO> occurrenceRefs() {
            Map<String, ResumeSourceRefDTO> result = new LinkedHashMap<>();
            for (ResumeSourceEvidenceMatcher.Occurrence occurrence : occurrences) {
                if (occurrence == null) {
                    continue;
                }
                ResumeSourceRefDTO ref = reference(List.of(occurrence));
                for (String id : occurrence.sourceOccurrenceIds()) {
                    if (id != null && !id.isBlank()
                            && !"null".equalsIgnoreCase(id.strip())
                            && !"undefined".equalsIgnoreCase(id.strip())) {
                        result.putIfAbsent(id.strip(), ref);
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }

        private Map<String, String> occurrencePrimaryIds() {
            Map<String, String> result = new LinkedHashMap<>();
            for (ResumeSourceEvidenceMatcher.Occurrence occurrence : occurrences) {
                if (occurrence == null || occurrence.primaryId() == null
                        || occurrence.primaryId().isBlank()) {
                    continue;
                }
                for (String id : occurrence.sourceOccurrenceIds()) {
                    if (id != null && !id.isBlank()
                            && !"null".equalsIgnoreCase(id.strip())
                            && !"undefined".equalsIgnoreCase(id.strip())) {
                        result.putIfAbsent(id.strip(), occurrence.primaryId());
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }

        private static String firstNonBlankValue(List<String> values) {
            if (values == null) {
                return null;
            }
            return values.stream().filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
        }

        private static List<String> nonBlankIds(List<String> values) {
            return validOccurrenceIds(values).stream().distinct().toList();
        }

        /** Preserve repeated raw IDs until the occurrence index can namespace collisions. */
        private static List<String> validOccurrenceIds(List<String> values) {
            return values == null ? List.of() : values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::strip)
                    .filter(value -> !"null".equalsIgnoreCase(value)
                            && !"undefined".equalsIgnoreCase(value))
                    .toList();
        }
    }

    private record HistoricalTreeProjection(
            JsonNode root,
            List<ResumeDocumentEntryDTO> educationEntries,
            List<ResumeDocumentEntryDTO> genericEducationEntries,
            boolean educationProjectionPresent) {
    }

    private record HistoricalEducationProjection(
            List<ResumeDocumentEntryDTO> entries,
            List<ResumeDocumentEntryDTO> genericEntries,
            boolean present) {

        private static HistoricalEducationProjection empty() {
            return new HistoricalEducationProjection(List.of(), List.of(), false);
        }
    }

    private record SourceMatch(
            ResumeSourceRefDTO reference,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences) {
    }

    private record SourceBackedValues(
            List<String> values,
            List<ResumeSourceRefDTO> references) {
    }

    private record ProjectSourceAllocation(
            ResumeSourceRefDTO reference,
            Set<String> occurrenceIds,
            boolean reviewRequired) {

        private static ProjectSourceAllocation empty() {
            return new ProjectSourceAllocation(null, Set.of(), false);
        }

        private static ProjectSourceAllocation review(Set<String> occurrenceIds) {
            return new ProjectSourceAllocation(null,
                    occurrenceIds == null ? Set.of() : new LinkedHashSet<>(occurrenceIds), true);
        }

        private static ProjectSourceAllocation accept(
                ResumeSourceRefDTO reference, Set<String> occurrenceIds) {
            return new ProjectSourceAllocation(reference,
                    occurrenceIds == null ? Set.of() : new LinkedHashSet<>(occurrenceIds), false);
        }
    }

    private static final class ExperienceBucket {

        private final String organization;
        private final String role;
        private final String startDate;
        private final String endDate;
        private final List<String> bullets = new ArrayList<>();
        private final List<String> sourceLines = new ArrayList<>();
        private final List<ResumeSourceRefDTO> sourceRefs = new ArrayList<>();

        private ExperienceBucket(ResumeExperienceDTO source, boolean header) {
            this.organization = trimToNull(source.getOrganization());
            this.role = trimToNull(source.getRole());
            this.startDate = trimToNull(source.getStartDate());
            this.endDate = trimToNull(source.getEndDate());
            add(source, header);
        }

        private void addContinuation(ResumeExperienceDTO source) {
            add(source, false);
        }

        private void appendBodyText(String text) {
            String trimmed = trimToNull(text);
            if (trimmed == null) {
                return;
            }
            if (!bullets.isEmpty() && shouldMergeWrappedLine(bullets.get(bullets.size() - 1), trimmed)) {
                bullets.set(bullets.size() - 1, bullets.get(bullets.size() - 1) + trimmed);
            } else {
                bullets.add(trimmed);
            }
        }

        private void add(ResumeExperienceDTO source, boolean header) {
            if (source.getSourceRef() != null) {
                sourceRefs.add(source.getSourceRef());
            }
            appendSourceLine(source.getOrganization());
            appendSourceLine(source.getRole());
            appendSourceLine(source.getStartDate());
            appendSourceLine(source.getEndDate());
            String description = trimToNull(source.getDescription());
            appendSourceLine(description);
            boolean headerEcho = header && isExperienceHeaderEcho(source, description);
            if (!header || !headerEcho) {
                appendBodyText(description);
            }
            for (String bullet : source.getBullets() == null ? List.<String>of() : source.getBullets()) {
                appendSourceLine(bullet);
                if (!sameText(description, bullet)) {
                    appendBodyText(bullet);
                }
            }
        }

        private void appendSourceLine(String text) {
            String trimmed = trimToNull(text);
            if (trimmed != null) {
                sourceLines.add(trimmed);
            }
        }

        private String sourceRef() {
            return String.join("\n", sourceLines);
        }

        private ResumeDocumentEntryDTO reviewCandidate() {
            List<ResumeDocumentBulletDTO> reviewBullets = bullets.stream()
                    .map(text -> ResumeDocumentBulletDTO.builder().text(text).build())
                    .toList();
            return ResumeDocumentEntryDTO.builder()
                    .organization(organization)
                    .role(role)
                    .startDate(startDate)
                    .endDate(endDate)
                    .bullets(new ArrayList<>(reviewBullets))
                    .build();
        }

        private static boolean isExperienceHeaderEcho(ResumeExperienceDTO source, String description) {
            if (description == null || description.isBlank() || source.getOrganization() == null
                    || source.getOrganization().isBlank()) {
                return false;
            }
            String normalizedDescription = normalize(description);
            boolean hasOrganization = normalizedDescription.contains(normalize(source.getOrganization()));
            boolean hasDate = (!isBlank(source.getStartDate()) && normalizedDescription.contains(normalize(source.getStartDate())))
                    || (!isBlank(source.getEndDate()) && normalizedDescription.contains(normalize(source.getEndDate())));
            return hasOrganization && (hasDate || (!isBlank(source.getRole())
                    && normalizedDescription.contains(normalize(source.getRole()))));
        }
    }

    private static final class ProjectBucket {

        private final String name;
        private final String role;
        private final String startDate;
        private final String endDate;
        private String environment;
        private String mentor;
        private final List<String> bullets = new ArrayList<>();
        private final List<String> techStack = new ArrayList<>();
        private final List<String> sourceLines = new ArrayList<>();
        private final List<ResumeSourceRefDTO> sourceRefs = new ArrayList<>();

        private ProjectBucket(ResumeProjectDTO source, boolean header) {
            this.name = safeProjectName(source.getName());
            this.role = trimToNull(source.getRole());
            this.startDate = firstNonBlank(source.getStartDate(), source.getTimeRange());
            this.endDate = trimToNull(source.getEndDate());
            this.environment = trimToNull(source.getEnvironment());
            this.mentor = trimToNull(source.getMentor());
            add(source, header);
        }

        private void addContinuation(ResumeProjectDTO source) {
            if (environment == null) {
                environment = trimToNull(source.getEnvironment());
            }
            if (mentor == null) {
                mentor = trimToNull(source.getMentor());
            }
            add(source, false);
        }

        private void add(ResumeProjectDTO source, boolean header) {
            if (source.getSourceRef() != null) {
                sourceRefs.add(source.getSourceRef());
            }
            appendSourceLine(source.getName());
            appendSourceLine(source.getRole());
            appendSourceLine(source.getStartDate());
            appendSourceLine(source.getEndDate());
            appendSourceLine(source.getTimeRange());
            appendSourceLine(source.getEnvironment());
            appendSourceLine(source.getMentor());
            String description = trimToNull(source.getDescription());
            appendSourceLine(description);
            boolean headerEcho = header && isProjectHeaderEcho(source, description, name);
            if (!headerEcho && !isProjectFieldOnly(description) && !sameText(description, name)) {
                appendBodyText(description);
            }
            for (String responsibility : source.getResponsibilities() == null
                    ? List.<String>of()
                    : source.getResponsibilities()) {
                appendSourceLine(responsibility);
                if (!sameText(responsibility, description) && !sameText(responsibility, name)) {
                    appendBodyText(responsibility);
                }
            }
            for (String item : source.getTechStack() == null ? List.<String>of() : source.getTechStack()) {
                appendSourceLine(item);
                String trimmedItem = trimToNull(item);
                if (trimmedItem != null) {
                    techStack.add(trimmedItem);
                }
            }
        }

        private void appendSourceLine(String text) {
            String trimmed = trimToNull(text);
            if (trimmed != null) {
                sourceLines.add(trimmed);
            }
        }

        private String sourceRef() {
            return String.join("\n", sourceLines);
        }

        private void appendBodyText(String text) {
            String trimmed = trimToNull(text);
            if (trimmed == null) {
                return;
            }
            if (!bullets.isEmpty() && shouldMergeWrappedLine(bullets.get(bullets.size() - 1), trimmed)) {
                bullets.set(bullets.size() - 1, bullets.get(bullets.size() - 1) + trimmed);
            } else {
                bullets.add(trimmed);
            }
        }

        private boolean hasDate(ResumeProjectDTO source) {
            return !isBlank(source.getStartDate())
                    || !isBlank(source.getEndDate())
                    || !isBlank(source.getTimeRange());
        }

        private ResumeDocumentEntryDTO reviewCandidate() {
            List<ResumeDocumentBulletDTO> reviewBullets = bullets.stream()
                    .map(text -> ResumeDocumentBulletDTO.builder().text(text).build())
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            return ResumeDocumentEntryDTO.builder()
                    .organization(name)
                    .role(role)
                    .startDate(startDate)
                    .endDate(endDate)
                    .environment(environment)
                    .mentor(mentor)
                    .techStack(new ArrayList<>(techStack))
                    .bullets(reviewBullets)
                    .build();
        }
    }

    private static boolean shouldMergeWrappedLine(String previous, String current) {
        if (previous == null || current == null || previous.isBlank() || current.isBlank()
                || endsWithSentencePunctuation(previous)) {
            return false;
        }
        String first = current.strip().substring(0, 1);
        return "均动至性了和与的等到".contains(first);
    }

    private static boolean endsWithSentencePunctuation(String value) {
        String trimmed = value == null ? "" : value.strip();
        return !trimmed.isEmpty() && trimmed.matches(".*[。！？!?；;]$");
    }

    private static boolean isProjectHeaderEcho(
            ResumeProjectDTO source, String description, String name) {
        if (description == null || description.isBlank() || name == null || name.isBlank()
                || description.length() > 100 || !normalize(description).contains(normalize(name))) {
            return false;
        }
        boolean hasStart = !isBlank(source.getStartDate())
                && normalize(description).contains(normalize(source.getStartDate()));
        boolean hasEnd = !isBlank(source.getEndDate())
                && normalize(description).contains(normalize(source.getEndDate()));
        return hasStart || hasEnd;
    }

    private static String safeProjectName(String value) {
        String candidate = trimToNull(value);
        if (candidate == null || candidate.length() > 100
                || candidate.matches("^(负责|参与|使用|采用|通过|实现|开发|编写|维护|优化|设计|管理|完成|基于|做|对|是一个|该系统|该项目|主要).*")) {
            return null;
        }
        return candidate;
    }

    private static boolean isProjectFieldOnly(String value) {
        return value != null && value.strip().matches(
                "^(技术栈|技术选型|使用技术|开发框架|开发环境|开发工具|环境|项目周期|开发时间|时间)\\s*[:：].*");
    }

    private static boolean sameText(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private ResumeDocumentDTO emptyDocument() {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder().contacts(new ArrayList<>()).build())
                .sections(new ArrayList<>())
                .build();
    }

    private static void appendUnique(List<String> bullets, String text) {
        String trimmed = trimToNull(text);
        if (trimmed != null && !bullets.contains(trimmed)) {
            bullets.add(trimmed);
        }
    }

    private static void appendLabeled(List<String> bullets, String label, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            appendUnique(bullets, label + "：" + trimmed);
        }
    }

    private static List<String> nonBlankDistinct(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                appendUnique(result, value);
            }
        }
        return result;
    }

    private static List<String> nonBlankIds(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .filter(value -> !"null".equalsIgnoreCase(value)
                        && !"undefined".equalsIgnoreCase(value))
                .distinct()
                .toList();
    }

    /** Keep each candidate position; occurrence IDs, not text equality, define identity. */
    private static List<String> nonBlankPreserve(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                String trimmed = trimToNull(value);
                if (trimmed != null) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                parts.add(trimmed);
            }
        }
        return parts.isEmpty() ? null : String.join(separator, parts);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (trimToNull(value) != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void appendIfPresent(StringBuilder builder, String text) {
        if (text != null && !text.isBlank()) {
            builder.append(text).append('\n');
        }
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : text.replaceAll("[\\s\\p{Punct}、，。·．：:；;（）()\\[\\]【】]", "").toLowerCase(Locale.ROOT);
    }
}
