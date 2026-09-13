package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.entity.ResumeAiRepairAttempt;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeAiRepairAttemptMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiRepairCoordinator;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed reservation and result store for reference-only AI repair. */
@Service
public class ResumeAiRepairCoordinatorImpl implements ResumeAiRepairCoordinator {

    private final ResumeAiRepairAttemptMapper attemptMapper;

    public ResumeAiRepairCoordinatorImpl(ResumeAiRepairAttemptMapper attemptMapper) {
        this.attemptMapper = attemptMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RepairReservation reserve(Long userId, Long resumeId, String repairKey) {
        String ownerToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        int inserted = attemptMapper.insertIfAbsent(userId, resumeId, repairKey, ownerToken, now);
        if (inserted == 1) {
            return RepairReservation.owner(ownerToken);
        }
        if (attemptMapper.claimRetryIfNoDispatch(userId, repairKey, ownerToken, now) == 1) {
            return RepairReservation.owner(ownerToken);
        }
        ResumeAiRepairAttempt attempt = attemptMapper.selectByUserAndKey(userId, repairKey);
        if (attempt == null) {
            throw new IllegalStateException("AI repair reservation disappeared");
        }
        if ("CLAIMED".equals(attempt.getStatus())
                && ownerToken.equals(attempt.getOwnerToken())) {
            return RepairReservation.owner(ownerToken);
        }
        return reservationOf(attempt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public RepairReservation find(Long userId, String repairKey) {
        ResumeAiRepairAttempt attempt = attemptMapper.selectByUserAndKey(userId, repairKey);
        return attempt == null ? null : reservationOf(attempt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            Long userId,
            String repairKey,
            String ownerToken,
            String resultJson,
            int providerDispatchCount) {
        if (resultJson == null || resultJson.isBlank()) {
            throw new IllegalArgumentException("AI repair result cannot be blank");
        }
        int updated = attemptMapper.completeIfOwned(
                userId,
                repairKey,
                ownerToken,
                resultJson,
                Math.max(1, providerDispatchCount),
                LocalDateTime.now());
        requireOwnedUpdate(updated, userId, repairKey, "complete");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            Long userId,
            String repairKey,
            String ownerToken,
            String failureReason,
            int providerDispatchCount) {
        String safeReason = failureReason == null || failureReason.isBlank()
                ? "AI 结构化补全失败"
                : failureReason.strip();
        if (safeReason.length() > 500) {
            safeReason = safeReason.substring(0, 500);
        }
        int dispatchCount = Math.max(0, providerDispatchCount);
        int updated = attemptMapper.failIfOwned(
                userId,
                repairKey,
                ownerToken,
                dispatchCount > 0 ? "FAILED_AFTER_DISPATCH" : "FAILED_NO_DISPATCH",
                safeReason,
                dispatchCount,
                LocalDateTime.now());
        requireOwnedUpdate(updated, userId, repairKey, "fail");
    }

    private void requireOwnedUpdate(int updated, Long userId, String repairKey, String operation) {
        if (updated == 1) {
            return;
        }
        ResumeAiRepairAttempt current = attemptMapper.selectByUserAndKey(userId, repairKey);
        if (current == null) {
            throw new IllegalStateException("AI repair reservation disappeared during " + operation);
        }
        throw new IllegalStateException("AI repair reservation no longer owned during " + operation
                + ": status=" + current.getStatus());
    }

    private RepairReservation reservationOf(ResumeAiRepairAttempt attempt) {
        return switch (attempt.getStatus()) {
            case "CLAIMED" -> RepairReservation.inProgress(
                    attempt.getProviderDispatchCount() != null && attempt.getProviderDispatchCount() > 0);
            case "SUCCEEDED" -> RepairReservation.succeeded(attempt.getResultJson());
            case "FAILED_NO_DISPATCH", "FAILED_AFTER_DISPATCH" -> RepairReservation.failed(
                    attempt.getFailureReason(),
                    attempt.getProviderDispatchCount() != null && attempt.getProviderDispatchCount() > 0);
            default -> throw new IllegalStateException("Unknown AI repair reservation status");
        };
    }
}
