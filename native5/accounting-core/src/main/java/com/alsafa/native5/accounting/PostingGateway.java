package com.alsafa.native5.accounting;

/**
 * Persistence contract: a successful return means journal, stock deltas, and outbox
 * are committed together. Any failure must roll back ALL writes.
 */
public interface PostingGateway {
    void post(PostingBatch batch);
}
