package com.alsafa.native5.accounting;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Objects;

/**
 * Android SQLite adapter for the same atomic posting contract as the JDBC reference.
 * Caller must use a writable database initialized with schema/001_initial.sql.
 * A single SQLiteDatabase transaction contains the event, journal, stock and outbox.
 */
public final class AndroidSqlitePostingPort implements AtomicPostingService.TransactionPort {
    private final SQLiteDatabase db;
    private boolean active;
    public AndroidSqlitePostingPort(SQLiteDatabase db) {
        this.db = Objects.requireNonNull(db, "db");
    }
    @Override public void begin() {
        if (active || db.inTransaction()) throw new IllegalStateException("Transaction already active");
        db.beginTransactionNonExclusive();
        active = true;
    }
    @Override public boolean eventExists(String id) {
        requireActive();
        try (Cursor c = db.rawQuery("SELECT 1 FROM accounting_events WHERE event_id=? LIMIT 1",new String[]{id})) {
            return c.moveToFirst();
        }
    }
    @Override public void insertEvent(String id,String kind) {
        requireActive();
        ContentValues v = new ContentValues();
        v.put("event_id",id); v.put("operation_kind",kind);
        v.put("created_at_ms",System.currentTimeMillis());
        insert("accounting_events",v);
    }
    @Override public void insertJournalLine(String id,int lineNo,JournalEntry.Line line) {
        requireActive();
        ContentValues v = new ContentValues();
        v.put("event_id",id); v.put("line_no",lineNo);
        v.put("account_id",line.accountId);
        v.put("currency",line.amount.currency().name());
        v.put("signed_minor_units",line.amount.minorUnits());
        insert("journal_lines",v);
    }
    @Override public void applyInventory(String id,int lineNo,InventoryDelta delta) {
        requireActive();
        long change=delta.quantityChange();
        if (change==Long.MIN_VALUE) throw new IllegalArgumentException("Inventory delta exceeds supported range");
        db.execSQL("UPDATE inventory_balances SET quantity=quantity+? WHERE product_id=? AND quantity>=? AND quantity<=?",
            new Object[]{change,delta.productId(),change<0?-change:0L,change>0?Long.MAX_VALUE-change:Long.MAX_VALUE});
        if (android.database.DatabaseUtils.longForQuery(db,"SELECT changes()",null)!=1)
            throw new IllegalStateException("Insufficient stock, overflow, or missing product: "+delta.productId());
        ContentValues v = new ContentValues();
        v.put("event_id",id); v.put("line_no",lineNo);
        v.put("product_id",delta.productId()); v.put("quantity_delta",change);
        insert("inventory_movements",v);
    }
    @Override public void insertOutbox(String id,String kind,String payload) {
        requireActive();
        ContentValues v = new ContentValues();
        v.put("operation_id",id); v.put("operation_kind",kind);
        v.put("payload",payload); v.put("created_at_ms",System.currentTimeMillis());
        insert("outbox",v);
    }
    @Override public void commit() {
        requireActive();
        db.setTransactionSuccessful();
        try { db.endTransaction(); }
        finally { active=false; }
    }
    @Override public void rollback() {
        if (!active) return;
        try { db.endTransaction(); }
        finally { active=false; }
    }
    private void insert(String table,ContentValues values) {
        if (db.insertOrThrow(table,null,values)<0)
            throw new IllegalStateException("Failed to insert into "+table);
    }
    private void requireActive() {
        if (!active) throw new IllegalStateException("Posting transaction not started");
    }
}
