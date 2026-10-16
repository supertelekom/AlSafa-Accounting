package com.alsafa.native5.accounting;

import java.util.Objects;

/**
 * Upload-only synchronization contract. The tablet owns the accounting journal;
 * the cloud must deduplicate by operationId and never replace local journal rows.
 */
public final class UploadOnlySync {
    public interface Queue {
        Item claim(long nowMs, long leaseMs);
        boolean confirm(String operationId, long attempt, long cloudRevision);
        boolean retry(String operationId, long attempt, String error);
    }
    public static final class Item {
        public final String operationId;
        public final String kind;
        public final String payload;
        public final long attempt;
        public Item(String operationId, String kind, String payload, long attempt) {
            if (operationId == null || operationId.trim().isEmpty()) throw new IllegalArgumentException("Missing operation ID");
            if (kind == null || kind.trim().isEmpty()) throw new IllegalArgumentException("Missing kind");
            this.payload = Objects.requireNonNull(payload, "payload");
            if (attempt <= 0) throw new IllegalArgumentException("Invalid attempt");
            this.operationId = operationId;
            this.kind = kind;
            this.attempt = attempt;
        }
    }
    public interface CloudWriter {
        /** Upsert using operationId as immutable deduplication key. Return positive cloud revision. */
        long writeOnce(String operationId, String kind, String payload) throws Exception;
    }
    private final Queue queue;
    private final CloudWriter writer;
    public UploadOnlySync(Queue queue, CloudWriter writer) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.writer = Objects.requireNonNull(writer, "writer");
    }
    /** Process at most one operation. A failure is durable and will be retried. */
    public boolean uploadNext(long nowMs, long leaseMs) {
        Item item = queue.claim(nowMs, leaseMs);
        if (item == null) return false;
        try {
            long revision = writer.writeOnce(item.operationId, item.kind, item.payload);
            if (revision <= 0) throw new IllegalStateException("Invalid cloud revision");
            if (!queue.confirm(item.operationId, item.attempt, revision))
                throw new IllegalStateException("Outbox confirmation rejected for " + item.operationId);
        } catch (Exception error) {
            queue.retry(item.operationId, item.attempt, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        return true;
    }
}
