package com.alsafa.native5.accounting;

import java.util.Objects;

/**
 * Validates cumulative online invoice collections and releases the corresponding
 * inventory cost only once. Persist the cumulative amounts with the invoice and
 * update them in the SAME SQLite transaction as the journal and outbox.
 */
public final class OnlineCollectionPlan {
    public final Money receipt;
    public final Money costToRelease;
    public final Money remainingDue;
    public final Money remainingTransitCost;
    public final boolean fullyPaid;

    private OnlineCollectionPlan(Money receipt,Money cost,Money due,Money transit) {
        this.receipt=receipt;
        this.costToRelease=cost;
        this.remainingDue=due;
        this.remainingTransitCost=transit;
        this.fullyPaid=due.minorUnits()==0;
    }

    public static OnlineCollectionPlan calculate(Money invoiceTotal,Money inventoryCost,
            Money alreadyCollected,Money alreadyCostReleased,Money receipt) {
        Objects.requireNonNull(invoiceTotal,"invoiceTotal");
        Objects.requireNonNull(inventoryCost,"inventoryCost");
        Objects.requireNonNull(alreadyCollected,"alreadyCollected");
        Objects.requireNonNull(alreadyCostReleased,"alreadyCostReleased");
        Objects.requireNonNull(receipt,"receipt");
        Money.Currency currency=invoiceTotal.currency();
        if(inventoryCost.currency()!=currency || alreadyCollected.currency()!=currency ||
           alreadyCostReleased.currency()!=currency || receipt.currency()!=currency)
            throw new IllegalArgumentException("Explicit FX handling required");
        long total=invoiceTotal.minorUnits(),cost=inventoryCost.minorUnits();
        long previous=alreadyCollected.minorUnits(),previousCost=alreadyCostReleased.minorUnits();
        long incoming=receipt.minorUnits();
        if(total<=0 || cost<=0 || previous<0 || previous>=total ||
           previousCost<0 || previousCost>cost || incoming<=0 || incoming>total-previous)
            throw new IllegalArgumentException("Invalid or duplicate collection");
        long after=Math.addExact(previous,incoming);
        long targetCost=after==total ? cost : java.math.BigInteger.valueOf(cost)
            .multiply(java.math.BigInteger.valueOf(after))
            .divide(java.math.BigInteger.valueOf(total)).longValueExact();
        if(targetCost<previousCost)
            throw new IllegalArgumentException("Inconsistent prior cost release");
        return new OnlineCollectionPlan(receipt,
            new Money(currency,Math.subtractExact(targetCost,previousCost)),
            new Money(currency,Math.subtractExact(total,after)),
            new Money(currency,Math.subtractExact(cost,targetCost)));
    }

    public PostingBatch toPosting(String eventId,String invoiceId,InvoiceSettlement.Channel destination) {
        return OnlineOrderAccounting.collect(eventId,invoiceId,receipt,costToRelease,destination);
    }
}
