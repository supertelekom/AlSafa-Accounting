package com.alsafa.native5.accounting;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/** Upload-only review queue. Cloud writer must be immutable and idempotent. */
public final class ReviewSyncUploader {
    private final SQLiteDatabase db;
    private final UploadOnlySync.CloudWriter writer;
    public ReviewSyncUploader(SQLiteDatabase db,UploadOnlySync.CloudWriter writer) {
        this.db=Objects.requireNonNull(db);
        this.writer=Objects.requireNonNull(writer);
    }

    /** Return false if no pending operation remains. Must run on background thread. */
    public boolean uploadNext() throws Exception {
        String id,kind,payload;
        try(Cursor c=db.rawQuery(
            "SELECT operation_id,kind,payload FROM review_sync_outbox "+
            "WHERE state='PENDING' ORDER BY created_at_ms,operation_id LIMIT 1",null)) {
            if(!c.moveToFirst()) return false;
            id=c.getString(0);kind=c.getString(1);payload=c.getString(2);
        }
        long revision=writer.writeOnce(id,kind,payload);
        if(revision<=0) throw new IllegalStateException("Invalid cloud confirmation");
        ContentValues v=new ContentValues();
        v.put("state","CONFIRMED");
        int changed=db.update("review_sync_outbox",v,
            "operation_id=? AND state='PENDING'",new String[]{id});
        if(changed!=1) throw new IllegalStateException("Review confirmation race");
        return true;
    }
}
