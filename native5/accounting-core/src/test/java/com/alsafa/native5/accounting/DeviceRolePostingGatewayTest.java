package com.alsafa.native5.accounting;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceRolePostingGatewayTest {
    private PostingBatch batch() {
        JournalEntry j=new JournalEntry("sale-role-test",Arrays.asList(
            new JournalEntry.Line("cash",new Money(Money.Currency.USD,100)),
            new JournalEntry.Line("sales",new Money(Money.Currency.USD,-100))));
        return new PostingBatch(j,"SALE","{}",Collections.emptyList());
    }
    @Test public void phoneCannotPost() {
        final int[] calls={0};
        PostingGateway delegate=b->calls[0]++;
        DeviceRolePostingGateway gateway=new DeviceRolePostingGateway(
            DeviceRolePostingGateway.Role.PHONE_READ_ONLY,delegate);
        try { gateway.post(batch()); fail("Phone was allowed to post"); }
        catch(SecurityException expected) { }
        assertEquals(0,calls[0]);
    }
    @Test public void tabletCanPost() {
        final int[] calls={0};
        PostingGateway delegate=b->calls[0]++;
        new DeviceRolePostingGateway(DeviceRolePostingGateway.Role.TABLET_AUTHORITY,delegate).post(batch());
        assertEquals(1,calls[0]);
    }
}
