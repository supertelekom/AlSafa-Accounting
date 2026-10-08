package com.alsafa.native4.core;
import android.database.sqlite.SQLiteDatabase;
/** Additive chart of accounts; journal posting remains opt-in until reconciled. */
public final class CoreSchemaV27 {
 private CoreSchemaV27(){}
 public static void create(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS ledger_accounts(account_code TEXT PRIMARY KEY,account_name TEXT NOT NULL,account_type TEXT NOT NULL CHECK(account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),created_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_ledger_accounts_type ON ledger_accounts(account_type)");
  seed(db,"CASH_MAIN","Main drawer","ASSET");
  seed(db,"CASH_EXTERNAL","External safe","ASSET");
  seed(db,"SHAM_CASH","Sham Cash","ASSET");
  seed(db,"ACCOUNTS_RECEIVABLE","Receivables","ASSET");
  seed(db,"INVENTORY","Inventory","ASSET");
  seed(db,"ACCOUNTS_PAYABLE","Payables","LIABILITY");
  seed(db,"OWNER_CAPITAL","Capital","EQUITY");
  seed(db,"SALES_REVENUE","Sales revenue","REVENUE");
  seed(db,"REPAIR_REVENUE","Repair revenue","REVENUE");
  seed(db,"COST_OF_GOODS_SOLD","Cost of sales","EXPENSE");
  seed(db,"OPERATING_EXPENSE","Operating expenses","EXPENSE");
 }
 private static void seed(SQLiteDatabase db,String code,String name,String type){
  db.execSQL("INSERT OR IGNORE INTO ledger_accounts(account_code,account_name,account_type,active,created_at) VALUES(?,?,?,1,?)",new Object[]{code,name,type,System.currentTimeMillis()});
 }
}
