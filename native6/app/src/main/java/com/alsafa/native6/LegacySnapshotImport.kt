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
    private fun legacyPhotos(item:JSONObject,context:android.content.Context,created:MutableList<java.io.File>):JSONObject {
        // Avoid serializing the complete record (including base64 photos) just
        // to clone it. The original source JSON is not modified: values are
        // transferred into a new object and image fields are removed there.
        val copy=JSONObject()
        val photoFields=setOf("photo","photo2","photo3","couplePhoto","barcodePhoto","watchPhoto")
        // Do not put base64 image strings into the destination object. The
        // source remains intact for audit, and photo bytes are decoded once.
        for(key in item.keys()) {
            if(key !in photoFields) copy.put(key,item.get(key))
            else {
                val raw=item.optString(key)
                if(!raw.startsWith("data:image/")) copy.put(key,item.get(key))
            }
        }
        val productPhotos=org.json.JSONArray()
        val directory=java.io.File(context.filesDir,"images").apply { require(isDirectory || mkdirs()) }
        for(field in listOf("photo","photo2","photo3","couplePhoto","barcodePhoto","watchPhoto")) {
            val raw=item.optString(field)
            if(raw.isBlank()) continue
            if(!raw.startsWith("data:image/")) continue
            require(raw.contains(";base64,")) { "صورة مضمّنة دون ترميز Base64 صحيح: $field" }
            val mime=raw.substringAfter("data:").substringBefore(";base64,")
            require(mime in setOf("image/jpeg","image/png","image/webp")) { "نوع صورة غير مدعوم: $field" }
            // Avoid allocating a second multi-megabyte String via substringAfter.
            // Android Base64 accepts the data URI prefix only after its comma.
            val separator=raw.indexOf(";base64,")
            require(separator>=0) { "صيغة ترميز الصورة غير صالحة: $field" }
            val encodedStart=separator+8
            val encodedLength=raw.length-encodedStart
            require(encodedLength in 1..16*1024*1024) { "صورة كبيرة جداً: $field" }
            // Decode the encoded span without creating an intermediate String.
            val encodedBytes=ByteArray(encodedLength)
            for(index in 0 until encodedLength) {
                val character=raw[encodedStart+index]
                require(character.code<=127) { "رمز غير صالح داخل الصورة: $field" }
                encodedBytes[index]=character.code.toByte()
            }
            // Decode the bounded byte array, then let it become unreachable
            // before allocating additional buffers for image verification.
            val bytes=android.util.Base64.decode(encodedBytes,android.util.Base64.DEFAULT)
            require(bytes.isNotEmpty() && bytes.size<=12*1024*1024) { "حجم صورة غير صالح: $field" }
            require((mime=="image/jpeg" && bytes.size>=3 && bytes[0]==0xFF.toByte() && bytes[1]==0xD8.toByte()) ||
                (mime=="image/png" && bytes.size>=8 && bytes[0]==0x89.toByte() && bytes[1]==0x50.toByte()) ||
                (mime=="image/webp" && bytes.size>=12 && String(bytes,0,4,Charsets.US_ASCII)=="RIFF" && String(bytes,8,4,Charsets.US_ASCII)=="WEBP")) { "ترويسة صورة غير مطابقة: $field" }
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            require(bounds.outWidth in 1..10000 && bounds.outHeight in 1..10000) { "صورة غير قابلة للقراءة: $field" }
            val extension=when(mime) {"image/png"->".png";"image/webp"->".webp";else->".jpg"}
            val file=java.io.File(directory,java.util.UUID.randomUUID().toString()+extension)
            try {
                file.outputStream().use { output ->
                    output.write(bytes)
                    output.flush()
                    if(output is java.io.FileOutputStream) output.fd.sync()
                }
                require(file.length()==bytes.size.toLong()) { "صورة لم تُحفظ كاملة: $field" }
                val expected=java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                val actual=file.inputStream().use { input ->
                    val digest=java.security.MessageDigest.getInstance("SHA-256")
                    val buffer=ByteArray(8192)
                    while(true) {
                        val read=input.read(buffer)
                        if(read<0) break
                        digest.update(buffer,0,read)
                    }
                    digest.digest()
                }
                require(java.security.MessageDigest.isEqual(expected,actual)) { "اختلاف محتوى الصورة: $field" }
            } catch(error:Exception) {
                file.delete()
                throw error
            }
            created.add(file)
            when(field) {
                "photo","photo2","photo3" -> productPhotos.put(file.absolutePath)
                "watchPhoto" -> copy.put("imagePath",file.absolutePath)
                else -> copy.put(field+"Path",file.absolutePath)
            }
            copy.remove(field)
        }
        if(productPhotos.length()>0) {
            copy.put("imagePath",productPhotos.getString(0))
            copy.put("imagePaths",productPhotos)
        }
        return copy
    }
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
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" && root.optInt("formatVersion")==2)
        val d=root.getJSONObject("data")
        val manifest=LegacyRestoreManifest.inspectData(root,payload)
        val readiness=LegacyRestoreReadiness.inspectRoot(root)
        val blocking=LegacyImportPreflight.inspectInventoryData(d).size
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
        val warnings=LegacyImportPreflight.inspectFinancialHistoryData(d).size
        val continuity=LegacyClosingContinuity.inspectRoot(root)
        val cashIssues=continuity.getJSONArray("arithmeticMismatches").length()
        val invalidClosingDates=continuity.getJSONArray("invalidDateRows").length()
        val duplicateClosingDates=continuity.getJSONArray("duplicateClosingDates").length()
        val payments=LegacyPaymentLinkAudit.inspectRoot(root)
        val returns=LegacyReturnLinkAudit.inspectRoot(root)
        val brokenPaymentLinks=payments.getJSONArray("unresolvedReferenceRows").length()+payments.getJSONArray("missingReferenceRows").length()
        val crossCurrencyPaymentCount=payments.getJSONArray("crossCurrencyPaymentRows").length()
        val cashMatch=LegacyPaymentCashAudit.inspectRoot(root)
        val vaults=LegacyVaultSnapshot.inspectRoot(root)
        val vaultPrecisionWarnings=vaults.getJSONArray("fractionalMinorUnitRows").length()
        val supplierCash=LegacySupplierCashAudit.inspectRoot(root)
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
        val openingWorksheet=runCatching { LegacyFinancialOpeningReport.summaryRoot(root) }
            .getOrElse { "تعذرت مطابقة الديون التاريخية: ${it.message}. ستظل البيانات المالية محفوظة دون تفعيل." }
        return LegacyBackupFreshness.summaryRoot(root) + "\n" + openingWorksheet + "\n" + "جاهزية أرشفة النسخة: ${if(readiness.getBoolean("archiveImportPermitted")) "نعم" else "لا"}، عوائق التفعيل المالي: ${readiness.getInt("financialBlockerCount")}، حركات نقدية ذات كسور دقيقة: $vaultPrecisionWarnings، التفعيل المالي: غير مسموح قبل المطابقة. أخطاء الاستيراد: $blocking، قيم أسعار ستُقرب للسنت مع حفظ الأصل: $roundedPrices، تحذيرات مالية مؤرشفة: $warnings، فروقات إقفال فعلية: $cashIssues، تواريخ إقفال غير صالحة: $invalidClosingDates، تواريخ إقفال مكررة: $duplicateClosingDates، دفعات تحتاج مطابقة مرجع: $brokenPaymentLinks، دفعات بعملة قبض مختلفة: $crossCurrencyPaymentCount، دفعات مطابقة لحركات النقد: $matchedCashPayments، غير مطابقة: $unmatchedCashPayments، ملتبسة: $ambiguousCashPayments، مرتجعات تحتاج مطابقة مرجع: $brokenReturnLinks، سداد موردين مطابق للخزنة: $matchedSupplierPayments، غير مطابق: $unmatchedSupplierPayments. بصمة النسخة: ${manifest.getString("sha256").take(16)}…، إجمالي السجلات: ${manifest.getLong("recordCount")}. مخزون الساعات: ${products.length()}، قطع الصيانة: ${parts.length()}، الموردون: ${suppliers.length()}، سجل الصيانة: ${repairs.length()}. " +
            "سيُنقل المخزون والموردون والصيانة، وتُحفظ الفواتير والحركات القديمة كأرشيف قابل للقراءة فقط، دون احتسابها في الأرصدة أو الأرباح."
    }
    /**
     * Fast confirmation for large backups. The actual import still performs
     * inventory and financial checks and verifies every archived record.
     */
    private fun requireRequiredCollections(data:JSONObject) {
        // A missing collection must never be interpreted as an empty store.
        // This is especially important for stock, suppliers and repair history.
        val required=listOf("products","maintenanceParts","suppliers","repairs",
            "sales","purchases","payments","cashTransactions",
            "supplierLedger","partnerWithdrawals","dailyClosings")
        val missing=required.filter { !data.has(it) || data.optJSONArray(it)==null }
        require(missing.isEmpty()) {
            "النسخة الاحتياطية ناقصة أو تالفة؛ أقسام مفقودة: ${missing.joinToString(", ")}"
        }
    }
    fun quickPreview(payload:String):String = quickPreviewRoot(JSONObject(payload))

    fun quickPreviewRoot(root:JSONObject):String {
        require(root.optString("format")=="alsafa-accounting-backup" &&
            root.optInt("formatVersion")==2) { "صيغة النسخة الاحتياطية غير مدعومة" }
        val data=root.getJSONObject("data")
        requireRequiredCollections(data)
        val sections=listOf("products","maintenanceParts","suppliers","repairs",
            "sales","purchases","payments","cashTransactions","supplierLedger",
            "partnerWithdrawals","dailyClosings","returns")
        val labels=mapOf(
            "products" to "الساعات", "maintenanceParts" to "قطع الصيانة",
            "suppliers" to "الموردون", "repairs" to "طلبات الصيانة",
            "sales" to "فواتير البيع", "purchases" to "فواتير الشراء",
            "payments" to "الدفعات", "cashTransactions" to "حركات الخزنة",
            "supplierLedger" to "سجل الموردين",
            "partnerWithdrawals" to "سحوبات الشركاء",
            "dailyClosings" to "الإقفالات اليومية", "returns" to "المرتجعات"
        )
        val counts=sections.joinToString("\n") { section ->
            "${labels.getValue(section)}: ${data.optJSONArray(section)?.length() ?: 0}"
        }
        val imageFields=listOf("photo","photo2","photo3","couplePhoto","barcodePhoto","watchPhoto")
        val photoCounts=linkedMapOf<String,Int>()
        for(section in listOf("products","maintenanceParts","repairs")) {
            val array=data.optJSONArray(section) ?: continue
            for(index in 0 until array.length()) {
                val row=array.optJSONObject(index) ?: continue
                for(field in imageFields) if(row.optString(field).startsWith("data:image/")) {
                    val key="$section.$field"
                    photoCounts[key]=(photoCounts[key] ?: 0)+1
                }
            }
        }
        val imageSummary="الصور المدمجة في النسخة: "+photoCounts.values.sum()+
            " (صور المنتجات والصيانة ستُستخرج إلى ملفات محلية عند الاستعادة)"
        return "نسخة بتاريخ: ${root.optString("exportedAt")}\n" +
            "الإصدار: ${root.optString("appVersion")}\n" +
            counts + "\n" + imageSummary + "\n\n" +
            "هذه معاينة سريعة فقط. ستجري الاستعادة الفحوصات التفصيلية " +
            "داخل عملية الحفظ. لن تُفعّل الأرصدة النقدية والديون والأرباح " +
            "التاريخية تلقائياً، بل ستُحفظ في أرشيف للقراءة والمطابقة."
    }
    /**
     * Read-only comparison for a newer v2 snapshot. Identity is the immutable
     * source ID, never the display name or barcode. Existing rows are not
     * inserted again, and changed rows are not silently overwritten.
     */
    fun incrementalPreview(store:Store,payload:String):String {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" &&
            root.optInt("formatVersion")==2) { "الملف ليس نسخة الصفا القديمة المدعومة" }
        val data=root.getJSONObject("data")
        requireRequiredCollections(data)
        val sections=listOf(
            Triple("products","products","الساعات"),
            Triple("maintenanceParts","products","قطع الصيانة"),
            Triple("suppliers","suppliers","الموردون"),
            Triple("repairs","repairs","طلبات الصيانة")
        )
        val report=StringBuilder("فحص النسخة الأحدث — قراءة فقط\n")
        var totalNew=0
        var totalExisting=0
        val idsAcrossSections=HashSet<String>()
        // One indexed table scan per destination instead of thousands of
        // individual SQLite queries while examining a large legacy snapshot.
        val productRows=store.rows("SELECT id,kind,barcode FROM products")
        val existingProducts=productRows.associate { it.getValue("id") to it.getValue("kind") }
        val existingBarcodes=HashMap<String,String>()
        for(row in productRows) {
            val barcode=row.getValue("barcode").trim()
            if(barcode.isNotEmpty()) {
                val owner=existingBarcodes.putIfAbsent(barcode,row.getValue("id"))
                require(owner==null || owner==row.getValue("id")) {
                    "قاعدة البيانات تحتوي باركوداً مرتبطاً بأكثر من منتج: $barcode"
                }
            }
        }
        val incomingBarcodes=HashMap<String,String>()
        val existingSuppliers=store.rows("SELECT id FROM suppliers")
            .mapTo(HashSet()) { it.getValue("id") }
        val existingRepairs=store.rows("SELECT id FROM repairs")
            .mapTo(HashSet()) { it.getValue("id") }
        for((source,table,title) in sections) {
            val array=data.getJSONArray(source)
            var newCount=0
            var existingCount=0
            val seen=HashSet<String>()
            for(i in 0 until array.length()) {
                val record=array.optJSONObject(i)
                    ?: error("سجل غير صالح في $title عند الفهرس $i")
                val id=record.optString("id").trim()
                require(id.isNotBlank() && seen.add(id)) {
                    "معرف مفقود أو مكرر في $title عند السجل $i"
                }
                require(idsAcrossSections.add(id)) {
                    "المعرف $id مكرر بين أقسام النسخة؛ أُوقف الفحص لمنع دمج خاطئ"
                }
                val exists=when(table) {
                    "products" -> {
                        val kind=existingProducts[id]
                        if(kind!=null) require(kind==(if(source=="products") "watch" else "part")) {
                            "تعارض نوع المنتج للمعرف $id: الموجود $kind"
                        }
                        kind!=null
                    }
                    "suppliers" -> id in existingSuppliers
                    "repairs" -> id in existingRepairs
                    else -> error("قسم مقارنة غير مدعوم")
                }
                if(table=="products") {
                    val barcode=record.optString("barcode").trim()
                    if(barcode.isNotEmpty()) {
                        val incomingOwner=incomingBarcodes.putIfAbsent(barcode,id)
                        require(incomingOwner==null || incomingOwner==id) {
                            "الباركود $barcode مكرر بين معرفين في النسخة الجديدة"
                        }
                        val storedOwner=existingBarcodes[barcode]
                        require(storedOwner==null || storedOwner==id) {
                            "الباركود $barcode مرتبط مسبقاً بمنتج آخر: $storedOwner"
                        }
                    }
                }
                if(exists) existingCount++ else newCount++
            }
            totalNew+=newCount
            totalExisting+=existingCount
            report.append("$title: جديد $newCount، موجود $existingCount، إجمالي ${array.length()}\n")
        }
        report.append("\nالمجموع: جديد $totalNew، موجود مسبقاً $totalExisting\n")
        report.append("\nالمطابقة تعتمد على المعرّف الأصلي، وليس الاسم. الموجود مسبقاً قد يكون تغيّر محتواه، ولا يُعد مطابقاً تلقائياً. هذا فحص فقط؛ لا تُضاف سجلات أو تُستبدل بيانات أو تُنشأ فواتير. يلزم اعتماد آلية آمنة للتحديثات والحذف قبل تفعيل الدمج.")
        return report.toString()
    }
    private fun requireEmptyAccounting(store:Store) {
        val tables=listOf("legacy_records","repairs","products","suppliers","events",
            "invoices","journal","receipts","stock_moves","returns","refunds","closings")
        for (table in tables) {
            require(store.scalar("SELECT count(*) FROM $table")==0L) {
                "قاعدة Native 6 تحتوي بيانات في $table؛ لا يمكن استعادة نسخة قديمة فوقها"
            }
        }
        require(store.scalar("SELECT count(*) FROM settings WHERE key=?",
            "legacy_snapshot_import")==0L) { "هذه النسخة مستعادة مسبقاً" }
    }
    fun import(store:Store,payload:String,context:android.content.Context,progress:(Int,String)->Unit = { _,_ -> }):String {
        progress(1,"التحقق من قاعدة البيانات")
        // Reject an occupied database before parsing and auditing a large backup.
        requireEmptyAccounting(store)
        progress(4,"قراءة وتحليل ملف النسخة الاحتياطية")
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup" &&
            root.optInt("formatVersion")==2) { "صيغة النسخة الاحتياطية غير مدعومة" }
        val d=root.getJSONObject("data")
        requireRequiredCollections(d)
        // Reject invalid stock immediately, before the more expensive financial
        // reconciliation passes over the large backup.
        progress(10,"فحص المخزون والبيانات")
        val issues=LegacyImportPreflight.inspectInventoryData(d)
        require(issues.isEmpty()) { "فحص النسخة القديمة فشل: "+issues.take(5).joinToString("; ") }
        progress(15,"فحص سلامة الملف وبصمة النسخة")
        val manifest=LegacyRestoreManifest.inspectData(root,payload)
        progress(20,"مراجعة المراجع والبيانات المحاسبية")
        val readiness=LegacyRestoreReadiness.inspectRoot(root)
        val financialWarnings=LegacyImportPreflight.inspectFinancialHistoryData(d)
        val closingAudit=LegacyClosingContinuity.inspectRoot(root)
        val paymentLinkAudit=LegacyPaymentLinkAudit.inspectRoot(root)
        val returnLinkAudit=LegacyReturnLinkAudit.inspectRoot(root)
        val supplierCashAudit=LegacySupplierCashAudit.inspectRoot(root)
        val vaultSnapshot=LegacyVaultSnapshot.inspectRoot(root)
        val paymentCashAudit=LegacyPaymentCashAudit.inspectRoot(root)
        val financialLinkAudit=LegacyFinancialLinkAudit.inspectData(d)
        require(financialLinkAudit.getJSONArray("duplicateIds").length()==0) {
            "معرفات مالية مكررة في النسخة القديمة؛ تم إيقاف الاستعادة لحماية البيانات"
        }
        require(financialLinkAudit.getJSONArray("missingIds").length()==0) {
            "معرفات مالية مفقودة في النسخة القديمة؛ تم إيقاف الاستعادة لحماية البيانات"
        }
        // Source v2 may contain historical references to deleted invoices.
        // They are archived unchanged and NEVER posted into native financial
        // journals; missing links must not block inventory-only restoration.
        val missingHistoricalLinks=financialLinkAudit.getInt("missingReferenceCount")
        val lists=listOf("products" to "watch","maintenanceParts" to "part")
        val suppliers=d.getJSONArray("suppliers")
        val createdImages=mutableListOf<java.io.File>()
        val imageOwners=linkedMapOf<String,Pair<String,String>>()
        progress(30,"بدء حفظ البيانات في قاعدة SQLite")
        return try { store.transaction {
            // Recheck under the write transaction to prevent accidental overwrite.
            requireEmptyAccounting(store)
            val seen=HashSet<String>()
            val supplierIds=HashSet<String>()
            val repairIds=HashSet<String>()
            val barcodes=HashSet<String>()
            lists.forEach { (key,kind) ->
                progress(if(key=="products") 33 else 43,if(key=="products") "استعادة الساعات وصورها" else "استعادة قطع الصيانة وصورها")
                val a=d.getJSONArray(key)
                for(i in 0 until a.length()) {
                    if(i%25==0) progress((if(key=="products") 33 else 43)+i*9/maxOf(1,a.length()),"${if(key=="products") "الساعات" else "قطع الصيانة"}: ${i+1} / ${a.length()}")
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
                    store.addProduct(name,barcode,currency,price,cost,qty,kind,legacyPhotos(item,context,createdImages).also { details ->
                            val keys=listOf("imagePath","couplePhotoPath","barcodePhotoPath")
                            for(key in keys) details.optString(key).takeIf { it.isNotBlank() }?.let { imageOwners[it]="products" to id }
                            val paths=details.optJSONArray("imagePaths")
                            if(paths!=null) for(n in 0 until paths.length()) imageOwners[paths.getString(n)]="products" to id
                        }.toString(),id,
                        brand=item.optString("brand").take(500),
                        model=item.optString("model").take(500))
                }
            }
            progress(53,"استعادة الموردين")
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
            progress(58,"استعادة طلبات الصيانة وصورها")
            for(i in 0 until repairs.length()) {
                if(i%20==0) progress(58+i*10/maxOf(1,repairs.length()),"طلبات الصيانة: ${i+1} / ${repairs.length()}")
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
                    "details" to legacyPhotos(repair,context,createdImages).also { details ->
                        for(key in listOf("imagePath","couplePhotoPath","barcodePhotoPath")) {
                            details.optString(key).takeIf { it.isNotBlank() }?.let { imageOwners[it]="repairs" to id }
                        }
                        val paths=details.optJSONArray("imagePaths")
                        if(paths!=null) for(n in 0 until paths.length()) imageOwners[paths.getString(n)]="repairs" to id
                    }.put("_legacyImported",true).toString())
            }
            // Preserve historical financial records verbatim for accurate later ledger
            // reconstruction. They are NOT booked as new income or cash movements.
            // Archive every other array from the original backup, including
            // future sections unknown to this app version.
            val historical=d.keys().asSequence()
                .filter { it !in setOf("products","maintenanceParts","suppliers","repairs") }
                .filter { d.optJSONArray(it)!=null }.toList()
            progress(70,"أرشفة الفواتير والحركات التاريخية")
            historical.forEachIndexed { sectionIndex,key ->
                progress(70+sectionIndex*9/maxOf(1,historical.size),"أرشفة قسم $key (${sectionIndex+1} / ${historical.size})")
                val array=d.optJSONArray(key) ?: return@forEachIndexed
                for(i in 0 until array.length()) {
                    if(i%100==0) progress(70+sectionIndex*9/maxOf(1,historical.size),"أرشفة $key: ${i+1} / ${array.length()}")
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
            require("deviceSettings" !in configurationKeys && "deviceSettings" !in scalarKeys) {
                "تعارض اسم إعدادات الجهاز مع بيانات النسخة"
            }
            if(deviceSettings!=null) store.insert("legacy_records",
                "category" to "configuration","legacy_id" to "deviceSettings",
                "payload" to deviceSettings.toString())
            progress(80,"التحقق من أعداد السجلات المنقولة")
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
                val restored=store.rows("SELECT customer,phone,watch,problem FROM repairs WHERE id=?",
                    original.getString("id").trim()).singleOrNull()
                    ?: error("طلب صيانة مفقود بعد الاستعادة: $i")
                require(restored.getValue("customer")==original.optString("customer") &&
                    restored.getValue("phone")==original.optString("phone") &&
                    restored.getValue("watch")==original.optString("watch") &&
                    restored.getValue("problem")==original.optString("problem")) {
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
            for (key in configurationKeys) {
                val restored=store.rows(
                    "SELECT payload FROM legacy_records WHERE category=? AND legacy_id=?",
                    "configuration",key
                ).singleOrNull() ?: error("إعدادات قديمة مفقودة: $key")
                require(restored.getValue("payload")==d.getJSONObject(key).toString()) {
                    "اختلاف إعدادات النسخة الأصلية: $key"
                }
            }
            for (key in scalarKeys) {
                val restored=store.rows(
                    "SELECT payload FROM legacy_records WHERE category=? AND legacy_id=?",
                    "configuration",key
                ).singleOrNull() ?: error("بيانات وصفية قديمة مفقودة: $key")
                require(restored.getValue("payload")==JSONObject()
                    .put("_legacyValue",d.opt(key)).toString()) {
                    "اختلاف بيانات النسخة الوصفية: $key"
                }
            }
            // CursorWindow cannot hold a single JSON row containing large base64 photos.
            // Verify archived payloads in bounded UTF-8 byte chunks, never selecting
            // the full payload into a CursorWindow. The enclosing transaction rolls
            // back the import if any byte differs from the original snapshot.
            for (section in historical) {
                val expected=d.getJSONArray(section)
                val actualCount=store.scalar(
                    "SELECT count(*) FROM legacy_records WHERE category=?",section)
                require(actualCount==expected.length().toLong()) {
                    "أرشيف غير مكتمل: $section"
                }
                for (index in 0 until expected.length()) {
                    val original=expected.get(index)
                    val sourceId=(original as? JSONObject)?.optString("id")?.trim()
                    val key="$index:${sourceId?.takeIf { it.isNotEmpty() } ?: "no-id"}"
                    val serialized=if(original is JSONObject) original.toString()
                        else JSONObject().put("_legacyValue",original).toString()
                    val bytes=serialized.toByteArray(Charsets.UTF_8)
                    val storedSize=store.scalar(
                        "SELECT length(CAST(payload AS BLOB)) FROM legacy_records WHERE category=? AND legacy_id=?",
                        section,key)
                    require(storedSize==bytes.size.toLong()) {
                        "اختلاف حجم سجل الأرشيف: $section / $index"
                    }
                    var offset=0
                    while(offset<bytes.size) {
                        val count=minOf(8192,bytes.size-offset)
                        val expectedHex=bytes.copyOfRange(offset,offset+count)
                            .joinToString("") { "%02X".format(it.toInt() and 255) }
                        store.db.rawQuery(
                            "SELECT hex(substr(CAST(payload AS BLOB),?,?)) FROM legacy_records WHERE category=? AND legacy_id=?",
                            arrayOf((offset+1).toString(),count.toString(),section,key)
                        ).use { cursor ->
                            require(cursor.moveToFirst() && cursor.getString(0)==expectedHex) {
                                "فشل التحقق من سجل الأرشيف: $section / $index / $offset"
                            }
                        }
                        offset+=count
                    }
                }
            }
            require(actualArchive==expectedArchive.toLong()) {
                "عدد السجلات التاريخية المستعادة لا يطابق النسخة الأصلية: $actualArchive / $expectedArchive"
            }
            progress(88,"مطابقة بيانات المخزون والصيانة")
            for ((section,kind) in lists) {
                val items=d.getJSONArray(section)
                val sourceQuantity=(0 until items.length()).sumOf { i -> items.getJSONObject(i).getLong("stock") }
                for (i in 0 until items.length()) {
                    val original=items.getJSONObject(i)
                    val productId=original.getString("id").trim()
                    val restored=store.rows("SELECT stock,price,cost,barcode FROM products WHERE id=? AND kind=?",productId,kind).singleOrNull()
                        ?: error("منتج مفقود بعد الاستعادة: $section / $i")
                    require(restored.getValue("stock").toLong()==original.getLong("stock") &&
                        restored.getValue("price").toLong()==legacyMinor(original.optString("salePrice","0")) &&
                        restored.getValue("cost").toLong()==legacyMinor(original.optString("cost","0")) &&
                        restored.getValue("barcode")==original.optString("barcode").trim()) {
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
                .put("financialLinkAudit",financialLinkAudit)
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
            require(createdImages.all { it.isFile && it.length()>0L }) { "ملفات الصور المستعادة غير مكتملة" }
            require(createdImages.distinctBy { it.canonicalPath }.size==createdImages.size) { "تكرار مسار صورة أثناء الاستعادة" }
            progress(94,"فحص الصور المستعادة وربطها بالسجلات")
            val expectedPhotos=listOf("products","maintenanceParts","repairs").sumOf { section ->
                val array=d.getJSONArray(section)
                (0 until array.length()).sumOf { index ->
                    val row=array.getJSONObject(index)
                    listOf("photo","photo2","photo3","couplePhoto","barcodePhoto","watchPhoto")
                        .count { row.optString(it).startsWith("data:image/") }
                }
            }
            require(createdImages.size==expectedPhotos) { "عدد الصور المستعادة لا يطابق النسخة الأصلية" }
            // Avoid SQLite JSON1 here: it is not guaranteed on older Android builds.
            // Confirm every image-bearing source record has its local photo reference.
            for(section in listOf("products","maintenanceParts","repairs")) {
                val array=d.getJSONArray(section)
                for(index in 0 until array.length()) {
                    val original=array.getJSONObject(index)
                    val fields=if(section=="repairs") listOf("watchPhoto")
                        else listOf("photo","photo2","photo3","couplePhoto","barcodePhoto")
                    if(fields.none { original.optString(it).startsWith("data:image/") }) continue
                    val table=if(section=="repairs") "repairs" else "products"
                    val id=original.getString("id").trim()
                    // A product can contain only a couple or barcode image;
                    // neither creates the primary imagePath field.
                    val expectedPaths=if(section=="repairs") listOf("imagePath")
                        else listOf("imagePath","couplePhotoPath","barcodePhotoPath")
                    require(expectedPaths.any { path ->
                        store.scalar("SELECT count(*) FROM $table WHERE id=? AND instr(details,?)>0",
                            id,"\"$path\"")==1L
                    }) { "صورة غير مرتبطة بسجلها: $section / $id" }
                }
            }
            // Each extracted file has one source owner. Validate against its
            // own persisted record, rather than scanning every large JSON blob
            // (which can exhaust memory and incorrectly reject secondary photos).
            require(imageOwners.size==createdImages.size) {
                "عدد الصور المرتبطة بالسجلات (${imageOwners.size}) لا يطابق الصور المحفوظة (${createdImages.size})"
            }
            for(image in createdImages) {
                val owner=imageOwners[image.absolutePath]
                    ?: error("صورة بلا سجل أصلي: "+image.name)
                require(image.isFile && image.length()>0L) {
                    "صورة غير محفوظة: "+image.name
                }
                require(store.scalar(
                    "SELECT count(*) FROM ${owner.first} WHERE id=? AND instr(details,?)>0",
                    owner.second,image.absolutePath)==1L) {
                    "الصورة لا تظهر في تفاصيل سجلها الأصلي: "+image.name
                }
            }
            progress(99,"إنهاء التحقق وحفظ الاستعادة")
            val photoCount=createdImages.size
            val receipt=LegacyRestoreReceipt.inspect(store)
            val financialSummary=runCatching { LegacyArchivedFinancialPosition.summary(store) }
                .getOrElse { "البيانات المالية محفوظة، لكن تقرير الذمم يحتاج مراجعة: ${it.message}" }
            "تمت مطابقة الاستعادة مع قاعدة البيانات: ${receipt.getLong("products")} منتجًا، " +
                "${receipt.getLong("maintenanceParts")} قطعة صيانة، ${receipt.getLong("suppliers")} موردًا، " +
                "${receipt.getLong("repairs")} طلب صيانة، $photoCount صورة محفوظة محلياً. " +
                "بصمة المصدر: ${receipt.getString("sourceSha256").take(16)}… " +
                "الفواتير والحركات المالية القديمة محفوظة للقراءة فقط؛ لم تُفعّل أرصدتها أو أرباحها.\n" + financialSummary
        } } catch (failure:Throwable) {
            // SQLite transaction rolls back on failure; files are external to
            // SQLite and must also be cleaned even after OutOfMemoryError.
            createdImages.forEach { file -> runCatching { file.delete() } }
            throw failure
        }
    }
}
