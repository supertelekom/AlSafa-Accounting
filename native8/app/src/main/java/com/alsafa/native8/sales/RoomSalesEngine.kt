package com.alsafa.native8.sales

import android.content.Context
import androidx.room.withTransaction
import com.alsafa.native8.data.operational.RoomProductStockRepository
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID

/** Unified Room posting engine. Never opens OperationalDatabase or SalesDatabase. */
class RoomSalesEngine(context: Context) {
    private val db = AppDatabase.get(context.applicationContext)
    private val posting get() = db.salesPostingDao()
    private val payments get() = db.legacyPaymentDao()
    private val debts get() = db.legacyReceivablesDao()
    private val stock get() = RoomProductStockRepository(db)

    private fun validateFx(payment: SalePayment) {
        val fields = listOf(payment.invoiceCurrency, payment.fxRate, payment.creditedAmount)
        require(fields.all { it == null } || fields.all { it != null }) {
            "يجب حفظ عملة الفاتورة وسعر الصرف والقيمة المعتمدة معاً"
        }
        if (payment.invoiceCurrency != null) {
            PaymentExchangeSnapshot(payment.currency, payment.invoiceCurrency,
                payment.fxRate!!.toPlainString(), payment.amount.toPlainString(),
                payment.creditedAmount!!.toPlainString()).validate()
        }
    }

    suspend fun save(draft: SaleDraft, totals: SaleTotals): String = db.withTransaction {
        require(draft.lines.isNotEmpty()) { "لا يمكن حفظ فاتورة دون أصناف" }
        require(draft.lines.all { it.quantity > BigDecimal.ZERO && it.unitPrice >= BigDecimal.ZERO && it.unitCost >= BigDecimal.ZERO && it.discount >= BigDecimal.ZERO }) { "بيانات الأصناف غير صالحة" }
        draft.payments.forEach(::validateFx)
        require(draft.payments.all { it.amount > BigDecimal.ZERO && it.cashbox.isNotBlank() && it.currency in setOf("USD", "TRY", "SYP") }) { "بيانات الدفعات غير صالحة" }
        require(totals.net >= BigDecimal.ZERO && totals.paid >= BigDecimal.ZERO && totals.due >= BigDecimal.ZERO) { "إجمالي الفاتورة غير صالح" }
        require(totals.change.compareTo(BigDecimal.ZERO) == 0) { "صرف الباقي يحتاج تسجيل حركة الصرف أولاً" }
        if (totals.due > BigDecimal.ZERO)
            require(draft.customerName.isNotBlank() || draft.customerPhone.isNotBlank()) { "العميل مطلوب للبيع الآجل" }
        val grouped = draft.lines.groupBy { Triple(it.productSource, it.productId, it.variantId) }
        for ((key, lines) in grouped) {
            val available = stock.currentStock(key.first, key.second, key.third).toBigDecimal()
            val required = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.quantity }
            require(required <= available) { "الكمية غير كافية: ${lines.first().title}" }
        }
        val number = maxOf(posting.maxNewNumber(), db.legacySaleDao().maxLegacyInvoiceNumber()) + 1
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        posting.insertSale(RoomSale(id, number, now, draft.customerName, draft.customerPhone,
            totals.net.toPlainString(), totals.discount.toPlainString(), totals.paid.toPlainString(),
            if (totals.due > BigDecimal.ZERO) "آجل" else "مدفوع"))
        for (line in draft.lines) {
            posting.insertLine(RoomSaleLine(UUID.randomUUID().toString(), id, line.productSource,
                line.productId, line.variantId, line.title, line.quantity.toPlainString(),
                line.unitPrice.toPlainString(), line.unitCost.toPlainString(), line.discount.toPlainString()))
            stock.adjust(line.productSource, line.productId, line.variantId,
                line.quantity.negate().toPlainString(), "بيع فاتورة $number")
        }
        for (payment in draft.payments) {
            payments.insertPayment(RoomSalePayment(UUID.randomUUID().toString(), id, payment.cashbox,
                payment.currency, payment.amount.toPlainString(), now,
                payment.invoiceCurrency, payment.fxRate?.toPlainString(), payment.creditedAmount?.toPlainString()))
            payments.insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),
                payment.cashbox, payment.currency, payment.amount.toPlainString(), id, now,
                "قبض فاتورة $number"))
        }
        if (totals.due > BigDecimal.ZERO)
            debts.insert(RoomReceivable(id, draft.customerName, draft.customerPhone, totals.due.toPlainString()))
        id
    }

    suspend fun collect(saleId: String, payment: SalePayment): String = db.withTransaction {
        validateFx(payment)
        require(payment.amount > BigDecimal.ZERO && payment.cashbox.isNotBlank())
        val debt = requireNotNull(debts.bySaleId(saleId)) { "لا توجد ذمة لهذه الفاتورة" }
        val sale = requireNotNull(posting.sale(saleId)) { "الفاتورة غير موجودة" }
        require(sale.status != "ملغاة") { "لا يمكن قبض فاتورة ملغاة" }
        val balance = debt.balance.toBigDecimal()
        require(payment.amount <= balance) { "الدفعة أكبر من المتبقي" }
        val remaining = balance - payment.amount
        check(debts.compareAndSetBalance(saleId, debt.balance, remaining.toPlainString()) == 1)
        check(posting.updatePayment(saleId,
            (sale.paid.toBigDecimal() + payment.amount).toPlainString(),
            if (remaining.compareTo(BigDecimal.ZERO) == 0) "مدفوع" else "آجل") == 1)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        payments.insertPayment(RoomSalePayment(id, saleId, payment.cashbox, payment.currency,
            payment.amount.toPlainString(), now,
            payment.invoiceCurrency, payment.fxRate?.toPlainString(), payment.creditedAmount?.toPlainString()))
        payments.insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),
            payment.cashbox, payment.currency, payment.amount.toPlainString(), saleId, now, "تحصيل آجل"))
        id
    }

    suspend fun returnLine(
        saleId: String, lineId: String, quantity: BigDecimal, reason: String,
        refundCashbox: String = "", refundCurrency: String = ""
    ): String = db.withTransaction {
        require(quantity > BigDecimal.ZERO && reason.isNotBlank())
        val sale = requireNotNull(posting.sale(saleId)) { "الفاتورة غير موجودة" }
        require(sale.status != "ملغاة") { "الفاتورة ملغاة" }
        val line = requireNotNull(posting.line(saleId, lineId)) { "البند غير موجود" }
        val original = line.quantity.toBigDecimal()
        val returned = line.returned_qty.toBigDecimal()
        require(quantity <= original - returned) { "كمية المرتجع تتجاوز المباع" }
        val allLines = posting.lines(saleId)
        val gross = allLines.fold(BigDecimal.ZERO) { sum, item ->
            sum + item.quantity.toBigDecimal() * item.unit_price.toBigDecimal()
        }
        require(gross > BigDecimal.ZERO) { "تعذر حساب قيمة المرتجع" }
        // Distribute the invoice's total discount proportionally to the returned gross.
        val amount = (line.unit_price.toBigDecimal() * quantity)
            .multiply(sale.total.toBigDecimal())
            .divide(gross, 2, java.math.RoundingMode.HALF_UP)
        require(amount >= BigDecimal.ZERO) { "قيمة المرتجع غير صالحة" }
        val debt = debts.bySaleId(saleId)
        val due = debt?.balance?.toBigDecimal() ?: BigDecimal.ZERO
        val reduceDue = amount.min(due)
        val refund = amount - reduceDue
        if (refund > BigDecimal.ZERO) {
            require(refundCashbox.isNotBlank() && refundCurrency.isNotBlank()) {
                "المرتجع يتطلب تحديد صندوق وعملة رد المبلغ"
            }
            val existingCurrencies = payments.payments(saleId).map { it.currency }.distinct()
            require(existingCurrencies.size == 1 && existingCurrencies.single() == refundCurrency) {
                "عملة رد المبلغ يجب أن تطابق عملة القبض المسجلة"
            }
            require(sale.paid.toBigDecimal() >= refund) { "المبلغ المسترد يتجاوز المقبوض" }
        }
        stock.adjust(line.source, line.product_id, line.variant_id, quantity.toPlainString(),
            "مرتجع فاتورة $saleId: $reason")
        check(posting.updateReturned(lineId, (returned + quantity).toPlainString()) == 1)
        if (debt != null && reduceDue > BigDecimal.ZERO) {
            check(debts.compareAndSetBalance(saleId, debt.balance, (due - reduceDue).toPlainString()) == 1)
        }
        if (refund > BigDecimal.ZERO) {
            check(posting.updatePayment(saleId, (sale.paid.toBigDecimal() - refund).toPlainString(),
                if (due - reduceDue > BigDecimal.ZERO) "آجل" else "مدفوع") == 1)
            val now = System.currentTimeMillis()
            payments.insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),
                refundCashbox, refundCurrency, refund.negate().toPlainString(), saleId, now,
                "رد مبلغ مرتجع: $reason"))
        }
        val id = UUID.randomUUID().toString()
        posting.insertReturn(RoomSaleReturn(id, saleId, lineId, quantity.toPlainString(),
            System.currentTimeMillis(), reason))
        id
    }

    /** Cancel only invoices without any collected funds. Paid/mixed invoices require a verified refund workflow. */
    suspend fun cancelUnpaid(saleId: String): String = db.withTransaction {
        val sale = requireNotNull(posting.sale(saleId)) { "الإلغاء متاح للفواتير الجديدة فقط" }
        require(sale.status != "ملغاة") { "الفاتورة ملغاة مسبقاً" }
        val received = payments.cashboxMovements(saleId).filter { it.amount.toBigDecimal() > BigDecimal.ZERO }
        require(received.isEmpty() && sale.paid.toBigDecimal().compareTo(BigDecimal.ZERO) == 0) {
            "الفاتورة المقبوضة تتطلب تسوية رد المبلغ قبل الإلغاء"
        }
        val lines = posting.lines(saleId)
        require(lines.isNotEmpty()) { "الفاتورة بلا بنود" }
        for (line in lines) {
            val remaining = line.quantity.toBigDecimal() - line.returned_qty.toBigDecimal()
            require(remaining >= BigDecimal.ZERO) { "كمية مرتجع غير متسقة" }
            if (remaining > BigDecimal.ZERO) {
                stock.adjust(line.source, line.product_id, line.variant_id, remaining.toPlainString(),
                    "إلغاء المتبقي من فاتورة ${sale.number}")
                check(posting.updateReturned(line.id, line.quantity) == 1)
                posting.insertReturn(RoomSaleReturn(UUID.randomUUID().toString(), saleId, line.id,
                    remaining.toPlainString(), System.currentTimeMillis(), "إلغاء الفاتورة"))
            }
        }
        val debt = debts.bySaleId(saleId)
        if (debt != null) {
            check(debts.compareAndSetBalance(saleId, debt.balance, "0") == 1)
        }
        check(posting.updatePayment(saleId, "0", "ملغاة") == 1)
        saleId
    }

    /**
     * Cancel a collected invoice by reversing its remaining cash movements in their
     * original cashboxes and currencies. No exchange-rate inference is performed.
     * A prior return refund is already a negative movement and is not refunded again.
     * All stock, debt and cash changes are atomic; a second cancellation is rejected.
     */
    suspend fun cancelWithRefund(saleId: String): String = db.withTransaction {
        val sale = requireNotNull(posting.sale(saleId)) { "الإلغاء متاح للفواتير الجديدة فقط" }
        require(sale.status != "ملغاة") { "الفاتورة ملغاة مسبقاً" }
        val movements = payments.cashboxMovements(saleId)
        val currencies = movements.map { it.currency }.distinct()
        require(currencies.size <= 1) {
            "إلغاء قبض متعدد العملات يتطلب مراجعة أسعار الصرف المسجلة؛ لم تُسجل في هذه الفاتورة"
        }
        val grouped = movements.groupBy { it.cashbox to it.currency }
            .mapValues { (_, entries) -> entries.fold(BigDecimal.ZERO) { a, entry ->
                a + entry.amount.toBigDecimal()
            } }
        require(grouped.values.all { it >= BigDecimal.ZERO }) { "حركات رد المبالغ غير متسقة" }
        val outstanding = grouped.values.fold(BigDecimal.ZERO, BigDecimal::add)
        require(outstanding.compareTo(sale.paid.toBigDecimal()) == 0) {
            "المقبوض في الفاتورة لا يطابق صافي حركات الصناديق؛ الإلغاء موقوف لحماية البيانات"
        }
        val lines = posting.lines(saleId)
        require(lines.isNotEmpty()) { "الفاتورة بلا بنود" }
        val now = System.currentTimeMillis()
        for (line in lines) {
            val remaining = line.quantity.toBigDecimal() - line.returned_qty.toBigDecimal()
            require(remaining >= BigDecimal.ZERO) { "كمية مرتجع غير متسقة" }
            if (remaining > BigDecimal.ZERO) {
                stock.adjust(line.source, line.product_id, line.variant_id, remaining.toPlainString(),
                    "إلغاء المتبقي من فاتورة ${sale.number}")
                check(posting.updateReturned(line.id, line.quantity) == 1)
                posting.insertReturn(RoomSaleReturn(UUID.randomUUID().toString(), saleId, line.id,
                    remaining.toPlainString(), now, "إلغاء الفاتورة"))
            }
        }
        val debt = debts.bySaleId(saleId)
        if (debt != null) {
            check(debts.compareAndSetBalance(saleId, debt.balance, "0") == 1)
        }
        for ((key, amount) in grouped) {
            if (amount > BigDecimal.ZERO) {
                payments.insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),
                    key.first, key.second, amount.negate().toPlainString(), saleId, now,
                    "رد قبض عند إلغاء فاتورة ${sale.number}"))
            }
        }
        check(posting.updatePayment(saleId, "0", "ملغاة") == 1)
        saleId
    }

    suspend fun lines(saleId: String): List<SalesEngine.InvoiceLine> {
        val current = posting.lines(saleId)
        if (current.isNotEmpty()) return current.map {
            SalesEngine.InvoiceLine(it.id, it.title, it.quantity, it.returned_qty, it.unit_price)
        }
        return db.legacySaleDao().legacyLines(saleId).map {
            val obj = org.json.JSONObject(it.original_json)
            SalesEngine.InvoiceLine(it.id, obj.optString("name"), obj.optString("qty", "0"),
                "0", obj.optString("price", "0"))
        }
    }

    suspend fun archive(search: String, from: Long?, to: Long?): List<SaleRecord> =
        db.legacySaleDao().unifiedArchive(search.trim(), from ?: 0L, to ?: Long.MAX_VALUE).map {
            SaleRecord(it.id, it.number, it.created_at, it.customer, it.phone, it.total, it.paid, it.status)
        }
}
