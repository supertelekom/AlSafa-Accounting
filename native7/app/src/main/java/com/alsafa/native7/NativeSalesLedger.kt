package com.alsafa.native7

import android.database.sqlite.SQLiteDatabase
import java.util.UUID

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
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_returns (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, refund REAL NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_change (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, amount_in_invoice_currency REAL NOT NULL, invoice_currency TEXT NOT NULL, payout REAL NOT NULL, payout_currency TEXT NOT NULL, payout_account TEXT NOT NULL, tender_account TEXT NOT NULL, exchange_rate REAL NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_mixed (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, account TEXT NOT NULL, invoice_equivalent REAL NOT NULL, exchange_rate REAL NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_collections (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, account TEXT NOT NULL, created_at INTEGER NOT NULL, reason TEXT NOT NULL)")
    }
    fun stockDelta(db:SQLiteDatabase,productId:String):Int {
        create(db)
        db.rawQuery("SELECT COALESCE(SUM(delta),0) FROM native_sale_stock WHERE product_id=?",arrayOf(productId)).use { c ->
            return if(c.moveToFirst()) c.getInt(0) else 0
        }
    }
    fun save(db:SQLiteDatabase,lines:List<Line>,currency:String,channel:String,customer:String,phone:String,
             initialCollection:Double=0.0,account:String="درج المحل",changeAccount:String=account,changeCurrency:String=currency):String {
        require(changeCurrency==currency) { "إرجاع الباقي بعملة أخرى يحتاج سعر تحويل صريح" }
        create(db)
        db.beginTransaction()
        try {
            val id=saveInternal(db,lines,currency,channel,customer,phone,initialCollection,account,changeAccount)
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    private fun saveInternal(db:SQLiteDatabase,lines:List<Line>,currency:String,channel:String,
        customer:String,phone:String,initialCollection:Double,account:String,changeAccount:String=account):String {
        require(lines.isNotEmpty()) { "أضف صنفاً إلى الفاتورة" }
        require(currency in setOf("USD","TRY","SYP")) { "عملة غير مدعومة" }
        require(initialCollection.isFinite() && initialCollection>=0) { "قيمة القبض غير صالحة" }
        require(lines.all { it.quantity>0 && it.unitPrice.isFinite() && it.unitPrice>=0 && it.productId.isNotBlank() }) { "بيانات الأصناف غير صالحة" }
        val id=UUID.randomUUID().toString()
        val grouped=lines.groupBy { it.productId }
        var total=0.0
        var cost=0.0
        val now=System.currentTimeMillis()
        for((productId,entries) in grouped) {
            val product=LegacyProductRepository.findById(db,productId) ?: error("المنتج غير موجود")
            val requested=entries.sumOf { it.quantity }
            require(product.id==productId) { "تعذر تحديد المنتج" }
            require(product.stock>=requested) { "المخزون غير كافٍ: "+product.name }
            require(product.currency==currency) { "تحتاج أسعار العملات المختلفة إلى سعر تحويل صريح" }
            require(product.cost.isFinite() && product.cost>=0) { "كلفة المنتج غير صالحة" }
            for(line in entries) {
                total+=line.unitPrice*line.quantity
                cost+=product.cost*line.quantity
                db.execSQL("INSERT INTO native_sale_lines VALUES (?,?,?,?,?,?)",arrayOf<Any>(UUID.randomUUID().toString(),id,productId,line.quantity,line.unitPrice,product.cost))
            }
            db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",arrayOf<Any>(UUID.randomUUID().toString(),id,productId,-requested,"sale",now))
        }
        require(total.isFinite() && cost.isFinite() && total>=0) { "قيمة الفاتورة غير صالحة" }
        require(total>0 || initialCollection==0.0) { "لا يمكن قبض مبلغ لفاتورة قيمتها صفر" }
        db.execSQL("INSERT INTO native_sales VALUES (?,?,?,?,?,?,?,?,?)",arrayOf<Any>(id,now,currency,channel,customer,phone,total,cost,"active"))
        if(initialCollection>0) insertCollection(db,id,initialCollection,currency,account,now,"collection")
        val change=(initialCollection-total).coerceAtLeast(0.0)
        if(change>0.000001) insertCollection(db,id,-change,currency,changeAccount,now,"change")
        return id
    }
    /**
     * Record customer tender and change as one transaction. The change payout is
     * tracked separately, with an explicit exchange rate for another currency.
     */
    /**
     * One outer transaction for sale, stock, tender and change. SQLite transaction
     * nesting is avoided by delegating to a transaction-aware internal save.
     */
    fun saveWithChange(db:SQLiteDatabase,lines:List<Line>,currency:String,channel:String,
        customer:String,phone:String,tendered:Double,tenderAccount:String,
        changeCurrency:String,changeAccount:String,changeRate:Double):String {
        require(tendered.isFinite() && tendered>=0) { "المبلغ المدفوع غير صالح" }
        require(changeCurrency in setOf("USD","TRY","SYP")) { "عملة الباقي غير صالحة" }
        require(changeRate.isFinite() && changeRate>0) { "سعر الصرف غير صالح" }
        require(tenderAccount.isNotBlank() && changeAccount.isNotBlank()) { "حدد خزنة القبض والباقي" }
        if(currency==changeCurrency) require(kotlin.math.abs(changeRate-1.0)<0.000001) { "سعر الصرف لنفس العملة يجب أن يكون 1" }
        create(db)
        db.beginTransaction()
        try {
            val id=saveInternal(db,lines,currency,channel,customer,phone,0.0,tenderAccount)
            val sale=find(db,id) ?: error("تعذر قراءة الفاتورة")
            val collected=tendered.coerceAtMost(sale.total)
            val change=(tendered-sale.total).coerceAtLeast(0.0)
            val now=System.currentTimeMillis()
            if(collected>0) insertCollection(db,id,collected,currency,tenderAccount,now,"collection")
            if(change>0) {
                val payout=change*changeRate
                require(payout.isFinite()) { "قيمة الباقي غير صالحة" }
                db.execSQL("INSERT INTO native_sale_change VALUES (?,?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any>(UUID.randomUUID().toString(),id,change,currency,payout,changeCurrency,changeAccount,tenderAccount,changeRate,now))
            }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    data class Tender(val amount:Double,val currency:String,val account:String,val rate:Double)
    fun saveMixed(db:SQLiteDatabase,lines:List<Line>,currency:String,channel:String,
        customer:String,phone:String,tenders:List<Tender>):String {
        require(tenders.isNotEmpty()) { "حدد مبالغ القبض" }
        require(tenders.all { it.amount.isFinite() && it.amount>0 && it.rate.isFinite() && it.rate>0 &&
            it.currency in setOf("USD","TRY","SYP") && it.account.isNotBlank() &&
            (it.currency!=currency || kotlin.math.abs(it.rate-1.0)<0.000001) }) { "المبالغ أو أسعار الصرف غير صالحة" }
        create(db)
        db.beginTransaction()
        try {
            val id=saveInternal(db,lines,currency,channel,customer,phone,0.0,tenders.first().account)
            val sale=find(db,id) ?: error("الفاتورة غير موجودة")
            val total=tenders.sumOf { it.amount*it.rate }
            require(total.isFinite() && total<=sale.total+0.000001) { "الدفع المختلط أكبر من قيمة الفاتورة؛ استخدم إرجاع الباقي" }
            val now=System.currentTimeMillis()
            tenders.forEach { t ->
                db.execSQL("INSERT INTO native_sale_mixed VALUES (?,?,?,?,?,?,?,?)",
                    arrayOf<Any>(UUID.randomUUID().toString(),id,t.amount,t.currency,t.account,t.amount*t.rate,t.rate,now))
            }
            if(total>0) insertCollection(db,id,total,currency,"mixed",now,"mixed_collection")
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
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
            val refunded=db.rawQuery("SELECT COALESCE(SUM(refund),0) FROM native_sale_returns WHERE sale_id=?",arrayOf(id)).use { c -> if(c.moveToFirst()) c.getDouble(0) else 0.0 }
            require(sale.collected+amount<=sale.total-refunded+0.000001) { "القبض أكبر من المتبقي" }
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
            db.rawQuery("SELECT product_id,-SUM(delta) FROM native_sale_stock WHERE sale_id=? GROUP BY product_id HAVING SUM(delta)<0",arrayOf(id)).use { c ->
                while(c.moveToNext()) db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",
                    arrayOf<Any>(UUID.randomUUID().toString(),id,c.getString(0),c.getInt(1),"cancel: "+reason,at))
            }
            db.rawQuery("SELECT account,SUM(amount) FROM native_sale_collections WHERE sale_id=? GROUP BY account HAVING SUM(amount)>0",arrayOf(id)).use { c ->
                while(c.moveToNext()) insertCollection(db,id,-c.getDouble(1),sale.currency,c.getString(0),at,"cancel: "+reason)
            }
            db.rawQuery("SELECT amount_in_invoice_currency,invoice_currency,payout,payout_currency,payout_account,tender_account,exchange_rate FROM native_sale_change WHERE sale_id=? AND amount_in_invoice_currency>0",arrayOf(id)).use { c ->
                while(c.moveToNext()) {
                    db.execSQL("INSERT INTO native_sale_change VALUES (?,?,?,?,?,?,?,?,?,?)",
                        arrayOf<Any>(UUID.randomUUID().toString(),id,-c.getDouble(0),c.getString(1),
                            -c.getDouble(2),c.getString(3),c.getString(4),c.getString(5),c.getDouble(6),at))
                }
            }
            db.execSQL("UPDATE native_sales SET status='cancelled' WHERE id=?",arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /**
     * A partial return restores only the returned quantity and creates an auditable
     * refund entry. A return cannot exceed quantities still sold on this invoice.
     * Refunds are recorded against the same account and currency as the sale.
     */
    fun returnLine(db:SQLiteDatabase,saleId:String,productId:String,quantity:Int,reason:String,
                   account:String="درج المحل",at:Long=System.currentTimeMillis()) {
        require(quantity>0 && reason.isNotBlank()) { "الكمية وسبب المرتجع مطلوبان" }
        create(db)
        db.beginTransaction()
        try {
            val sale=find(db,saleId) ?: error("الفاتورة غير موجودة")
            require(sale.status=="active") { "الفاتورة ملغاة" }
            val sold=db.rawQuery("SELECT COALESCE(SUM(quantity),0),COALESCE(SUM(quantity*unit_price),0) FROM native_sale_lines WHERE sale_id=? AND product_id=?",
                arrayOf(saleId,productId)).use { c -> c.moveToFirst();Pair(c.getInt(0),c.getDouble(1)) }
            val returned=db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM native_sale_returns WHERE sale_id=? AND product_id=?",
                arrayOf(saleId,productId)).use { c -> c.moveToFirst();c.getInt(0) }
            require(sold.first>0 && returned+quantity<=sold.first) { "كمية المرتجع تتجاوز الكمية المباعة" }
            val refund=sold.second*quantity/sold.first
            val existingRefund=db.rawQuery("SELECT COALESCE(SUM(refund),0) FROM native_sale_returns WHERE sale_id=?",
                arrayOf(saleId)).use { c -> c.moveToFirst();c.getDouble(0) }
            require(existingRefund+refund<=sale.total+0.000001) { "قيمة المرتجعات تتجاوز الفاتورة" }
            db.execSQL("INSERT INTO native_sale_returns VALUES (?,?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),saleId,productId,quantity,refund,reason,at))
            db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),saleId,productId,quantity,"return: "+reason,at))
            val refundable=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM native_sale_collections WHERE sale_id=? AND account=?",arrayOf(saleId,account)).use { c -> c.moveToFirst();c.getDouble(0).coerceAtLeast(0.0) }
            val cashRefund=refund.coerceAtMost(refundable)
            if(cashRefund>0) insertCollection(db,saleId,-cashRefund,sale.currency,account,at,"return: "+reason)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun remaining(db:SQLiteDatabase,id:String):Double {
        val sale=find(db,id) ?: error("الفاتورة غير موجودة")
        val returns=db.rawQuery("SELECT COALESCE(SUM(refund),0) FROM native_sale_returns WHERE sale_id=?",arrayOf(id)).use { c -> c.moveToFirst();c.getDouble(0) }
        return (sale.total-returns-sale.collected).coerceAtLeast(0.0)
    }
    fun accountMovement(db:SQLiteDatabase,account:String,currency:String,from:Long,to:Long):Double {
        create(db)
        db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM native_sale_collections WHERE account=? AND currency=? AND created_at>=? AND created_at<?",arrayOf(account,currency,from.toString(),to.toString())).use { c ->
            c.moveToFirst()
            val collected=c.getDouble(0)
            val tenderExcess=db.rawQuery("SELECT COALESCE(SUM(amount_in_invoice_currency),0) FROM native_sale_change WHERE tender_account=? AND invoice_currency=? AND created_at>=? AND created_at<?",
                arrayOf(account,currency,from.toString(),to.toString())).use { x -> x.moveToFirst();x.getDouble(0) }
            val paidChange=db.rawQuery("SELECT COALESCE(SUM(payout),0) FROM native_sale_change WHERE payout_account=? AND payout_currency=? AND created_at>=? AND created_at<?",
                arrayOf(account,currency,from.toString(),to.toString())).use { x -> x.moveToFirst();x.getDouble(0) }
            return collected+tenderExcess-paidChange
        }
    }
    fun profitByCurrency(db:SQLiteDatabase,from:Long,to:Long):Map<String,Double> {
        create(db)
        val result=linkedMapOf<String,Double>()
        db.rawQuery("SELECT s.currency,COALESCE(SUM(CASE WHEN s.total>0 THEN c.amount*(s.total-s.cost)/s.total ELSE 0 END),0) FROM native_sale_collections c JOIN native_sales s ON s.id=c.sale_id WHERE c.created_at>=? AND c.created_at<? GROUP BY s.currency",arrayOf(from.toString(),to.toString())).use { cursor ->
            while(cursor.moveToNext()) result[cursor.getString(0)]=cursor.getDouble(1)
        }
        return result
    }
    /** Profit is earned only on the actual collection day, proportionally to cash received. */
    fun recognizedProfit(db:SQLiteDatabase,from:Long,to:Long):Double {
        create(db)
        db.rawQuery("SELECT COALESCE(SUM(CASE WHEN s.total>0 THEN c.amount*(s.total-s.cost)/s.total ELSE 0 END),0) FROM native_sale_collections c JOIN native_sales s ON s.id=c.sale_id WHERE c.created_at>=? AND c.created_at<?",arrayOf(from.toString(),to.toString())).use { c ->
            return if(c.moveToFirst()) c.getDouble(0) else 0.0
        }
    }
}
