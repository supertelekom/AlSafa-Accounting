package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Operational receivables for restored sales. Historical paid snapshots are opening
 * balances, not new cash receipts. All money remains exact decimal text.
 */
object LegacyCustomerAccounts {
    private const val table="legacy_customer_accounts"
    private const val receipts="legacy_customer_receipts"
    private fun amount(s:String):BigDecimal =
        s.toBigDecimal().also { require(it.signum()>=0 && it.precision()<=1000) { "مبلغ تاريخي غير صالح" } }
    private fun sql(store:Store) {
        store.db.execSQL("""CREATE TABLE IF NOT EXISTS $table(
            archive_id TEXT PRIMARY KEY NOT NULL,
            currency TEXT NOT NULL CHECK(currency IN ('USD','TRY','SYP')),
            invoice_number TEXT NOT NULL,
            party TEXT NOT NULL,
            phone TEXT NOT NULL,
            invoice_date TEXT NOT NULL,
            total_exact TEXT NOT NULL,
            paid_at_import_exact TEXT NOT NULL,
            source_payload_hash TEXT NOT NULL
        )""")
        store.db.execSQL("""CREATE TABLE IF NOT EXISTS $receipts(
            id TEXT PRIMARY KEY NOT NULL,
            archive_id TEXT NOT NULL REFERENCES $table(archive_id),
            date TEXT NOT NULL,
            currency TEXT NOT NULL,
            amount_exact TEXT NOT NULL,
            vault TEXT NOT NULL,
            journal_event_id TEXT NOT NULL UNIQUE REFERENCES events(id)
        )""")
        store.db.execSQL("""CREATE INDEX IF NOT EXISTS legacy_customer_receipts_invoice
            ON $receipts(archive_id,date)""")
        store.db.execSQL("""CREATE TRIGGER IF NOT EXISTS legacy_customer_accounts_immutable
            BEFORE UPDATE ON $table BEGIN SELECT RAISE(ABORT,'Immutable imported debt'); END""")
        store.db.execSQL("""CREATE TRIGGER IF NOT EXISTS legacy_customer_accounts_no_delete
            BEFORE DELETE ON $table BEGIN SELECT RAISE(ABORT,'Immutable imported debt'); END""")
        store.db.execSQL("""CREATE TRIGGER IF NOT EXISTS legacy_customer_receipts_immutable
            BEFORE UPDATE ON $receipts BEGIN SELECT RAISE(ABORT,'Immutable legacy receipt'); END""")
        store.db.execSQL("""CREATE TRIGGER IF NOT EXISTS legacy_customer_receipts_no_delete
            BEFORE DELETE ON $receipts BEGIN SELECT RAISE(ABORT,'Immutable legacy receipt'); END""")
    }
    /** Idempotent activation: create an exact receivable subledger, without historical cash or stock posting. */
    fun activate(store:Store):JSONObject=store.transaction {
        LegacyArchivedDebtReport.verifyArchiveCounts(store)
        sql(store)
        val archived=store.legacyRows("sales")
        val existing=store.scalar("SELECT count(*) FROM $table")
        require(existing==0L || existing==archived.size.toLong()) {
            "دفتر الذمم القديمة غير مكتمل؛ أوقفت التشغيل دون إضافة قيود"
        }
        var added=0
        for(record in archived) {
            val invoice=JSONObject(record.getValue("payload"))
            val total=amount(invoice.optString("total"))
            val paid=amount(invoice.optString("paid","0"))
            require(total>BigDecimal.ZERO && paid<=total) { "مبالغ الفاتورة القديمة غير متسقة" }
            val currency=invoice.optString("currency")
            require(currency in listOf("USD","TRY","SYP")) { "عملة فاتورة قديمة غير معروفة" }
            val date=invoice.optString("date").take(10)
            LocalDate.parse(date)
            val key=record.getValue("legacy_id")
            val hash=java.security.MessageDigest.getInstance("SHA-256")
                .digest(record.getValue("payload").toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val original=store.rows("SELECT archive_id,currency,total_exact,paid_at_import_exact,source_payload_hash FROM $table WHERE archive_id=?",key).singleOrNull()
            if(original!=null) {
                require(original.getValue("currency")==currency &&
                    amount(original.getValue("total_exact")).compareTo(total)==0 &&
                    amount(original.getValue("paid_at_import_exact")).compareTo(paid)==0 &&
                    original.getValue("source_payload_hash")==hash) {
                    "تعارض في فاتورة مستوردة؛ لم يتم استبدال بياناتها"
                }
                continue
            }
            store.insert(table,"archive_id" to key,"currency" to currency,
                "invoice_number" to invoice.optString("number"),
                "party" to invoice.optString("customer",invoice.optString("customerName")),
                "phone" to invoice.optString("phone"),"invoice_date" to date,
                "total_exact" to total.toPlainString(),
                "paid_at_import_exact" to paid.toPlainString(),
                "source_payload_hash" to hash)
            added++
        }
        require(store.scalar("SELECT count(*) FROM $table")==archived.size.toLong()) {
            "لم يكتمل إنشاء دفتر الذمم القديمة"
        }
        // Establish journal-backed opening receivables without touching cash or stock.
        // The journal stores cents; exact sub-cent remainders remain in the immutable
        // decimal subledger until an explicit precision policy is approved.
        var opened=0
        var fractional=0
        for(row in store.rows("SELECT archive_id,currency,total_exact,paid_at_import_exact FROM $table")) {
            val key=row.getValue("archive_id")
            val currency=row.getValue("currency")
            val due=amount(row.getValue("total_exact"))
                .subtract(amount(row.getValue("paid_at_import_exact")))
            val cents=due.movePointRight(2).setScale(0,java.math.RoundingMode.DOWN).longValueExact()
            if(due.movePointRight(2).stripTrailingZeros().scale()>0) fractional++
            val openingId="legacy:opening:$key"
            val already=store.scalar("SELECT count(*) FROM events WHERE id=?",openingId)
            if(already>0L) {
                require(store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=? AND event_id=?",
                    "legacy_receivable:$key",currency,openingId)==cents) {
                    "قيد افتتاحي تاريخي لا يطابق ذمة العميل"
                }
                continue
            }
            require(store.scalar("SELECT count(*) FROM $receipts WHERE archive_id=?",key)==0L) {
                "لا يمكن إنشاء رصيد افتتاحي بعد تسجيل قبض سابق"
            }
            if(cents==0L) continue
            store.insert("events","id" to openingId,"kind" to "LEGACY_RECEIVABLE_OPENING",
                "date" to Store.today(),"created" to System.currentTimeMillis(),
                "memo" to "ذمة افتتاحية من النسخة القديمة $key")
            store.insert("journal","event_id" to openingId,"line" to 0,
                "account" to "legacy_receivable:$key","currency" to currency,"amount" to cents)
            store.insert("journal","event_id" to openingId,"line" to 1,
                "account" to "equity:legacy_receivable_opening","currency" to currency,"amount" to -cents)
            opened++
        }
        JSONObject().put("invoices",archived.size).put("added",added)
            .put("openingCashPosted",false).put("stockReposted",false)
            .put("openingReceivableEntries",opened).put("subCentRemainders",fractional)
    }
    fun outstanding(store:Store,archiveId:String):BigDecimal {
        val row=store.rows("SELECT total_exact,paid_at_import_exact FROM $table WHERE archive_id=?",archiveId)
            .singleOrNull() ?: error("فاتورة تاريخية غير مفعلة")
        var due=amount(row.getValue("total_exact")).subtract(amount(row.getValue("paid_at_import_exact")))
        for(receipt in store.rows("SELECT amount_exact FROM $receipts WHERE archive_id=?",archiveId)) {
            due=due.subtract(amount(receipt.getValue("amount_exact")))
        }
        require(due.signum()>=0) { "التحصيلات تجاوزت رصيد الفاتورة القديمة" }
        return due
    }
    /** Reconcile every posted collection against its two immutable journal lines. */
    fun reconcile(store:Store):JSONObject {
        val exists=store.scalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='$table'")
        if(exists==0L) return JSONObject().put("activated",false).put("invoices",0)
        val issues=org.json.JSONArray()
        var total=0
        var collected=0
        for(row in store.rows("SELECT archive_id,currency,total_exact,paid_at_import_exact FROM $table")) {
            total++
            val id=row.getValue("archive_id")
            val currency=row.getValue("currency")
            val due=amount(row.getValue("total_exact")).subtract(amount(row.getValue("paid_at_import_exact")))
            val opening=store.scalar(
                "SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=? AND event_id=?",
                "legacy_receivable:$id",currency,"legacy:opening:$id")
            val expected=due.movePointRight(2).setScale(0,java.math.RoundingMode.DOWN).longValueExact()
            val openingId="legacy:opening:$id"
            val equity=store.scalar(
                "SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=? AND event_id=?",
                "equity:legacy_receivable_opening",currency,openingId)
            val openingLines=store.scalar("SELECT count(*) FROM journal WHERE event_id=?",openingId)
            if(opening!=expected || (expected>0L && (equity != -expected || openingLines!=2L)) ||
                (expected==0L && openingLines!=0L)) {
                issues.put("Opening receivable mismatch: $id")
            }
            var received=BigDecimal.ZERO
            for(receipt in store.rows("SELECT * FROM $receipts WHERE archive_id=?",id)) {
                collected++
                val receiptId=receipt.getValue("id")
                val cash=store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account=? AND currency=?",
                    receiptId,"cash:"+receipt.getValue("vault"),currency)
                val receivable=store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE event_id=? AND account=? AND currency=?",
                    receiptId,"legacy_receivable:$id",currency)
                val value=amount(receipt.getValue("amount_exact"))
                val minor=runCatching { value.movePointRight(2).longValueExact() }.getOrNull()
                if(minor==null || cash!=minor || receivable!= -cash ||
                    receipt.getValue("currency")!=currency ||
                    store.scalar("SELECT count(*) FROM journal WHERE event_id=?",receiptId)!=2L) {
                    issues.put("Receipt journal mismatch: $receiptId")
                }
                received=received.add(value)
            }
            val remaining=due.subtract(received)
            val journalBalance=store.scalar(
                "SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=?",
                "legacy_receivable:$id",currency)
            val expectedBalance=remaining.movePointRight(2).setScale(0,java.math.RoundingMode.DOWN).longValueExact()
            if(remaining.signum()<0 || journalBalance!=expectedBalance) {
                issues.put("Outstanding receivable mismatch: $id")
            }
        }
        return JSONObject().put("activated",true).put("invoices",total)
            .put("newReceipts",collected).put("issues",issues)
            .put("reconciled",issues.length()==0)
    }
    /** Post only new money received after migration; never replay historical payments. */
    fun collect(store:Store,archiveId:String,minor:Long,vault:String,date:String=Store.today(),
                command:String=Store.id()):BigDecimal=store.transaction {
        require(minor>0 && vault in Store.vaults)
        val day=LocalDate.parse(date)
        require(!day.isAfter(LocalDate.now())) { "تاريخ القبض في المستقبل" }
        sql(store)
        val invoice=store.rows("SELECT currency,invoice_date FROM $table WHERE archive_id=?",archiveId)
            .singleOrNull() ?: error("يجب تفعيل الفواتير القديمة قبل القبض")
        require(!day.isBefore(LocalDate.parse(invoice.getValue("invoice_date"))))
        val openingDate=store.rows(
            "SELECT date FROM events WHERE id=?", "legacy:opening:$archiveId"
        ).singleOrNull()?.getValue("date") ?: error("القيد الافتتاحي لهذه الذمة غير موجود")
        require(!day.isBefore(LocalDate.parse(openingDate))) {
            "لا يمكن تسجيل قبض قبل تاريخ ترحيل الرصيد الافتتاحي"
        }
        val lastReceipt=store.rows(
            "SELECT max(date) AS last_date FROM $receipts WHERE archive_id=?",archiveId
        ).single().getValue("last_date")
        require(lastReceipt.isBlank() || day.toString()>=lastReceipt) {
            "لا يمكن إدخال قبض بتاريخ أقدم من آخر قبض مسجل"
        }
        val currency=invoice.getValue("currency")
        val amount=BigDecimal.valueOf(minor,2)
        val due=outstanding(store,archiveId)
        val reconciliation=reconcile(store)
        require(reconciliation.optBoolean("reconciled")) {
            "توجد فروقات في دفتر ذمم العملاء؛ أوقفت القبض حتى تصحيحها"
        }
        // Do not allow a receipt to drive the journal's legacy receivable account
        // negative. Opening receivables must be posted and reconciled first.
        val opening=store.scalar("SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=?",
            "legacy_receivable:$archiveId",currency)
        require(opening>=minor) {
            "لم يُثبت الرصيد الافتتاحي لهذه الذمة في دفتر القيود؛ تم إيقاف القبض لحماية الحسابات"
        }
        require(amount<=due) { "الدفعة تتجاوز المتبقي" }
        require(store.scalar("SELECT count(*) FROM $receipts WHERE id=?",command)==0L) { "رقم قبض مستخدم" }
        // Legacy receivables are a separate subledger: cash is real, offset is a
        // legacy receivable, not sales revenue or a second historical receipt.
        store.insert("events","id" to command,"kind" to "LEGACY_RECEIPT",
            "date" to day.toString(),"created" to System.currentTimeMillis(),
            "memo" to "قبض ذمة مبيع قديم $archiveId")
        store.insert("journal","event_id" to command,"line" to 0,
            "account" to "cash:$vault","currency" to currency,"amount" to minor)
        store.insert("journal","event_id" to command,"line" to 1,
            "account" to "legacy_receivable:$archiveId","currency" to currency,"amount" to -minor)
        store.insert(receipts,"id" to command,"archive_id" to archiveId,
            "date" to day.toString(),"currency" to currency,
            "amount_exact" to amount.toPlainString(),"vault" to vault,
            "journal_event_id" to command)
        outstanding(store,archiveId)
    }
}
