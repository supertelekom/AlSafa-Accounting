package com.alsafa.native5.accounting;

import org.junit.Test;
import static org.junit.Assert.*;

public class VaultVarianceAdjustmentTest {
    @Test public void shortageAndSurplusUseSuspenseNotSalesProfit() {
        for(long difference:new long[]{-250,300}) {
            PostingBatch batch=VaultVarianceAdjustment.post("adjust-1","close-1",
                InvoiceSettlement.Channel.CASH_DRAWER,
                new Money(Money.Currency.USD,difference),"Physical count verified");
            assertEquals("VAULT_VARIANCE_ADJUSTMENT",batch.operationKind());
            assertEquals(difference,batch.journal().lines().get(0).amount.minorUnits());
            assertEquals(-difference,batch.journal().lines().get(1).amount.minorUnits());
            assertEquals("liability:cash_count_suspense",batch.journal().lines().get(1).accountId);
        }
    }
    @Test public void invalidShamCashCurrencyAndMissingReasonAreRejected() {
        try {
            VaultVarianceAdjustment.post("adjust-1","close-1",
                InvoiceSettlement.Channel.SHAM_CASH,
                new Money(Money.Currency.TRY,100),"Counted");
            fail("TRY accepted");
        } catch(IllegalArgumentException expected) {}
        try {
            VaultVarianceAdjustment.post("adjust-1","close-1",
                InvoiceSettlement.Channel.CASH_DRAWER,
                new Money(Money.Currency.USD,100),"");
            fail("Missing reason accepted");
        } catch(IllegalArgumentException expected) {}
    }
}
