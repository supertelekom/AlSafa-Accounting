package com.alsafa.native5.accounting;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/** Resolve a review without changing accounting; approval is NOT cancellation posting. */
public final class InvoiceCancellationDecisionService {
    private final SQLiteDatabase db;
    public InvoiceCancellationDecisionService(SQLiteDatabase db) { this.db=Objects.requireNonNull(db); }

    public void reject(String requestId,String reason) {
        decide(requestId,reason,"REJECTED");
    }

    /** Approval authorizes later review; it never posts a reversal or refund. */
    public void approveForPosting(String requestId,String reason) {
        decide(requestId,reason,"APPROVED_FOR_POSTING");
    }

    private void decide(String requestId,String reason,String decision) {
        if(requestId==null || !requestId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid request ID");
        if(reason==null || reason.trim().isEmpty() || reason.length()>500)
            throw new IllegalArgumentException("Decision reason required");
        if(db.inTransaction()) throw new IllegalStateException("Nested transaction");
        db.beginTransactionNonExclusive();
        try {
            try(Cursor c=db.rawQuery(
                "SELECT 1 FROM invoice_cancellation_requests WHERE request_id=? LIMIT 1",
                new String[]{requestId})) {
                if(!c.moveToFirst()) throw new IllegalArgumentException("Review request not found");
            }
            ContentValues v=new ContentValues();
            v.put("request_id",requestId);
            v.put("decision",decision);
            v.put("reason",reason.trim());
            v.put("decided_at_ms",System.currentTimeMillis());
            db.insertOrThrow("invoice_cancellation_decisions",null,v);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
