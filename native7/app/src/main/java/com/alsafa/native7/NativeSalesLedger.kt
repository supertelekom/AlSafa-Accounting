package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import java.util.UUID
import kotlin.math.abs

/**
 * Local append-only sales ledger. Source imports are immutable. All writes affecting a
 * sale, its stock movements and initial collections share one SQLite transaction.
 * Amounts are in the invoice currency; mixed-currency settlement requires an explicit
 * conversion rate and must not be silently summed.
 */
internal object NativeSalesLedger {
    data class Line(val productId:String,val quantity:Int,val unitPrice:Double)
    data class Sale(val id:String,val currency:String,val total:Double,val cost:Double,val collected:Double,val status:String)
    fun create(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sales (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, currency TEXT NOT NULL, channel TEXT NOT NULL, customer TEXT NOT NULL, phone TEXT NOT NULL, total REAL NOT NULL, cost REAL NOT NULL, status TEXT NOT NULL DEFAULT 'active')")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_lines (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price REAL NOT NULL, unit_cost REAL NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_stock (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, product_id TEXT NOT NULL, delta INTEGER NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_native_sale_stock_product ON native_sale_stock(product_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_collections (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, account TEXT NOT NULL, created_at INTEGER NOT NULL, reason TEXT NOT NULL)")
    }
    fun stockDelta(db:SQLiteDatabase,productId:String):Int {
        create(db)
        db.rawQuery("SELECT COALESCE(SUM(delta),0) FROM native_sale_stock WHERE product_id=?",arrayOf(productId)).use { c ->
            return if(c.moveToFirst()) c.getInt(0) else 0
        }
    }
    fun save(db:SQLiteDatabase,lines:List<Line>,currency:String,channel:String,customer:String,phone:String,
             initialCollection:Double=0.0,account:String="درج المحل"):String {
        require(lines.isNotEmpty()) { "أضف صنفاً إلى الفاتورة" }
        require(currency in setOf("USD","TRY","SYP")) { "عملة غير مدعومة" }
        require(initialCollection.isFinite() && initialCollection>=0) { "قيمة القبض غير صالحة" }
        require(lines.all { it.quantity>0 && it.unitPrice.isFinite() && it.unitPrice>=0 && it.productId.isNotBlank() }) { "بيانات الأصناف غير صالحة" }
        create(db)
        val id=UUID.randomUUID().toString()
        db.beginTransaction()
        try {
            val grouped=lines.groupBy { it.productId }
            var total=0.0
            var cost=0.0
            val now=System.currentTimeMillis()
            for((productId,entries) in grouped) {
                val product=LegacyProductRepository.findById(db,productId) ?: error("المنتج غير موجود")
                val requested=entries.sumOf { it.quantity }
                require(product.stock+stockDelta(db,productId)>=requested) { "المخزون غير كافٍ: "+product.name }
                require(product.currency==currency) { "تحتاج أسعار العملات المختلفة إلى سعر تحويل صريح" }
                require(product.cost.isFinite() && product.cost>=0) { "كلفة المنتج غير صالحة" }
                for(line in entries) {
                    total+=line.unitPrice*line.quantity
                    cost+=product.cost*line.quantity
                    db.execSQL("INSERT INTO native_sale_lines VALUES (?,?,?,?,?,?)",arrayOf<Any>(UUID.randomUUID().toString(),id,productId,line.quantity,line.unitPrice,product.cost))
                }
                db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",arrayOf<Any>(UUID.randomUUID().toString(),id,productId,-requested,"sale",now))
            }
            require(total.isFinite() && cost.isFinite() && initialCollection<=total+0.000001) { "القبض أكبر من قيمة الفاتورة" }
            db.execSQL("INSERT INTO native_sales VALUES (?,?,?,?,?,?,?,?,?)",arrayOf<Any>(id,now,currency,channel,customer,phone,total,cost,"active"))
            if(initialCollection>0) insertCollection(db,id,initialCollection,currency,account,now,"collection")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return id
    }
    private fun insertCollection(db:SQLiteDatabase,id:String,amount:Double,currency:String,account:String,at:Long,reason:String) {
        db.execSQL("INSERT INTO native_sale_collections VALUES (?,?,?,?,?,?,?)",arrayOf<Any>(UUID.randomUUID().toString(),id,amount,currency,account,at,reason))
    }
    fun find(db:SQLiteDatabase,id:String):Sale? {
        create(db)
        db.rawQuery("SELECT s.id,s.currency,s.total,s.cost,s.status,COALESCE((SELECT SUM(c.amount) FROM native_sale_collections c WHERE c.sale_id=s.id),0) FROM native_sales s WHERE s.id=?",arrayOf(id)).use { c ->
            return if(c.moveToFirst()) Sale(c.getString(0),c.getString(1),c.getDouble(2),c.getDouble(3),c.getDouble(5),c.getString(4)) else null
        }
    }
    fun collect(db:SQLiteDatabase,id:String,amount:Double,account:String,at:Long=System.currentTimeMillis()) {
        require(amount.isFinite() && amount>0) { "قيمة القبض غير صالحة" }
        create(db)
        db.beginTransaction()
        try {
            val sale=find(db,id) ?: error("الفاتورة غير موجودة")
            require(sale.status=="active") { "الفاتورة ملغاة" }
            require(sale.collected+amount<=sale.total+0.000001) { "القبض أكبر من المتبقي" }
            insertCollection(db,id,amount,sale.currency,account,at,"collection")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun cancel(db:SQLiteDatabase,id:String,reason:String,at:Long=System.currentTimeMillis()) {
        require(reason.isNotBlank()) { "سبب الإلغاء مطلوب" }
        create(db)
        db.beginTransaction()
        try {
            val sale=find(db,id) ?: error("الفاتورة غير موجودة")
            require(sale.status=="active") { "الفاتورة ملغاة مسبقاً" }
            db.rawQuery("SELECT product_id,SUM(quantity) FROM native_sale_lines WHERE sale_id=? GROUP BY product_id",arrayOf(id)).use { c ->
                while(c.moveToNext()) db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",
                    arrayOf<Any>(UUID.randomUUID().toString(),id,c.getString(0),c.getInt(1),"cancel: "+reason,at))
            }
            db.rawQuery("SELECT amount,currency,account FROM native_sale_collections WHERE sale_id=? AND amount>0",arrayOf(id)).use { c ->
                while(c.moveToNext()) insertCollection(db,id,-c.getDouble(0),c.getString(1),c.getString(2),at,"cancel: "+reason)
            }
            db.execSQL("UPDATE native_sales SET status='cancelled' WHERE id=?",arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /** Profit is earned only on the actual collection day, proportionally to cash received. */
    fun recognizedProfit(db:SQLiteDatabase,from:Long,to:Long):Double {
        create(db)
        db.rawQuery("SELECT COALESCE(SUM(CASE WHEN s.total>0 THEN c.amount*(s.total-s.cost)/s.total ELSE 0 END),0) FROM native_sale_collections c JOIN native_sales s ON s.id=c.sale_id WHERE c.created_at>=? AND c.created_at<?",arrayOf(from.toString(),to.toString())).use { c ->
            return if(c.moveToFirst()) c.getDouble(0) else 0.0
        }
    }
}
