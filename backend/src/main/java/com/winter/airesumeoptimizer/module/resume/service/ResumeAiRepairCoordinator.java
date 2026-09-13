package com.winter.airesumeoptimizer.module.resume.service;

/**
 * Durable idempotency seam for the optional reference-only resume repair.
 *
 * <p>The reservation is committed before a provider call. Implementations must never reclaim an
 * existing claim: after a worker disappears, the provider may already have accepted the request,
 * so retrying would violate the repair call's at-most-once contract.</p>
 */
public interface ResumeAiRepairCoordinator {

    RepairReservation reserve(Long userId, Long resumeId, String repairKey);

    RepairReservation find(Long userId, String repairKey);

    void complete(Long userId, String repairKey, String ownerToken, String resultJson, int providerDispatchCount);

    void fail(Long userId, String repairKey, String ownerToken, String failureReason, int providerDispatchCount);

    enum State {
        OWNER,
        IN_PROGRESS,
        SUCCEEDED,
        FAILED_NO_DISPATCH,
        FAILED_AFTER_DISPATCH
    }

    record RepairReservation(
            State state,
            String ownerToken,
            String resultJson,
            String failureReason,
            boolean providerDispatched) {

        public static RepairReservation owner(String ownerToken) {
            return new RepairReservation(State.OWNER, ownerToken, null, null, false);
        }

        public static RepairReservation inProgress(boolean providerDispatched) {
            return new RepairReservation(State.IN_PROGRESS, null, null, null, providerDispatched);
        }

        public static RepairReservation succeeded(String resultJson) {
            return new RepairReservation(State.SUCCEEDED, null, resultJson, null, true);
        }

        public static RepairReservation failed(String failureReason, boolean providerDispatched) {
            return new RepairReservation(
                    providerDispatched ? State.FAILED_AFTER_DISPATCH : State.FAILED_NO_DISPATCH,
                    null,
                    null,
                    failureReason,
                    providerDispatched);
        }
    }
}
