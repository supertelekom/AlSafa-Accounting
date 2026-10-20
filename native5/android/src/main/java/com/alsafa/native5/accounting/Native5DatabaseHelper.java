package com.alsafa.native5.accounting;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Schema v7. Future schema upgrades must use additive, tested migrations;
 * NEVER delete or replace a shop's database to upgrade.
 */
public final class Native5DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "alsafa_native5.db";
    public Native5DatabaseHelper(Context context) {
        this(context,DATABASE_NAME);
    }
    Native5DatabaseHelper(Context context,String databaseName) {
        super(context,databaseName,null,7);
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
        db.execSQL("CREATE TRIGGER journal_no_update BEFORE UPDATE ON journal_lines BEGIN SELECT RAISE(ABORT, 'Posted journal is immutable'); END");
        db.execSQL("CREATE TRIGGER journal_no_delete BEFORE DELETE ON journal_lines BEGIN SELECT RAISE(ABORT, 'Posted journal is immutable'); END");
        db.execSQL("CREATE TRIGGER events_no_update BEFORE UPDATE ON accounting_events BEGIN SELECT RAISE(ABORT, 'Posted event is immutable'); END");
        addOnlineInvoiceTables(db);
        addClosingTables(db);
        addVarianceAdjustmentTables(db);
        addInvoiceCancellationTables(db);
        addInvoiceNumberMapping(db);
        addCancellationDecisions(db);
    }
    private static void addOnlineInvoiceTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS online_invoices(invoice_id TEXT PRIMARY KEY NOT NULL,total_minor INTEGER NOT NULL CHECK(total_minor>0),cost_minor INTEGER NOT NULL CHECK(cost_minor>0),currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),collected_minor INTEGER NOT NULL DEFAULT 0 CHECK(collected_minor>=0),released_cost_minor INTEGER NOT NULL DEFAULT 0 CHECK(released_cost_minor>=0),created_at_ms INTEGER NOT NULL,CHECK(collected_minor<=total_minor),CHECK(released_cost_minor<=cost_minor))");
        db.execSQL("CREATE TABLE IF NOT EXISTS online_collections(collection_id TEXT PRIMARY KEY NOT NULL REFERENCES accounting_events(event_id) ON DELETE RESTRICT,invoice_id TEXT NOT NULL REFERENCES online_invoices(invoice_id) ON DELETE RESTRICT,received_minor INTEGER NOT NULL CHECK(received_minor>0),released_cost_minor INTEGER NOT NULL CHECK(released_cost_minor>=0),currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),destination TEXT NOT NULL,collected_at_ms INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_online_collections_invoice ON online_collections(invoice_id,collected_at_ms)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS online_collections_no_update BEFORE UPDATE ON online_collections BEGIN SELECT RAISE(ABORT,'Collection is immutable'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS online_collections_no_delete BEFORE DELETE ON online_collections BEGIN SELECT RAISE(ABORT,'Collection is immutable'); END");
    }
    private static void addClosingTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS vault_closings(closing_id TEXT PRIMARY KEY NOT NULL REFERENCES accounting_events(event_id) ON DELETE RESTRICT,vault TEXT NOT NULL,closed_at_ms INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS vault_closing_counts(closing_id TEXT NOT NULL REFERENCES vault_closings(closing_id) ON DELETE RESTRICT,currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),expected_minor INTEGER NOT NULL,counted_minor INTEGER NOT NULL CHECK(counted_minor>=0),variance_minor INTEGER NOT NULL,PRIMARY KEY(closing_id,currency),CHECK(variance_minor=counted_minor-expected_minor))");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS vault_closings_no_update BEFORE UPDATE ON vault_closings BEGIN SELECT RAISE(ABORT,'Closing is immutable'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS vault_closings_no_delete BEFORE DELETE ON vault_closings BEGIN SELECT RAISE(ABORT,'Closing is immutable'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS vault_closing_counts_no_update BEFORE UPDATE ON vault_closing_counts BEGIN SELECT RAISE(ABORT,'Closing count is immutable'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS vault_closing_counts_no_delete BEFORE DELETE ON vault_closing_counts BEGIN SELECT RAISE(ABORT,'Closing count is immutable'); END");
    }
    private static void addVarianceAdjustmentTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS vault_variance_adjustments(adjustment_id TEXT PRIMARY KEY NOT NULL REFERENCES accounting_events(event_id) ON DELETE RESTRICT,closing_id TEXT NOT NULL REFERENCES vault_closings(closing_id) ON DELETE RESTRICT,currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),variance_minor INTEGER NOT NULL CHECK(variance_minor<>0),reason TEXT NOT NULL CHECK(length(trim(reason))>=3),UNIQUE(closing_id,currency))");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS variance_adjustment_no_update BEFORE UPDATE ON vault_variance_adjustments BEGIN SELECT RAISE(ABORT,'Variance adjustment is immutable'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS variance_adjustment_no_delete BEFORE DELETE ON vault_variance_adjustments BEGIN SELECT RAISE(ABORT,'Variance adjustment is immutable'); END");
    }
    /**
     * Immutable cancellation requests and decisions. These are not accounting
     * reversals: posting will require a separate atomic, tested workflow.
     */
    private static void addInvoiceCancellationTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS invoice_cancellation_requests(request_id TEXT PRIMARY KEY NOT NULL,invoice_id TEXT NOT NULL,invoice_number TEXT NOT NULL,reason TEXT NOT NULL CHECK(length(trim(reason))>=1),requested_at_ms INTEGER NOT NULL,UNIQUE(invoice_id))");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS cancellation_requests_no_update BEFORE UPDATE ON invoice_cancellation_requests BEGIN SELECT RAISE(ABORT,'Cancellation request is immutable'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS cancellation_requests_no_delete BEFORE DELETE ON invoice_cancellation_requests BEGIN SELECT RAISE(ABORT,'Cancellation request is immutable'); END");
    }
    /** Human-facing invoice numbers are separate from opaque internal invoice IDs.
     * Never assign or guess numbers for historical invoices during migration.
     */
    private static void addInvoiceNumberMapping(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS invoice_number_mapping(invoice_id TEXT PRIMARY KEY NOT NULL REFERENCES online_invoices(invoice_id) ON DELETE RESTRICT,invoice_number TEXT NOT NULL UNIQUE CHECK(length(trim(invoice_number))>0),assigned_at_ms INTEGER NOT NULL)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS invoice_number_mapping_no_update BEFORE UPDATE ON invoice_number_mapping BEGIN SELECT RAISE(ABORT,'Invoice number is immutable'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS invoice_number_mapping_no_delete BEFORE DELETE ON invoice_number_mapping BEGIN SELECT RAISE(ABORT,'Invoice number is immutable'); END");
    }
    /** Immutable decisions. A rejected review reopens collection; approval is not a posted reversal. */
    private static void addCancellationDecisions(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS invoice_cancellation_decisions(request_id TEXT PRIMARY KEY NOT NULL REFERENCES invoice_cancellation_requests(request_id) ON DELETE RESTRICT,decision TEXT NOT NULL CHECK(decision IN ('REJECTED','APPROVED_FOR_POSTING')),reason TEXT NOT NULL CHECK(length(trim(reason))>0),decided_at_ms INTEGER NOT NULL)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS cancellation_decisions_no_update BEFORE UPDATE ON invoice_cancellation_decisions BEGIN SELECT RAISE(ABORT,'Cancellation decision is immutable'); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS cancellation_decisions_no_delete BEFORE DELETE ON invoice_cancellation_decisions BEGIN SELECT RAISE(ABORT,'Cancellation decision is immutable'); END");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2 && newVersion>=2) addOnlineInvoiceTables(db);
        if(oldVersion<3 && newVersion>=3) addClosingTables(db);
        if(oldVersion<4 && newVersion>=4) addVarianceAdjustmentTables(db);
        if(oldVersion<5 && newVersion>=5) addInvoiceCancellationTables(db);
        if(oldVersion<6 && newVersion>=6) addInvoiceNumberMapping(db);
        if(oldVersion<7 && newVersion>=7) addCancellationDecisions(db);
        if(newVersion>7) throw new IllegalStateException("Unknown Native5 schema version");
    }
}
