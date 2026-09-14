package com.winter.airesumeoptimizer.module.workspace.vo;

import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceMappingStatus;
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
        boolean exportBlocked) {

    public WorkspaceSourceReferenceVO {
        sourceBlocks = sourceBlocks == null ? List.of() : List.copyOf(sourceBlocks);
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
        fidelityIssues = fidelityIssues == null ? List.of() : List.copyOf(fidelityIssues);
        statusCounts = statusCounts == null ? Map.of() : Map.copyOf(statusCounts);
    }

    public record SourceBlock(
            String id,
            int order,
            String text,
            List<String> occurrenceIds,
            SourceGeometry sourceGeometry,
            List<String> targetNodeIds,
            WorkspaceSourceMappingStatus status,
            boolean reliable) {
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
            boolean textChanged) {
        public TargetMapping {
            sourceOccurrenceIds = sourceOccurrenceIds == null ? List.of() : List.copyOf(sourceOccurrenceIds);
        }
    }

    public record FidelityIssue(
            String code,
            String severity,
            String message,
            List<String> sourceOccurrenceIds,
            List<String> targetNodeIds) {
        public FidelityIssue {
            sourceOccurrenceIds = sourceOccurrenceIds == null ? List.of() : List.copyOf(sourceOccurrenceIds);
            targetNodeIds = targetNodeIds == null ? List.of() : List.copyOf(targetNodeIds);
        }

        public boolean blocker() {
            return "BLOCKER".equals(severity);
        }
    }
}
