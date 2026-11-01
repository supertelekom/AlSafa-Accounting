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

    /** Inventory and sales use separate SQLite databases; never claim cross-database atomicity. */
    fun save(draft: SaleDraft): String {
        val totals = calculate(draft)
        require(draft.payments.map { it.currency }.distinct().size <= 1)
        val id = UUID.randomUUID().toString()
        val stock = OperationalDatabase(context)
        val sales = SalesDatabase(context)
        try {
            val stockRepo = ProductStockRepository(stock)
            for (line in draft.lines) {
                val available = stockRepo.currentStock(line.productSource, line.productId, line.variantId).toBigDecimal()
                require(available >= line.quantity) { "الكمية غير كافية: ${line.title}" }
            }
            // No posting until inventory and accounting can commit in one durable journal.
            error("الحفظ المالي موقوف حتى ربط دفتر عمليات ذري بين المبيعات والمخزون")
        } finally { stock.close(); sales.close() }
    }

    fun archive(search: String = "", from: Long? = null, to: Long? = null): List<SaleRecord> {
        val db = SalesDatabase(context)
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
