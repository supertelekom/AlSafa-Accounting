package com.alsafa.native5.accounting;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.alsafa.native5.accounting.Money.Currency.USD;

public class PostingBatchTest {
    private JournalEntry journal() {
        return new JournalEntry("sale-001", Arrays.asList(
            new JournalEntry.Line("cash-usd", new Money(USD, 1800)),
            new JournalEntry.Line("sales-usd", new Money(USD, -1800))));
    }
    @Test public void immutableValidatedBatch() {
        PostingBatch batch = new PostingBatch(journal(), "SALE", "{\"id\":\"sale-001\"}",
            Collections.singletonList(new InventoryDelta("watch-01", -1)));
        assertEquals("sale-001", batch.journal().eventId());
        assertEquals(-1, batch.inventory().get(0).quantityChange());
        try { batch.inventory().clear(); fail("Inventory list should be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }
    @Test(expected = IllegalArgumentException.class) public void missingPayloadRejected() {
        new PostingBatch(journal(), "SALE", " ", Collections.emptyList());
    }
    @Test(expected = IllegalArgumentException.class) public void zeroStockDeltaRejected() {
        new InventoryDelta("watch-01", 0);
    }
}
