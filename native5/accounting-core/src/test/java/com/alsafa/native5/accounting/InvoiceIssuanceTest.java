package com.alsafa.native5.accounting;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class InvoiceIssuanceTest {
    @Test public void onlineInvoicePostsReceivableRevenueAndCost() {
        PostingBatch batch=InvoiceIssuance.issue("sale-1","invoice-1",
            new Money(Money.Currency.USD,6000),
            new Money(Money.Currency.USD,3500),Collections.emptyList());
        assertEquals("INVOICE_ISSUED",batch.operationKind());
        assertEquals(4,batch.journal().lines().size());
        assertEquals("asset:receivable",batch.journal().lines().get(0).accountId);
        assertEquals("income:sales",batch.journal().lines().get(1).accountId);
        assertEquals("expense:cost_of_goods_sold",batch.journal().lines().get(2).accountId);
        assertEquals("asset:inventory",batch.journal().lines().get(3).accountId);
    }
    @Test public void rejectsNegativeCostAndCurrencyMismatch() {
        try {
            InvoiceIssuance.issue("sale-2","invoice-2",
                new Money(Money.Currency.USD,6000),
                new Money(Money.Currency.TRY,2000),Collections.emptyList());
            fail("Mismatched cost currency accepted");
        } catch(IllegalArgumentException expected) {}
        try {
            InvoiceIssuance.issue("sale-3","invoice-3",
                new Money(Money.Currency.USD,6000),
                new Money(Money.Currency.USD,-1),Collections.emptyList());
            fail("Negative cost accepted");
        } catch(IllegalArgumentException expected) {}
    }
}
