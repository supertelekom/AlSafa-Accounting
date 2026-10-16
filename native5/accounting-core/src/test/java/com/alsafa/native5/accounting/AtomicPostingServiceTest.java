package com.alsafa.native5.accounting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.alsafa.native5.accounting.Money.Currency.USD;

public class AtomicPostingServiceTest {
    private static final class FakePort implements AtomicPostingService.TransactionPort {
        final List<String> calls = new ArrayList<>();
        boolean duplicate;
        boolean failInventory;
        @Override public void begin() { calls.add("begin"); }
        @Override public boolean eventExists(String id) { calls.add("exists"); return duplicate; }
        @Override public void insertEvent(String id, String kind) { calls.add("event"); }
        @Override public void insertJournalLine(String id, int no, JournalEntry.Line line) { calls.add("journal"); }
        @Override public void applyInventory(String id, int no, InventoryDelta delta) {
            calls.add("inventory");
            if (failInventory) throw new IllegalStateException("Simulated inventory failure");
        }
        @Override public void insertOutbox(String id, String kind, String payload) { calls.add("outbox"); }
        @Override public void commit() { calls.add("commit"); }
        @Override public void rollback() { calls.add("rollback"); }
    }
    private PostingBatch batch() {
        JournalEntry journal = new JournalEntry("sale-1", Arrays.asList(
            new JournalEntry.Line("cash", new Money(USD, 1800)),
            new JournalEntry.Line("revenue", new Money(USD, -1800))));
        return new PostingBatch(journal, "SALE", "{\"eventId\":\"sale-1\"}",
            Collections.singletonList(new InventoryDelta("watch-1", -1)));
    }
    @Test public void allWritesCommitInOneTransaction() {
        FakePort port = new FakePort();
        new AtomicPostingService(port).post(batch());
        assertEquals(Arrays.asList("begin","exists","event","journal","journal","inventory","outbox","commit"), port.calls);
    }
    @Test public void failedInventoryRollsBackBeforeOutbox() {
        FakePort port = new FakePort(); port.failInventory = true;
        try { new AtomicPostingService(port).post(batch()); fail("Expected failure"); }
        catch (IllegalStateException expected) { }
        assertEquals(Arrays.asList("begin","exists","event","journal","journal","inventory","rollback"), port.calls);
    }
    @Test public void duplicateEventDoesNotPostAgain() {
        FakePort port = new FakePort(); port.duplicate = true;
        try { new AtomicPostingService(port).post(batch()); fail("Expected duplicate rejection"); }
        catch (IllegalStateException expected) { }
        assertEquals(Arrays.asList("begin","exists","rollback"), port.calls);
    }
}
