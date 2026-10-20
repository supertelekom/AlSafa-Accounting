package com.alsafa.native5.accounting;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/**
 * All-or-nothing online receipt posting. Caller schedules background upload
 * only AFTER this method returns successfully.
 */
public final class OnlineReceiptPostingService {
    private final SQLiteDatabase db;
    public OnlineReceiptPostingService(SQLiteDatabase db) { this.db=Objects.requireNonNull(db); }

    public OnlineCollectionPlan collect(String operationId,String invoiceId,Money receipt,
            InvoiceSettlement.Channel destination) {
        Objects.requireNonNull(destination,"destination");
        if(operationId==null || !operationId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid operation ID");
        if(invoiceId==null || !invoiceId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid invoice ID");
        if(db.inTransaction()) throw new IllegalStateException("Nested transaction not supported");
        db.beginTransactionNonExclusive();
        try {
            try(android.database.Cursor pending=db.rawQuery(
                "SELECT 1 FROM invoice_cancellation_requests r LEFT JOIN invoice_cancellation_decisions d ON d.request_id=r.request_id WHERE r.invoice_id=? AND (d.decision IS NULL OR d.decision<>\'REJECTED\') LIMIT 1",
                new String[]{invoiceId})) {
                if(pending.moveToFirst())
                    throw new IllegalStateException("Invoice has pending cancellation review");
            }
            OnlineCollectionPlan plan=new OnlineInvoiceBalanceGuard(db).apply(invoiceId,receipt);
            PostingBatch batch=plan.toPosting(operationId,invoiceId,destination);
            long now=System.currentTimeMillis();
            ContentValues event=new ContentValues();
            event.put("event_id",operationId);
            event.put("operation_kind",batch.operationKind());
            event.put("created_at_ms",now);
            db.insertOrThrow("accounting_events",null,event);
            int lineNumber=0;
            for(JournalEntry.Line line:batch.journal().lines()) {
                ContentValues row=new ContentValues();
                row.put("event_id",operationId);
                row.put("line_no",lineNumber++);
                row.put("account_id",line.accountId);
                row.put("currency",line.amount.currency().name());
                row.put("signed_minor_units",line.amount.minorUnits());
                db.insertOrThrow("journal_lines",null,row);
            }
            ContentValues collection=new ContentValues();
            collection.put("collection_id",operationId);
            collection.put("invoice_id",invoiceId);
            collection.put("received_minor",receipt.minorUnits());
            collection.put("released_cost_minor",plan.costToRelease.minorUnits());
            collection.put("currency",receipt.currency().name());
            collection.put("destination",destination.name());
            collection.put("collected_at_ms",now);
            db.insertOrThrow("online_collections",null,collection);
            ContentValues upload=new ContentValues();
            upload.put("operation_id",operationId);
            upload.put("operation_kind",batch.operationKind());
            upload.put("payload",batch.payload());
            upload.put("created_at_ms",now);
            db.insertOrThrow("outbox",null,upload);
            db.setTransactionSuccessful();
            return plan;
        } finally {
            db.endTransaction();
        }
    }
}
