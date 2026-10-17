package com.alsafa.native5.accounting;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/**
 * Corrects an existing closing variance exactly once per currency, only when
 * explicitly requested. Historical counts stay immutable.
 */
public final class VaultVarianceAdjustmentService {
    private final SQLiteDatabase db;
    public VaultVarianceAdjustmentService(SQLiteDatabase db) {
        this.db=Objects.requireNonNull(db,"db");
    }

    public PostingBatch adjust(String eventId,String closingId,Money variance,String reason) {
        if(db.inTransaction()) throw new IllegalStateException("Nested adjustment transaction");
        AndroidSqlitePostingPort port=new AndroidSqlitePostingPort(db);
        port.begin();
        try {
            VaultClosingHistory.Snapshot snapshot=new VaultClosingHistory(db).get(closingId);
            if(snapshot==null) throw new IllegalArgumentException("Closing not found");
            VaultClosingReconciliation.Difference difference=snapshot.counts.get(
                Objects.requireNonNull(variance,"variance").currency());
            if(difference==null || difference.variance.minorUnits()!=variance.minorUnits())
                throw new IllegalArgumentException("Adjustment must equal recorded closing variance");
            PostingBatch batch=VaultVarianceAdjustment.post(
                eventId,closingId,snapshot.vault,variance,reason);
            try(Cursor c=db.rawQuery(
                "SELECT 1 FROM vault_variance_adjustments WHERE closing_id=? AND currency=? LIMIT 1",
                new String[]{closingId,variance.currency().name()})) {
                if(c.moveToFirst()) throw new IllegalStateException("Variance already adjusted");
            }
            if(port.eventExists(eventId)) throw new IllegalStateException("Duplicate adjustment ID");
            port.insertEvent(eventId,batch.operationKind());
            int i=0;
            for(JournalEntry.Line line:batch.journal().lines())
                port.insertJournalLine(eventId,i++,line);
            db.execSQL("INSERT INTO vault_variance_adjustments(adjustment_id,closing_id,currency,variance_minor,reason) VALUES(?,?,?,?,?)",
                new Object[]{eventId,closingId,variance.currency().name(),variance.minorUnits(),reason.trim()});
            port.insertOutbox(eventId,batch.operationKind(),batch.payload());
            port.commit();
            return batch;
        } catch(RuntimeException | Error failure) {
            port.rollback();
            throw failure;
        }
    }
}
