package com.alsafa.native5.accounting;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/** Read-only cloud projection access for phones. No journal or stock writes allowed. */
public final class PhoneReadOnlyStore {
    private final SQLiteDatabase db;
    public PhoneReadOnlyStore(SQLiteDatabase db) { this.db=Objects.requireNonNull(db,"db"); }
    public long inventoryQuantity(String productId) {
        try(Cursor c=db.rawQuery("SELECT quantity FROM inventory_balances WHERE product_id=?",
                new String[]{Objects.requireNonNull(productId,"productId")})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Unknown product");
            return c.getLong(0);
        }
    }
    public long journalLineCount(String eventId) {
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM journal_lines WHERE event_id=?",
                new String[]{Objects.requireNonNull(eventId,"eventId")})) {
            c.moveToFirst(); return c.getLong(0);
        }
    }
    public String outboxState(String operationId) {
        try(Cursor c=db.rawQuery("SELECT state FROM outbox WHERE operation_id=?",
                new String[]{Objects.requireNonNull(operationId,"operationId")})) {
            return c.moveToFirst()?c.getString(0):null;
        }
    }
}
