package com.alsafa.native5.accounting;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineOrderAccountingTest {
    private static Money usd(long cents){return new Money(Money.Currency.USD,cents);}
    @Test public void dispatchOnlyMovesStockCostToTransitNoCashOrProfit() {
        PostingBatch batch=OnlineOrderAccounting.dispatch("dispatch-1","invoice-1",usd(3500),
            Collections.singletonList(new InventoryDelta("watch-1",-1)));
        assertEquals("ONLINE_DISPATCH",batch.operationKind());
        assertEquals(1,batch.inventory().size());
        assertEquals(2,batch.journal().lines().size());
        assertEquals("asset:goods_in_transit",batch.journal().lines().get(0).accountId);
        assertEquals("asset:inventory",batch.journal().lines().get(1).accountId);
    }
    @Test public void octoberCollectionBooksCashSalesAndCostOnlyThen() {
        PostingBatch batch=OnlineOrderAccounting.collect("collect-1","invoice-1",
            usd(6000),usd(3500),InvoiceSettlement.Channel.SHAM_CASH);
        assertEquals("ONLINE_COLLECTION",batch.operationKind());
        assertEquals(4,batch.journal().lines().size());
        assertEquals("asset:payment:SHAM_CASH",batch.journal().lines().get(0).accountId);
        assertEquals("income:sales",batch.journal().lines().get(1).accountId);
        assertEquals("expense:cost_of_goods_sold",batch.journal().lines().get(2).accountId);
        assertEquals("asset:goods_in_transit",batch.journal().lines().get(3).accountId);
    }
}
