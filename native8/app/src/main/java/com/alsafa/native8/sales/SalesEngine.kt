package com.alsafa.native8.sales

import android.content.Context
import android.content.ContentValues
import com.alsafa.native8.data.operational.OperationalDatabase
import com.alsafa.native8.data.operational.ProductStockRepository
import java.math.BigDecimal
import java.util.UUID

/** Shared financial calculation and persistence for full and quick sales. */
class SalesEngine(private val context: Context) {
    fun calculate(draft: SaleDraft): SaleTotals {
        require(draft.lines.isNotEmpty()) { "الفاتورة فارغة" }
        require(draft.lines.all { it.quantity > BigDecimal.ZERO && it.unitPrice >= BigDecimal.ZERO && it.discount >= BigDecimal.ZERO })
        val gross = draft.lines.fold(BigDecimal.ZERO) { a, l -> a + l.quantity * l.unitPrice }
        val discount = draft.discount + draft.lines.fold(BigDecimal.ZERO) { a, l -> a + l.discount }
        require(discount >= BigDecimal.ZERO && discount <= gross) { "الخصم غير صالح" }
        val net = gross - discount
        val currencies = draft.payments.map { it.currency }.distinct()
        require(currencies.size <= 1) { "القبض بعملات مختلفة يحتاج أسعار صرف معتمدة" }
        require(draft.payments.all { it.amount > BigDecimal.ZERO && it.cashbox.isNotBlank() })
        val paid = draft.payments.fold(BigDecimal.ZERO) { a, p -> a + p.amount }
        return SaleTotals(gross, discount, net, paid, (net - paid).max(BigDecimal.ZERO), (paid - net).max(BigDecimal.ZERO))
    }

    /** All financial and inventory writes share the operational SQLite connection. */
    fun save(draft: SaleDraft): String {
        val totals = calculate(draft)
        require(totals.change == BigDecimal.ZERO) { "صرف الباقي يحتاج تسجيل حركة الصرف أولاً" }
        val operational = OperationalDatabase(context)
        val db = operational.writableDatabase
        val saleId = UUID.randomUUID().toString()
        db.beginTransaction()
        try {
            val stock = ProductStockRepository(operational)
            val required = draft.lines.groupBy { Triple(it.productSource, it.productId, it.variantId) }
            for ((key, lines) in required) {
                val available = stock.currentStock(key.first,key.second,key.third).toBigDecimal()
                val count = lines.fold(BigDecimal.ZERO) { a,l -> a+l.quantity }
                require(count <= available) { "الكمية غير كافية: ${lines.first().title}" }
            }
            val number = db.rawQuery("SELECT COALESCE(MAX(number),0)+1 FROM sales",null).use { it.moveToFirst(); it.getLong(0) }
            val now = System.currentTimeMillis()
            db.insertOrThrow("sales",null,ContentValues().apply {
                put("id",saleId);put("number",number);put("created_at",now)
                put("customer",draft.customerName);put("phone",draft.customerPhone)
                put("total",totals.net.toPlainString());put("discount",totals.discount.toPlainString())
                put("paid",totals.paid.toPlainString());put("status",if(totals.due>BigDecimal.ZERO)"آجل" else "مدفوع")
            })
            draft.lines.forEach { line ->
                db.insertOrThrow("sale_lines",null,ContentValues().apply {
                    put("id",UUID.randomUUID().toString());put("sale_id",saleId)
                    put("source",line.productSource);put("product_id",line.productId)
                    put("variant_id",line.variantId);put("title",line.title)
                    put("quantity",line.quantity.toPlainString());put("unit_price",line.unitPrice.toPlainString())
                    put("unit_cost",line.unitCost.toPlainString());put("discount",line.discount.toPlainString())
                })
                stock.adjust(line.productSource,line.productId,line.variantId,line.quantity.negate().toPlainString(),"بيع فاتورة $number")
            }
            draft.payments.forEach { payment ->
                db.insertOrThrow("sale_payments",null,ContentValues().apply {
                    put("id",UUID.randomUUID().toString());put("sale_id",saleId)
                    put("cashbox",payment.cashbox);put("currency",payment.currency)
                    put("amount",payment.amount.toPlainString());put("created_at",now)
                })
                db.insertOrThrow("cashbox_movements",null,ContentValues().apply {
                    put("id",UUID.randomUUID().toString());put("cashbox",payment.cashbox)
                    put("currency",payment.currency);put("amount",payment.amount.toPlainString())
                    put("sale_id",saleId);put("created_at",now);put("note","قبض فاتورة $number")
                })
            }
            if(totals.due>BigDecimal.ZERO) {
                require(draft.customerName.isNotBlank() || draft.customerPhone.isNotBlank()) { "العميل مطلوب للبيع الآجل" }
                db.insertOrThrow("customer_receivables",null,ContentValues().apply {
                    put("sale_id",saleId);put("customer",draft.customerName)
                    put("phone",draft.customerPhone);put("balance",totals.due.toPlainString())
                })
            }
            db.setTransactionSuccessful()
            return saleId
        } finally { db.endTransaction(); operational.close() }
    }

    fun collect(saleId: String, payment: SalePayment): String {
        require(payment.amount > BigDecimal.ZERO && payment.cashbox.isNotBlank())
        val operational = OperationalDatabase(context)
        val db = operational.writableDatabase
        db.beginTransaction()
        try {
            val balance = db.rawQuery("SELECT balance FROM customer_receivables WHERE sale_id=?",arrayOf(saleId)).use {
                require(it.moveToFirst()) { "لا توجد ذمة لهذه الفاتورة" }
                it.getString(0).toBigDecimal()
            }
            require(payment.amount <= balance) { "الدفعة أكبر من المتبقي" }
            val newBalance = balance-payment.amount
            db.execSQL("UPDATE customer_receivables SET balance=? WHERE sale_id=?",arrayOf(newBalance.toPlainString(),saleId))
            db.execSQL("UPDATE sales SET paid=CAST(paid AS REAL)+?, status=? WHERE id=?",arrayOf(payment.amount.toPlainString(),if(newBalance==BigDecimal.ZERO)"مدفوع" else "آجل",saleId))
            val id=UUID.randomUUID().toString()
            val now=System.currentTimeMillis()
            db.insertOrThrow("sale_payments",null,ContentValues().apply {
                put("id",id);put("sale_id",saleId);put("cashbox",payment.cashbox)
                put("currency",payment.currency);put("amount",payment.amount.toPlainString());put("created_at",now)
            })
            db.insertOrThrow("cashbox_movements",null,ContentValues().apply {
                put("id",UUID.randomUUID().toString());put("cashbox",payment.cashbox)
                put("currency",payment.currency);put("amount",payment.amount.toPlainString())
                put("sale_id",saleId);put("created_at",now);put("note","تحصيل آجل")
            })
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction();operational.close() }
    }

    fun returnLine(saleId: String, lineId: String, quantity: BigDecimal, reason: String): String {
        require(quantity>BigDecimal.ZERO && reason.isNotBlank())
        val operational=OperationalDatabase(context)
        val db=operational.writableDatabase
        db.beginTransaction()
        try {
            val row=db.rawQuery("SELECT source,product_id,variant_id,quantity,returned_qty,unit_price,discount FROM sale_lines WHERE id=? AND sale_id=?",arrayOf(lineId,saleId)).use { c ->
                require(c.moveToFirst()) { "البند غير موجود" }
                listOf(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6))
            }
            val original=row[3]!!.toBigDecimal()
            val returned=row[4]!!.toBigDecimal()
            require(quantity<=original-returned) { "كمية المرتجع تتجاوز المباع" }
            val amount=(row[5]!!.toBigDecimal()*quantity)-(row[6]!!.toBigDecimal()*quantity/original)
            require(amount>=BigDecimal.ZERO)
            val due=db.rawQuery("SELECT balance FROM customer_receivables WHERE sale_id=?",arrayOf(saleId)).use { if(it.moveToFirst()) it.getString(0).toBigDecimal() else BigDecimal.ZERO }
            // Any cash refund requires explicit cashbox and currency choice; do not silently debit cash.
            require(amount<=due) { "المرتجع يتطلب رد مبلغ مقبوض؛ اختر الصندوق والعملة أولاً" }
            ProductStockRepository(operational).adjust(row[0]!!,row[1]!!,row[2],quantity.toPlainString(),"مرتجع فاتورة $saleId: $reason")
            db.execSQL("UPDATE sale_lines SET returned_qty=? WHERE id=?",arrayOf((returned+quantity).toPlainString(),lineId))
            db.execSQL("UPDATE customer_receivables SET balance=? WHERE sale_id=?",arrayOf((due-amount).toPlainString(),saleId))
            val id=UUID.randomUUID().toString()
            db.insertOrThrow("sale_returns",null,ContentValues().apply {
                put("id",id);put("sale_id",saleId);put("line_id",lineId)
                put("quantity",quantity.toPlainString());put("created_at",System.currentTimeMillis());put("reason",reason)
            })
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction();operational.close() }
    }

    fun archive(search: String = "", from: Long? = null, to: Long? = null): List<SaleRecord> {
        val db = OperationalDatabase(context)
        try {
            val rows = mutableListOf<SaleRecord>()
            val q = "%${search.trim()}%"
            val first = (from ?: 0L).toString()
            val last = (to ?: Long.MAX_VALUE).toString()
            db.readableDatabase.rawQuery("SELECT id,number,created_at,customer,phone,total,paid,status FROM sales WHERE (customer LIKE ? OR phone LIKE ? OR CAST(number AS TEXT) LIKE ?) AND created_at >= ? AND created_at <= ? ORDER BY created_at DESC LIMIT 100", arrayOf(q,q,q,first,last)).use { c ->
                while (c.moveToNext()) rows.add(SaleRecord(c.getString(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7)))
            }
            return rows
        } finally { db.close() }
    }
}
