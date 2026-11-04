package com.alsafa.native8.sales

import android.content.Context
import androidx.room.withTransaction
import com.alsafa.native8.data.operational.RoomProductStockRepository
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID

/** Unified Room posting engine. Never opens OperationalDatabase or SalesDatabase. */
class RoomSalesEngine(private val context: Context) {
    private val db = AppDatabase.get(context.applicationContext)
    private val posting get() = db.salesPostingDao()
    private val payments get() = db.legacyPaymentDao()
    private val debts get() = db.legacyReceivablesDao()
    private val stock get() = RoomProductStockRepository(db)
    private val audit get() = db.salesFinancialAuditDao()
    private suspend fun recognize(paymentId:String,saleId:String,at:Long,credit:BigDecimal,net:BigDecimal) {
        if (credit == BigDecimal.ZERO || net == BigDecimal.ZERO) return
        val invoice=requireNotNull(posting.sale(saleId)) { "الفاتورة غير موجودة" }
        // STORE profit belongs to invoice date, never to a receipt.
        // UNKNOWN is deliberately not recognized until source classification.
        if (invoice.channel != SalesChannelClassification.ONLINE) return
        val cost = posting.lines(saleId).fold(BigDecimal.ZERO) { a,l ->
            a + l.quantity.toBigDecimal() * l.unit_cost.toBigDecimal()
        }
        val profit = ProportionalProfit.forCollection(net,cost,credit)
        audit.insertProfit(SaleProfitEvent(paymentId,saleId,at,credit.toPlainString(),
            net.toPlainString(),cost.toPlainString(),profit.toPlainString()))
    }

    /** Reverse only fully verified, unreversed payment profits; no FX inference. */
    private suspend fun reverseAllProfits(saleId:String, refundMovementId:String, at:Long) {
        val events = audit.profits(saleId)
        val existing = audit.reversals(saleId)
        require(existing.isEmpty()) { "توجد تسويات ربح سابقة تحتاج مطابقة" }
        val receipts = payments.payments(saleId)
        require(events.map { it.payment_id }.toSet() ==
            receipts.filter { it.credited_amount?.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 1 }.map { it.id }.toSet()) { "سجل الربح لا يطابق الدفعات" }
        for (event in events) {
            audit.insertReversal(SaleProfitReversal(UUID.randomUUID().toString(),saleId,
                event.payment_id,refundMovementId,event.credited_base,
                event.recognized_profit,at))
        }
    }

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
        require(draft.payments.all { it.invoiceCurrency == "USD" && it.creditedAmount != null }) { "سعر الصرف غير مسجل" }
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
            val paymentId = UUID.randomUUID().toString()
            payments.insertPayment(RoomSalePayment(paymentId, id, payment.cashbox,
                payment.currency, payment.amount.toPlainString(), now,
                payment.invoiceCurrency, payment.fxRate?.toPlainString(), payment.creditedAmount?.toPlainString()))
            payments.insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),
                payment.cashbox, payment.currency, payment.amount.toPlainString(), id, now,
                "قبض فاتورة $number"))
            recognize(paymentId,id,now,payment.creditedAmount!!,totals.net)
        }
        if (totals.due > BigDecimal.ZERO)
            debts.insert(RoomReceivable(id, draft.customerName, draft.customerPhone, totals.due.toPlainString()))
        id
    }

    suspend fun collect(saleId: String, payment: SalePayment): String = db.withTransaction {
        validateFx(payment)
        require(payment.invoiceCurrency == "USD" && payment.creditedAmount != null) { "سعر الصرف غير مسجل" }
        require(payment.amount > BigDecimal.ZERO && payment.cashbox.isNotBlank())
        val debt = requireNotNull(debts.bySaleId(saleId)) { "لا توجد ذمة لهذه الفاتورة" }
        val sale = requireNotNull(posting.sale(saleId)) { "الفاتورة غير موجودة" }
        require(sale.status != "ملغاة") { "لا يمكن قبض فاتورة ملغاة" }
        val balance = debt.balance.toBigDecimal()
        require(balance > BigDecimal.ZERO && payment.creditedAmount!! > BigDecimal.ZERO && payment.creditedAmount!! <= balance) { "الدفعة غير صالحة أو أكبر من المتبقي" }
        val remaining = balance - payment.creditedAmount!!
        check(debts.compareAndSetBalance(saleId, debt.balance, remaining.toPlainString()) == 1)
        check(posting.updatePayment(saleId,
            (sale.paid.toBigDecimal() + payment.creditedAmount!!).toPlainString(),
            if (remaining.compareTo(BigDecimal.ZERO) == 0) "مدفوع" else "آجل") == 1)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        payments.insertPayment(RoomSalePayment(id, saleId, payment.cashbox, payment.currency,
            payment.amount.toPlainString(), now,
            payment.invoiceCurrency, payment.fxRate?.toPlainString(), payment.creditedAmount?.toPlainString()))
        payments.insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),
            payment.cashbox, payment.currency, payment.amount.toPlainString(), saleId, now, "تحصيل آجل"))
        recognize(id,saleId,now,payment.creditedAmount!!,sale.total.toBigDecimal())
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
        val alreadyRefunded = audit.refunds(saleId).fold(BigDecimal.ZERO) { a, entry ->
            a + entry.credited_base.toBigDecimal()
        }
        val receivedBase = payments.payments(saleId).fold(BigDecimal.ZERO) { a, entry ->
            a + (entry.credited_amount?.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }
        require(alreadyRefunded >= BigDecimal.ZERO && alreadyRefunded <= receivedBase) {
            "مجموع الردود السابقة غير متسق"
        }
        val reduceDue = amount.min(due)
        val refund = amount - reduceDue
        require(refund <= receivedBase - alreadyRefunded) {
            "المبلغ المرتجع يتجاوز القبض المتاح بعد الردود السابقة"
        }
        if (refund > BigDecimal.ZERO) {
            require(refundCashbox.isNotBlank() && refundCurrency.isNotBlank()) {
                "المرتجع يتطلب تحديد صندوق وعملة رد المبلغ"
            }
            val originals = payments.payments(saleId)
            require(originals.isNotEmpty() && originals.all {
                it.invoice_currency == "USD" && it.fx_rate != null && it.credited_amount != null
            }) { "لا يمكن رد دفعات دون أسعار صرف تاريخية محفوظة" }
            require(sale.paid.toBigDecimal() >= refund) { "الرد يتجاوز المقبوض المتبقي" }
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
            val originals = payments.payments(saleId)
            val prior = audit.refunds(saleId).groupBy { it.payment_id }
            val allocations = RefundAllocationMath.allocate(refund, originals.map { payment ->
                RefundAllocationMath.Source(payment.id, payment.credited_amount!!.toBigDecimal(),
                    prior[payment.id].orEmpty().fold(BigDecimal.ZERO) { a, entry ->
                        a + entry.credited_base.toBigDecimal()
                    })
            })
            val fx = ExchangeRateSettings(context)
                .rate(refundCurrency, "USD")
            val profits = audit.profits(saleId).associateBy { it.payment_id }
            val reversals = audit.reversals(saleId).groupBy { it.payment_id }
            for (part in allocations) {
                val payment = originals.first { it.id == part.paymentId }
                val cash = RefundAllocationMath.convertExact(part.baseAmount, fx)
                val profit = requireNotNull(profits[payment.id]) { "سجل ربح الدفعة مفقود" }
                val oldBase = reversals[payment.id].orEmpty().fold(BigDecimal.ZERO) { a, e ->
                    a + e.reversed_base.toBigDecimal()
                }
                val oldProfit = reversals[payment.id].orEmpty().fold(BigDecimal.ZERO) { a, e ->
                    a + e.reversed_profit.toBigDecimal()
                }
                require(oldBase + part.baseAmount <= profit.credited_base.toBigDecimal()) {
                    "عكس الربح يتجاوز الدفعة"
                }
                val targetProfit = if ((oldBase + part.baseAmount).compareTo(
                    profit.credited_base.toBigDecimal()) == 0) profit.recognized_profit.toBigDecimal()
                else profit.recognized_profit.toBigDecimal()
                    .multiply(oldBase + part.baseAmount)
                    .divide(profit.credited_base.toBigDecimal(), 2, java.math.RoundingMode.HALF_UP)
                val reverseProfit = targetProfit - oldProfit
                val movementId = UUID.randomUUID().toString()
                payments.insertCashboxMovement(RoomCashboxMovement(movementId,
                    refundCashbox, refundCurrency, cash.negate().toPlainString(), saleId, now,
                    "رد مبلغ مرتجع: $reason"))
                audit.insertRefund(SaleRefundAllocation(UUID.randomUUID().toString(), saleId,
                    payment.id, movementId, refundCashbox, refundCurrency,
                    cash.toPlainString(), part.baseAmount.toPlainString(), fx.toPlainString(), now))
                audit.insertReversal(SaleProfitReversal(UUID.randomUUID().toString(), saleId,
                    payment.id, movementId, part.baseAmount.toPlainString(),
                    reverseProfit.toPlainString(), now))
            }
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
        val receipts = payments.payments(saleId)
        require(receipts.isNotEmpty() || movements.isEmpty()) { "سجل الدفعات مفقود" }
        // Only independently verifiable payment snapshots may be reversed.
        require(receipts.all { it.invoice_currency == "USD" && it.fx_rate != null &&
            it.credited_amount != null }) { "دفعات بلا سعر صرف تاريخي؛ الإلغاء موقوف" }
        val refunds = audit.refunds(saleId)
        val reversals = audit.reversals(saleId)
        val profits = audit.profits(saleId).associateBy { it.payment_id }
        require(receipts.map { it.id }.toSet() == profits.keys) {
            "سجل الأرباح غير مكتمل"
        }
        val movementsById = movements.associateBy { it.id }
        require(movementsById.size == movements.size)
        require(refunds.map { it.movement_id }.distinct().size == refunds.size)
        require(refunds.all { entry ->
            val movement = movementsById[entry.movement_id]
            movement != null && movement.cashbox == entry.cashbox &&
                movement.currency == entry.currency &&
                movement.amount.toBigDecimal().compareTo(entry.refunded_amount.toBigDecimal().negate()) == 0 &&
                entry.credited_base.toBigDecimal() > BigDecimal.ZERO &&
                entry.exchange_rate_to_base.toBigDecimal() > BigDecimal.ZERO &&
                entry.refunded_amount.toBigDecimal().multiply(entry.exchange_rate_to_base.toBigDecimal())
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .compareTo(entry.credited_base.toBigDecimal()) == 0
        }) { "سجل الرد لا يطابق حركات الصناديق" }
        val positive = movements.filter { it.amount.toBigDecimal() > BigDecimal.ZERO }
            .groupBy { it.cashbox to it.currency }.mapValues { (_, entries) ->
                entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.amount.toBigDecimal() }
            }
        val expected = receipts.groupBy { it.cashbox to it.currency }
            .mapValues { (_, entries) -> entries.fold(BigDecimal.ZERO) { sum, entry ->
                sum + entry.amount.toBigDecimal()
            } }
        require(positive.keys == expected.keys &&
            positive.all { (key, value) -> value.compareTo(expected[key]!!) == 0 } &&
            movements.filter { it.amount.toBigDecimal() < BigDecimal.ZERO }.size == refunds.size) {
            "حركات القبض أو الرد غير موثقة بالكامل"
        }
        val allocatedByPayment = refunds.groupBy { it.payment_id }
        val reversedByPayment = reversals.groupBy { it.payment_id }
        val remaining = receipts.map { payment ->
            val credited = payment.credited_amount!!.toBigDecimal()
            val allocated = allocatedByPayment[payment.id].orEmpty().fold(BigDecimal.ZERO) { sum, entry ->
                sum + entry.credited_base.toBigDecimal()
            }
            val reversedBase = reversedByPayment[payment.id].orEmpty().fold(BigDecimal.ZERO) { sum, entry ->
                sum + entry.reversed_base.toBigDecimal()
            }
            require(allocated >= BigDecimal.ZERO && allocated <= credited &&
                reversedBase.compareTo(allocated) == 0) { "تخصيص الرد وعكس الربح غير متطابقين" }
            payment to (credited - allocated)
        }
        val paidBase = remaining.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.second }
        require(paidBase.compareTo(sale.paid.toBigDecimal()) == 0) {
            "المتبقي المقبوض لا يطابق سجل التسويات"
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
        for ((payment, outstanding) in remaining) {
            if (outstanding <= BigDecimal.ZERO) continue
            val fx = payment.fx_rate!!.toBigDecimal()
            val originalCash = RefundAllocationMath.convertExact(outstanding, fx)
            val nowMovementId = UUID.randomUUID().toString()
            payments.insertCashboxMovement(RoomCashboxMovement(nowMovementId,
                payment.cashbox, payment.currency, originalCash.negate().toPlainString(),
                saleId, now, "رد المتبقي عند إلغاء فاتورة ${sale.number}"))
            audit.insertRefund(SaleRefundAllocation(UUID.randomUUID().toString(),saleId,
                payment.id,nowMovementId,payment.cashbox,payment.currency,
                originalCash.toPlainString(),outstanding.toPlainString(),fx.toPlainString(),now))
            val event = requireNotNull(profits[payment.id])
            val alreadyReversed = reversedByPayment[payment.id].orEmpty()
                .fold(BigDecimal.ZERO) { sum, entry -> sum + entry.reversed_profit.toBigDecimal() }
            audit.insertReversal(SaleProfitReversal(UUID.randomUUID().toString(),saleId,
                payment.id,nowMovementId,outstanding.toPlainString(),
                (event.recognized_profit.toBigDecimal()-alreadyReversed).toPlainString(),now))
        }
        check(posting.updatePayment(saleId, "0", "ملغاة") == 1)
        saleId
    }

    /**
     * Narrow, auditable correction: only uncollected invoices without returns.
     * Financially posted invoices must use compensating entries, not in-place edits.
     */
    suspend fun correctUnpaidLine(saleId:String,lineId:String,newQuantity:BigDecimal,
        newUnitPrice:BigDecimal):Unit = db.withTransaction {
        require(newQuantity > BigDecimal.ZERO && newUnitPrice >= BigDecimal.ZERO)
        val sale = requireNotNull(posting.sale(saleId))
        require(sale.status != "ملغاة" && sale.paid.toBigDecimal().compareTo(BigDecimal.ZERO) == 0)
        require(payments.payments(saleId).isEmpty() && payments.cashboxMovements(saleId).isEmpty()) {
            "تعديل فاتورة مقبوضة يتطلب قيود تسوية مستقلة"
        }
        val lines = posting.lines(saleId)
        require(lines.isNotEmpty() && lines.all { it.returned_qty.toBigDecimal().compareTo(BigDecimal.ZERO) == 0 }) {
            "تعديل فاتورة ذات مرتجع يتطلب قيود تسوية مستقلة"
        }
        val line = requireNotNull(lines.firstOrNull { it.id == lineId })
        val delta = newQuantity - line.quantity.toBigDecimal()
        if (delta > BigDecimal.ZERO) {
            require(stock.currentStock(line.source,line.product_id,line.variant_id).toBigDecimal() >= delta) {
                "الكمية غير كافية"
            }
        }
        val originalGross = lines.fold(BigDecimal.ZERO) { a,l ->
            a + l.quantity.toBigDecimal() * l.unit_price.toBigDecimal()
        }
        val newGross = originalGross - line.quantity.toBigDecimal() * line.unit_price.toBigDecimal() +
            newQuantity * newUnitPrice
        val discount = sale.discount.toBigDecimal()
        require(newGross >= discount) { "الخصم يتجاوز الإجمالي الجديد" }
        val newNet = newGross - discount
        val debt = requireNotNull(debts.bySaleId(saleId)) { "ذمة الفاتورة غير موجودة" }
        require(debt.balance.toBigDecimal().compareTo(sale.total.toBigDecimal()) == 0) {
            "الذمة لا تطابق الفاتورة"
        }
        check(posting.correctUnpaidLine(saleId,lineId,newQuantity.toPlainString(),newUnitPrice.toPlainString()) == 1)
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            stock.adjust(line.source,line.product_id,line.variant_id,delta.negate().toPlainString(),
                "تصحيح كمية فاتورة ${sale.number}")
        }
        check(posting.correctUnpaidTotal(saleId,newNet.toPlainString(),sale.discount) == 1)
        check(debts.compareAndSetBalance(saleId,debt.balance,newNet.toPlainString()) == 1)
    }

    /** Correct customer metadata without rewriting posted financial history. */
    suspend fun correctCustomer(saleId: String, customer: String, phone: String): Unit = db.withTransaction {
        val sale = requireNotNull(posting.sale(saleId)) { "الفاتورة غير موجودة" }
        require(sale.status != "ملغاة") { "لا يمكن تعديل فاتورة ملغاة" }
        require(customer.isNotBlank() || phone.isNotBlank()) { "بيانات العميل مطلوبة" }
        check(posting.updateCustomer(saleId, customer.trim(), phone.trim()) == 1)
        if (debts.bySaleId(saleId) != null) {
            check(debts.updateCustomer(saleId, customer.trim(), phone.trim()) == 1)
        }
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
