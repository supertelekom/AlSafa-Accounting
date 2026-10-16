package com.alsafa.native5.accounting;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/**
 * Durable, upload-only queue. One operation ID maps to one immutable accounting event.
 * Attempt numbers act as fencing tokens so stale network callbacks cannot acknowledge
 * a later retry. Cloud writer must use operation_id as an idempotency key.
 */
public final class AndroidOutboxQueue implements UploadOnlySync.Queue {
    public static final class Item {
        public final String operationId;
        public final String kind;
        public final String payload;
        public final long attempt;
        Item(String id,String kind,String payload,long attempt) {
            this.operationId=id; this.kind=kind; this.payload=payload; this.attempt=attempt;
        }
    }
    private final SQLiteDatabase db;
    public AndroidOutboxQueue(SQLiteDatabase db) { this.db=Objects.requireNonNull(db,"db"); }

    /** Claim one pending/retry item or expired in-flight attempt. Call off the UI thread. */
    public synchronized UploadOnlySync.Item claim(long nowMs,long leaseMs) {
        if (leaseMs<=0 || nowMs<0) throw new IllegalArgumentException("Invalid lease clock");
        db.beginTransactionNonExclusive();
        try {
            Item candidate=null;
            try(Cursor c=db.rawQuery(
                "SELECT operation_id,operation_kind,payload,attempts FROM outbox "+
                "WHERE state IN ('PENDING','RETRY') OR (state='SENDING' AND last_attempt_at_ms<=?) "+
                "ORDER BY created_at_ms,operation_id LIMIT 1",
                new String[]{Long.toString(Math.max(0L,nowMs-leaseMs))})) {
                if(c.moveToFirst()) candidate=new Item(c.getString(0),c.getString(1),c.getString(2),c.getLong(3));
            }
            if(candidate==null) { db.setTransactionSuccessful(); return null; }
            if(candidate.attempt==Long.MAX_VALUE) throw new IllegalStateException("Outbox attempts exhausted");
            ContentValues v=new ContentValues();
            v.put("state","SENDING"); v.put("attempts",candidate.attempt+1);
            v.put("last_attempt_at_ms",nowMs); v.putNull("last_error");
            int changed=db.update("outbox",v,"operation_id=? AND attempts=? AND state IN ('PENDING','RETRY','SENDING')",
                new String[]{candidate.operationId,Long.toString(candidate.attempt)});
            if(changed!=1) throw new IllegalStateException("Concurrent outbox claim");
            db.setTransactionSuccessful();
            return new UploadOnlySync.Item(candidate.operationId,candidate.kind,candidate.payload,candidate.attempt+1);
        } finally { db.endTransaction(); }
    }

    /** Only a currently active attempt may acknowledge successful remote persistence. */
    @Override public synchronized boolean confirm(String operationId,long attempt,long revision) {
        if(revision<=0 || attempt<=0) throw new IllegalArgumentException("Invalid confirmation");
        ContentValues v=new ContentValues();
        v.put("state","CONFIRMED"); v.put("confirmed_revision",revision); v.putNull("last_error");
        return db.update("outbox",v,"operation_id=? AND state='SENDING' AND attempts=?",
            new String[]{operationId,Long.toString(attempt)})==1;
    }

    /** Transient network failures remain durable and retryable; never delete the event. */
    @Override public synchronized boolean retry(String operationId,long attempt,String error) {
        if(attempt<=0) throw new IllegalArgumentException("Invalid attempt");
        ContentValues v=new ContentValues();
        v.put("state","RETRY"); v.put("last_error",error);
        return db.update("outbox",v,"operation_id=? AND state='SENDING' AND attempts=?",
            new String[]{operationId,Long.toString(attempt)})==1;
    }
}
