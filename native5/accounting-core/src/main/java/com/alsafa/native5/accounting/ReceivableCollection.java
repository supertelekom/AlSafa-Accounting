package com.alsafa.native5.accounting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the receivable settlement journal without recording revenue twice.
 * Invoice issuance must separately debit receivables and credit sales.
 * Subsequent collection debits each payment channel and credits receivables.
 * Foreign currency conversion requires separate FX accounting; this builder
 * refuses cross-currency posting until that mechanism is implemented.
 */
public final class ReceivableCollection {
    private ReceivableCollection() {}
    public static PostingBatch collect(String operationId,String invoiceId,
        Money invoiceCurrencyAmount,List<InvoiceSettlement.Receipt> receipts) {
        if(operationId==null || operationId.trim().isEmpty() ||
           invoiceId==null || invoiceId.trim().isEmpty())
            throw new IllegalArgumentException("Missing operation or invoice ID");
        Objects.requireNonNull(invoiceCurrencyAmount,"invoiceCurrencyAmount");
        Objects.requireNonNull(receipts,"receipts");
        if(receipts.isEmpty()) throw new IllegalArgumentException("No collection received");
        long collected=0;
        List<JournalEntry.Line> lines=new ArrayList<>();
        for(InvoiceSettlement.Receipt receipt:receipts) {
            Objects.requireNonNull(receipt,"receipt");
            if(receipt.received.currency()!=invoiceCurrencyAmount.currency())
                throw new IllegalArgumentException("Foreign currency collection needs an explicit FX journal");
            if(receipt.invoiceCurrencyValue.currency()!=invoiceCurrencyAmount.currency())
                throw new IllegalArgumentException("Invalid invoice valuation currency");
            collected=Math.addExact(collected,receipt.received.minorUnits());
            lines.add(new JournalEntry.Line("asset:payment:"+receipt.channel.name(),
                receipt.received));
        }
        if(collected!=invoiceCurrencyAmount.minorUnits() || collected<=0)
            throw new IllegalArgumentException("Collection amount does not match receipts");
        lines.add(new JournalEntry.Line("asset:receivable",
            invoiceCurrencyAmount.negate()));
        JournalEntry journal=new JournalEntry(operationId,lines);
        String payload="{\"invoiceId\":\""+safeId(invoiceId)+"\",\"collectedMinor\":"+collected+
            ",\"currency\":\""+invoiceCurrencyAmount.currency().name()+"\"}";
        return new PostingBatch(journal,"INVOICE_COLLECTION",payload,java.util.Collections.emptyList());
    }
    private static String safeId(String id) {
        if(!id.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid invoice ID");
        return id;
    }
}
