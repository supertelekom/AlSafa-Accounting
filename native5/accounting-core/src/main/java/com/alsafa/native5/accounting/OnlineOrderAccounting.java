package com.alsafa.native5.accounting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Store-management policy for online orders: reserve/remove stock at dispatch,
 * but do NOT book sales revenue, collected cash or realized profit until receipt.
 *
 * The stock cost is moved from available inventory to goods-in-transit, so the
 * balance sheet remains balanced while collection is pending.
 */
public final class OnlineOrderAccounting {
    private OnlineOrderAccounting() {}

    public static PostingBatch dispatch(String eventId,String invoiceId,Money stockCost,
                                        List<InventoryDelta> stockRemoval) {
        requireId(invoiceId);
        Objects.requireNonNull(stockCost,"stockCost");
        Objects.requireNonNull(stockRemoval,"stockRemoval");
        if(stockCost.minorUnits()<=0) throw new IllegalArgumentException("Stock cost must be positive; zero-cost stock requires a dedicated inventory-only event");
        if(stockRemoval.isEmpty()) throw new IllegalArgumentException("Online dispatch requires stock movement");
        List<JournalEntry.Line> lines=new ArrayList<>();
        lines.add(new JournalEntry.Line("asset:goods_in_transit",stockCost));
        lines.add(new JournalEntry.Line("asset:inventory",stockCost.negate()));
        String payload="{\"invoiceId\":\""+invoiceId+"\",\"costMinor\":"+
            stockCost.minorUnits()+",\"currency\":\""+stockCost.currency()+"\"}";
        return new PostingBatch(new JournalEntry(eventId,lines),"ONLINE_DISPATCH",payload,stockRemoval);
    }

    /**
     * Record cash receipt and realized revenue at collection, never at dispatch.
     * For partial payments, pass the proportionate cost allocated to this receipt.
     * The last receipt must release all remaining goods-in-transit cost.
     */
    public static PostingBatch collect(String eventId,String invoiceId,Money collected,
                                       Money costReleased,InvoiceSettlement.Channel destination) {
        requireId(invoiceId);
        Objects.requireNonNull(collected,"collected");
        Objects.requireNonNull(costReleased,"costReleased");
        Objects.requireNonNull(destination,"destination");
        VaultRouting.validate(destination,collected.currency());
        if(collected.minorUnits()<=0 || costReleased.minorUnits()<0 ||
           collected.currency()!=costReleased.currency())
            throw new IllegalArgumentException("Invalid collection/cost currency");
        List<JournalEntry.Line> lines=new ArrayList<>();
        lines.add(new JournalEntry.Line("asset:payment:"+destination.name(),collected));
        lines.add(new JournalEntry.Line("income:sales",collected.negate()));
        if(costReleased.minorUnits()>0) {
            lines.add(new JournalEntry.Line("expense:cost_of_goods_sold",costReleased));
            lines.add(new JournalEntry.Line("asset:goods_in_transit",costReleased.negate()));
        }
        String payload="{\"invoiceId\":\""+invoiceId+"\",\"collectedMinor\":"+
            collected.minorUnits()+",\"costReleasedMinor\":"+costReleased.minorUnits()+
            ",\"currency\":\""+collected.currency()+"\",\"destination\":\""+
            destination.name()+"\"}";
        return new PostingBatch(new JournalEntry(eventId,lines),"ONLINE_COLLECTION",payload,
            Collections.emptyList());
    }

    private static void requireId(String id) {
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid invoice ID");
    }
}
