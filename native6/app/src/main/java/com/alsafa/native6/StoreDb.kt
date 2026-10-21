package com.alsafa.native6

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class StoreDb(context: Context, name: String = "alsafa_native6.db") : SQLiteOpenHelper(context,name,null,6) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true); db.execSQL("PRAGMA synchronous=FULL") }
    override fun onCreate(db: SQLiteDatabase) {
        schema.forEach(db::execSQL)
        db.execSQL(legacyRecordsTable)
        db.execSQL(legacyRecordsNoUpdate)
        db.execSQL(legacyRecordsNoDelete)
        com.alsafa.native6.accounting.PartnerSchema.statements.forEach(db::execSQL)
        immutableTriggers.forEach(db::execSQL)
        db.execSQL(autoClosingReviewsTable)
        reviewTriggers.forEach(db::execSQL)
    }
    override fun onUpgrade(db: SQLiteDatabase,old: Int,new: Int) {
        require(old in 1..5 && new == 6) {
            "Unsupported migration $old → $new; database preserved"
        }
        if (old <= 2) {
            com.alsafa.native6.accounting.PartnerSchema.statements.forEach(db::execSQL)
        }
        if (old <= 3) {
            db.execSQL(legacyRecordsTable)
            db.execSQL(legacyRecordsNoUpdate)
            db.execSQL(legacyRecordsNoDelete)
            immutableTriggers.forEach(db::execSQL)
        }
        db.execSQL(autoClosingReviewsTable)
        reviewTriggers.forEach(db::execSQL)
        // Migrate physical counts without losing original closing events.
        db.execSQL("CREATE TABLE closings_v6(event_id TEXT NOT NULL REFERENCES events(id),vault TEXT NOT NULL,currency TEXT NOT NULL,expected INTEGER NOT NULL,counted INTEGER CHECK(counted>=0),PRIMARY KEY(event_id,currency))")
        db.execSQL("INSERT INTO closings_v6 SELECT c.event_id,c.vault,c.currency,c.expected,CASE WHEN e.kind='AUTO_CLOSING' THEN NULL ELSE c.counted END FROM closings c JOIN events e ON e.id=c.event_id")
        db.execSQL("DROP TRIGGER IF EXISTS closings_update")
        db.execSQL("DROP TRIGGER IF EXISTS closings_delete")
        db.execSQL("DROP TABLE closings")
        db.execSQL("ALTER TABLE closings_v6 RENAME TO closings")
        immutableTriggers.filter { it.contains("closings_") }.forEach(db::execSQL)
    }
    companion object {
        val autoClosingReviewsTable = "CREATE TABLE IF NOT EXISTS auto_closing_reviews(event_id TEXT NOT NULL REFERENCES events(id),closing_event_id TEXT NOT NULL REFERENCES events(id),vault TEXT NOT NULL,currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),review_date TEXT NOT NULL,book_balance INTEGER NOT NULL,actual_count INTEGER NOT NULL CHECK(actual_count>=0),note TEXT NOT NULL,PRIMARY KEY(closing_event_id,currency))"
        val reviewTriggers = listOf("UPDATE","DELETE").map { action -> "CREATE TRIGGER IF NOT EXISTS auto_closing_reviews_"+action.lowercase()+" BEFORE "+action+" ON auto_closing_reviews BEGIN SELECT RAISE(ABORT,'Immutable cash review'); END" }
        val immutableTriggers = listOf("events","journal","stock_moves","invoice_lines","receipts","returns","refunds","closings").flatMap { table -> listOf("UPDATE","DELETE").map { action -> "CREATE TRIGGER IF NOT EXISTS "+table+"_"+action.lowercase()+" BEFORE "+action+" ON "+table+" BEGIN SELECT RAISE(ABORT,'Immutable accounting record'); END" } }
        val legacyRecordsNoUpdate = "CREATE TRIGGER legacy_records_no_update BEFORE UPDATE ON legacy_records BEGIN SELECT RAISE(ABORT,'Immutable legacy archive'); END"
        val legacyRecordsNoDelete = "CREATE TRIGGER legacy_records_no_delete BEFORE DELETE ON legacy_records BEGIN SELECT RAISE(ABORT,'Immutable legacy archive'); END"
        val legacyRecordsTable = "CREATE TABLE legacy_records(category TEXT NOT NULL,legacy_id TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(category,legacy_id))"
        val schema = listOf(
            "CREATE TABLE events(id TEXT PRIMARY KEY NOT NULL,kind TEXT NOT NULL,date TEXT NOT NULL,created INTEGER NOT NULL,memo TEXT NOT NULL)",
            "CREATE TABLE journal(event_id TEXT NOT NULL REFERENCES events(id),line INTEGER NOT NULL,account TEXT NOT NULL,currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),amount INTEGER NOT NULL CHECK(typeof(amount)='integer' AND amount<>0),PRIMARY KEY(event_id,line))",
            "CREATE INDEX journal_account ON journal(account,currency)",
            "CREATE TABLE products(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,barcode TEXT NOT NULL DEFAULT '',brand TEXT NOT NULL DEFAULT '',model TEXT NOT NULL DEFAULT '',currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),price INTEGER NOT NULL CHECK(price>=0),cost INTEGER NOT NULL CHECK(cost>=0),stock INTEGER NOT NULL CHECK(typeof(stock)='integer' AND stock>=0),value INTEGER NOT NULL DEFAULT 0 CHECK(typeof(value)='integer' AND value>=0),kind TEXT NOT NULL DEFAULT 'watch',details TEXT NOT NULL DEFAULT '{}',archived INTEGER NOT NULL DEFAULT 0)",
            "CREATE INDEX product_barcode ON products(barcode)",
            "CREATE TABLE stock_moves(event_id TEXT NOT NULL REFERENCES events(id),line INTEGER NOT NULL,product_id TEXT NOT NULL REFERENCES products(id),quantity INTEGER NOT NULL CHECK(quantity<>0),value_delta INTEGER NOT NULL,PRIMARY KEY(event_id,line))",
            "CREATE TABLE suppliers(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,phone TEXT NOT NULL)",
            "CREATE TABLE invoices(id TEXT PRIMARY KEY NOT NULL REFERENCES events(id),number INTEGER NOT NULL UNIQUE,kind TEXT NOT NULL CHECK(kind IN ('sale','purchase','repair')),party TEXT NOT NULL,phone TEXT NOT NULL,currency TEXT NOT NULL,total INTEGER NOT NULL CHECK(total>0),cost INTEGER NOT NULL CHECK(cost>=0),status TEXT NOT NULL DEFAULT 'posted',notes TEXT NOT NULL)",
            "CREATE TABLE invoice_lines(invoice_id TEXT NOT NULL REFERENCES invoices(id),line INTEGER NOT NULL,product_id TEXT NOT NULL REFERENCES products(id),name TEXT NOT NULL,qty INTEGER NOT NULL CHECK(qty>0),net INTEGER NOT NULL CHECK(net>=0),cost INTEGER NOT NULL CHECK(cost>=0),PRIMARY KEY(invoice_id,line))",
            "CREATE TABLE receipts(event_id TEXT PRIMARY KEY NOT NULL REFERENCES events(id),invoice_id TEXT NOT NULL REFERENCES invoices(id),amount INTEGER NOT NULL CHECK(amount>0),vault TEXT NOT NULL)",
            "CREATE TABLE returns(event_id TEXT NOT NULL REFERENCES events(id),invoice_id TEXT NOT NULL REFERENCES invoices(id),line INTEGER NOT NULL,qty INTEGER NOT NULL CHECK(qty>0),credit INTEGER NOT NULL CHECK(credit>=0),cost INTEGER NOT NULL CHECK(cost>=0),PRIMARY KEY(event_id,line),FOREIGN KEY(invoice_id,line) REFERENCES invoice_lines(invoice_id,line))",
            "CREATE TABLE refunds(event_id TEXT PRIMARY KEY NOT NULL REFERENCES events(id),invoice_id TEXT NOT NULL REFERENCES invoices(id),amount INTEGER NOT NULL CHECK(amount>0),vault TEXT NOT NULL)",
            "CREATE TABLE closings(event_id TEXT NOT NULL REFERENCES events(id),vault TEXT NOT NULL,currency TEXT NOT NULL,expected INTEGER NOT NULL,counted INTEGER CHECK(counted>=0),PRIMARY KEY(event_id,currency))",
            "CREATE TABLE repairs(id TEXT PRIMARY KEY NOT NULL,customer TEXT NOT NULL,phone TEXT NOT NULL,watch TEXT NOT NULL,problem TEXT NOT NULL,status TEXT NOT NULL,date TEXT NOT NULL,invoice_id TEXT REFERENCES invoices(id),details TEXT NOT NULL DEFAULT '{}')",
            "CREATE TABLE settings(key TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)",
            "CREATE TABLE legacy_imports(hash TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL,created INTEGER NOT NULL,summary TEXT NOT NULL)"
        )
    }
}
