package com.winter.airesumeoptimizer.module.task.service;

import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** In-process dispatch only; a process crash between commit and dispatch is not recoverable here. */
@Component
public class CommittedTaskDispatcher {

    private final TransactionTemplate failureTransaction;

    @org.springframework.beans.factory.annotation.Autowired
    public CommittedTaskDispatcher(PlatformTransactionManager transactionManager) {
        failureTransaction = new TransactionTemplate(transactionManager);
        failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private CommittedTaskDispatcher() {
        failureTransaction = null;
    }

    /** Compatibility for directly constructed, nontransactional callers (not Spring wiring). */
    public static CommittedTaskDispatcher nonTransactional() {
        return new CommittedTaskDispatcher();
    }

    public void dispatch(TaskExecutor executor, Runnable worker, Consumer<RejectedExecutionException> rejected) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("Task submission requires transaction synchronization");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        execute(executor, worker, rejected, true);
                    }
                }
            });
        } else {
            execute(executor, worker, rejected, false);
        }
    }

    private void execute(TaskExecutor executor, Runnable worker,
            Consumer<RejectedExecutionException> rejected, boolean afterCompletion) {
        try {
            executor.execute(worker);
        } catch (RejectedExecutionException exception) {
            // Even afterCompletion can retain the completed transaction's resources.
            // Suspend them and commit rejection state in a genuinely new transaction.
            if (failureTransaction != null) {
                failureTransaction.executeWithoutResult(status -> rejected.accept(exception));
            } else if (!afterCompletion) {
                rejected.accept(exception);
            } else {
                throw new IllegalStateException("Transactional dispatch requires a transaction manager", exception);
            }
        }
    }
}
