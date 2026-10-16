package com.alsafa.native5.accounting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Validates a posted journal before it can enter the persistence layer. */
public final class JournalEntry {
    public static final class Line {
        public final String accountId;
        public final Money amount;
        /** Positive = debit, negative = credit, in the currency's minor units. */
        public Line(String accountId, Money amount) {
            if (accountId == null || accountId.trim().isEmpty()) throw new IllegalArgumentException("Missing account");
            this.accountId = accountId;
            this.amount = Objects.requireNonNull(amount, "amount");
            if (amount.minorUnits() == 0) throw new IllegalArgumentException("Zero-value journal line");
        }
    }
    private final String eventId;
    private final List<Line> lines;
    public JournalEntry(String eventId, List<Line> lines) {
        if (eventId == null || eventId.trim().isEmpty()) throw new IllegalArgumentException("Missing immutable event ID");
        Objects.requireNonNull(lines, "lines");
        if (lines.size() < 2) throw new IllegalArgumentException("Journal needs at least two lines");
        EnumMap<Money.Currency, Long> totals = new EnumMap<>(Money.Currency.class);
        for (Line line : lines) {
            Objects.requireNonNull(line, "line");
            totals.put(line.amount.currency(), Math.addExact(totals.getOrDefault(line.amount.currency(), 0L), line.amount.minorUnits()));
        }
        for (long balance : totals.values()) if (balance != 0) throw new IllegalArgumentException("Unbalanced journal currency");
        this.eventId = eventId;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }
    public String eventId() { return eventId; }
    public List<Line> lines() { return lines; }
}
