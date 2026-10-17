package com.alsafa.native5.accounting;

import org.junit.Test;
import static org.junit.Assert.*;

public class OnlineCollectionPlanTest {
    private static Money usd(long cents){return new Money(Money.Currency.USD,cents);}
    @Test public void partialCollectionReleasesProportionalCostThenRemainder() {
        OnlineCollectionPlan first=OnlineCollectionPlan.calculate(
            usd(6000),usd(3500),usd(0),usd(0),usd(2000));
        assertEquals(4000,first.remainingDue.minorUnits());
        assertEquals(1166,first.costToRelease.minorUnits());
        assertFalse(first.fullyPaid);
        OnlineCollectionPlan finalPayment=OnlineCollectionPlan.calculate(
            usd(6000),usd(3500),usd(2000),first.costToRelease,usd(4000));
        assertEquals(2334,finalPayment.costToRelease.minorUnits());
        assertEquals(0,finalPayment.remainingTransitCost.minorUnits());
        assertTrue(finalPayment.fullyPaid);
        assertEquals("ONLINE_COLLECTION",
            finalPayment.toPosting("receipt-2","invoice-1",InvoiceSettlement.Channel.SHAM_CASH)
                .operationKind());
    }
    @Test public void preventsOvercollection() {
        try {
            OnlineCollectionPlan.calculate(usd(6000),usd(3500),usd(5000),usd(2916),usd(2000));
            fail("Overcollection accepted");
        } catch(IllegalArgumentException expected) {}
    }
    @Test public void preventsDoubleCollectionAfterFullyPaid() {
        try {
            OnlineCollectionPlan.calculate(usd(6000),usd(3500),usd(6000),usd(3500),usd(100));
            fail("Duplicate collection accepted");
        } catch(IllegalArgumentException expected) {}
    }
    @Test public void rejectsInconsistentPreviouslyReleasedCost() {
        try {
            OnlineCollectionPlan.calculate(usd(6000),usd(3500),usd(2000),usd(3000),usd(100));
            fail("Inconsistent cost release accepted");
        } catch(IllegalArgumentException expected) {}
    }
}
