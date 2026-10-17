package com.alsafa.native5.accounting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Issue a credit/online invoice once. Revenue is recognized at issuance,
 * and later collections settle receivables without creating a second sale.
 * The caller must supply cost of goods sold and inventory delta for stock sales.
 */
public final class InvoiceIssuance {
    private InvoiceIssuance() {}

    public static PostingBatch issue(String eventId,String invoiceId,Money saleTotal,
                                     Money cost,List<InventoryDelta> stockMovements) {
        requireId(invoiceId);
        Objects.requireNonNull(saleTotal,"saleTotal");
        Objects.requireNonNull(cost,"cost");
        Objects.requireNonNull(stockMovements,"stockMovements");
        if(saleTotal.minorUnits()<=0) throw new IllegalArgumentException("Sale total must be positive");
        if(cost.currency()!=saleTotal.currency() || cost.minorUnits()<0)
            throw new IllegalArgumentException("Invalid cost currency or amount");
        List<JournalEntry.Line> lines=new ArrayList<>();
        lines.add(new JournalEntry.Line("asset:receivable",saleTotal));
        lines.add(new JournalEntry.Line("income:sales",saleTotal.negate()));
        if(cost.minorUnits()>0) {
            lines.add(new JournalEntry.Line("expense:cost_of_goods_sold",cost));
            lines.add(new JournalEntry.Line("asset:inventory",cost.negate()));
        }
        JournalEntry journal=new JournalEntry(eventId,lines);
        String payload="{\"invoiceId\":\""+invoiceId+"\",\"totalMinor\":"+
            saleTotal.minorUnits()+",\"costMinor\":"+cost.minorUnits()+
            ",\"currency\":\""+saleTotal.currency().name()+"\"}";
        return new PostingBatch(journal,"INVOICE_ISSUED",payload,stockMovements);
    }

    private static void requireId(String id) {
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid invoice ID");
    }
}
