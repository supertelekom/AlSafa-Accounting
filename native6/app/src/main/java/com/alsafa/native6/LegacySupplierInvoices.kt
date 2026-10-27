package com.alsafa.native6

import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.security.MessageDigest

/**
 * Historical PURCHASE INVOICE obligations only.
 * Never adds supplierLedger balances on top: that ledger may already represent
 * these very same invoices. No historical stock or cash replay.
 */
object LegacySupplierInvoices {
    private const val accounts="legacy_purchase_payables"
    private const val payments="legacy_purchase_payments"
    private fun money(raw:String):BigDecimal=raw.toBigDecimal().also {
        require(it.signum()>=0 && it.precision()<=1000) { "مبلغ مورد تاريخي غير صالح" }
    }
    private fun schema(store:Store) {
        store.db.execSQL("""CREATE TABLE IF NOT EXISTS $accounts(
            archive_id TEXT PRIMARY KEY NOT NULL,
            invoice_number TEXT NOT NULL,
            supplier_name TEXT NOT NULL,
            supplier_id TEXT NOT NULL,
            currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),
            invoice_date TEXT NOT NULL,
            total_exact TEXT NOT NULL,
            paid_at_import_exact TEXT NOT NULL,
            payload_hash TEXT NOT NULL
        )""")
        store.db.execSQL("""CREATE TABLE IF NOT EXISTS $payments(
            id TEXT PRIMARY KEY NOT NULL,
            archive_id TEXT NOT NULL REFERENCES $accounts(archive_id),
            date TEXT NOT NULL,
            currency TEXT NOT NULL,
            amount_exact TEXT NOT NULL,
            vault TEXT NOT NULL,
            event_id TEXT NOT NULL UNIQUE REFERENCES events(id)
        )""")
        store.db.execSQL("""CREATE INDEX IF NOT EXISTS legacy_purchase_payments_by_invoice
            ON $payments(archive_id,date)""")
        for(t in listOf(accounts,payments)) {
            store.db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${t}_immutable_update
                BEFORE UPDATE ON $t BEGIN SELECT RAISE(ABORT,'Immutable legacy supplier entry'); END""")
            store.db.execSQL("""CREATE TRIGGER IF NOT EXISTS ${t}_immutable_delete
                BEFORE DELETE ON $t BEGIN SELECT RAISE(ABORT,'Immutable legacy supplier entry'); END""")
        }
    }
    fun activate(store:Store):JSONObject=store.transaction {
        LegacyArchivedDebtReport.verifyArchiveCounts(store)
        schema(store)
        val archive=store.legacyRows("purchases")
        // A supplier ledger entry that explicitly references a purchase but
        // disagrees on supplier or currency makes financial activation unsafe.
        val overlap=LegacySupplierOverlap.inspect(store)
        require(overlap.getJSONArray("conflictingLedgerReferences").length()==0) {
            "تعارض في مرجع فاتورة شراء مع المورد أو العملة في دفتر المورد القديم"
        }
        require(store.scalar("SELECT count(*) FROM $accounts") in listOf(0L,archive.size.toLong())) {
            "دفتر مشتريات قديم غير مكتمل"
        }
        var inserted=0
        var opened=0
        var subcent=0
        for(record in archive) {
            val invoice=JSONObject(record.getValue("payload"))
            val key=record.getValue("legacy_id")
            val total=money(invoice.optString("total"))
            val paid=money(invoice.optString("paid","0"))
            require(total>BigDecimal.ZERO && paid<=total)
            val currency=invoice.optString("currency")
            require(currency in setOf("USD","TRY","SYP"))
            val date=invoice.optString("date").take(10)
            LocalDate.parse(date)
            val hash=MessageDigest.getInstance("SHA-256")
                .digest(record.getValue("payload").toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val existing=store.rows("SELECT * FROM $accounts WHERE archive_id=?",key).singleOrNull()
            if(existing==null) {
                store.insert(accounts,"archive_id" to key,
                    "invoice_number" to invoice.optString("invoiceNo").ifBlank { invoice.optString("number") },
                    "supplier_name" to invoice.optString("supplierName"),
                    "supplier_id" to invoice.optString("supplierId"),
                    "currency" to currency,"invoice_date" to date,
                    "total_exact" to total.toPlainString(),
                    "paid_at_import_exact" to paid.toPlainString(),"payload_hash" to hash)
                inserted++
            } else {
                require(existing.getValue("payload_hash")==hash &&
                    existing.getValue("currency")==currency &&
                    money(existing.getValue("total_exact")).compareTo(total)==0 &&
                    money(existing.getValue("paid_at_import_exact")).compareTo(paid)==0) {
                    "تعارض في بيانات فاتورة شراء مستعادة"
                }
            }
            val due=total.subtract(paid)
            val minor=due.movePointRight(2).setScale(0,RoundingMode.DOWN).longValueExact()
            if(due.movePointRight(2).stripTrailingZeros().scale()>0) subcent++
            val eventId="legacy:purchase:opening:$key"
            if(store.scalar("SELECT count(*) FROM events WHERE id=?",eventId)>0L) {
                require(store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account=? AND currency=?",
                    eventId,"legacy_payable:$key",currency)== -minor) {
                    "رصيد مورد افتتاحي لا يطابق المصدر"
                }
                continue
            }
            require(store.scalar("SELECT count(*) FROM $payments WHERE archive_id=?",key)==0L)
            if(minor==0L) continue
            store.insert("events","id" to eventId,"kind" to "LEGACY_PURCHASE_OPENING",
                "date" to Store.today(),"created" to System.currentTimeMillis(),
                "memo" to "ذمة فاتورة شراء قديمة $key")
            store.insert("journal","event_id" to eventId,"line" to 0,
                "account" to "equity:legacy_purchase_opening","currency" to currency,"amount" to minor)
            store.insert("journal","event_id" to eventId,"line" to 1,
                "account" to "legacy_payable:$key","currency" to currency,"amount" to -minor)
            opened++
        }
        require(store.scalar("SELECT count(*) FROM $accounts")==archive.size.toLong())
        JSONObject().put("invoices",archive.size).put("newAccounts",inserted)
            .put("openingEntries",opened).put("subCentRemainders",subcent)
            .put("supplierLedgerAdded",false).put("historicalCashReplayed",false)
            .put("historicalStockReplayed",false)
    }
    fun remaining(store:Store,key:String):BigDecimal {
        val row=store.rows("SELECT total_exact,paid_at_import_exact FROM $accounts WHERE archive_id=?",key)
            .singleOrNull() ?: error("فاتورة شراء تاريخية غير مفعلة")
        var due=money(row.getValue("total_exact")).subtract(money(row.getValue("paid_at_import_exact")))
        for(payment in store.rows("SELECT amount_exact FROM $payments WHERE archive_id=?",key)) {
            due=due.subtract(money(payment.getValue("amount_exact")))
        }
        require(due.signum()>=0) { "مدفوعات المورد تجاوزت الفاتورة" }
        return due
    }
    fun page(store:Store,term:String="",limit:Int=50,offset:Int=0):List<Map<String,String>> {
        require(limit in 1..200 && offset>=0 && term.length<=200)
        if(store.scalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='$accounts'")==0L) return emptyList()
        val result=mutableListOf<Map<String,String>>()
        for(row in store.rows("SELECT archive_id,invoice_number,supplier_name,currency FROM $accounts ORDER BY invoice_date DESC,archive_id DESC")) {
            if(term.isNotBlank() && listOf("invoice_number","supplier_name","archive_id").none {
                row.getValue(it).contains(term,true)
            }) continue
            val due=remaining(store,row.getValue("archive_id"))
            if(due.signum()>0) result.add(row+mapOf("remaining_exact" to due.toPlainString()))
        }
        return result.drop(offset).take(limit)
    }
    fun reconcile(store:Store):JSONObject {
        if(store.scalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='$accounts'")==0L)
            return JSONObject().put("activated",false)
        val issues=JSONArray()
        var invoices=0
        var paymentsCount=0
        val archive=store.legacyRows("purchases")
        val archivedIds=archive.map { it.getValue("legacy_id") }.toSet()
        val activatedIds=store.rows("SELECT archive_id FROM $accounts")
            .map { it.getValue("archive_id") }.toSet()
        if(archivedIds!=activatedIds) issues.put("Archived purchase invoice identity mismatch")
        for(source in archive) {
            val key=source.getValue("legacy_id")
            val hash=MessageDigest.getInstance("SHA-256")
                .digest(source.getValue("payload").toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val stored=store.rows("SELECT payload_hash FROM $accounts WHERE archive_id=?",key)
                .singleOrNull()
            if(stored==null || stored.getValue("payload_hash")!=hash)
                issues.put("Archived purchase invoice source mismatch: $key")
        }
        for(row in store.rows("SELECT * FROM $accounts")) {
            invoices++
            val key=row.getValue("archive_id")
            val currency=row.getValue("currency")
            val original=money(row.getValue("total_exact")).subtract(money(row.getValue("paid_at_import_exact")))
            val minor=original.movePointRight(2).setScale(0,RoundingMode.DOWN).longValueExact()
            val eventId="legacy:purchase:opening:$key"
            val opening=store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account=? AND currency=?",
                eventId,"legacy_payable:$key",currency)
            val equity=store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account=? AND currency=?",
                eventId,"equity:legacy_purchase_opening",currency)
            if(opening!= -minor || equity!=minor ||
                store.scalar("SELECT count(*) FROM journal WHERE event_id=?",eventId)!=(if(minor>0L)2L else 0L))
                issues.put("Opening payable mismatch: $key")
            for(payment in store.rows("SELECT * FROM $payments WHERE archive_id=?",key)) {
                paymentsCount++
                val id=payment.getValue("id")
                val amount=money(payment.getValue("amount_exact")).movePointRight(2).longValueExact()
                val debit=store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account=? AND currency=?",
                    id,"legacy_payable:$key",currency)
                val cash=store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account=? AND currency=?",
                    id,"cash:"+payment.getValue("vault"),currency)
                if(debit!=amount || cash!= -amount ||
                    store.scalar("SELECT count(*) FROM journal WHERE event_id=?",id)!=2L)
                    issues.put("Supplier payment journal mismatch: $id")
            }
            val journal=store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=?",
                "legacy_payable:$key",currency)
            val expected=remaining(store,key).movePointRight(2).setScale(0,RoundingMode.DOWN).longValueExact()
            if(journal!= -expected) issues.put("Supplier payable mismatch: $key")
        }
        return JSONObject().put("activated",true).put("invoices",invoices)
            .put("newPayments",paymentsCount).put("issues",issues)
            .put("reconciled",issues.length()==0)
    }
    fun pay(store:Store,key:String,minor:Long,vault:String,date:String=Store.today(),
            command:String=Store.id()):BigDecimal=store.transaction {
        require(minor>0 && vault in Store.vaults)
        val day=LocalDate.parse(date)
        require(!day.isAfter(LocalDate.now()))
        schema(store)
        val row=store.rows("SELECT currency,invoice_date FROM $accounts WHERE archive_id=?",key)
            .singleOrNull() ?: error("فاتورة شراء غير مفعلة")
        val currency=row.getValue("currency")
        require(store.scalar("SELECT count(*) FROM events WHERE id=?",command)==0L) {
            "معرف عملية الدفع مستخدم مسبقاً"
        }
        val openingDate=store.rows("SELECT date FROM events WHERE id=?", "legacy:purchase:opening:$key")
            .singleOrNull()?.getValue("date") ?: error("رصيد مورد افتتاحي غير مثبت")
        require(!day.isBefore(LocalDate.parse(openingDate)) &&
            !day.isBefore(LocalDate.parse(row.getValue("invoice_date"))))
        val last=store.rows("SELECT max(date) AS last_date FROM $payments WHERE archive_id=?",key)
            .single().getValue("last_date")
        require(last.isBlank() || day.toString()>=last)
        require(reconcile(store).optBoolean("reconciled")) { "دفتر ديون الموردين غير متطابق" }
        val amount=BigDecimal.valueOf(minor,2)
        require(amount<=remaining(store,key)) { "الدفعة تتجاوز المتبقي للمورد" }
        // Do not post a supplier payment for an invoice whose immutable
        // archive source changed after activation.
        val overlap=LegacySupplierOverlap.inspect(store)
        require(overlap.getJSONArray("conflictingLedgerReferences").length()==0) {
            "توجد مراجع فواتير شراء متعارضة في دفتر المورد القديم؛ تم إيقاف الدفع"
        }
        val source=store.legacyRecord("purchases",key)
            ?: error("فاتورة الشراء الأصلية مفقودة من الأرشيف")
        val sourceHash=MessageDigest.getInstance("SHA-256")
            .digest(source.getValue("payload").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val storedHash=store.rows("SELECT payload_hash FROM $accounts WHERE archive_id=?",key)
            .single().getValue("payload_hash")
        require(sourceHash==storedHash) { "مصدر فاتورة الشراء لا يطابق الرصيد المفعل" }
        val balance=store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=?",
            "legacy_payable:$key",currency)
        require(balance<= -minor) { "رصيد المورد في دفتر القيود غير كاف" }
        // Do not permit an overdraft on a cash account from a historical supplier payment.
        val currentBalance=store.balance(vault,Currency.valueOf(currency))
        require(currentBalance>=minor) { "رصيد الخزنة غير كاف" }
        val earlierCash=store.scalar(
            "SELECT coalesce(sum(j.amount),0) FROM journal j JOIN events e ON e.id=j.event_id WHERE j.account=? AND j.currency=? AND e.date<=?",
            "cash:$vault",currency,day.toString())
        require(earlierCash>=minor) { "رصيد الخزنة بتاريخ الدفعة غير كاف" }
        // Backdated supplier payments must not make any subsequent day's cash
        // position negative, even if today's vault balance is sufficient.
        val futureDaily=store.rows("""
            SELECT e.date AS day,coalesce(sum(j.amount),0) AS change_amount
            FROM journal j JOIN events e ON e.id=j.event_id
            WHERE j.account=? AND j.currency=? AND e.date>?
            GROUP BY e.date ORDER BY e.date
        """.trimIndent(),"cash:$vault",currency,day.toString())
        var running=earlierCash-minor
        for(daily in futureDaily) {
            running=Math.addExact(running,daily.getValue("change_amount").toLong())
            require(running>=0L) {
                "دفعة المورد تجعل رصيد الخزنة سالباً بتاريخ "+daily.getValue("day")
            }
        }
        store.insert("events","id" to command,"kind" to "LEGACY_SUPPLIER_PAYMENT",
            "date" to day.toString(),"created" to System.currentTimeMillis(),
            "memo" to "دفع فاتورة شراء قديمة $key")
        store.insert("journal","event_id" to command,"line" to 0,
            "account" to "legacy_payable:$key","currency" to currency,"amount" to minor)
        store.insert("journal","event_id" to command,"line" to 1,
            "account" to "cash:$vault","currency" to currency,"amount" to -minor)
        store.insert(payments,"id" to command,"archive_id" to key,"date" to day.toString(),
            "currency" to currency,"amount_exact" to amount.toPlainString(),
            "vault" to vault,"event_id" to command)
        remaining(store,key)
    }
}
