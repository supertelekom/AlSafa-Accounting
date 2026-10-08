package com.alsafa.native4.core;
import android.database.sqlite.SQLiteDatabase;
/** Additive chart of accounts; journal posting remains opt-in until reconciled. */
public final class CoreSchemaV27 {
 private CoreSchemaV27(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS ledger_accounts(account_code TEXT PRIMARY KEY,account_name TEXT NOT NULL,account_type TEXT NOT NULL CHECK(account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_ledger_accounts_type ON ledger_accounts(account_type)");
 }
}
