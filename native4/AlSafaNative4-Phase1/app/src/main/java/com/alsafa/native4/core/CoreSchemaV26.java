package com.alsafa.native4.core;
import android.database.sqlite.SQLiteDatabase;
/** Additive accounting journal schema; no legacy invoice or cash records are rewritten. */
public final class CoreSchemaV26 {
 private CoreSchemaV26(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS journal_entries(entry_id TEXT PRIMARY KEY,source_type TEXT NOT NULL,source_id TEXT NOT NULL,entry_kind TEXT NOT NULL,currency TEXT NOT NULL,posted_at INTEGER NOT NULL,description TEXT,UNIQUE(source_type,source_id,entry_kind,currency))");
  db.execSQL("CREATE TABLE IF NOT EXISTS journal_lines(line_id TEXT PRIMARY KEY,entry_id TEXT NOT NULL,account_code TEXT NOT NULL,debit_minor INTEGER NOT NULL DEFAULT 0 CHECK(debit_minor>=0),credit_minor INTEGER NOT NULL DEFAULT 0 CHECK(credit_minor>=0),CHECK((debit_minor>0 AND credit_minor=0) OR (credit_minor>0 AND debit_minor=0)),FOREIGN KEY(entry_id) REFERENCES journal_entries(entry_id))");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_lines_entry ON journal_lines(entry_id)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_entries_source ON journal_entries(source_type,source_id)");
 }
}
