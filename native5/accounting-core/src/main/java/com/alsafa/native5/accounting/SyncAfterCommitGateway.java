package com.alsafa.native5.accounting;

import java.util.Objects;

/**
 * One write gateway for all financial workflows (simple and advanced modes).
 * Successful local commits schedule upload; failed/duplicate postings do not.
 * UI modules must use this gateway, never write accounting tables directly.
 */
public final class SyncAfterCommitGateway implements PostingGateway {
    public interface Scheduler { void schedule(); }
    private final PostingGateway local;
    private final Scheduler scheduler;
    public SyncAfterCommitGateway(PostingGateway local,Scheduler scheduler) {
        this.local=Objects.requireNonNull(local,"local");
        this.scheduler=Objects.requireNonNull(scheduler,"scheduler");
    }
    @Override public void post(PostingBatch batch) {
        local.post(batch);
        // The durable outbox is committed before scheduling. A scheduler failure
        // must not make callers repeat an already committed financial transaction.
        try { scheduler.schedule(); }
        catch(RuntimeException schedulingFailure) {
            // Periodic WorkManager fallback will pick up the durable outbox.
        }
    }
}
