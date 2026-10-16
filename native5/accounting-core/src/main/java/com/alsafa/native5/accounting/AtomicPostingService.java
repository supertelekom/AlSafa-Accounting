package com.alsafa.native5.accounting;

import java.util.Objects;

/**
 * Database-independent transaction orchestration. The platform adapter must implement
 * TransactionPort using one SQLite connection and BEGIN/COMMIT/ROLLBACK semantics.
 */
public final class AtomicPostingService implements PostingGateway {
    public interface TransactionPort {
        void begin();
        /** True if the event already exists. Must not modify any data. */
        boolean eventExists(String eventId);
        void insertEvent(String eventId, String kind);
        void insertJournalLine(String eventId, int lineNo, JournalEntry.Line line);
        void applyInventory(String eventId, int lineNo, InventoryDelta delta);
        void insertOutbox(String eventId, String kind, String payload);
        void commit();
        void rollback();
    }
    private final TransactionPort port;
    public AtomicPostingService(TransactionPort port) {
        this.port = Objects.requireNonNull(port, "port");
    }
    @Override public synchronized void post(PostingBatch batch) {
        Objects.requireNonNull(batch, "batch");
        boolean begun = false;
        boolean committed = false;
        try {
            port.begin();
            begun = true;
            String eventId = batch.journal().eventId();
            if (port.eventExists(eventId)) throw new IllegalStateException("Duplicate event ID: " + eventId);
            port.insertEvent(eventId, batch.operationKind());
            int lineNo = 0;
            for (JournalEntry.Line line : batch.journal().lines())
                port.insertJournalLine(eventId, lineNo++, line);
            lineNo = 0;
            for (InventoryDelta delta : batch.inventory())
                port.applyInventory(eventId, lineNo++, delta);
            port.insertOutbox(eventId, batch.operationKind(), batch.payload());
            port.commit();
            committed = true;
        } catch (RuntimeException | Error original) {
            if (begun && !committed) {
                try { port.rollback(); }
                catch (RuntimeException | Error rollbackFailure) {
                    original.addSuppressed(rollbackFailure);
                }
            }
            throw original;
        }
    }
}
