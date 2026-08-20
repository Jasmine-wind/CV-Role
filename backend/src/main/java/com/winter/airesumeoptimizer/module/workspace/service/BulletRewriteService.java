package com.winter.airesumeoptimizer.module.workspace.service;

import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceBulletSuggestRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceBulletSuggestionVO;

/**
 * 单 Bullet 岗位定向改写建议服务。
 *
 * <p>本服务完全只读：不修改 TARGET / SOURCE / snapshot / EvidenceAnalysis，不持久化建议，
 * 也不提供 Apply 写入链路。Apply 只发生在前端 draft，并复用 Phase 4 Auto Save / CAS。
 */
public interface BulletRewriteService {

    WorkspaceBulletSuggestionVO suggestBulletRewrite(
            Long userId,
            Long optimizationTaskId,
            WorkspaceBulletSuggestRequestDTO request);
}
