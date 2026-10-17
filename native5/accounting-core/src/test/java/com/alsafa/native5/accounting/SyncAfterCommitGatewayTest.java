package com.alsafa.native5.accounting;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class SyncAfterCommitGatewayTest {
    private PostingBatch batch() {
        JournalEntry journal=new JournalEntry("event-1",Arrays.asList(
            new JournalEntry.Line("cash",new Money(Money.Currency.USD,100)),
            new JournalEntry.Line("sales",new Money(Money.Currency.USD,-100))));
        return new PostingBatch(journal,"SALE","{}",Collections.emptyList());
    }
    @Test public void schedulesOnlyAfterSuccessfulLocalCommit() {
        final int[] commits={0},scheduled={0};
        PostingGateway gateway=new SyncAfterCommitGateway(b->commits[0]++,()->scheduled[0]++);
        gateway.post(batch());
        assertEquals(1,commits[0]);
        assertEquals(1,scheduled[0]);
    }
    @Test public void failedPostingDoesNotSchedule() {
        final int[] scheduled={0};
        PostingGateway gateway=new SyncAfterCommitGateway(b->{throw new IllegalStateException("rollback");},
            ()->scheduled[0]++);
        try {gateway.post(batch());fail("Expected rollback");}
        catch(IllegalStateException expected) {}
        assertEquals(0,scheduled[0]);
    }
    @Test public void schedulerFailureDoesNotMisreportCommittedSale() {
        final int[] commits={0};
        PostingGateway gateway=new SyncAfterCommitGateway(b->commits[0]++,
            ()->{throw new IllegalStateException("WorkManager unavailable");});
        gateway.post(batch());
        assertEquals(1,commits[0]);
    }
}
