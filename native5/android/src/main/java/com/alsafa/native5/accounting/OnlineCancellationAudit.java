package com.alsafa.native5.accounting;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/** Read-only verification; does not modify invoice, cash, stock or cloud state. */
public final class OnlineCancellationAudit {
    private final SQLiteDatabase db;
    public OnlineCancellationAudit(SQLiteDatabase db) { this.db=Objects.requireNonNull(db); }

    public String summary(String invoiceId) {
        if(invoiceId==null || !invoiceId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid invoice ID");
        try(Cursor c=db.rawQuery(
            "SELECT x.cancellation_event_id,o.state,"+
            "(SELECT COUNT(*) FROM journal_lines j WHERE j.event_id=x.cancellation_event_id),"+
            "(SELECT COUNT(*) FROM inventory_movements m WHERE m.event_id=x.cancellation_event_id) "+
            "FROM online_cancellations x LEFT JOIN outbox o ON o.operation_id=x.cancellation_event_id "+
            "WHERE x.invoice_id=?",new String[]{invoiceId})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Cancellation not found");
            return "event="+c.getString(0)+";sync="+(c.isNull(1)?"MISSING":c.getString(1))+
                ";journalLines="+c.getLong(2)+";inventoryMovements="+c.getLong(3);
        }
    }
}
