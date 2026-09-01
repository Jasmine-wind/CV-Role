package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeReviewResolveRequestDTO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeReviewVO;

/**
 * 简历解析确认服务（Slice A）。
 * 用户确认的对象是 canonical SOURCE 版本中的字段与候选项，不是 parser JSON；
 * canonical 字节只存在于 resume_versions，解析表仅保存当前 SOURCE 指针与审查 sidecar。
 * 全部候选项处理完毕且确定性校验通过后质量状态回到 READY。
 */
public interface ResumeReviewService {

    ResumeReviewVO getReview(Long userId, Long resumeId);

    ResumeReviewVO resolve(Long userId, Long resumeId, ResumeReviewResolveRequestDTO request);
}
