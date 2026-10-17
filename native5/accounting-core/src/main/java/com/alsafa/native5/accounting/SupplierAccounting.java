package com.alsafa.native5.accounting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Supplier invoice: debit inventory/expense, credit accounts payable.
 * Supplier settlement: debit payable, credit the selected payment account.
 * Never count a supplier payment as an expense a second time.
 */
public final class SupplierAccounting {
    private SupplierAccounting() {}

    public static PostingBatch recordInvoice(String eventId,String supplierInvoiceId,
                                              Money inventoryCost,Money expenseCost) {
        requireId(supplierInvoiceId);
        requireNonNegative(inventoryCost);
        requireNonNegative(expenseCost);
        if(inventoryCost.currency()!=expenseCost.currency())
            throw new IllegalArgumentException("Supplier invoice currencies must match");
        long total=Math.addExact(inventoryCost.minorUnits(),expenseCost.minorUnits());
        if(total<=0) throw new IllegalArgumentException("Empty supplier invoice");
        List<JournalEntry.Line> lines=new ArrayList<>();
        if(inventoryCost.minorUnits()>0)
            lines.add(new JournalEntry.Line("asset:inventory",inventoryCost));
        if(expenseCost.minorUnits()>0)
            lines.add(new JournalEntry.Line("expense:supplier",expenseCost));
        lines.add(new JournalEntry.Line("liability:accounts_payable",
            new Money(inventoryCost.currency(),Math.negateExact(total))));
        return new PostingBatch(new JournalEntry(eventId,lines),"SUPPLIER_INVOICE",
            "{\"supplierInvoiceId\":\""+supplierInvoiceId+"\",\"amountMinor\":"+total+
                ",\"currency\":\""+inventoryCost.currency()+"\"}",Collections.emptyList());
    }

    public static PostingBatch settle(String eventId,String supplierInvoiceId,
                                      Money paid,InvoiceSettlement.Channel source) {
        requireId(supplierInvoiceId);
        requireNonNegative(paid);
        Objects.requireNonNull(source,"source");
        VaultRouting.validate(source,paid.currency());
        if(paid.minorUnits()==0) throw new IllegalArgumentException("Zero supplier payment");
        List<JournalEntry.Line> lines=new ArrayList<>();
        lines.add(new JournalEntry.Line("liability:accounts_payable",paid));
        lines.add(new JournalEntry.Line("asset:payment:"+source.name(),paid.negate()));
        return new PostingBatch(new JournalEntry(eventId,lines),"SUPPLIER_PAYMENT",
            "{\"supplierInvoiceId\":\""+supplierInvoiceId+"\",\"paidMinor\":"+
                paid.minorUnits()+",\"currency\":\""+paid.currency()+"\",\"source\":\""+
                source.name()+"\"}",Collections.emptyList());
    }

    private static void requireNonNegative(Money money) {
        Objects.requireNonNull(money,"money");
        if(money.minorUnits()<0) throw new IllegalArgumentException("Negative amount");
    }
    private static void requireId(String id) {
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid supplier invoice ID");
    }
}
