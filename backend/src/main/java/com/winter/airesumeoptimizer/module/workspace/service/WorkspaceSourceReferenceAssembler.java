package com.winter.airesumeoptimizer.module.workspace.service;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO;

/** Pure, deterministic provenance projection. It never infers links from similar display text. */
public interface WorkspaceSourceReferenceAssembler {

    WorkspaceSourceReferenceVO assemble(
            Long optimizationTaskId,
            Long sourceResumeVersionId,
            Long targetResumeVersionId,
            long targetRevision,
            String sourceFilename,
            boolean sourcePdfAvailable,
            ResumeDocumentDTO source,
            ResumeDocumentDTO target);
}
