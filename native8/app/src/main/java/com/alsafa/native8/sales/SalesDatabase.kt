package com.alsafa.native8.sales

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SalesDatabase(context: Context): SQLiteOpenHelper(context, "alsafa_native8_sales.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sales (id TEXT PRIMARY KEY, number INTEGER UNIQUE NOT NULL, created_at INTEGER NOT NULL, customer TEXT NOT NULL, phone TEXT NOT NULL, total TEXT NOT NULL, discount TEXT NOT NULL, paid TEXT NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE sale_lines (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, source TEXT NOT NULL, product_id TEXT NOT NULL, variant_id TEXT, title TEXT NOT NULL, quantity TEXT NOT NULL, unit_price TEXT NOT NULL, unit_cost TEXT NOT NULL, discount TEXT NOT NULL, returned_qty TEXT NOT NULL DEFAULT '0')")
        db.execSQL("CREATE TABLE sale_payments (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, cashbox TEXT NOT NULL, currency TEXT NOT NULL, amount TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE sale_returns (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, line_id TEXT NOT NULL, quantity TEXT NOT NULL, created_at INTEGER NOT NULL, reason TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_sales_date ON sales(created_at)")
        db.execSQL("CREATE INDEX idx_sales_customer ON sales(customer,phone)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
