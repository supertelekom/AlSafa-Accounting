package com.alsafa.native5.accounting;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;
import java.util.Objects;

/**
 * Persist online invoice creation, stock removal, transit journal and outbox
 * atomically. No cash, revenue or profit is booked at dispatch.
 */
public final class OnlineDispatchPostingService {
    private final SQLiteDatabase db;
    public OnlineDispatchPostingService(SQLiteDatabase db) {
        this.db=Objects.requireNonNull(db,"db");
    }

    public void dispatch(String operationId,String invoiceId,Money invoiceTotal,
                         Money inventoryCost,List<InventoryDelta> stockRemoval) {
        if(operationId==null || !operationId.matches("[A-Za-z0-9_-]{1,128}") ||
           invoiceId==null || !invoiceId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid operation/invoice ID");
        Objects.requireNonNull(invoiceTotal,"invoiceTotal");
        Objects.requireNonNull(inventoryCost,"inventoryCost");
        Objects.requireNonNull(stockRemoval,"stockRemoval");
        if(invoiceTotal.minorUnits()<=0 || inventoryCost.minorUnits()<=0 ||
           invoiceTotal.currency()!=inventoryCost.currency())
            throw new IllegalArgumentException("Invalid online invoice total/cost");
        if(stockRemoval.isEmpty()) throw new IllegalArgumentException("Missing stock removal");
        for(InventoryDelta delta:stockRemoval) {
            Objects.requireNonNull(delta,"stock delta");
            if(delta.quantityChange()>=0) throw new IllegalArgumentException("Dispatch must remove stock");
        }
        PostingBatch batch=OnlineOrderAccounting.dispatch(
            operationId,invoiceId,inventoryCost,stockRemoval);
        AndroidSqlitePostingPort port=new AndroidSqlitePostingPort(db);
        port.begin();
        boolean finished=false;
        try {
            if(port.eventExists(operationId)) throw new IllegalStateException("Duplicate operation");
            ContentValues invoice=new ContentValues();
            invoice.put("invoice_id",invoiceId);
            invoice.put("total_minor",invoiceTotal.minorUnits());
            invoice.put("cost_minor",inventoryCost.minorUnits());
            invoice.put("currency",invoiceTotal.currency().name());
            invoice.put("created_at_ms",System.currentTimeMillis());
            db.insertOrThrow("online_invoices",null,invoice);
            port.insertEvent(operationId,batch.operationKind());
            int i=0;
            for(JournalEntry.Line line:batch.journal().lines())
                port.insertJournalLine(operationId,i++,line);
            i=0;
            for(InventoryDelta delta:stockRemoval)
                port.applyInventory(operationId,i++,delta);
            port.insertOutbox(operationId,batch.operationKind(),batch.payload());
            port.commit();
            finished=true;
        } finally {
            if(!finished) port.rollback();
        }
    }
}
