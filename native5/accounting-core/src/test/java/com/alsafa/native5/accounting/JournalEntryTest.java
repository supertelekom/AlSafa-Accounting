package com.alsafa.native5.accounting;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
import static com.alsafa.native5.accounting.Money.Currency.*;

public class JournalEntryTest {
    @Test public void balancedSingleCurrencySale() {
        JournalEntry entry = new JournalEntry("sale-1", Arrays.asList(
            new JournalEntry.Line("cash-usd", new Money(USD, 1800)),
            new JournalEntry.Line("sales-usd", new Money(USD, -1800))));
        assertEquals("sale-1", entry.eventId());
        assertEquals(2, entry.lines().size());
    }
    @Test(expected = IllegalArgumentException.class) public void unbalancedSaleRejected() {
        new JournalEntry("sale-2", Arrays.asList(
            new JournalEntry.Line("cash", new Money(USD, 1800)),
            new JournalEntry.Line("sales", new Money(USD, -1700))));
    }
    @Test(expected = IllegalArgumentException.class) public void crossCurrencyNettingRejected() {
        new JournalEntry("sale-3", Arrays.asList(
            new JournalEntry.Line("cash-try", new Money(TRY, 10000)),
            new JournalEntry.Line("sales-usd", new Money(USD, -10000))));
    }
    @Test(expected = IllegalArgumentException.class) public void directCrossCurrencyAdditionRejected() {
        new Money(USD, 100).plus(new Money(TRY, 100));
    }
    @Test(expected = ArithmeticException.class) public void overflowRejected() {
        new Money(USD, Long.MAX_VALUE).plus(new Money(USD, 1));
    }
    @Test public void eachCurrencyBalancesIndependently() {
        new JournalEntry("exchange-1", Arrays.asList(
            new JournalEntry.Line("usd-cash", new Money(USD, -1000)),
            new JournalEntry.Line("usd-exchange", new Money(USD, 1000)),
            new JournalEntry.Line("try-cash", new Money(TRY, 42000)),
            new JournalEntry.Line("try-exchange", new Money(TRY, -42000))));
    }
}
