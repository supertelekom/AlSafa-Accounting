package com.alsafa.native6

import org.json.JSONObject

/**
 * Post-import evidence, calculated from SQLite rather than trusting the backup's
 * advertised counts. This is NOT a financial activation certificate.
 */
object LegacyRestoreReceipt {
    fun inspect(store:Store):JSONObject {
        val rows=store.rows("SELECT value FROM settings WHERE key=?", "legacy_snapshot_import")
        require(rows.size==1) { "لا توجد عملية استيراد قديمة معتمدة" }
        val metadata=JSONObject(rows.single().getValue("value"))
        require(metadata.optString("scope")=="inventory_suppliers_repairs_only")
        require(metadata.optString("financialPostingStatus")=="NOT_POSTED")
        require(store.rows("PRAGMA integrity_check").single().values.single()=="ok") {
            "فشل فحص سلامة قاعدة البيانات بعد الاستعادة"
        }
        require(store.rows("PRAGMA foreign_key_check").isEmpty()) {
            "مراجع مفقودة في قاعدة البيانات بعد الاستعادة"
        }
        require(store.rows(
            "SELECT event_id,currency,sum(amount) AS total FROM journal " +
                "GROUP BY event_id,currency HAVING total<>0"
        ).isEmpty()) { "قيود المخزون الافتتاحية غير متوازنة" }
        val counts=metadata.getJSONObject("sourceCollections")
        val products=store.scalar("SELECT count(*) FROM products WHERE kind=?", "watch")
        val parts=store.scalar("SELECT count(*) FROM products WHERE kind=?", "part")
        val repairs=store.scalar("SELECT count(*) FROM repairs")
        val suppliers=store.scalar("SELECT count(*) FROM legacy_records WHERE category=?", "source_suppliers")
        require(products==counts.getLong("products") &&
            parts==counts.getLong("maintenanceParts") &&
            repairs==counts.getLong("repairs") &&
            suppliers==counts.getLong("suppliers")) {
            "اختلاف عدد سجلات الاستعادة عن النسخة الأصلية"
        }
        val financialSections=listOf("sales","purchases","payments","supplierLedger",
            "cashTransactions","dailyClosings","partnerWithdrawals","returns")
        val archived=JSONObject()
        for(section in financialSections) {
            val actual=store.scalar("SELECT count(*) FROM legacy_records WHERE category=?",section)
            require(actual==counts.optLong(section,0L)) { "نقص في أرشيف $section" }
            archived.put(section,actual)
        }
        // Reconcile all archived arrays, including collections introduced in later
        // legacy-app versions. Newer backups must not silently drop new sections.
        val excluded=setOf("products","maintenanceParts","suppliers","repairs")
        for (section in counts.keys()) {
            if(section in excluded) continue
            val expected=counts.optLong(section,0L)
            val actual=store.scalar("SELECT count(*) FROM legacy_records WHERE category=?",section)
            require(actual==expected) { "أرشيف القسم الجديد أو القديم غير مكتمل: $section" }
        }
        require(store.scalar("SELECT count(*) FROM invoices")==0L &&
            store.scalar("SELECT count(*) FROM receipts")==0L &&
            store.scalar("SELECT count(*) FROM journal WHERE account LIKE ?", "cash:%")==0L &&
            store.scalar("SELECT count(*) FROM journal WHERE account LIKE ?", "payable:%")==0L) {
            "ظهرت قيود مالية تشغيلية غير متوقعة أثناء استعادة الأرشيف"
        }
        val stockMoveCount=store.scalar("SELECT count(*) FROM stock_moves")
        val nonzeroStockProducts=store.scalar("SELECT count(*) FROM products WHERE stock>0")
        require(stockMoveCount==nonzeroStockProducts) {
            "عدد حركات الجرد الافتتاحي لا يطابق المنتجات ذات المخزون"
        }
        require(store.scalar("SELECT count(*) FROM events WHERE kind=?", "OPENING_STOCK")==
            products+parts) { "عدد أحداث المخزون الافتتاحي غير مطابق" }
        require(store.scalar("SELECT count(*) FROM events WHERE kind<>?", "OPENING_STOCK")==0L) {
            "أحداث مالية تاريخية غير متوقعة أثناء الاستعادة"
        }
        require(store.rows(
            "SELECT p.id FROM products p LEFT JOIN stock_moves m ON m.product_id=p.id " +
                "GROUP BY p.id HAVING p.stock<>coalesce(sum(m.quantity),0) OR " +
                "p.value<>coalesce(sum(m.value_delta),0)"
        ).isEmpty()) { "حركة المخزون أو قيمته غير متطابقة مع جدول المنتجات" }
        val quantities=JSONObject()
        val values=JSONObject()
        for(kind in listOf("watch","part")) {
            quantities.put(kind,store.scalar("SELECT coalesce(sum(stock),0) FROM products WHERE kind=?",kind))
            for(currency in listOf("USD","TRY","SYP")) {
                values.put("$kind:$currency",store.scalar(
                    "SELECT coalesce(sum(value),0) FROM products WHERE kind=? AND currency=?",kind,currency))
            }
        }
        for(currency in listOf("USD","TRY","SYP")) {
            val stockValue=store.scalar(
                "SELECT coalesce(sum(value),0) FROM products WHERE currency=?",currency)
            val journalValue=store.scalar(
                "SELECT coalesce(sum(amount),0) FROM journal WHERE account=? AND currency=?",
                "inventory",currency)
            require(stockValue==journalValue) {
                "قيمة المخزون لا تتطابق مع قيد الجرد الافتتاحي بعملة $currency"
            }
        }
        return JSONObject()
            .put("sourceSha256",metadata.getString("sourceSha256"))
            .put("products",products)
            .put("maintenanceParts",parts)
            .put("suppliers",suppliers)
            .put("repairs",repairs)
            .put("stockQuantities",quantities)
            .put("stockValuesMinorUnits",values)
            .put("archivedFinancialRecords",archived)
            .put("financialPostingStatus","NOT_POSTED")
            .put("financialActivationPermitted",false)
    }
}
