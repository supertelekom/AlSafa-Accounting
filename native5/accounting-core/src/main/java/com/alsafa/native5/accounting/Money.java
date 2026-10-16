package com.alsafa.native5.accounting;

import java.util.Objects;

/** Exact currency-denominated minor units. Never use floating-point for accounting. */
public final class Money {
    public enum Currency { USD, TRY, SYP }
    private final Currency currency;
    private final long minorUnits;

    public Money(Currency currency, long minorUnits) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.minorUnits = minorUnits;
    }
    public Currency currency() { return currency; }
    public long minorUnits() { return minorUnits; }
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(currency, Math.addExact(minorUnits, other.minorUnits));
    }
    public Money negate() { return new Money(currency, Math.negateExact(minorUnits)); }
    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (currency != other.currency) throw new IllegalArgumentException("Cross-currency sum requires a recorded exchange");
    }
    @Override public boolean equals(Object obj) {
        if (!(obj instanceof Money)) return false;
        Money other = (Money) obj;
        return currency == other.currency && minorUnits == other.minorUnits;
    }
    @Override public int hashCode() { return Objects.hash(currency, minorUnits); }
    @Override public String toString() { return currency + " " + minorUnits; }
}
