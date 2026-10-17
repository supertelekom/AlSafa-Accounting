package com.alsafa.native5.accounting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * A currency-safe invoice settlement plan. Never silently convert currencies.
 * Every receipt is allocated to a specific payment destination.
 * An explicit valuation in invoice currency is required for foreign-currency receipts.
 */
public final class InvoiceSettlement {
    public enum Channel { CASH_DRAWER, SHAM_CASH, EXTERNAL_CASHBOX, BANK, DELIVERY_COLLECTOR }
    public enum Status { UNPAID, PARTIAL, PAID }
    public static final class Receipt {
        public final Channel channel;
        public final Money received;
        public final Money invoiceCurrencyValue;
        public Receipt(Channel channel,Money received,Money invoiceCurrencyValue) {
            this.channel=Objects.requireNonNull(channel,"channel");
            this.received=Objects.requireNonNull(received,"received");
            this.invoiceCurrencyValue=Objects.requireNonNull(invoiceCurrencyValue,"invoiceCurrencyValue");
            if(received.minorUnits()<=0 || invoiceCurrencyValue.minorUnits()<=0)
                throw new IllegalArgumentException("Receipt and valuation must be positive");
            if(received.currency()==invoiceCurrencyValue.currency()
                && received.minorUnits()!=invoiceCurrencyValue.minorUnits())
                throw new IllegalArgumentException("Same-currency valuation must equal receipt");
        }
    }
    private final Money total;
    private final List<Receipt> receipts;
    private final Money outstanding;
    public InvoiceSettlement(Money total,List<Receipt> receipts) {
        this.total=Objects.requireNonNull(total,"total");
        if(total.minorUnits()<=0) throw new IllegalArgumentException("Invoice total must be positive");
        Objects.requireNonNull(receipts,"receipts");
        List<Receipt> copy=new ArrayList<>(receipts);
        long settled=0;
        for(Receipt receipt:copy) {
            Objects.requireNonNull(receipt,"receipt");
            if(receipt.invoiceCurrencyValue.currency()!=total.currency())
                throw new IllegalArgumentException("Valuation must use invoice currency");
            settled=Math.addExact(settled,receipt.invoiceCurrencyValue.minorUnits());
            if(settled>total.minorUnits()) throw new IllegalArgumentException("Overpayment is not an invoice settlement");
        }
        this.receipts=Collections.unmodifiableList(copy);
        this.outstanding=new Money(total.currency(),Math.subtractExact(total.minorUnits(),settled));
    }
    public Money total(){return total;}
    public List<Receipt> receipts(){return receipts;}
    public Money outstanding(){return outstanding;}
    public Status status() {
        if(outstanding.minorUnits()==0) return Status.PAID;
        return outstanding.minorUnits()==total.minorUnits()?Status.UNPAID:Status.PARTIAL;
    }
}
