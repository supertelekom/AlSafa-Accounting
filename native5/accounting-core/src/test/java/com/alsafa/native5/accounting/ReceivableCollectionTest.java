package com.alsafa.native5.accounting;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReceivableCollectionTest {
    @Test public void cashCollectionCreditsReceivableNotRevenue() {
        Money paid=new Money(Money.Currency.USD,2500);
        PostingBatch batch=ReceivableCollection.collect("collect-1","invoice-1",paid,
            Collections.singletonList(new InvoiceSettlement.Receipt(
                InvoiceSettlement.Channel.CASH_DRAWER,paid,paid)));
        assertEquals("INVOICE_COLLECTION",batch.operationKind());
        assertEquals("asset:payment:CASH_DRAWER",batch.journal().lines().get(0).accountId);
        assertEquals("asset:receivable",batch.journal().lines().get(1).accountId);
        assertEquals(-2500,batch.journal().lines().get(1).amount.minorUnits());
    }
    @Test public void foreignCurrencyNeedsExplicitFxPosting() {
        Money usd=new Money(Money.Currency.USD,500);
        Money tryAmount=new Money(Money.Currency.TRY,20000);
        try {
            ReceivableCollection.collect("collect-2","invoice-2",usd,
                Collections.singletonList(new InvoiceSettlement.Receipt(
                    InvoiceSettlement.Channel.CASH_DRAWER,tryAmount,usd)));
            fail("Foreign currency collection must not silently post");
        } catch(IllegalArgumentException expected) {}
    }
}
