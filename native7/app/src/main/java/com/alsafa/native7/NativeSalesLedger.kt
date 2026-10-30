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
    data class Line(val productId:String,val quantity:Int,val unitPrice:Double,val name:String="",val model:String="",val unitCost:Double=0.0)
    data class Sale(val id:String,val currency:String,val total:Double,val cost:Double,val collected:Double,val status:String)
    // Direct Kotlin port of returnFundingAllocationsForQty(item, qty, skipQty)
    // in the original assets/main.js. Allocation order and reverse skipping matter.
    data class FundingAllocation(
        val lotId:String, val purchaseId:String, val source:String,
        val partnerId:String, val partnerName:String, val share:Double,
        val qty:Double, val unitCost:Double, val currency:String
    )
    fun returnFundingAllocationsForQty(
        allocations:List<FundingAllocation>, qty:Double, skipQty:Double=0.0
    ):List<FundingAllocation> {
        var need=qty.coerceAtLeast(0.0)
        var skip=skipQty.coerceAtLeast(0.0)
        val out=mutableListOf<FundingAllocation>()
        for(i in allocations.indices.reversed()) {
            if(need<=0.0001 && skip<=0.0001) break
            val a=allocations[i]
            val available=a.qty.coerceAtLeast(0.0)
            if(available<=0.0) continue
            val skipped=minOf(skip,available)
            skip-=skipped
            val remaining=available-skipped
            if(remaining<=0.0 || need<=0.0) continue
            val take=minOf(need,remaining)
            out.add(0,a.copy(qty=take))
            need-=take
        }
        return out
    }
    // Original consumeFundingLots: oldest createdAt first, consuming qtyRemaining.
    // Caller must run this inside the same SQLite transaction as the sale/return.
    fun consumeFundingLots(db:SQLiteDatabase,productId:String,quantity:Double):List<FundingAllocation> {
        require(quantity.isFinite() && quantity>=0.0)
        var need=quantity
        val allocations=mutableListOf<FundingAllocation>()
        db.rawQuery("SELECT id,purchase_id,source,partner_id,partner_name,share,qty_remaining,unit_cost,currency FROM native_funding_lots WHERE product_id=? ORDER BY created_at ASC,id ASC",
            arrayOf(productId)).use { c ->
            while(c.moveToNext() && need>0.0) {
                val available=c.getDouble(6).coerceAtLeast(0.0)
                val take=minOf(need,available)
                if(take<=0.0) continue
                val lotId=c.getString(0)
                db.execSQL("UPDATE native_funding_lots SET qty_remaining=qty_remaining-? WHERE id=?",arrayOf(take,lotId))
                allocations.add(FundingAllocation(lotId,c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getDouble(5).coerceIn(0.0,100.0),take,c.getDouble(7),c.getString(8)))
                need-=take
            }
        }
        // Original returns [] on shortage. Throw here so SQLite rolls back any
        // already consumed lots along with the enclosing sale/return.
        require(need<=0.0001) { "لا يمكن البيع: يوجد عدم تطابق بين المخزون ورأس المال" }
        return allocations
    }
    // Original restoreFundingAllocations: restore an existing lot, or recreate
    // a missing lot using the original allocation's funding attributes.
    fun restoreFundingAllocations(db:SQLiteDatabase,productId:String,
        allocations:List<FundingAllocation>,ratio:Double=1.0,at:Long=System.currentTimeMillis()) {
        require(ratio.isFinite() && ratio>=0.0)
        for(a in allocations) {
            val q=a.qty.coerceAtLeast(0.0)*ratio
            if(q<=0.0) continue
            val existing=db.rawQuery("SELECT COUNT(*) FROM native_funding_lots WHERE id=? AND product_id=?",
                arrayOf(a.lotId,productId)).use { c -> c.moveToFirst();c.getInt(0)>0 }
            if(existing) db.execSQL("UPDATE native_funding_lots SET qty_remaining=qty_remaining+? WHERE id=? AND product_id=?",
                arrayOf(q,a.lotId,productId))
            else db.execSQL("INSERT INTO native_funding_lots VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any>(if(a.lotId.isBlank()||a.lotId=="untracked") UUID.randomUUID().toString() else a.lotId,
                    productId,if(a.purchaseId.isBlank()) "return" else a.purchaseId,a.source,a.partnerId,a.partnerName,
                    a.share.coerceIn(0.0,100.0),q,q,a.unitCost,a.currency,at))
        }
    }
    private fun fundingAllocationsForSale(db:SQLiteDatabase,saleId:String,productId:String):List<FundingAllocation> {
        val out=mutableListOf<FundingAllocation>()
        db.rawQuery("SELECT lot_id,purchase_id,source,partner_id,partner_name,share,qty,unit_cost,currency FROM native_sale_funding_allocations WHERE sale_id=? AND product_id=? ORDER BY created_at ASC,rowid ASC",
            arrayOf(saleId,productId)).use { c ->
            while(c.moveToNext()) out.add(FundingAllocation(c.getString(0),c.getString(1),
                c.getString(2),c.getString(3),c.getString(4),c.getString(5),
                c.getDouble(6),c.getDouble(7),c.getString(8)))
        }
        return out
    }
    private fun recordVaultMovement(db:SQLiteDatabase,saleId:String,vault:String,
        currency:String,amount:Double,type:String,at:Long) {
        require(vault.isNotBlank() && vault!="mixed" && vault!="converted") { "الخزنة الفعلية مطلوبة" }
        require(currency in setOf("USD","TRY","SYP") && amount.isFinite())
        if(amount==0.0) return
        db.execSQL("INSERT INTO native_sale_vault_movements VALUES (?,?,?,?,?,?,?)",
            arrayOf<Any>(UUID.randomUUID().toString(),saleId,vault,currency,amount,type,at))
    }
    // Exact currency path from legacy convertCurrencyAmount(amount,from,to,snapshot).
    data class RateSnapshot(val TRY:Double?=null,val SYP:Double?=null,val TRY_SYP:Double?=null)
    fun convertCurrencyAmount(value:Double,from:String,to:String,rates:RateSnapshot):Double? {
        if(from==to) return value
        val direct=rates.TRY_SYP
        if(direct!=null && direct>0.0 && from=="TRY" && to=="SYP") return value*direct
        if(direct!=null && direct>0.0 && from=="SYP" && to=="TRY") return value/direct
        val fromRate=if(from=="USD") 1.0 else if(from=="TRY") rates.TRY else rates.SYP
        val toRate=if(to=="USD") 1.0 else if(to=="TRY") rates.TRY else rates.SYP
        if(fromRate==null || toRate==null || fromRate<=0.0 || toRate<=0.0) return null
        return value/fromRate*toRate
    }
    // Verify that the physical-vault journal agrees with payment detail.
    // This does not replace comparison against imported legacy vault balances.
    data class VaultAudit(val vault:String,val currency:String,val recorded:Double,val expected:Double) {
        val difference:Double get()=recorded-expected
    }
    fun auditSaleVaultMovements(db:SQLiteDatabase,saleId:String):List<VaultAudit> {
        create(db)
        val actual=linkedMapOf<Pair<String,String>,Double>()
        db.rawQuery("SELECT vault,currency,SUM(amount) FROM native_sale_vault_movements WHERE sale_id=? GROUP BY vault,currency",
            arrayOf(saleId)).use { c ->
            while(c.moveToNext()) actual[c.getString(0) to c.getString(1)]=c.getDouble(2)
        }
        val expected=linkedMapOf<Pair<String,String>,Double>()
        fun add(vault:String,currency:String,amount:Double) {
            val key=vault to currency
            expected[key]=(expected[key]?:0.0)+amount
        }
        db.rawQuery("SELECT account,currency,SUM(amount) FROM native_sale_collections WHERE sale_id=? AND account NOT IN ('mixed','converted') GROUP BY account,currency",
            arrayOf(saleId)).use { c ->
            while(c.moveToNext()) add(c.getString(0),c.getString(1),c.getDouble(2))
        }
        db.rawQuery("SELECT account,currency,SUM(amount) FROM native_sale_mixed WHERE sale_id=? GROUP BY account,currency",
            arrayOf(saleId)).use { c ->
            while(c.moveToNext()) add(c.getString(0),c.getString(1),c.getDouble(2))
        }
        db.rawQuery("SELECT account,payment_currency,SUM(payment_amount) FROM native_sale_payment_currency WHERE sale_id=? GROUP BY account,payment_currency",
            arrayOf(saleId)).use { c ->
            while(c.moveToNext()) add(c.getString(0),c.getString(1),c.getDouble(2))
        }
        db.rawQuery("SELECT payout_account,payout_currency,SUM(payout) FROM native_sale_change WHERE sale_id=? GROUP BY payout_account,payout_currency",
            arrayOf(saleId)).use { c ->
            while(c.moveToNext()) add(c.getString(0),c.getString(1),-c.getDouble(2))
        }
        db.rawQuery("SELECT tender_account,invoice_currency,SUM(amount_in_invoice_currency) FROM native_sale_change WHERE sale_id=? GROUP BY tender_account,invoice_currency",
            arrayOf(saleId)).use { c ->
            while(c.moveToNext()) add(c.getString(0),c.getString(1),c.getDouble(2))
        }
        return (actual.keys+expected.keys).map { k ->
            VaultAudit(k.first,k.second,actual[k]?:0.0,expected[k]?:0.0)
        }.filter { kotlin.math.abs(it.difference)>0.000001 }
    }
    fun exchangeLine(db:SQLiteDatabase,saleId:String,returnedProductId:String,
        returnedQuantity:Int,replacementProductId:String,replacementQuantity:Int,
        vault:String="درج المحل",extraPaymentNow:Double=0.0,
        penalty:Double=0.0,reason:String="",at:Long=System.currentTimeMillis()) {
        require(returnedQuantity>0 && replacementQuantity>0 && returnedProductId!=replacementProductId)
        require(extraPaymentNow.isFinite() && extraPaymentNow>=0.0 && penalty.isFinite() && penalty>=0.0)
        create(db)
        db.beginTransaction()
        try {
            val sale=find(db,saleId) ?: error("الفاتورة غير موجودة")
            require(sale.status=="active")
            val sold=db.rawQuery("SELECT COALESCE(SUM(quantity),0),COALESCE(SUM(quantity*unit_price),0) FROM native_sale_lines WHERE sale_id=? AND product_id=?",
                arrayOf(saleId,returnedProductId)).use { c -> c.moveToFirst();Pair(c.getInt(0),c.getDouble(1)) }
            val prior=db.rawQuery("SELECT COALESCE(SUM(quantity),0) FROM native_sale_returns WHERE sale_id=? AND product_id=?",
                arrayOf(saleId,returnedProductId)).use { c -> c.moveToFirst();c.getInt(0) }
            require(sold.first>0 && prior+returnedQuantity<=sold.first) { "كمية المرتجع تتجاوز المبيع" }
            val replacement=LegacyProductRepository.findById(db,replacementProductId) ?: error("البديل غير موجود")
            require(replacement.stock>=replacementQuantity) { "مخزون البديل غير كاف" }
            val snapshot=db.rawQuery("SELECT try_rate,syp_rate,try_syp_rate FROM native_sale_rate_snapshots WHERE sale_id=?",
                arrayOf(saleId)).use { c ->
                if(c.moveToFirst()) RateSnapshot(
                    if(c.isNull(0)) null else c.getDouble(0),
                    if(c.isNull(1)) null else c.getDouble(1),
                    if(c.isNull(2)) null else c.getDouble(2)) else null
            }
            fun convert(value:Double,from:String):Double =
                if(from==sale.currency) value else
                    snapshot?.let { convertCurrencyAmount(value,from,sale.currency,it) }
                        ?: error("سعر الصرف المحفوظ غير متوفر")
            val gross=sold.second*returnedQuantity/sold.first
            val returnValue=db.rawQuery("SELECT subtotal,manual_discount,rule_discount,tax_rate FROM native_sale_discounts WHERE sale_id=?",arrayOf(saleId)).use { c ->
                if(c.moveToFirst() && c.getDouble(0)>0) gross*
                    (1.0-(c.getDouble(1)+c.getDouble(2))/c.getDouble(0))*(1.0+c.getDouble(3)/100.0)
                else gross
            }
            val replacementBase=convert(replacement.salePrice,replacement.currency)*replacementQuantity
            val taxRate=db.rawQuery("SELECT tax_rate FROM native_sale_discounts WHERE sale_id=?",
                arrayOf(saleId)).use { c -> if(c.moveToFirst()) c.getDouble(0) else 0.0 }
            val replacementValue=replacementBase*(1.0+taxRate/100.0)
            val oldReturns=db.rawQuery("SELECT COALESCE(SUM(refund),0) FROM native_sale_returns WHERE sale_id=?",
                arrayOf(saleId)).use { c -> c.moveToFirst();c.getDouble(0) }
            val credit=returnValue-replacementValue-penalty
            val paid=sale.collected
            val newTotal=sale.total-oldReturns-credit
            require(newTotal>=-0.000001) { "قيمة الاستبدال غير صالحة" }
            val refund=(paid-newTotal).coerceAtLeast(0.0)
            val extraDue=(newTotal-paid).coerceAtLeast(0.0)
            require(extraPaymentNow<=extraDue+0.000001) { "فرق الاستبدال المقبوض يتجاوز المستحق" }
            val funding=fundingAllocationsForSale(db,saleId,returnedProductId)
            val restore=returnFundingAllocationsForQty(funding,returnedQuantity.toDouble(),prior.toDouble())
            require(restore.sumOf { it.qty }+0.0001>=returnedQuantity) { "تخصيص التمويل للمرتجع غير مكتمل" }
            val consumed=consumeFundingLots(db,replacementProductId,replacementQuantity.toDouble())
            restoreFundingAllocations(db,returnedProductId,restore,at=at)
            val eventId=UUID.randomUUID().toString()
            db.execSQL("INSERT INTO native_sale_returns VALUES (?,?,?,?,?,?,?)",
                arrayOf<Any>(eventId,saleId,returnedProductId,returnedQuantity,credit,reason,at))
            db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),saleId,returnedProductId,returnedQuantity,"exchange-return",at))
            db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),saleId,replacementProductId,-replacementQuantity,"exchange-replacement",at))
            for(a in consumed) db.execSQL("INSERT INTO native_sale_funding_allocations VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),saleId,replacementProductId,a.lotId,a.purchaseId,a.source,
                    a.partnerId,a.partnerName,a.share,a.qty,a.unitCost,a.currency,at))
            db.execSQL("INSERT INTO native_sale_exchanges VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any>(eventId,saleId,returnedProductId,replacementProductId,replacementQuantity,
                    replacementValue,penalty,refund,extraPaymentNow,vault,at))
            if(refund>0) insertCollection(db,saleId,-refund,sale.currency,vault,at,"customer-refund")
            if(extraPaymentNow>0) insertCollection(db,saleId,extraPaymentNow,sale.currency,vault,at,"exchange-difference")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun create(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_exchanges (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, returned_product_id TEXT NOT NULL, replacement_product_id TEXT NOT NULL, replacement_quantity INTEGER NOT NULL, replacement_value REAL NOT NULL, penalty REAL NOT NULL, refund REAL NOT NULL, extra_collected REAL NOT NULL, vault TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_vault_movements (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, vault TEXT NOT NULL, currency TEXT NOT NULL, amount REAL NOT NULL, type TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_rate_snapshots (sale_id TEXT PRIMARY KEY, try_rate REAL, syp_rate REAL, try_syp_rate REAL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_funding_lots (id TEXT PRIMARY KEY, product_id TEXT NOT NULL, purchase_id TEXT NOT NULL, source TEXT NOT NULL, partner_id TEXT NOT NULL, partner_name TEXT NOT NULL, share REAL NOT NULL, qty_original REAL NOT NULL, qty_remaining REAL NOT NULL, unit_cost REAL NOT NULL, currency TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_funding_allocations (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, product_id TEXT NOT NULL, lot_id TEXT NOT NULL, purchase_id TEXT NOT NULL, source TEXT NOT NULL, partner_id TEXT NOT NULL, partner_name TEXT NOT NULL, share REAL NOT NULL, qty REAL NOT NULL, unit_cost REAL NOT NULL, currency TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sales (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, currency TEXT NOT NULL, channel TEXT NOT NULL, customer TEXT NOT NULL, phone TEXT NOT NULL, total REAL NOT NULL, cost REAL NOT NULL, status TEXT NOT NULL DEFAULT 'active')")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_lines (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price REAL NOT NULL, unit_cost REAL NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_manual_items (line_id TEXT PRIMARY KEY, name TEXT NOT NULL, model TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_discounts (sale_id TEXT PRIMARY KEY, subtotal REAL NOT NULL, manual_discount REAL NOT NULL, rule_discount REAL NOT NULL, tax_rate REAL NOT NULL, tax_amount REAL NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_stock (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, product_id TEXT NOT NULL, delta INTEGER NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_native_sale_stock_product ON native_sale_stock(product_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_returns (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, product_id TEXT NOT NULL, quantity INTEGER NOT NULL, refund REAL NOT NULL, reason TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_change (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, amount_in_invoice_currency REAL NOT NULL, invoice_currency TEXT NOT NULL, payout REAL NOT NULL, payout_currency TEXT NOT NULL, payout_account TEXT NOT NULL, tender_account TEXT NOT NULL, exchange_rate REAL NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_mixed (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, account TEXT NOT NULL, invoice_equivalent REAL NOT NULL, exchange_rate REAL NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_partner_withdrawals (id TEXT PRIMARY KEY, partner TEXT NOT NULL, currency TEXT NOT NULL, amount REAL NOT NULL, created_at INTEGER NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_profit_adjustments (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, created_at INTEGER NOT NULL, reason TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_collections (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, account TEXT NOT NULL, created_at INTEGER NOT NULL, reason TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS native_sale_payment_currency (id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, invoice_amount REAL NOT NULL, invoice_currency TEXT NOT NULL, payment_amount REAL NOT NULL, payment_currency TEXT NOT NULL, account TEXT NOT NULL, exchange_rate REAL NOT NULL, created_at INTEGER NOT NULL)")
    }
    /** Verifies every native invoice's net stock movements against sold and returned units.
     * A cancelled invoice must have zero net stock effect.
     */
    data class StockAuditIssue(val saleId:String,val productId:String,
        val expected:Int,val actual:Int)
    fun auditStock(db:SQLiteDatabase):List<StockAuditIssue> {
        create(db)
        val issues=mutableListOf<StockAuditIssue>()
        db.rawQuery("""
            SELECT s.id, l.product_id,
              CASE WHEN s.status='active' THEN -SUM(l.quantity)+
                COALESCE((SELECT SUM(r.quantity) FROM native_sale_returns r
                  WHERE r.sale_id=s.id AND r.product_id=l.product_id),0)
                ELSE 0 END AS expected,
              COALESCE((SELECT SUM(st.delta) FROM native_sale_stock st
                WHERE st.sale_id=s.id AND st.product_id=l.product_id),0) AS actual
            FROM native_sales s JOIN native_sale_lines l ON l.sale_id=s.id AND l.product_id<>''
            GROUP BY s.id,l.product_id
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) {
                val expected=c.getInt(2)
                val actual=c.getInt(3)
                if(expected!=actual)issues.add(StockAuditIssue(c.getString(0),c.getString(1),expected,actual))
            }
        }
        return issues
    }
    /** Reconciles the invoice-currency collection entry with the actual mixed tenders.
     * The synthetic "mixed" account is not a physical cashbox.
     */
    data class MixedAuditIssue(val saleId:String,val tenderEquivalent:Double,
        val recordedEquivalent:Double)
    fun auditMixedCollections(db:SQLiteDatabase):List<MixedAuditIssue> {
        create(db)
        val issues=mutableListOf<MixedAuditIssue>()
        db.rawQuery("""
            SELECT s.id,
                COALESCE((SELECT SUM(m.invoice_equivalent) FROM native_sale_mixed m
                    WHERE m.sale_id=s.id),0),
                COALESCE((SELECT SUM(c.amount) FROM native_sale_collections c
                    WHERE c.sale_id=s.id AND c.account='mixed'),0)
            FROM native_sales s
            WHERE EXISTS(SELECT 1 FROM native_sale_mixed m WHERE m.sale_id=s.id)
        """.trimIndent(),null).use { c ->
            while(c.moveToNext()) {
                val tenders=c.getDouble(1)
                val recorded=c.getDouble(2)
                if(kotlin.math.abs(tenders-recorded)>0.000001)
                    issues.add(MixedAuditIssue(c.getString(0),tenders,recorded))
            }
        }
        return issues
    }
    fun stockDelta(db:SQLiteDatabase,productId:String):Int {
        create(db)
        db.rawQuery("SELECT COALESCE(SUM(delta),0) FROM native_sale_stock WHERE product_id=?",arrayOf(productId)).use { c ->
            return if(c.moveToFirst()) c.getInt(0) else 0
        }
    }
    fun save(db:SQLiteDatabase,lines:List<Line>,currency:String,channel:String,customer:String,phone:String,
             initialCollection:Double=0.0,account:String="درج المحل",changeAccount:String=account,changeCurrency:String=currency,manualDiscount:Double=0.0,ruleDiscount:Double=0.0,taxRate:Double=0.0,rateSnapshot:RateSnapshot?=null):String {
        require(changeCurrency==currency) { "إرجاع الباقي بعملة أخرى يحتاج سعر تحويل صريح" }
        create(db)
        db.beginTransaction()
        try {
            val id=saveInternal(db,lines,currency,channel,customer,phone,initialCollection,account,changeAccount,manualDiscount,ruleDiscount,taxRate,rateSnapshot)
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    private fun saveInternal(db:SQLiteDatabase,lines:List<Line>,currency:String,channel:String,
        customer:String,phone:String,initialCollection:Double,account:String,changeAccount:String=account,
        manualDiscount:Double=0.0,ruleDiscount:Double=0.0,taxRate:Double=0.0,rateSnapshot:RateSnapshot?=null):String {
        require(lines.isNotEmpty()) { "أضف صنفاً إلى الفاتورة" }
        require(currency in setOf("USD","TRY","SYP")) { "عملة غير مدعومة" }
        require(initialCollection.isFinite() && initialCollection>=0) { "قيمة القبض غير صالحة" }
        require(lines.all { it.quantity>0 && it.unitPrice.isFinite() && it.unitPrice>=0 && it.unitCost.isFinite() && it.unitCost>=0 && (it.productId.isNotBlank() || it.name.isNotBlank()) }) { "بيانات الأصناف غير صالحة" }
        require(channel in setOf("بيع من المحل","بيع أونلاين")) { "نوع البيع غير صالح" }
        require(account.isNotBlank() && changeAccount.isNotBlank()) { "حدد خزنة القبض والباقي" }
        val id=UUID.randomUUID().toString()
        val grouped=lines.filter { it.productId.isNotBlank() }.groupBy { it.productId }
        var total=0.0
        var cost=0.0
        val now=System.currentTimeMillis()
        for((productId,entries) in grouped) {
            val product=LegacyProductRepository.findById(db,productId) ?: error("المنتج غير موجود")
            val requested=entries.sumOf { it.quantity }
            require(product.id==productId) { "تعذر تحديد المنتج" }
            require(product.stock>=requested) { "المخزون غير كافٍ: "+product.name }
            require(product.cost.isFinite() && product.cost>=0) { "كلفة المنتج غير صالحة" }
            // The original sale consumes funding lots before recording the sale.
            // Persist the exact allocations so a return/cancellation can restore them.
            val allocations=consumeFundingLots(db,productId,requested.toDouble())
            val fundingCost=allocations.sumOf { a ->
                val converted=if(a.currency==currency) a.unitCost else
                    rateSnapshot?.let { convertCurrencyAmount(a.unitCost,a.currency,currency,it) }
                require(converted!=null && converted.isFinite()) { "سعر الصرف المحفوظ مطلوب لتحويل كلفة التمويل" }
                a.qty*converted
            }
            for(a in allocations) db.execSQL(
                "INSERT INTO native_sale_funding_allocations VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),id,productId,a.lotId,a.purchaseId,a.source,
                    a.partnerId,a.partnerName,a.share,a.qty,a.unitCost,a.currency,now))
            for(line in entries) {
                total+=line.unitPrice*line.quantity
                // Funding unit costs are persisted in their original currencies.
                // Currency conversion must be supplied from the sale snapshot.
                val unitCost=if(requested>0) fundingCost/requested else 0.0
                cost+=unitCost*line.quantity
                db.execSQL("INSERT INTO native_sale_lines VALUES (?,?,?,?,?,?)",arrayOf<Any>(UUID.randomUUID().toString(),id,productId,line.quantity,line.unitPrice,unitCost))
            }
            db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",arrayOf<Any>(UUID.randomUUID().toString(),id,productId,-requested,"sale",now))
        }
        for(line in lines.filter { it.productId.isBlank() }) {
            val lineId=UUID.randomUUID().toString()
            total+=line.unitPrice*line.quantity
            cost+=line.unitCost*line.quantity
            db.execSQL("INSERT INTO native_sale_lines VALUES (?,?,?,?,?,?)",
                arrayOf<Any>(lineId,id,"",line.quantity,line.unitPrice,line.unitCost))
            db.execSQL("INSERT INTO native_sale_manual_items VALUES (?,?,?)",
                arrayOf<Any>(lineId,line.name.trim(),line.model.trim()))
        }
        require(total.isFinite() && cost.isFinite() && total>=0) { "قيمة الفاتورة غير صالحة" }
        require(manualDiscount.isFinite() && manualDiscount>=0 && ruleDiscount.isFinite() && ruleDiscount>=0 &&
            manualDiscount+ruleDiscount<=total) { "الخصم غير صالح" }
        require(taxRate.isFinite() && taxRate>=0) { "نسبة الضريبة غير صالحة" }
        val subtotal=total
        val taxable=total-manualDiscount-ruleDiscount
        val tax=taxable*taxRate/100.0
        require(tax.isFinite()) { "الضريبة غير صالحة" }
        total=taxable+tax
        db.execSQL("INSERT INTO native_sale_discounts VALUES (?,?,?,?,?,?)",
            arrayOf<Any>(id,subtotal,manualDiscount,ruleDiscount,taxRate,tax))
        require(total>0 || initialCollection==0.0) { "لا يمكن قبض مبلغ لفاتورة قيمتها صفر" }
        db.execSQL("INSERT INTO native_sales VALUES (?,?,?,?,?,?,?,?,?)",arrayOf<Any>(id,now,currency,channel,customer,phone,total,cost,"active"))
        if(rateSnapshot!=null) db.execSQL("INSERT INTO native_sale_rate_snapshots VALUES (?,?,?,?)",
            arrayOf(id,rateSnapshot.TRY,rateSnapshot.SYP,rateSnapshot.TRY_SYP))
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
        changeCurrency:String,changeAccount:String,changeRate:Double,manualDiscount:Double=0.0,ruleDiscount:Double=0.0,taxRate:Double=0.0,rateSnapshot:RateSnapshot?=null):String {
        require(tendered.isFinite() && tendered>=0) { "المبلغ المدفوع غير صالح" }
        require(changeCurrency in setOf("USD","TRY","SYP")) { "عملة الباقي غير صالحة" }
        require(changeRate.isFinite() && changeRate>0) { "سعر الصرف غير صالح" }
        require(tenderAccount.isNotBlank() && changeAccount.isNotBlank()) { "حدد خزنة القبض والباقي" }
        if(currency==changeCurrency) require(kotlin.math.abs(changeRate-1.0)<0.000001) { "سعر الصرف لنفس العملة يجب أن يكون 1" }
        create(db)
        db.beginTransaction()
        try {
            val id=saveInternal(db,lines,currency,channel,customer,phone,0.0,tenderAccount,manualDiscount=manualDiscount,ruleDiscount=ruleDiscount,taxRate=taxRate,rateSnapshot=rateSnapshot)
            val sale=find(db,id) ?: error("تعذر قراءة الفاتورة")
            val collected=tendered.coerceAtMost(sale.total)
            val change=(tendered-sale.total).coerceAtLeast(0.0)
            val now=System.currentTimeMillis()
            if(collected>0) insertCollection(db,id,collected,currency,tenderAccount,now,"collection")
            // The customer physically hands over the full tender. The invoice
            // collection is capped at total, so the excess must still enter the
            // vault before the change is paid out.
            if(change>0) recordVaultMovement(db,id,tenderAccount,currency,change,"tender-excess",now)
            if(change>0) {
                val payout=change*changeRate
                require(payout.isFinite()) { "قيمة الباقي غير صالحة" }
                db.execSQL("INSERT INTO native_sale_change VALUES (?,?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any>(UUID.randomUUID().toString(),id,change,currency,payout,changeCurrency,changeAccount,tenderAccount,changeRate,now))
                recordVaultMovement(db,id,changeAccount,changeCurrency,-payout,"change",now)
            }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    data class Tender(val amount:Double,val currency:String,val account:String,val rate:Double)
    fun saveMixed(db:SQLiteDatabase,lines:List<Line>,currency:String,channel:String,
        customer:String,phone:String,tenders:List<Tender>,manualDiscount:Double=0.0,ruleDiscount:Double=0.0,taxRate:Double=0.0,rateSnapshot:RateSnapshot?=null):String {
        require(tenders.isNotEmpty()) { "حدد مبالغ القبض" }
        require(tenders.all { it.amount.isFinite() && it.amount>0 && it.rate.isFinite() && it.rate>0 &&
            it.currency in setOf("USD","TRY","SYP") && it.account.isNotBlank() &&
            (it.currency!=currency || kotlin.math.abs(it.rate-1.0)<0.000001) }) { "المبالغ أو أسعار الصرف غير صالحة" }
        create(db)
        db.beginTransaction()
        try {
            val id=saveInternal(db,lines,currency,channel,customer,phone,0.0,tenders.first().account,manualDiscount=manualDiscount,ruleDiscount=ruleDiscount,taxRate=taxRate,rateSnapshot=rateSnapshot)
            val sale=find(db,id) ?: error("الفاتورة غير موجودة")
            val total=tenders.sumOf { it.amount*it.rate }
            require(total.isFinite() && total<=sale.total+0.000001) { "الدفع المختلط أكبر من قيمة الفاتورة؛ استخدم إرجاع الباقي" }
            val now=System.currentTimeMillis()
            tenders.forEach { t ->
                db.execSQL("INSERT INTO native_sale_mixed VALUES (?,?,?,?,?,?,?,?)",
                    arrayOf<Any>(UUID.randomUUID().toString(),id,t.amount,t.currency,t.account,t.amount*t.rate,t.rate,now))
            }
            tenders.forEach { t -> recordVaultMovement(db,id,t.account,t.currency,t.amount,"mixed_collection",now) }
            if(total>0) insertCollection(db,id,total,currency,"mixed",now,"mixed_collection")
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    private fun insertCollection(db:SQLiteDatabase,id:String,amount:Double,currency:String,account:String,at:Long,reason:String) {
        db.execSQL("INSERT INTO native_sale_collections VALUES (?,?,?,?,?,?,?)",arrayOf<Any>(UUID.randomUUID().toString(),id,amount,currency,account,at,reason))
        if(account!="mixed" && account!="converted") recordVaultMovement(db,id,account,currency,amount,reason,at)
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
            // Original appendPayment: applied = min(max(0, amount), balanceFor(...)).
            val applied=amount.coerceAtMost((sale.total-refunded-sale.collected).coerceAtLeast(0.0))
            if(applied<=0.0) { db.setTransactionSuccessful(); return }
            insertCollection(db,id,applied,sale.currency,account,at,"collection")
            val returnedValue=db.rawQuery("SELECT COALESCE(SUM(refund),0) FROM native_sale_returns WHERE sale_id=?",arrayOf(id)).use { c -> c.moveToFirst();c.getDouble(0) }
            if(returnedValue>0.000001) {
                val netValue=sale.total-returnedValue
                val margin=if(netValue>0.000001) (netValue-sale.cost+returnedCost(db,id))/netValue else 0.0
                val initialMargin=if(sale.total>0) (sale.total-sale.cost)/sale.total else 0.0
                val adjustment=applied*(margin-initialMargin)
                if(kotlin.math.abs(adjustment)>0.000001) db.execSQL("INSERT INTO native_sale_profit_adjustments VALUES (?,?,?,?,?,?)",
                    arrayOf<Any>(UUID.randomUUID().toString(),id,adjustment,sale.currency,at,"collection_after_return"))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /**
     * Legacy appendPayment records the amount against the invoice currency and
     * the actual cash receipt against the selected payment currency. The
     * exchange rate must be supplied from the invoice's saved rate snapshot.
     */
    fun collectInCurrency(db:SQLiteDatabase,id:String,amount:Double,
        paymentCurrency:String,account:String,invoiceToPaymentRate:Double,
        at:Long=System.currentTimeMillis()) {
        require(amount.isFinite() && amount>0) { "قيمة القبض غير صالحة" }
        require(paymentCurrency in setOf("USD","TRY","SYP")) { "عملة القبض غير صالحة" }
        require(invoiceToPaymentRate.isFinite() && invoiceToPaymentRate>0) { "سعر الصرف غير متوفر" }
        require(account.isNotBlank()) { "حدد خزنة القبض" }
        create(db)
        db.beginTransaction()
        try {
            val sale=find(db,id) ?: error("الفاتورة غير موجودة")
            require(sale.status=="active") { "الفاتورة ملغاة" }
            require(paymentCurrency!=sale.currency || kotlin.math.abs(invoiceToPaymentRate-1.0)<0.000001) { "سعر الصرف لنفس العملة يجب أن يكون 1" }
            // appendPayment: applied=min(max(0,amount),balanceFor(...)).
            val applied=amount.coerceAtMost(remaining(db,id).coerceAtLeast(0.0))
            if(applied<=0) { db.setTransactionSuccessful();return }
            val actual=applied*invoiceToPaymentRate
            require(actual.isFinite()) { "مبلغ القبض غير صالح" }
            insertCollection(db,id,applied,sale.currency,"converted",at,"collection")
            db.execSQL("INSERT INTO native_sale_payment_currency VALUES (?,?,?,?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),id,applied,sale.currency,actual,paymentCurrency,account,invoiceToPaymentRate,at))
            recordVaultMovement(db,id,account,paymentCurrency,actual,"collection",at)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun cancel(db:SQLiteDatabase,id:String,reason:String,at:Long=System.currentTimeMillis()) {
        // Original deleteRecord(type, recordId) does not require a cancellation reason.
        create(db)
        db.beginTransaction()
        try {
            val sale=find(db,id) ?: error("الفاتورة غير موجودة")
            require(sale.status=="active") { "الفاتورة ملغاة مسبقاً" }
            // Legacy deleteRecord additionally refuses sales linked to customer orders
            // or used-watch exchanges. These links are stored by the legacy import.
            val linkedOrder=db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='legacy_customer_orders'",null
            ).use { it.moveToFirst() }
            if(linkedOrder) {
                // The imported record's exact relationship is checked in its source
                // table; do not infer an order from the customer's name or phone.
                val columns=mutableSetOf<String>()
                db.rawQuery("PRAGMA table_info(legacy_customer_orders)",null).use { c ->
                    while(c.moveToNext()) columns.add(c.getString(1))
                }
                if("sale_id" in columns) {
                    db.rawQuery("SELECT COUNT(*) FROM legacy_customer_orders WHERE sale_id=?",arrayOf(id)).use { c ->
                        c.moveToFirst()
                        require(c.getInt(0)==0) { "لا يمكن حذف فاتورة ناتجة عن طلب عميل؛ فهي مرتبطة بعربون وتسوية الطلب. احتفظ بها كسجل محاسبي." }
                    }
                }
            }
            // Legacy deleteRecord forbids cancelling/deleting a sale with linked returns.
            db.rawQuery("SELECT COUNT(*) FROM native_sale_returns WHERE sale_id=?",arrayOf(id)).use { c ->
                c.moveToFirst()
                require(c.getInt(0)==0) { "لا يمكن حذف فاتورة لها مرتجع/استبدال. احتفظ بها كسجل محاسبي." }
            }
            db.rawQuery("SELECT DISTINCT product_id FROM native_sale_funding_allocations WHERE sale_id=?",arrayOf(id)).use { c ->
                while(c.moveToNext()) {
                    val productId=c.getString(0)
                    restoreFundingAllocations(db,productId,fundingAllocationsForSale(db,id,productId),at=at)
                }
            }
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
                    recordVaultMovement(db,id,c.getString(4),c.getString(3),c.getDouble(2),"cancel-change",at)
                    recordVaultMovement(db,id,c.getString(5),c.getString(1),-c.getDouble(0),"cancel-tender-excess",at)
                }
            }
            // Reverse only the remaining net tender after any prior returns.
            db.rawQuery("SELECT currency,account,SUM(amount),SUM(invoice_equivalent) FROM native_sale_mixed WHERE sale_id=? GROUP BY currency,account HAVING SUM(amount)>0",arrayOf(id)).use { c ->
                while(c.moveToNext()) {
                    val amount=c.getDouble(2)
                    val equivalent=c.getDouble(3)
                    db.execSQL("INSERT INTO native_sale_mixed VALUES (?,?,?,?,?,?,?,?)",
                        arrayOf<Any>(UUID.randomUUID().toString(),id,-amount,c.getString(0),c.getString(1),
                            -equivalent,if(amount>0)equivalent/amount else 1.0,at))
                    recordVaultMovement(db,id,c.getString(1),c.getString(0),-amount,"cancel",at)
                }
            }
            val originalProfit=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN amount>0 THEN amount ELSE 0 END),0) FROM native_sale_collections WHERE sale_id=?",arrayOf(id)).use { c -> c.moveToFirst();c.getDouble(0) } * (if(sale.total>0) (sale.total-sale.cost)/sale.total else 0.0)
            val adjusted=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM native_sale_profit_adjustments WHERE sale_id=?",arrayOf(id)).use { c -> c.moveToFirst();c.getDouble(0) }
            // Profit reporting recognizes positive collection entries and adjustments.
            // Cancellation reverses the entire previously recognized profit, even
            // when returns or mixed payments have already reduced net collections.
            val difference=-(originalProfit+adjusted)
            if(kotlin.math.abs(difference)>0.000001) db.execSQL("INSERT INTO native_sale_profit_adjustments VALUES (?,?,?,?,?,?)",arrayOf<Any>(UUID.randomUUID().toString(),id,difference,sale.currency,at,"cancel: "+reason))
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
        // saveSaleReturn reads notes optionally; it does not require a reason.
        require(quantity>0) { "كمية المرتجع غير صالحة" }
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
            val grossRefund=sold.second*quantity/sold.first
            val refund=db.rawQuery("SELECT subtotal,manual_discount,rule_discount,tax_rate FROM native_sale_discounts WHERE sale_id=?",arrayOf(saleId)).use { c ->
                if(c.moveToFirst() && c.getDouble(0)>0) {
                    val factor=(c.getDouble(0)-c.getDouble(1)-c.getDouble(2))/c.getDouble(0)
                    grossRefund*factor*(1.0+c.getDouble(3)/100.0)
                } else grossRefund
            }
            val existingRefund=db.rawQuery("SELECT COALESCE(SUM(refund),0) FROM native_sale_returns WHERE sale_id=?",
                arrayOf(saleId)).use { c -> c.moveToFirst();c.getDouble(0) }
            require(existingRefund+refund<=sale.total+0.000001) { "قيمة المرتجعات تتجاوز الفاتورة" }
            if(productId.isNotBlank()) {
                val funding=fundingAllocationsForSale(db,saleId,productId)
                val allocations=returnFundingAllocationsForQty(funding,quantity.toDouble(),returned.toDouble())
                require(allocations.sumOf { it.qty }+0.0001>=quantity) {
                    "تخصيصات رأس المال للمرتجع غير مكتملة"
                }
                restoreFundingAllocations(db,productId,allocations,at=at)
            }
            db.execSQL("INSERT INTO native_sale_returns VALUES (?,?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),saleId,productId,quantity,refund,reason,at))
            if(productId.isNotBlank()) db.execSQL("INSERT INTO native_sale_stock VALUES (?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),saleId,productId,quantity,"return: "+reason,at))
            // Mixed tender is held in native_sale_mixed, while the invoice-equivalent
            // collection uses the synthetic account "mixed". Never pay a refund
            // from a physical account without reversing its matching tender row.
            val mixedAvailable=db.rawQuery("SELECT COALESCE(SUM(invoice_equivalent),0) FROM native_sale_mixed WHERE sale_id=?",arrayOf(saleId)).use { c -> c.moveToFirst();c.getDouble(0) }
            require(mixedAvailable<=0.000001 || account=="mixed") {
                "المرتجع بعد القبض المختلط يحتاج عكس دفعاته الأصلية؛ لا يمكن الصرف من خزنة مختلفة"
            }
            val refundable=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM native_sale_collections WHERE sale_id=? AND account=?",arrayOf(saleId,account)).use { c -> c.moveToFirst();c.getDouble(0).coerceAtLeast(0.0) }
            // An unpaid return reduces receivables; never pay out more cash than
            // the customer's actual net collections on the whole invoice.
            val netCollected=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM native_sale_collections WHERE sale_id=?",
                arrayOf(saleId)).use { c -> c.moveToFirst();c.getDouble(0).coerceAtLeast(0.0) }
            // For mixed tenders, reverse the exact physical amounts proportionally
            // to their invoice-equivalent contributions, preserving each currency.
            // The refund must never leave net collections above the reduced
            // invoice total. Unpaid portions of a return reduce receivables.
            val minimumCashRefund=(netCollected-(sale.total-existingRefund-refund)).coerceAtLeast(0.0)
            require(refundable+0.000001>=minimumCashRefund) {
                "لا تكفي مبالغ القبض في الخزنة المحددة لإعادة قيمة المرتجع"
            }
            // A return first reduces the unpaid balance. Only the excess of
            // collected funds over the revised invoice total is paid back.
            val cashRefund=minimumCashRefund.coerceAtMost(refundable).coerceAtMost(netCollected)
            if(cashRefund>0.000001 && account=="mixed") {
                val tenders=mutableListOf<Array<Any>>()
                db.rawQuery("SELECT currency,account,COALESCE(SUM(amount),0),COALESCE(SUM(invoice_equivalent),0) FROM native_sale_mixed WHERE sale_id=? GROUP BY currency,account HAVING SUM(invoice_equivalent)>0",arrayOf(saleId)).use { c ->
                    while(c.moveToNext()) tenders.add(arrayOf(c.getString(0),c.getString(1),c.getDouble(2),c.getDouble(3)))
                }
                val totalEquivalent=tenders.sumOf { it[3] as Double }
                require(totalEquivalent+0.000001>=cashRefund) { "مبالغ القبض المختلط غير كافية للمرتجع" }
                for(t in tenders) {
                    val ratio=cashRefund/totalEquivalent
                    val originalAmount=t[2] as Double
                    val originalEquivalent=t[3] as Double
                    db.execSQL("INSERT INTO native_sale_mixed VALUES (?,?,?,?,?,?,?,?)",
                        arrayOf<Any>(UUID.randomUUID().toString(),saleId,-originalAmount*ratio,
                            t[0],t[1],-originalEquivalent*ratio,
                            if(originalAmount>0) originalEquivalent/originalAmount else 1.0,at))
                    recordVaultMovement(db,saleId,t[1] as String,t[0] as String,
                        -originalAmount*ratio,"customer-refund",at)
                }
            }
            if(cashRefund>0) insertCollection(db,saleId,-cashRefund,sale.currency,account,at,"return: "+reason)
            val collected=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM native_sale_collections WHERE sale_id=?",arrayOf(saleId)).use { c -> c.moveToFirst();c.getDouble(0) }
            val returnedCost=returnedCost(db,saleId)
            val netValue=sale.total-existingRefund-refund
            val target=if(netValue>0.000001) collected*(netValue-sale.cost+returnedCost)/netValue else 0.0
            val baseline=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN amount>0 THEN amount ELSE 0 END),0) FROM native_sale_collections WHERE sale_id=?",arrayOf(saleId)).use { c -> c.moveToFirst();c.getDouble(0) } *
                (if(sale.total>0) (sale.total-sale.cost)/sale.total else 0.0)
            val previous=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM native_sale_profit_adjustments WHERE sale_id=?",arrayOf(saleId)).use { c -> c.moveToFirst();c.getDouble(0) }
            val correction=target-baseline-previous
            if(kotlin.math.abs(correction)>0.000001) db.execSQL("INSERT INTO native_sale_profit_adjustments VALUES (?,?,?,?,?,?)",
                arrayOf<Any>(UUID.randomUUID().toString(),saleId,correction,sale.currency,at,"return: "+reason))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun remaining(db:SQLiteDatabase,id:String):Double {
        val sale=find(db,id) ?: error("الفاتورة غير موجودة")
        if(sale.status=="cancelled") return 0.0
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
            val mixed=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM native_sale_mixed WHERE account=? AND currency=? AND created_at>=? AND created_at<?",
                arrayOf(account,currency,from.toString(),to.toString())).use { x -> x.moveToFirst();x.getDouble(0) }
            val paidChange=db.rawQuery("SELECT COALESCE(SUM(payout),0) FROM native_sale_change WHERE payout_account=? AND payout_currency=? AND created_at>=? AND created_at<?",
                arrayOf(account,currency,from.toString(),to.toString())).use { x -> x.moveToFirst();x.getDouble(0) }
            // Mixed invoice-equivalent collections are a bookkeeping entry, not cash.
            // Only physical tender rows contribute to the actual account balance.
            return (if(account=="mixed") 0.0 else collected)+tenderExcess+mixed-paidChange
        }
    }
    fun profitByCurrency(db:SQLiteDatabase,from:Long,to:Long):Map<String,Double> {
        create(db)
        val totals=linkedMapOf<String,Double>()
        db.rawQuery("SELECT s.currency,COALESCE(SUM(CASE WHEN c.amount>0 AND s.total>0 THEN c.amount*(s.total-s.cost)/s.total ELSE 0 END),0) FROM native_sale_collections c JOIN native_sales s ON s.id=c.sale_id WHERE c.created_at>=? AND c.created_at<? GROUP BY s.currency",arrayOf(from.toString(),to.toString())).use { c ->
            while(c.moveToNext()) totals[c.getString(0)]=c.getDouble(1)
        }
        db.rawQuery("SELECT currency,COALESCE(SUM(amount),0) FROM native_sale_profit_adjustments WHERE created_at>=? AND created_at<? GROUP BY currency",arrayOf(from.toString(),to.toString())).use { c ->
            while(c.moveToNext()) totals[c.getString(0)]=(totals[c.getString(0)] ?: 0.0)+c.getDouble(1)
        }
        return totals
    }
    data class PartnerShare(val name:String,val percent:Int,val profit:Double)
    fun partnerShares(db:SQLiteDatabase,from:Long,to:Long):Map<String,List<PartnerShare>> =
        profitByCurrency(db,from,to).mapValues { (_,value) ->
            listOf(PartnerShare("الأب",40,value*0.40),
                PartnerShare("عبد الكريم",35,value*0.35),
                PartnerShare("يزن",25,value*0.25))
        }
    data class PartnerBalance(val name:String,val currency:String,val profit:Double,
        val withdrawals:Double,val remaining:Double,val previousUnpaid:Double)
    fun recordPartnerWithdrawal(db:SQLiteDatabase,name:String,currency:String,amount:Double,
        at:Long=System.currentTimeMillis(),note:String="") {
        require(name in setOf("الأب","عبد الكريم","يزن") && currency in setOf("USD","TRY","SYP"))
        require(amount.isFinite() && amount>0)
        create(db)
        db.execSQL("INSERT INTO native_partner_withdrawals VALUES (?,?,?,?,?,?)",
            arrayOf<Any>(UUID.randomUUID().toString(),name,currency,amount,at,note))
    }
    fun partnerBalances(db:SQLiteDatabase,from:Long,to:Long):List<PartnerBalance> {
        require(to>from)
        LegacyDomainTables.create(db)
        val monthly=partnerShares(db,from,to)
        val previous=partnerShares(db,0,from)
        return listOf("USD","TRY","SYP").flatMap { currency ->
            listOf("الأب","عبد الكريم","يزن").map { name ->
                val earned=monthly[currency]?.firstOrNull { it.name==name }?.profit ?: 0.0
                val old=previous[currency]?.firstOrNull { it.name==name }?.profit ?: 0.0
                fun withdrawn(start:Long,end:Long):Double =
                    db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM native_partner_withdrawals WHERE partner=? AND currency=? AND created_at>=? AND created_at<?",
                        arrayOf(name,currency,start.toString(),end.toString())).use { c -> c.moveToFirst();c.getDouble(0) }
                val currentWithdrawals=withdrawn(from,to)+legacyWithdrawn(db,name,currency,from,to)
                val historicalWithdrawals=withdrawn(0,from)+legacyWithdrawn(db,name,currency,0,from)
                PartnerBalance(name,currency,earned,currentWithdrawals,earned-currentWithdrawals,
                    old-historicalWithdrawals)
            }
        }
    }
    private fun legacyWithdrawn(db:SQLiteDatabase,name:String,currency:String,from:Long,to:Long):Double {
        val format=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).apply {
            timeZone=java.util.TimeZone.getTimeZone("Asia/Damascus")
        }
        val start=format.format(java.util.Date(from))
        val end=format.format(java.util.Date(to))
        val partnerId=when(name) {
            "عبد الكريم" -> "me"
            "يزن" -> "brother"
            else -> "father"
        }
        db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM domain_partnerWithdrawals WHERE currency=? AND date>=? AND date<? AND partnerId=?",
            arrayOf(currency,start,end,partnerId)).use { c ->
            c.moveToFirst()
            return c.getDouble(0)
        }
    }
    data class CashComparison(val account:String,val currency:String,
        val legacyMovement:Double,val nativeMovement:Double,val combinedMovement:Double)
    fun compareCashMovements(db:SQLiteDatabase,from:Long,to:Long):List<CashComparison> {
        require(to>from)
        LegacyDomainTables.create(db)
        val dateFormat=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).apply {
            timeZone=java.util.TimeZone.getTimeZone("Asia/Damascus")
        }
        val start=dateFormat.format(java.util.Date(from))
        val end=dateFormat.format(java.util.Date(to))
        val result=mutableListOf<CashComparison>()
        val pairs=linkedSetOf<Pair<String,String>>()
        db.rawQuery("SELECT DISTINCT vault,currency FROM domain_cashTransactions WHERE vault IS NOT NULL AND currency IS NOT NULL",null).use { c ->
            while(c.moveToNext()) pairs.add(c.getString(0) to c.getString(1))
        }
        db.rawQuery("SELECT DISTINCT account,currency FROM native_sale_collections UNION SELECT DISTINCT account,currency FROM native_sale_mixed UNION SELECT DISTINCT payout_account,payout_currency FROM native_sale_change",null).use { c ->
            while(c.moveToNext()) pairs.add(c.getString(0) to c.getString(1))
        }
        for((account,currency) in pairs) {
            val legacy=db.rawQuery("SELECT COALESCE(SUM(amount),0) FROM domain_cashTransactions WHERE vault=? AND currency=? AND substr(date,1,10)>=? AND substr(date,1,10)<?",
                arrayOf(account,currency,start,end)).use { c -> c.moveToFirst();c.getDouble(0) }
            val native=accountMovement(db,account,currency,from,to)
            result.add(CashComparison(account,currency,legacy,native,legacy+native))
        }
        return result
    }
    data class LegacyMonthlyTotals(val currency:String,val sales:Double,val withdrawals:Double)
    fun legacyMonthlyTotals(db:SQLiteDatabase,month:String):List<LegacyMonthlyTotals> {
        require(Regex("\\d{4}-\\d{2}").matches(month))
        LegacyDomainTables.create(db)
        return listOf("USD","TRY","SYP").map { currency ->
            fun total(sql:String):Double=db.rawQuery(sql,arrayOf(currency,month+"%")).use { c ->
                c.moveToFirst();c.getDouble(0)
            }
            LegacyMonthlyTotals(currency,
                total("SELECT COALESCE(SUM(total),0) FROM domain_sales WHERE currency=? AND date LIKE ?"),
                total("SELECT COALESCE(SUM(amount),0) FROM domain_partnerWithdrawals WHERE currency=? AND date LIKE ?"))
        }
    }
    data class Audit(val currency:String,val sales:Double,val collected:Double,
        val returned:Double,val recognizedProfit:Double)
    fun monthlyAudit(db:SQLiteDatabase,from:Long,to:Long):List<Audit> {
        require(to>from)
        create(db)
        val profit=profitByCurrency(db,from,to)
        return listOf("USD","TRY","SYP").map { currency ->
            fun sum(sql:String):Double=db.rawQuery(sql,arrayOf(currency,from.toString(),to.toString())).use { c ->
                c.moveToFirst();c.getDouble(0)
            }
            val sales=sum("SELECT COALESCE(SUM(total),0) FROM native_sales WHERE currency=? AND created_at>=? AND created_at<?")
            val collected=sum("SELECT COALESCE(SUM(c.amount),0) FROM native_sale_collections c JOIN native_sales s ON s.id=c.sale_id WHERE s.currency=? AND c.created_at>=? AND c.created_at<?")
            val returned=sum("SELECT COALESCE(SUM(r.refund),0) FROM native_sale_returns r JOIN native_sales s ON s.id=r.sale_id WHERE s.currency=? AND r.created_at>=? AND r.created_at<?")
            Audit(currency,sales,collected,returned,profit[currency] ?: 0.0)
        }
    }
    /** Returned cost uses weighted average historical cost of the sold product. */
    fun returnedCost(db:SQLiteDatabase,saleId:String):Double {
        create(db)
        db.rawQuery("SELECT COALESCE(SUM(r.quantity * (SELECT SUM(l.unit_cost*l.quantity)*1.0/NULLIF(SUM(l.quantity),0) FROM native_sale_lines l WHERE l.sale_id=r.sale_id AND l.product_id=r.product_id)),0) FROM native_sale_returns r WHERE r.sale_id=?",
            arrayOf(saleId)).use { c ->
            c.moveToFirst()
            return c.getDouble(0)
        }
    }
    /** Profit is earned only on the actual collection day, proportionally to cash received. */
    fun recognizedProfit(db:SQLiteDatabase,from:Long,to:Long):Double {
        create(db)
        db.rawQuery("SELECT COALESCE(SUM(CASE WHEN s.total>0 THEN c.amount*(s.total-s.cost)/s.total ELSE 0 END),0) FROM native_sale_collections c JOIN native_sales s ON s.id=c.sale_id WHERE c.created_at>=? AND c.created_at<?",arrayOf(from.toString(),to.toString())).use { c ->
            return if(c.moveToFirst()) c.getDouble(0) else 0.0
        }
    }
}
