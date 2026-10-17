package com.alsafa.native5.accounting;

import java.util.Objects;

/**
 * Cash-realized profit allocation for management reports, separate from the
 * accrual journal. This NEVER posts revenue or COGS a second time.
 *
 * Invoice net margin = invoice revenue minus attributable COGS and discounts
 * already reflected in the net invoice amount. Each collected fraction realizes
 * the same fraction of that margin; the final collection receives any rounding
 * remainder. A refund/reversal requires a separate compensating event.
 */
public final class CollectionProfitAllocation {
    private CollectionProfitAllocation() {}

    public static final class Allocation {
        public final Money collected;
        public final Money realizedProfit;
        public final Money remainingReceivable;
        public final Money remainingProfit;
        Allocation(Money collected,Money profit,Money receivable,Money margin) {
            this.collected=collected;
            this.realizedProfit=profit;
            this.remainingReceivable=receivable;
            this.remainingProfit=margin;
        }
    }

    /**
     * All inputs are in the invoice valuation currency. previouslyCollected
     * and previouslyRealizedProfit are cumulative before this receipt.
     * Reporting month is derived from the actual receipt timestamp, not invoice date.
     */
    public static Allocation allocate(Money invoiceNet,Money attributableCost,
                                      Money previouslyCollected,Money previouslyRealizedProfit,
                                      Money receiptInvoiceCurrencyValue) {
        Objects.requireNonNull(invoiceNet,"invoiceNet");
        Objects.requireNonNull(attributableCost,"attributableCost");
        Objects.requireNonNull(previouslyCollected,"previouslyCollected");
        Objects.requireNonNull(previouslyRealizedProfit,"previouslyRealizedProfit");
        Objects.requireNonNull(receiptInvoiceCurrencyValue,"receiptInvoiceCurrencyValue");
        Money.Currency c=invoiceNet.currency();
        if(attributableCost.currency()!=c || previouslyCollected.currency()!=c ||
           previouslyRealizedProfit.currency()!=c || receiptInvoiceCurrencyValue.currency()!=c)
            throw new IllegalArgumentException("Amounts require one invoice valuation currency");
        long total=invoiceNet.minorUnits(), cost=attributableCost.minorUnits();
        long before=previouslyCollected.minorUnits(), paid=receiptInvoiceCurrencyValue.minorUnits();
        if(total<=0 || cost<0 || before<0 || paid<=0 || before>=total ||
           paid>total-before)
            throw new IllegalArgumentException("Invalid collection allocation");
        long margin=Math.subtractExact(total,cost);
        long already=previouslyRealizedProfit.minorUnits();
        long target;
        if(before+paid==total) target=margin;
        else {
            // BigInteger prevents intermediate multiplication overflow and supports losses.
            java.math.BigInteger numerator=java.math.BigInteger.valueOf(margin)
                .multiply(java.math.BigInteger.valueOf(before+paid));
            target=numerator.divide(java.math.BigInteger.valueOf(total)).longValueExact();
        }
        long increment=Math.subtractExact(target,already);
        return new Allocation(new Money(c,paid),new Money(c,increment),
            new Money(c,total-before-paid),new Money(c,Math.subtractExact(margin,target)));
    }
}
