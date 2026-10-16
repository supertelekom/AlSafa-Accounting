package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Schema v1. Future schema upgrades must use additive, tested migrations;
 * NEVER delete or replace a shop's database to upgrade.
 */
public final class Native5DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "alsafa_native5.db";
    public Native5DatabaseHelper(Context context) {
        this(context,DATABASE_NAME);
    }
    Native5DatabaseHelper(Context context,String databaseName) {
        super(context,databaseName,null,1);
    }
    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounting_events(event_id TEXT PRIMARY KEY NOT NULL,operation_kind TEXT NOT NULL,created_at_ms INTEGER NOT NULL,CHECK(length(trim(event_id))>0),CHECK(length(trim(operation_kind))>0))");
        db.execSQL("CREATE TABLE journal_lines(event_id TEXT NOT NULL REFERENCES accounting_events(event_id) ON DELETE RESTRICT,line_no INTEGER NOT NULL CHECK(line_no>=0),account_id TEXT NOT NULL,currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),signed_minor_units INTEGER NOT NULL CHECK(signed_minor_units<>0),PRIMARY KEY(event_id,line_no))");
        db.execSQL("CREATE TABLE inventory_balances(product_id TEXT PRIMARY KEY NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>=0))");
        db.execSQL("CREATE TABLE inventory_movements(event_id TEXT NOT NULL REFERENCES accounting_events(event_id) ON DELETE RESTRICT,line_no INTEGER NOT NULL CHECK(line_no>=0),product_id TEXT NOT NULL REFERENCES inventory_balances(product_id) ON DELETE RESTRICT,quantity_delta INTEGER NOT NULL CHECK(quantity_delta<>0),PRIMARY KEY(event_id,line_no))");
        db.execSQL("CREATE TABLE outbox(operation_id TEXT PRIMARY KEY NOT NULL REFERENCES accounting_events(event_id) ON DELETE RESTRICT,operation_kind TEXT NOT NULL,payload TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','SENDING','RETRY','CONFIRMED','FAILED')),attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),created_at_ms INTEGER NOT NULL,last_attempt_at_ms INTEGER,confirmed_revision INTEGER CHECK(confirmed_revision IS NULL OR confirmed_revision>0),last_error TEXT)");
        db.execSQL("CREATE INDEX idx_native5_outbox_queue ON outbox(state,created_at_ms,operation_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        throw new IllegalStateException("Native5 migration not implemented: refusing destructive upgrade");
    }
}
