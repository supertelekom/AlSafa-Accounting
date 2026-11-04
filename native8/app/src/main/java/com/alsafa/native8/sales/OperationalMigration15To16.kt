package com.alsafa.native8.sales

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Allow expenses with payment methods that do not post to a cashbox. */
object OperationalMigration15To16:Migration(15,16) {
 override fun migrate(db:SupportSQLiteDatabase) {
  db.execSQL("""CREATE TABLE operational_expenses_v16 (
   id TEXT NOT NULL PRIMARY KEY,
   date TEXT NOT NULL,time TEXT NOT NULL,category TEXT NOT NULL,
   notes TEXT NOT NULL,currency TEXT NOT NULL,amount TEXT NOT NULL,
   payment_method TEXT NOT NULL,vault TEXT,
   usd_per_unit TEXT NOT NULL,created_at INTEGER NOT NULL,
   cash_movement_id TEXT)""")
  db.execSQL("""INSERT INTO operational_expenses_v16
   (id,date,time,category,notes,currency,amount,payment_method,vault,usd_per_unit,created_at,cash_movement_id)
   SELECT id,date,time,category,notes,currency,amount,payment_method,vault,usd_per_unit,created_at,cash_movement_id
   FROM operational_expenses""")
  db.execSQL("DROP TABLE operational_expenses")
  db.execSQL("ALTER TABLE operational_expenses_v16 RENAME TO operational_expenses")
  db.execSQL("CREATE UNIQUE INDEX index_operational_expenses_cash_movement_id ON operational_expenses(cash_movement_id)")
 }
}
