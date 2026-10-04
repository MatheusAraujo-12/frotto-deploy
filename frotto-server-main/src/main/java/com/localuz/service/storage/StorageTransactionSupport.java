package com.localuz.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ties physical file cleanup to the outcome of the surrounding database transaction.
 *
 * <ul>
 *   <li>{@link #runAfterCommit}: only once the new reference is durable (e.g. delete the replaced file). Without an
 *       active transaction the database write has already happened, so it runs immediately.</li>
 *   <li>{@link #runAfterRollback}: only on a confirmed rollback (e.g. delete the file uploaded for a change that was
 *       not saved). An unknown outcome (commit failure) keeps the files: an orphan is preferable to a database row
 *       pointing to a missing file.</li>
 * </ul>
 *
 * Cleanup failures are logged and never propagated: the request outcome is already decided.
 */
@Component
public class StorageTransactionSupport {

    private final Logger log = LoggerFactory.getLogger(StorageTransactionSupport.class);

    public void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runQuietly(action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runQuietly(action);
                }
            }
        );
    }

    public void runAfterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        runQuietly(action);
                    }
                }
            }
        );
    }

    private void runQuietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Storage cleanup failed: {}", e.getClass().getSimpleName());
        }
    }
}
