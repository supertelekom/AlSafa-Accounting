package com.alsafa.native5.accounting;

import org.junit.Test;
import static org.junit.Assert.*;

public class CollectionProfitAllocationTest {
    private static Money usd(long cents){return new Money(Money.Currency.USD,cents);}
    @Test public void oldOnlineInvoiceProfitAppearsAtCollection() {
        CollectionProfitAllocation.Allocation october=CollectionProfitAllocation.allocate(
            usd(6000),usd(4000),usd(0),usd(0),usd(6000));
        assertEquals(6000,october.collected.minorUnits());
        assertEquals(2000,october.realizedProfit.minorUnits());
        assertEquals(0,october.remainingReceivable.minorUnits());
    }
    @Test public void oldRepairPartialCollectionDoesNotRepeatProfit() {
        CollectionProfitAllocation.Allocation first=CollectionProfitAllocation.allocate(
            usd(5000),usd(1000),usd(0),usd(0),usd(2000));
        assertEquals(1600,first.realizedProfit.minorUnits());
        CollectionProfitAllocation.Allocation second=CollectionProfitAllocation.allocate(
            usd(5000),usd(1000),usd(2000),first.realizedProfit,usd(3000));
        assertEquals(2400,second.realizedProfit.minorUnits());
        assertEquals(0,second.remainingProfit.minorUnits());
    }
    @Test public void lossIsRecognizedInCollectionMonth() {
        CollectionProfitAllocation.Allocation result=CollectionProfitAllocation.allocate(
            usd(1000),usd(1500),usd(0),usd(0),usd(1000));
        assertEquals(-500,result.realizedProfit.minorUnits());
    }
    @Test public void cannotAllocateBeyondOutstandingBalance() {
        try {
            CollectionProfitAllocation.allocate(usd(1000),usd(400),usd(800),usd(480),usd(300));
            fail("Overcollection accepted");
        } catch(IllegalArgumentException expected) {}
    }
}
