package com.alsafa.native5.accounting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A validated accounting event to persist atomically with its outbox envelope. */
public final class PostingBatch {
    private final JournalEntry journal;
    private final String operationKind;
    private final String payload;
    private final List<InventoryDelta> inventory;

    public PostingBatch(JournalEntry journal, String operationKind, String payload, List<InventoryDelta> inventory) {
        this.journal = Objects.requireNonNull(journal, "journal");
        if (operationKind == null || operationKind.trim().isEmpty()) throw new IllegalArgumentException("Missing operation kind");
        if (payload == null || payload.trim().isEmpty()) throw new IllegalArgumentException("Missing operation payload");
        this.operationKind = operationKind;
        this.payload = payload;
        Objects.requireNonNull(inventory, "inventory");
        this.inventory = Collections.unmodifiableList(new ArrayList<>(inventory));
        for (InventoryDelta delta : this.inventory) Objects.requireNonNull(delta, "inventory delta");
    }
    public JournalEntry journal() { return journal; }
    public String operationKind() { return operationKind; }
    public String payload() { return payload; }
    public List<InventoryDelta> inventory() { return inventory; }
}
