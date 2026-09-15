package com.winter.airesumeoptimizer.module.workspace.vo;

import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceMappingStatus;
import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceRestoreScope;
import java.util.List;
import java.util.Map;

/** Frozen SOURCE blocks, authenticated TARGET provenance, and the structure fidelity verdict. */
public record WorkspaceSourceReferenceVO(
        Long optimizationTaskId,
        Long sourceResumeVersionId,
        Long targetResumeVersionId,
        long targetRevision,
        String sourceFilename,
        boolean sourcePdfAvailable,
        List<SourceBlock> sourceBlocks,
        List<TargetMapping> mappings,
        List<FidelityIssue> fidelityIssues,
        Map<WorkspaceSourceMappingStatus, Integer> statusCounts,
        int confirmedOmissionCount,
        /**
         * 兼容字段：fidelity issues 是 advisory，不再自动置位；当前恒为 false。
         * 真正阻止操作的只有技术上无法完成（render/storage/CAS/权限/内部错误）。
         */
        boolean exportBlocked) {

    public WorkspaceSourceReferenceVO {
        sourceBlocks = sourceBlocks == null ? List.of() : List.copyOf(sourceBlocks);
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
        fidelityIssues = fidelityIssues == null ? List.of() : List.copyOf(fidelityIssues);
        statusCounts = statusCounts == null ? Map.of() : Map.copyOf(statusCounts);
    }

    /** Compatibility constructor for callers that do not model intentional omissions. */
    public WorkspaceSourceReferenceVO(
            Long optimizationTaskId,
            Long sourceResumeVersionId,
            Long targetResumeVersionId,
            long targetRevision,
            String sourceFilename,
            boolean sourcePdfAvailable,
            List<SourceBlock> sourceBlocks,
            List<TargetMapping> mappings,
            List<FidelityIssue> fidelityIssues,
            Map<WorkspaceSourceMappingStatus, Integer> statusCounts,
            boolean exportBlocked) {
        this(optimizationTaskId, sourceResumeVersionId, targetResumeVersionId, targetRevision,
                sourceFilename, sourcePdfAvailable, sourceBlocks, mappings, fidelityIssues,
                statusCounts, 0, exportBlocked);
    }

    public record SourceBlock(
            String id,
            int order,
            String text,
            List<String> occurrenceIds,
            SourceGeometry sourceGeometry,
            List<String> targetNodeIds,
            WorkspaceSourceMappingStatus status,
            boolean reliable,
            String sourceNodeType,
            String sourceSectionKind,
            String sourceSectionId,
            String sourceEntryId,
            String sourceBulletId,
            boolean omissionConfirmed,
            boolean omissionEligible,
            /** Server-resolved restore unit kind; NONE when no authorized restore exists. */
            WorkspaceSourceRestoreScope restoreScope,
            /** True only while the whole boundary passes the server restore safety verdict. */
            boolean restoreEligible,
            /** Server verdict explaining why the resolved boundary is not restorable; null when eligible or NONE. */
            String restoreBlockedReason) {
        public SourceBlock {
            occurrenceIds = occurrenceIds == null ? List.of() : List.copyOf(occurrenceIds);
            targetNodeIds = targetNodeIds == null ? List.of() : List.copyOf(targetNodeIds);
        }
    }

    public record SourceGeometry(
            Integer page,
            Double x,
            Double y,
            Double width,
            Double height,
            Double fontSize,
            String fontName,
            Boolean boldHint,
            Integer indent,
            Boolean bulletHint) {
    }

    public record TargetMapping(
            String targetNodeId,
            String nodeType,
            String sectionId,
            String entryId,
            String bulletId,
            String targetText,
            List<String> sourceOccurrenceIds,
            WorkspaceSourceMappingStatus status,
            boolean reliable,
            boolean textChanged,
            boolean omissionConfirmed,
            boolean omissionEligible) {
        public TargetMapping {
            sourceOccurrenceIds = sourceOccurrenceIds == null ? List.of() : List.copyOf(sourceOccurrenceIds);
        }
    }

    public record FidelityIssue(
            String code,
            /** 仅表示问题严重程度（供聚合与排序使用），不再表示禁止导出。 */
            String severity,
            String message,
            List<String> sourceOccurrenceIds,
            List<String> targetNodeIds) {
        public FidelityIssue {
            sourceOccurrenceIds = sourceOccurrenceIds == null ? List.of() : List.copyOf(sourceOccurrenceIds);
            targetNodeIds = targetNodeIds == null ? List.of() : List.copyOf(targetNodeIds);
        }
    }
}
