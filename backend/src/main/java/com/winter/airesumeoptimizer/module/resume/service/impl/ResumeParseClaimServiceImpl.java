package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.enums.ResumeQualityStatus;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseClaimService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits parse ownership before CPU, file and provider work starts. Keeping this reservation in
 * its own transaction means another node can supersede a slow worker instead of waiting on its
 * long-running parse transaction.
 */
@Service
public class ResumeParseClaimServiceImpl implements ResumeParseClaimService {

    private final ResumeParseResultMapper resumeParseResultMapper;

    public ResumeParseClaimServiceImpl(ResumeParseResultMapper resumeParseResultMapper) {
        this.resumeParseResultMapper = resumeParseResultMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResumeParseClaim acquire(Long resumeId) {
        LocalDateTime now = LocalDateTime.now();
        int ensured = resumeParseResultMapper.ensureParseRow(resumeId, now);
        if (ensured < 0) {
            throw new BusinessException(500, "简历解析状态初始化失败");
        }

        String token = UUID.randomUUID().toString();
        Long generation = resumeParseResultMapper.claimParseGeneration(resumeId, token, now);
        if (generation == null || generation < 1L) {
            // A missing durable claim is not a compatibility case: continuing would re-enable
            // unconditional parse-result/SOURCE writes and let a stale worker publish data.
            throw new BusinessException(409, "简历解析状态已被更新，请重试");
        }
        // claimParseGeneration already resets quality_status, but keep the invariant explicit for
        // dialects/test doubles that implement only the generation/token portion of the mapper.
        int pendingRows = resumeParseResultMapper.update(null, new LambdaUpdateWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId)
                .eq(ResumeParseResult::getParseGeneration, generation)
                .eq(ResumeParseResult::getParseToken, token)
                .set(ResumeParseResult::getQualityStatus, ResumeQualityStatus.QUALITY_PENDING));
        if (pendingRows != 1) {
            throw new BusinessException(409, "简历解析状态已被更新，请重试");
        }
        return new ResumeParseClaim(generation, token);
    }
}
