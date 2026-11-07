package com.alsafa.native8.sales

import android.content.Context
import androidx.room.withTransaction
import com.alsafa.native8.data.operational.RoomProductStockRepository
import java.math.BigDecimal
import java.math.MathContext
import java.util.UUID

/** Unified Room posting engine. Never opens OperationalDatabase or SalesDatabase. */
class RoomSalesEngine(private val context: Context, private val db:AppDatabase=AppDatabase.get(context.applicationContext)) {
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
        if (!SalesChannelClassification.recognizeAtCollection(invoice.channel)) return
        val meta=org.json.JSONObject(invoice.metadata_json)
        val cost = posting.lines(saleId).fold(BigDecimal.ZERO) { a,l ->
            val quantity=l.quantity.toBigDecimal()-(if(meta.has("exchangeEffectiveTotal"))l.returned_qty.toBigDecimal() else BigDecimal.ZERO)
            a + quantity * l.unit_cost.toBigDecimal()
        }
        val effective=meta.optString("exchangeEffectiveTotal").toBigDecimalOrNull() ?: net
        val profit = ProportionalProfit.forCollection(effective,cost,credit)
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

    suspend fun save(draft: SaleDraft, totals: SaleTotals, requestId:String?=null): String = db.withTransaction {
        if(requestId!=null && posting.sale(requestId)!=null) return@withTransaction requestId
        SalesChannelClassification.requireVerified(draft.channel)
        require(draft.lines.isNotEmpty()) { "لا يمكن حفظ فاتورة دون أصناف" }
        require(draft.lines.all { it.quantity > BigDecimal.ZERO && it.unitPrice >= BigDecimal.ZERO && it.unitCost >= BigDecimal.ZERO && it.discount >= BigDecimal.ZERO }) { "بيانات الأصناف غير صالحة" }
        draft.payments.forEach(::validateFx)
        require(draft.payments.all { it.invoiceCurrency == "USD" && it.creditedAmount != null }) { "سعر الصرف غير مسجل" }
        require(draft.payments.all { it.amount > BigDecimal.ZERO && it.cashbox.isNotBlank() && it.currency in setOf("USD", "TRY", "SYP") }) { "بيانات الدفعات غير صالحة" }
        require(totals.net >= BigDecimal.ZERO && totals.paid >= BigDecimal.ZERO && totals.due >= BigDecimal.ZERO) { "إجمالي الفاتورة غير صالح" }
        val checked=SalesEngine(context).calculate(draft)
        require(listOf(checked.net to totals.net,checked.paid to totals.paid,checked.due to totals.due,checked.change to totals.change).all{it.first.compareTo(it.second)==0}){"تغير إجمالي الفاتورة"}
        val plan=if(totals.change>BigDecimal.ZERO)SaleChangePlan.build(totals.net,draft.payments,draft.changeCashbox,draft.changeCurrency,draft.changeRate)
          else SaleChangePlan.Plan(draft.payments,emptyList(),null)
        require(SaleCurrency.normalize(draft.invoiceCurrency)==draft.invoiceCurrency && draft.invoiceRate>BigDecimal.ZERO){"عملة الفاتورة غير صالحة"}
        require(draft.invoiceCurrency!="USD" || draft.invoiceRate.compareTo(BigDecimal.ONE)==0){"سعر الدولار غير صالح"}
        require(draft.warrantyMonths in setOf(0,3,6,12,24)){"مدة الضمان غير صالحة"}
        if (totals.due > BigDecimal.ZERO)
            require(draft.customerName.isNotBlank() || draft.customerPhone.isNotBlank()) { "العميل مطلوب للبيع الآجل" }
        val grouped = draft.lines.groupBy { Triple(it.productSource, it.productId, it.variantId) }
        for ((key, lines) in grouped) {
            val available = stock.currentStock(key.first, key.second, key.third).toBigDecimal()
            val required = lines.fold(BigDecimal.ZERO) { sum, line -> sum + line.quantity }
            require(required <= available) { "الكمية غير كافية: ${lines.first().title}" }
        }
        val number = maxOf(posting.maxNewNumber(), db.legacySaleDao().maxLegacyInvoiceNumber(),db.maintenanceDao().maxRepairInvoice()) + 1
        val id = requestId ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val invoiceAt=draft.invoiceAt ?: now
        val metadata=org.json.JSONObject().put("warrantyMonths",draft.warrantyMonths).put("notes",draft.notes).put("currency",draft.invoiceCurrency).put("rateToUsd",draft.invoiceRate.toPlainString())
        fun receiptJson(p:SalePayment)=org.json.JSONObject().put("cashbox",p.cashbox).put("currency",p.currency).put("amount",p.amount.toPlainString()).put("rate",p.fxRate.toString()).put("base",p.creditedAmount.toString())
        if(plan.change!=null){metadata.put("tender",org.json.JSONArray().apply{draft.payments.forEach{put(receiptJson(it))}});metadata.put("change",receiptJson(plan.change));metadata.put("changeMovementSource","sale-change:$id")}
        draft.senderShipping?.let { shipping ->
            require(draft.channel==SalesChannelClassification.ONLINE) { "أجرة الشحن لمبيع الأونلاين فقط" }
            require(shipping.amount.signum()>0) { "أدخل أجرة الشحن المدفوعة" }
            require(shipping.cashbox in setOf("drawer","shamcash")) { "اختر الدرج أو شام كاش للشحن" }
            val expenseId="shipping-"+id
            val stamp=java.time.Instant.ofEpochMilli(invoiceAt).atZone(java.time.ZoneId.systemDefault())
            val rate=ExchangeRateSettings(context).rate(shipping.currency,"USD")
            val expense=OperationalExpenseEntity(expenseId,stamp.toLocalDate().toString(),stamp.toLocalTime().withNano(0).toString(),
                "أجرة شحن","فاتورة أونلاين رقم $number",shipping.currency,shipping.amount.toPlainString(),shipping.cashbox,
                shipping.cashbox,rate.toPlainString(),invoiceAt,"financial-"+expenseId)
            OperationalExpensePartnerRepository(db).saveExpense(expense)
            metadata.put("shippingExpenseId",expenseId).put("shippingAmount",expense.amount)
                .put("shippingCurrency",expense.currency).put("shippingCashbox",shipping.cashbox)
        }
        posting.insertSale(RoomSale(id, number, invoiceAt, draft.customerName, draft.customerPhone,
            totals.net.toPlainString(), totals.discount.toPlainString(), totals.paid.min(totals.net).toPlainString(),
            if (totals.due > BigDecimal.ZERO) "آجل" else "مدفوع",draft.channel,metadata.toString()))
        for (line in draft.lines) {
            posting.insertLine(RoomSaleLine(UUID.randomUUID().toString(), id, line.productSource,
                line.productId, line.variantId, line.title, line.quantity.toPlainString(),
                line.unitPrice.toPlainString(), line.unitCost.toPlainString(), line.discount.toPlainString()))
            stock.adjust(line.productSource, line.productId, line.variantId,
                line.quantity.negate().toPlainString(), "بيع فاتورة $number")
        }
        if (draft.channel == SalesChannelClassification.STORE) {
            val cost = draft.lines.fold(BigDecimal.ZERO) { sum,line ->
                sum + line.quantity * line.unitCost
            }
            db.storeSaleProfitDao().insert(StoreSaleProfitEvent(
                id, invoiceAt, totals.net.toPlainString(), cost.toPlainString(),
                StoreSaleProfitMath.calculate(totals.net,cost).toPlainString()
            ))
        }
        for (payment in plan.receipts) {
            val paymentId = UUID.randomUUID().toString()
            payments.insertPayment(RoomSalePayment(paymentId, id, payment.cashbox,
                payment.currency, payment.amount.toPlainString(), now,
                payment.invoiceCurrency, payment.fxRate?.toPlainString(), payment.creditedAmount?.toPlainString()))
            payments.insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),
                payment.cashbox, payment.currency, payment.amount.toPlainString(), id, now,
                "قبض فاتورة $number"))
            recognize(paymentId,id,now,payment.creditedAmount!!,totals.net)
        }
        // Excess tender and returned change form a separate balanced exchange.
        // Keep it out of invoice refund allocations: the client has already received this money.
        if(plan.change!=null){
            val source="sale-change:$id"
            for(extra in plan.excess)payments.insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),extra.cashbox,extra.currency,extra.amount.toPlainString(),source,now,"زيادة قبض مرتبطة بفاتورة $number"))
            val change=plan.change
            payments.insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),change.cashbox,change.currency,change.amount.negate().toPlainString(),source,now,"باقي للعميل — فاتورة $number"))
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
        reconcileExchangeProfit(saleId)
        id
    }

    suspend fun returnLine(
        saleId: String, lineId: String, quantity: BigDecimal, reason: String,
        refundCashbox: String = "", refundCurrency: String = "", refundRate:BigDecimal?=null
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

        // Distribute the invoice's total discount proportionally to the returned gross.
        val amount = returnValue(sale,line,quantity,allLines)
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
        if(sale.channel==SalesChannelClassification.STORE) {
            db.storeSaleProfitDao().insertAdjustment(StoreProfitAdjustment(
                UUID.randomUUID().toString(),saleId,System.currentTimeMillis(),
                StoreProfitAdjustmentMath.returnedProfitDelta(amount,line.unit_cost.toBigDecimal(),quantity).toPlainString(),"مرتجع: "+reason))
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
            val fx = refundRate ?: ExchangeRateSettings(context).rate(refundCurrency, "USD")
            require(fx>BigDecimal.ZERO){"سعر الصرف غير صالح"}
            val profits = audit.profits(saleId).associateBy { it.payment_id }
            val reversals = audit.reversals(saleId).groupBy { it.payment_id }
            for (part in allocations) {
                val payment = originals.first { it.id == part.paymentId }
                val cash = RefundAllocationMath.convertExact(part.baseAmount, fx)
                val profit = if(sale.channel==SalesChannelClassification.STORE) null else requireNotNull(profits[payment.id]) { "سجل ربح الدفعة مفقود" }
                val reverseProfit=if(profit==null) BigDecimal.ZERO else run {
                    val oldBase = reversals[payment.id].orEmpty().fold(BigDecimal.ZERO) { a, e ->
                        a + e.reversed_base.toBigDecimal()
                    }
                    val oldProfit = reversals[payment.id].orEmpty().filterNot{it.refund_movement_id.startsWith("exchange-profit:")}.fold(BigDecimal.ZERO) { a, e ->
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
                    reverseProfit
                }
                val movementId = UUID.randomUUID().toString()
                payments.insertCashboxMovement(RoomCashboxMovement(movementId,
                    refundCashbox, refundCurrency, cash.negate().toPlainString(), saleId, now,
                    "رد مبلغ مرتجع: $reason"))
                audit.insertRefund(SaleRefundAllocation(UUID.randomUUID().toString(), saleId,
                    payment.id, movementId, refundCashbox, refundCurrency,
                    cash.toPlainString(), part.baseAmount.toPlainString(), fx.toPlainString(), now))
                if(profit!=null) audit.insertReversal(SaleProfitReversal(UUID.randomUUID().toString(), saleId,
                    payment.id, movementId, part.baseAmount.toPlainString(),
                    reverseProfit.toPlainString(), now))
            }
        }
        val id = UUID.randomUUID().toString()
        posting.insertReturn(RoomSaleReturn(id, saleId, lineId, quantity.toPlainString(),
            System.currentTimeMillis(), reason))
        val meta=org.json.JSONObject(sale.metadata_json)
        if(meta.has("exchangeEffectiveTotal")){
            meta.put("exchangeEffectiveTotal",(meta.getString("exchangeEffectiveTotal").toBigDecimal()-amount).max(BigDecimal.ZERO).toPlainString())
            check(posting.updateExchange(saleId,sale.total,meta.toString())==1)
            reconcileExchangeProfit(saleId)
        }
        // Keep the debt-only return status consistent too.
        val latest=requireNotNull(posting.sale(saleId))
        check(posting.updatePayment(saleId,latest.paid,if((debts.bySaleId(saleId)?.balance?.toBigDecimal() ?: BigDecimal.ZERO)>BigDecimal.ZERO)"آجل" else "مدفوع")==1)
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
        if(sale.channel==SalesChannelClassification.STORE) {
            val original=requireNotNull(db.storeSaleProfitDao().bySale(saleId))
            val previous=db.storeSaleProfitDao().adjustments(saleId).fold(BigDecimal.ZERO) { a,e ->
                a+e.profit_delta_usd.toBigDecimal()
            }
            db.storeSaleProfitDao().insertAdjustment(StoreProfitAdjustment(
                UUID.randomUUID().toString(),saleId,System.currentTimeMillis(),
                StoreProfitAdjustmentMath.cancellationDelta(original.profit_usd.toBigDecimal(),listOf(previous)).toPlainString(),"إلغاء فاتورة"))
        }
        check(posting.updatePayment(saleId, "0", "ملغاة") == 1)
        reconcileExchangeProfit(saleId)
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
        require(sale.channel==SalesChannelClassification.STORE || receipts.map { it.id }.toSet() == profits.keys) {
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
                (sale.channel==SalesChannelClassification.STORE || reversedBase.compareTo(allocated) == 0)) { "تخصيص الرد وعكس الربح غير متطابقين" }
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
            val event = if(sale.channel==SalesChannelClassification.STORE) null else requireNotNull(profits[payment.id])
            val alreadyReversed = reversedByPayment[payment.id].orEmpty()
                .fold(BigDecimal.ZERO) { sum, entry -> sum + entry.reversed_profit.toBigDecimal() }
            if(event!=null) audit.insertReversal(SaleProfitReversal(UUID.randomUUID().toString(),saleId,
                payment.id,nowMovementId,outstanding.toPlainString(),
                (event.recognized_profit.toBigDecimal()-alreadyReversed).toPlainString(),now))
        }
        if(sale.channel==SalesChannelClassification.STORE) {
            val original=requireNotNull(db.storeSaleProfitDao().bySale(saleId)) { "قيد ربح المحل مفقود" }
            val adjustments=db.storeSaleProfitDao().adjustments(saleId).map { it.profit_delta_usd.toBigDecimal() }
            db.storeSaleProfitDao().insertAdjustment(StoreProfitAdjustment(
                UUID.randomUUID().toString(),saleId,now,
                StoreProfitAdjustmentMath.cancellationDelta(original.profit_usd.toBigDecimal(),adjustments).toPlainString(),
                "إلغاء فاتورة مقبوضة"))
        }
        check(posting.updatePayment(saleId, "0", "ملغاة") == 1)
        reconcileExchangeProfit(saleId)
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
        if(sale.channel==SalesChannelClassification.STORE) {
            val original=requireNotNull(db.storeSaleProfitDao().bySale(saleId)) { "قيد ربح المحل مفقود" }
            val originalCost=lines.fold(BigDecimal.ZERO) { a,l ->
                a+l.quantity.toBigDecimal()*l.unit_cost.toBigDecimal()
            }
            val newCost=originalCost+delta*line.unit_cost.toBigDecimal()
            val oldProfit=sale.total.toBigDecimal()-originalCost
            val newProfit=newNet-newCost
            val prior=db.storeSaleProfitDao().adjustments(saleId).map { it.profit_delta_usd.toBigDecimal() }
            require(prior.isEmpty()) { "تصحيح فاتورة سبق تعديل ربحها يتطلب مطابقة" }
            require(original.profit_usd.toBigDecimal().compareTo(oldProfit)==0) { "قيد الربح الأصلي غير متطابق" }
            db.storeSaleProfitDao().insertAdjustment(StoreProfitAdjustment(
                UUID.randomUUID().toString(),saleId,System.currentTimeMillis(),
                (newProfit-oldProfit).toPlainString(),"تصحيح فاتورة غير مقبوضة"))
        }
    }

    private fun returnValue(sale:RoomSale,line:RoomSaleLine,quantity:BigDecimal,lines:List<RoomSaleLine>):BigDecimal {
        val frozen=org.json.JSONObject(sale.metadata_json).optJSONObject("exchangeUnitCredits")?.optString(line.id)?.toBigDecimalOrNull()
        if(frozen!=null)return (frozen*quantity).setScale(2,java.math.RoundingMode.HALF_UP)
        val gross=lines.fold(BigDecimal.ZERO){a,l->a+l.quantity.toBigDecimal()*l.unit_price.toBigDecimal()}
        if(gross.signum()==0)return BigDecimal.ZERO
        return (line.unit_price.toBigDecimal()*quantity*sale.total.toBigDecimal()).divide(gross,2,java.math.RoundingMode.HALF_UP)
    }

    /** Zero-base profit adjustments change economic recognition, never payment/refund allocations. */
    private suspend fun reconcileExchangeProfit(saleId:String){
        val sale=requireNotNull(posting.sale(saleId));val meta=org.json.JSONObject(sale.metadata_json)
        if(sale.channel!=SalesChannelClassification.ONLINE || !meta.has("exchangeEffectiveTotal"))return
        val events=audit.profits(saleId);if(events.isEmpty())return
        val current=events.fold(BigDecimal.ZERO){a,e->a+e.recognized_profit.toBigDecimal()}-audit.reversals(saleId).fold(BigDecimal.ZERO){a,e->a+e.reversed_profit.toBigDecimal()}
        val cost=posting.lines(saleId).fold(BigDecimal.ZERO){a,l->a+(l.quantity.toBigDecimal()-l.returned_qty.toBigDecimal())*l.unit_cost.toBigDecimal()}
        val target=if(sale.status=="ملغاة")BigDecimal.ZERO else SaleExchangeMath.recognized(meta.getString("exchangeEffectiveTotal").toBigDecimal(),cost,sale.paid.toBigDecimal())
        if(current.compareTo(target)!=0)audit.insertReversal(SaleProfitReversal(UUID.randomUUID().toString(),saleId,events.first().payment_id,"exchange-profit:"+UUID.randomUUID(),"0",(current-target).toPlainString(),System.currentTimeMillis()))
    }

    data class ExchangeRequest(val requestId:String,val saleId:String,val lineId:String,val quantity:BigDecimal,
        val replacement:SaleLine,val cashbox:String,val currency:String,val rate:BigDecimal,val collect:BigDecimal,val reason:String)
    data class ExchangePreview(val fingerprint:String,val returned:BigDecimal,val replacement:BigDecimal,val settlement:SaleExchangeMath.Settlement)
    suspend fun previewExchange(q:ExchangeRequest):ExchangePreview=db.withTransaction { exchangePreview(q) }
    suspend fun quoteExchange(q:ExchangeRequest):ExchangePreview=db.withTransaction { exchangePreview(q.copy(collect=BigDecimal.ZERO),quoteOnly=true) }
    private suspend fun exchangePreview(q:ExchangeRequest,quoteOnly:Boolean=false):ExchangePreview {
        val sale=requireNotNull(posting.sale(q.saleId)){"استبدال المنتجات متاح للفواتير التشغيلية الجديدة"}
        require(sale.status!="ملغاة"){"الفاتورة ملغاة"};SalesChannelClassification.requireVerified(sale.channel ?: "")
        require(q.requestId.isNotBlank()&&(quoteOnly||q.reason.isNotBlank())){"اكتب سبب الاستبدال"}
        require(UnifiedCashboxBalance.canonical(q.cashbox)!=null && q.currency in setOf("USD","TRY","SYP") && q.rate>BigDecimal.ZERO){"اختر الصندوق والعملة"}
        require(q.cashbox!="shamcash" || q.currency!="TRY"){"شام كاش لا يدعم التركية"}
        require(ExchangeRateSettings(context).rate(q.currency,"USD").compareTo(q.rate)==0){"تغير سعر الصرف، أعد المعاينة"}
        val lines=posting.lines(q.saleId);val line=requireNotNull(lines.find{it.id==q.lineId}){"البند غير موجود"}
        require(q.quantity>BigDecimal.ZERO && q.quantity<=line.quantity.toBigDecimal()-line.returned_qty.toBigDecimal()){"الكمية أكبر من المتاح للاستبدال"}
        val r=q.replacement
        require(r.quantity>BigDecimal.ZERO&&r.unitPrice>=BigDecimal.ZERO&&r.unitCost>=BigDecimal.ZERO&&r.discount.signum()==0){"بيانات البديل غير صالحة"}
        val same=r.productSource==line.source&&r.productId==line.product_id&&r.variantId==line.variant_id
        require(stock.currentStock(r.productSource,r.productId,r.variantId).toBigDecimal()+(if(same)q.quantity else BigDecimal.ZERO)>=r.quantity){"مخزون البديل غير كافٍ"}
        val due=debts.bySaleId(q.saleId)?.balance?.toBigDecimal() ?: BigDecimal.ZERO
        val current=org.json.JSONObject(sale.metadata_json).optString("exchangeEffectiveTotal").toBigDecimalOrNull() ?: (sale.paid.toBigDecimal()+due)
        val credit=returnValue(sale,line,q.quantity,lines);val replacement=(r.unitPrice*r.quantity).setScale(2,java.math.RoundingMode.HALF_UP)
        val collect=(q.collect*q.rate).setScale(2,java.math.RoundingMode.HALF_UP)
        val settlement=SaleExchangeMath.settle(current,sale.paid.toBigDecimal(),credit,replacement,collect)
        if(settlement.refund>BigDecimal.ZERO)RefundAllocationMath.convertExact(settlement.refund,q.rate)
        if(!quoteOnly && settlement.due>BigDecimal.ZERO)require(sale.customer.isNotBlank()||sale.phone.isNotBlank()){"بيانات العميل مطلوبة للفرق الآجل"}
        val fingerprint=sale.toString()+lines.toString()+due.toPlainString()
        return ExchangePreview(fingerprint,credit,replacement,settlement)
    }
    /** Outer Room transaction encloses stock, return, replacement, debt, cash, profit and document. */
    suspend fun exchange(q:ExchangeRequest,expected:String):String=db.withTransaction {
        var sale=requireNotNull(posting.sale(q.saleId));val meta=org.json.JSONObject(sale.metadata_json)
        val history=meta.optJSONArray("nativeExchanges") ?: org.json.JSONArray()
        for(i in 0 until history.length())if(history.getJSONObject(i).getString("id")==q.requestId)return@withTransaction q.requestId
        val p=exchangePreview(q);require(p.fingerprint==expected){"تغيرت الفاتورة، أعد المعاينة"}
        val lines=posting.lines(q.saleId);val source=lines.first{it.id==q.lineId};val r=q.replacement
        val credits=meta.optJSONObject("exchangeUnitCredits") ?: org.json.JSONObject().apply {
            val gross=lines.fold(BigDecimal.ZERO){a,l->a+l.quantity.toBigDecimal()*l.unit_price.toBigDecimal()}
            lines.forEach{l->put(l.id,if(gross.signum()==0)"0" else (l.unit_price.toBigDecimal()*sale.total.toBigDecimal()).divide(gross,16,java.math.RoundingMode.HALF_UP).toPlainString())}
        }
        val replacementId=UUID.randomUUID().toString();credits.put(replacementId,r.unitPrice.toPlainString())
        val due=debts.bySaleId(q.saleId);val before=sale.paid.toBigDecimal()+(due?.balance?.toBigDecimal() ?: BigDecimal.ZERO)
        meta.put("exchangeUnitCredits",credits).put("exchangeEffectiveTotal",(before+p.replacement).toPlainString())
        check(posting.updateExchange(q.saleId,(sale.total.toBigDecimal()+p.replacement).toPlainString(),meta.toString())==1)
        val increased=(due?.balance?.toBigDecimal() ?: BigDecimal.ZERO)+p.replacement
        if(due==null)debts.insert(RoomReceivable(q.saleId,sale.customer,sale.phone,increased.toPlainString()))
        else check(debts.compareAndSetBalance(q.saleId,due.balance,increased.toPlainString())==1)
        // Restock first permits exchanging into another variant of the same product atomically.
        val returnId=returnLine(q.saleId,q.lineId,q.quantity,q.reason,q.cashbox,q.currency,q.rate)
        stock.adjust(r.productSource,r.productId,r.variantId,r.quantity.negate().toPlainString(),"بديل استبدال فاتورة ${sale.number}")
        posting.insertLine(RoomSaleLine(replacementId,q.saleId,r.productSource,r.productId,r.variantId,r.title,r.quantity.toPlainString(),r.unitPrice.toPlainString(),r.unitCost.toPlainString(),"0"))
        if(sale.channel==SalesChannelClassification.STORE)db.storeSaleProfitDao().insertAdjustment(StoreProfitAdjustment(UUID.randomUUID().toString(),q.saleId,System.currentTimeMillis(),(p.replacement-r.quantity*r.unitCost).toPlainString(),"بديل استبدال: ${q.reason}"))
        if(q.collect>BigDecimal.ZERO)collect(q.saleId,SalePayment(q.cashbox,q.currency,q.collect,"USD",q.rate,(q.collect*q.rate).setScale(2,java.math.RoundingMode.HALF_UP)))
        reconcileExchangeProfit(q.saleId)
        sale=requireNotNull(posting.sale(q.saleId));val updated=org.json.JSONObject(sale.metadata_json)
        val now=java.time.ZonedDateTime.now();val record=org.json.JSONObject().put("id",q.requestId).put("referenceType","sale").put("referenceId",q.saleId).put("invoiceNo",sale.number).put("kind","exchange")
            .put("currency","USD").put("itemName",source.title).put("qty",q.quantity.toPlainString()).put("replacementName",r.title).put("replacementQty",r.quantity.toPlainString())
            .put("returnedValue",p.returned.toPlainString()).put("replacementValue",p.replacement.toPlainString()).put("creditAmount",(p.returned-p.replacement).toPlainString())
            .put("refundAmount",p.settlement.refund.toPlainString()).put("extraCollected",(q.collect*q.rate).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString()).put("remainingAfter",p.settlement.due.toPlainString())
            .put("cashCurrency",q.currency).put("cashRate",q.rate.toPlainString()).put("cashCollected",q.collect.toPlainString()).put("cashRefunded",if(p.settlement.refund.signum()>0)RefundAllocationMath.convertExact(p.settlement.refund,q.rate).toPlainString() else "0")
            .put("vault",q.cashbox).put("reason",q.reason).put("date",now.toLocalDate().toString()).put("time",now.toLocalTime().withNano(0).toString()).put("returnId",returnId).put("replacementLineId",replacementId)
        history.put(record);updated.put("nativeExchanges",history)
        check(posting.updateExchange(q.saleId,sale.total,updated.toString())==1)
        check(sale.paid.toBigDecimal().compareTo(p.settlement.paid)==0)
        check(requireNotNull(debts.bySaleId(q.saleId)).balance.toBigDecimal().compareTo(p.settlement.due)==0)
        q.requestId
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

    suspend fun archive(search:String,from:Long?,to:Long?,offset:Int=0):List<SaleRecord> =
        db.legacySaleDao().unifiedArchive(search.trim(),from ?: 0L,to ?: Long.MAX_VALUE,100,offset).map { row ->
            val raw=org.json.JSONObject(row.original_json ?: "{}")
            val old=row.historical==1
            val unit=raw.optString("currency","USD")
            val rate=if(old)BigDecimal.ONE else raw.optString("rateToUsd","1").toBigDecimal()
            val debt=db.legacyReceivablesDao().bySaleId(row.id)?.balance
            val remaining=debt ?: if(old)db.legacySaleDao().legacyById(row.id)?.due ?: "0" else "0"
            val titles=if(old){val items=raw.optJSONArray("items");(0 until (items?.length() ?: 0)).mapNotNull{items?.optJSONObject(it)?.optString("name")}.joinToString("، ")}
                else posting.lines(row.id).joinToString("، "){it.title}
            fun display(value:String)=value.toBigDecimal().divide(rate,2,java.math.RoundingMode.HALF_UP).toPlainString()
            val total=if(row.status=="ملغاة")"0" else if(old)row.total else raw.optString("exchangeEffectiveTotal").ifBlank{(row.paid.toBigDecimal()+remaining.toBigDecimal()).toPlainString()}
            SaleRecord(row.id,row.number,row.created_at,row.customer,row.phone,display(total),display(row.paid),row.status,
                if(old)when(LegacySaleChannel.fromOriginalJson(row.original_json!!)){SalesChannelProfit.Channel.ONLINE->"online";SalesChannelProfit.Channel.STORE->"store"} else row.channel,
                unit,display(remaining),titles,old)
        }
}
