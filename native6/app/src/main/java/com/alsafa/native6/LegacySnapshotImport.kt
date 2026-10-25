package com.alsafa.native6

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Imports only a verified current inventory snapshot from a staged AlSafa Backup v2.
 * Historical sales, cash, debts and partner profits are archived verbatim but NOT posted to the ledger.
 * The original backup remains intact in legacy_imports for a later ledger migration.
 */
object LegacySnapshotImport {
    private fun legacyMinor(raw:String):Long {
        val value=BigDecimal(raw)
        return value.setScale(2,RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }
    private fun data(payload:String):JSONObject {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup"){"يلزم ملف AlSafa Backup v2 الأصلي"}
        require(root.optInt("formatVersion")==2){"يتطلب الاستيراد نسخة الصفا بصيغة الإصدار 2 تحديداً"}
        return root.getJSONObject("data")
    }
    fun preview(payload:String):String {
        val d=data(payload)
        val manifest=LegacyRestoreManifest.inspect(payload)
        val readiness=LegacyRestoreReadiness.inspect(payload)
        val blocking=LegacyImportPreflight.inspectInventory(payload).size
        val roundedPrices=listOf("products","maintenanceParts").sumOf { section ->
            val items=d.getJSONArray(section)
            (0 until items.length()).sumOf { i ->
                val item=items.getJSONObject(i)
                listOf("cost","salePrice").count { field ->
                    val value=item.optString(field,"0").toBigDecimalOrNull()
                    value!=null && value.stripTrailingZeros().scale()>2
                }
            }
        }
        val warnings=LegacyImportPreflight.inspectFinancialHistory(payload).size
        val continuity=LegacyClosingContinuity.inspect(payload)
        val cashIssues=continuity.getJSONArray("arithmeticMismatches").length()
        val invalidClosingDates=continuity.getJSONArray("invalidDateRows").length()
        val duplicateClosingDates=continuity.getJSONArray("duplicateClosingDates").length()
        val payments=LegacyPaymentLinkAudit.inspect(payload)
        val returns=LegacyReturnLinkAudit.inspect(payload)
        val brokenPaymentLinks=payments.getJSONArray("unresolvedReferenceRows").length()+payments.getJSONArray("missingReferenceRows").length()
        val crossCurrencyPaymentCount=payments.getJSONArray("crossCurrencyPaymentRows").length()
        val cashMatch=LegacyPaymentCashAudit.inspect(payload)
        val vaults=LegacyVaultSnapshot.inspect(payload)
        val vaultPrecisionWarnings=vaults.getJSONArray("fractionalMinorUnitRows").length()
        val supplierCash=LegacySupplierCashAudit.inspect(payload)
        val matchedSupplierPayments=supplierCash.getJSONArray("matched").length()
        val unmatchedSupplierPayments=supplierCash.getJSONArray("unmatchedLedgerIndexes").length()
        val matchedCashPayments=cashMatch.getJSONArray("matches").length()
        val unmatchedCashPayments=cashMatch.getJSONArray("unmatched").length()
        val ambiguousCashPayments=cashMatch.getJSONArray("ambiguous").length()
        val brokenReturnLinks=returns.getJSONArray("unresolvedReferenceRows").length()+returns.getJSONArray("missingReferenceRows").length()
        val products=d.getJSONArray("products")
        val parts=d.getJSONArray("maintenanceParts")
        val suppliers=d.getJSONArray("suppliers")
        val repairs=d.getJSONArray("repairs")
        return "جاهزية أرشفة النسخة: ${if(readiness.getBoolean("archiveImportPermitted")) "نعم" else "لا"}، عوائق التفعيل المالي: ${readiness.getInt("financialBlockerCount")}، حركات نقدية ذات كسور دقيقة: $vaultPrecisionWarnings، التفعيل المالي: غير مسموح قبل المطابقة. أخطاء الاستيراد: $blocking، قيم أسعار ستُقرب للسنت مع حفظ الأصل: $roundedPrices، تحذيرات مالية مؤرشفة: $warnings، فروقات إقفال فعلية: $cashIssues، تواريخ إقفال غير صالحة: $invalidClosingDates، تواريخ إقفال مكررة: $duplicateClosingDates، دفعات تحتاج مطابقة مرجع: $brokenPaymentLinks، دفعات بعملة قبض مختلفة: $crossCurrencyPaymentCount، دفعات مطابقة لحركات النقد: $matchedCashPayments، غير مطابقة: $unmatchedCashPayments، ملتبسة: $ambiguousCashPayments، مرتجعات تحتاج مطابقة مرجع: $brokenReturnLinks، سداد موردين مطابق للخزنة: $matchedSupplierPayments، غير مطابق: $unmatchedSupplierPayments. بصمة النسخة: ${manifest.getString("sha256").take(16)}…، إجمالي السجلات: ${manifest.getLong("recordCount")}. مخزون الساعات: ${products.length()}، قطع الصيانة: ${parts.length()}، الموردون: ${suppliers.length()}، سجل الصيانة: ${repairs.length()}. " +
            "سيُنقل المخزون والموردون والصيانة، وتُحفظ الفواتير والحركات القديمة كأرشيف قابل للقراءة فقط، دون احتسابها في الأرصدة أو الأرباح."
    }
    fun import(store:Store,payload:String):String {
        val root=JSONObject(payload)
        val d=data(payload)
        val manifest=LegacyRestoreManifest.inspect(payload)
        val readiness=LegacyRestoreReadiness.inspect(payload)
        val issues=LegacyImportPreflight.inspectInventory(payload)
        val financialWarnings=LegacyImportPreflight.inspectFinancialHistory(payload)
        val closingAudit=LegacyClosingContinuity.inspect(payload)
        val paymentLinkAudit=LegacyPaymentLinkAudit.inspect(payload)
        val returnLinkAudit=LegacyReturnLinkAudit.inspect(payload)
        val supplierCashAudit=LegacySupplierCashAudit.inspect(payload)
        val vaultSnapshot=LegacyVaultSnapshot.inspect(payload)
        val paymentCashAudit=LegacyPaymentCashAudit.inspect(payload)
        require(issues.isEmpty()) { "فحص النسخة القديمة فشل: "+issues.take(5).joinToString("; ") }
        val lists=listOf("products" to "watch","maintenanceParts" to "part")
        val suppliers=d.getJSONArray("suppliers")
        return store.transaction {
            require(store.scalar("SELECT count(*) FROM legacy_records")==0L &&
                store.scalar("SELECT count(*) FROM repairs")==0L &&
                store.scalar("SELECT count(*) FROM products")==0L &&
                store.scalar("SELECT count(*) FROM suppliers")==0L &&
                store.scalar("SELECT count(*) FROM events")==0L &&
                store.scalar("SELECT count(*) FROM invoices")==0L &&
                store.scalar("SELECT count(*) FROM journal")==0L &&
                store.scalar("SELECT count(*) FROM receipts")==0L &&
                store.scalar("SELECT count(*) FROM stock_moves")==0L &&
                store.scalar("SELECT count(*) FROM returns")==0L &&
                store.scalar("SELECT count(*) FROM refunds")==0L &&
                store.scalar("SELECT count(*) FROM closings")==0L &&
                store.scalar("SELECT count(*) FROM settings WHERE key=?", "legacy_snapshot_import")==0L){"يجب أن تكون قاعدة Native 6 المحاسبية فارغة"}
            val seen=HashSet<String>()
            val supplierIds=HashSet<String>()
            val repairIds=HashSet<String>()
            val barcodes=HashSet<String>()
            lists.forEach { (key,kind) ->
                val a=d.getJSONArray(key)
                for(i in 0 until a.length()) {
                    val item=a.getJSONObject(i)
                    val id=item.getString("id").trim()
                    require(id.isNotBlank() && seen.add(id)){"معرف منتج مفقود أو مكرر: $key"}
                    val barcode=item.optString("barcode").trim()
                    require(barcode.isEmpty() || barcodes.add(barcode)){"باركود مكرر: $barcode"}
                    val name=item.getString("name").trim()
                    val currency=Currency.valueOf(item.getString("currency"))
                    val price=legacyMinor(item.optString("salePrice","0"))
                    val cost=legacyMinor(item.optString("cost","0"))
                    val qty=item.getLong("stock")
                    require(qty in 0L..100000000L && price>=0 && cost>=0 && name.isNotEmpty()){"بيانات مخزون غير صالحة: $id"}
                    // Preserve all legacy metadata and embedded photos as JSON details.
                    store.addProduct(name,barcode,currency,price,cost,qty,kind,item.toString(),id,
                        brand=item.optString("brand").take(500),
                        model=item.optString("model").take(500))
                }
            }
            for(i in 0 until suppliers.length()) {
                val s=suppliers.getJSONObject(i)
                val id=s.getString("id").trim()
                require(id.isNotEmpty() && supplierIds.add(id)){"معرف مورد مكرر"}
                require(s.optString("name").isNotBlank()){"اسم مورد مفقود"}
                store.insert("suppliers","id" to id,"name" to s.getString("name"),"phone" to s.optString("phone"))
                store.insert("legacy_records","category" to "source_suppliers",
                    "legacy_id" to id,"payload" to s.toString())
            }
            val repairs=d.getJSONArray("repairs")
            for(i in 0 until repairs.length()) {
                val repair=repairs.getJSONObject(i)
                val id=repair.getString("id").trim()
                require(id.isNotEmpty() && repairIds.add(id)){"معرف صيانة مكرر"}
                val originalStatus=repair.optString("status")
                val status=when(originalStatus) {
                    "تم التسليم","delivered" -> "delivered"
                    "قيد الصيانة","in_progress" -> "in_progress"
                    "جاهزة","جاهز","جاهز للاستلام","بانتظار التسليم","ready" -> "ready"
                    "مستلمة","تم الاستلام","قيد الانتظار","received" -> "received"
                    else -> if(originalStatus.isBlank()) "received" else "legacy_unknown"
                }
                store.insert("repairs","id" to id,"customer" to repair.optString("customer"),
                    "phone" to repair.optString("phone"),"watch" to repair.optString("watch"),
                    "problem" to repair.optString("problem"),"status" to status,
                    "date" to repair.optString("date"),"invoice_id" to null,
                    "details" to repair.toString())
            }
            // Preserve historical financial records verbatim for accurate later ledger
            // reconstruction. They are NOT booked as new income or cash movements.
            // Archive every other array from the original backup, including
            // future sections unknown to this app version.
            val historical=d.keys().asSequence()
                .filter { it !in setOf("products","maintenanceParts","suppliers","repairs") }
                .filter { d.optJSONArray(it)!=null }.toList()
            historical.forEach { key ->
                val array=d.optJSONArray(key) ?: return@forEach
                for(i in 0 until array.length()) {
                    // Some historical sections contain scalar values or nested arrays.
                    // Preserve their exact JSON representation instead of failing the import.
                    val raw=array.get(i)
                    val row=raw as? JSONObject
                    val sourceId=row?.optString("id")?.trim()
                    // A legacy ID may be duplicated or absent in malformed history.
                    // The array index is part of the archival key, so no entry is
                    // silently replaced and original IDs remain inside the payload.
                    val recordId="$i:${sourceId?.takeIf { it.isNotEmpty() } ?: "no-id"}"
                    val serialized=when(raw) {
                        is JSONObject -> raw.toString()
                        else -> JSONObject().put("_legacyValue",raw).toString()
                    }
                    store.insert("legacy_records","category" to key,
                        "legacy_id" to recordId,"payload" to serialized)
                }
            }
            // Retain non-list business configuration as source evidence.
            // No settings are activated until separately reviewed and mapped.
            val configurationKeys=d.keys().asSequence()
                .filter { d.optJSONArray(it)==null && d.optJSONObject(it)!=null }.toList()
            configurationKeys.forEach { key ->
                val source=d.optJSONObject(key) ?: return@forEach
                store.insert("legacy_records","category" to "configuration",
                    "legacy_id" to key,"payload" to source.toString())
            }
            // Preserve scalar metadata too (the original backup has data.version).
            val scalarKeys=d.keys().asSequence()
                .filter { d.optJSONArray(it)==null && d.optJSONObject(it)==null }.toList()
            scalarKeys.forEach { key ->
                store.insert("legacy_records","category" to "configuration",
                    "legacy_id" to key,"payload" to JSONObject()
                        .put("_legacyValue",d.opt(key)).toString())
            }
            val deviceSettings=root.optJSONObject("deviceSettings")
            if(deviceSettings!=null) store.insert("legacy_records",
                "category" to "configuration","legacy_id" to "deviceSettings",
                "payload" to deviceSettings.toString())
            val expectedArchive=suppliers.length() + historical.sumOf { d.getJSONArray(it).length() } +
                configurationKeys.size + scalarKeys.size +
                (if(deviceSettings!=null) 1 else 0)
            require(store.scalar("SELECT count(*) FROM repairs")==repairs.length().toLong()) {
                "سجل الصيانة غير مكتمل بعد الاستيراد"
            }
            for (i in 0 until suppliers.length()) {
                val original=suppliers.getJSONObject(i)
                val restored=store.rows("SELECT name,phone FROM suppliers WHERE id=?",original.getString("id").trim()).singleOrNull()
                    ?: error("مورد مفقود بعد الاستعادة: $i")
                require(restored.getValue("name")==original.getString("name") &&
                    restored.getValue("phone")==original.optString("phone")) {
                    "بيانات المورد غير مطابقة بعد الاستعادة: $i"
                }
            }
            for (i in 0 until repairs.length()) {
                val original=repairs.getJSONObject(i)
                val restored=store.rows("SELECT customer,phone,watch,problem,details FROM repairs WHERE id=?",
                    original.getString("id").trim()).singleOrNull()
                    ?: error("طلب صيانة مفقود بعد الاستعادة: $i")
                require(restored.getValue("customer")==original.optString("customer") &&
                    restored.getValue("phone")==original.optString("phone") &&
                    restored.getValue("watch")==original.optString("watch") &&
                    restored.getValue("problem")==original.optString("problem") &&
                    restored.getValue("details")==original.toString()) {
                    "بيانات طلب الصيانة غير مطابقة بعد الاستعادة: $i"
                }
            }
            require(store.scalar("SELECT count(*) FROM suppliers")==suppliers.length().toLong()) { "عدد الموردين المنقولين غير مطابق" }
            require(store.scalar(
                "SELECT count(*) FROM legacy_records WHERE category=?", "source_suppliers"
            )==suppliers.length().toLong()) {
                "نسخة بيانات الموردين الأصلية غير مكتملة"
            }
            require(store.scalar("SELECT count(*) FROM events WHERE kind<>?", "OPENING_STOCK")==0L) { "الاستعادة أنشأت حدثاً محاسبياً غير رصيد المخزون الافتتاحي" }
            require(store.scalar("SELECT count(*) FROM events WHERE kind=?", "OPENING_STOCK")==seen.size.toLong()) { "عدد أحداث المخزون الافتتاحي غير مطابق" }
            require(store.scalar("SELECT count(*) FROM journal WHERE account LIKE ?", "cash:%")==0L) { "لا يجوز ترحيل النقد ضمن استعادة المخزون" }
            require(store.scalar("SELECT count(*) FROM journal WHERE account LIKE ?", "payable:%")==0L) { "لا يجوز ترحيل ديون الموردين ضمن استعادة المخزون" }
            require(store.scalar("SELECT count(*) FROM invoices")==0L) { "استعادة المخزون لا يجوز أن تنشئ فواتير" }
            require(store.scalar("SELECT count(*) FROM invoice_lines")==0L) { "استعادة الأرشيف لا يجوز أن تنشئ بنود فواتير تشغيلية" }
            require(store.scalar("SELECT count(*) FROM receipts")==0L) { "استعادة الأرشيف لا يجوز أن تنشئ مقبوضات" }
            require(store.scalar("SELECT count(*) FROM returns")==0L && store.scalar("SELECT count(*) FROM refunds")==0L) { "استعادة الأرشيف لا يجوز أن تنشئ مرتجعات أو ردّ أموال محاسبي" }
            require(store.scalar("SELECT count(*) FROM closings")==0L) { "استعادة الأرشيف لا يجوز أن تنشئ إقفالات تشغيلية" }
            val actualArchive=store.scalar("SELECT count(*) FROM legacy_records")
            val expectedFinancialCategories=historical.filter {
                it in setOf("sales","purchases","payments","cashTransactions",
                    "dailyClosings","partnerWithdrawals","supplierLedger","returns")
            }
            expectedFinancialCategories.forEach { category ->
                val actual=store.scalar(
                    "SELECT count(*) FROM legacy_records WHERE category=?",category)
                require(actual==d.getJSONArray(category).length().toLong()) {
                    "أرشيف $category غير مكتمل: $actual"
                }
            }
            // Verify every critical financial archive row byte-for-byte as normalized JSON
            // before committing; archived money must never be silently lost or replaced.
            for (category in expectedFinancialCategories) {
                val source=d.getJSONArray(category)
                for (i in 0 until source.length()) {
                    val raw=source.get(i)
                    val sourceId=(raw as? JSONObject)?.optString("id")?.trim().orEmpty()
                    val archiveId="$i:${sourceId.ifEmpty { "no-id" }}"
                    val saved=store.rows(
                        "SELECT payload FROM legacy_records WHERE category=? AND legacy_id=?",
                        category,archiveId
                    ).singleOrNull() ?: error("سجل مالي قديم مفقود: $category / $i")
                    val expected=if(raw is JSONObject) raw.toString()
                        else JSONObject().put("_legacyValue",raw).toString()
                    require(saved.getValue("payload")==expected) {
                        "اختلاف في أرشيف السجل المالي: $category / $i"
                    }
                }
            }
            require(actualArchive==expectedArchive.toLong()) {
                "عدد السجلات التاريخية المستعادة لا يطابق النسخة الأصلية: $actualArchive / $expectedArchive"
            }
            for ((section,kind) in lists) {
                val items=d.getJSONArray(section)
                val sourceQuantity=(0 until items.length()).sumOf { i -> items.getJSONObject(i).getLong("stock") }
                for (i in 0 until items.length()) {
                    val original=items.getJSONObject(i)
                    val productId=original.getString("id").trim()
                    val restored=store.rows("SELECT stock,price,cost,barcode,details FROM products WHERE id=? AND kind=?",productId,kind).singleOrNull()
                        ?: error("منتج مفقود بعد الاستعادة: $section / $i")
                    require(restored.getValue("stock").toLong()==original.getLong("stock") &&
                        restored.getValue("price").toLong()==legacyMinor(original.optString("salePrice","0")) &&
                        restored.getValue("cost").toLong()==legacyMinor(original.optString("cost","0")) &&
                        restored.getValue("barcode")==original.optString("barcode").trim() &&
                        restored.getValue("details")==original.toString()) {
                        "اختلاف كمية أو سعر أو تكلفة المنتج بعد الاستعادة: $section / $i"
                    }
                }
                val importedQuantity=store.scalar("SELECT coalesce(sum(stock),0) FROM products WHERE kind=?",kind)
                require(sourceQuantity==importedQuantity) { "مجموع كميات $section لا يطابق النسخة الأصلية" }
            }
            require(store.scalar("SELECT count(*) FROM products WHERE kind=?", "watch")==d.getJSONArray("products").length().toLong()) {
                "عدد الساعات المنقولة غير مطابق للنسخة الأصلية"
            }
            require(store.scalar("SELECT count(*) FROM products WHERE kind=?", "part")==d.getJSONArray("maintenanceParts").length().toLong()) {
                "عدد قطع الصيانة المنقولة غير مطابق للنسخة الأصلية"
            }
            require(store.scalar("SELECT count(*) FROM products")==
                (d.getJSONArray("products").length()+d.getJSONArray("maintenanceParts").length()).toLong()) {
                "عدد سجلات المخزون لا يطابق النسخة الأصلية"
            }
            require(store.scalar("SELECT count(*) FROM repairs")==repairs.length().toLong()) {
                "عدد سجلات الصيانة لا يطابق النسخة الأصلية"
            }
            store.insert("settings","key" to "legacy_snapshot_import","value" to JSONObject()
                .put("scope","inventory_suppliers_repairs_only")
                .put("financialWarningCount",financialWarnings.size)
                .put("closingAudit",closingAudit)
                .put("restoreReadiness",readiness)
                .put("paymentLinkAudit",paymentLinkAudit)
                .put("returnLinkAudit",returnLinkAudit)
                .put("supplierCashAudit",supplierCashAudit)
                .put("vaultSnapshot",vaultSnapshot)
                .put("paymentCashAudit",paymentCashAudit)
                .put("financialPostingStatus","NOT_POSTED")
                .put("cashContinuityVerified",false)
                .put("financialWarnings",org.json.JSONArray(financialWarnings))
                .put("sourceSha256",manifest.getString("sha256"))
                .put("sourceRecordCount",manifest.getLong("recordCount"))
                .put("sourceCollections",manifest.getJSONObject("collections"))
                .put("sourceAppVersion",root.optString("appVersion","unknown"))
                .put("sourceExportedAt",root.optString("exportedAt",""))
                .put("products",d.getJSONArray("products").length())
                .put("maintenanceParts",d.getJSONArray("maintenanceParts").length())
                .put("suppliers",suppliers.length())
                .put("repairs",repairs.length())
                .put("archivedFinancialRecords",store.scalar("SELECT count(*) FROM legacy_records")).toString())
            "نُقل المخزون والموردون والصيانة، وحُفظت الحركات المالية التاريخية في أرشيف منفصل. لم تُرحّل الأرصدة والأرباح المحاسبية."
        }
    }
}
