package com.alsafa.native5.accounting;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/** Immutable review request only. Does not cancel, refund or reverse an invoice. */
public final class InvoiceCancellationRequestService {
    private final SQLiteDatabase db;
    public InvoiceCancellationRequestService(SQLiteDatabase db) { this.db=Objects.requireNonNull(db); }

    public String requestReview(String requestId,String lookup,String reason) {
        if(requestId==null || !requestId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid request ID");
        if(lookup==null || lookup.trim().isEmpty() || lookup.length()>128)
            throw new IllegalArgumentException("Invoice lookup required");
        if(reason==null || reason.trim().isEmpty() || reason.length()>500)
            throw new IllegalArgumentException("Reason required");
        if(db.inTransaction()) throw new IllegalStateException("Nested transaction");
        db.beginTransactionNonExclusive();
        try {
            String invoiceId,publicNumber;
            try(Cursor c=db.rawQuery("SELECT i.invoice_id,m.invoice_number FROM online_invoices i LEFT JOIN invoice_number_mapping m ON m.invoice_id=i.invoice_id WHERE i.invoice_id=? OR m.invoice_number=? ORDER BY i.invoice_id LIMIT 2",new String[]{lookup,lookup})) {
                if(!c.moveToFirst()) throw new IllegalArgumentException("Invoice not found");
                invoiceId=c.getString(0);
                publicNumber=c.isNull(1)?invoiceId:c.getString(1);
                if(c.moveToNext()&&!invoiceId.equals(c.getString(0)))
                    throw new IllegalStateException("Ambiguous invoice");
            }
            ContentValues v=new ContentValues();
            v.put("request_id",requestId);
            v.put("invoice_id",invoiceId);
            v.put("invoice_number",publicNumber);
            v.put("reason",reason.trim());
            v.put("requested_at_ms",System.currentTimeMillis());
            db.insertOrThrow("invoice_cancellation_requests",null,v);
            db.setTransactionSuccessful();
            return invoiceId;
        } finally { db.endTransaction(); }
    }
}
