package com.alsafa.native6

import org.json.JSONObject

/**
 * Imports only a verified current inventory snapshot from a staged AlSafa Backup v2.
 * Historical sales, cash, debts and partner profits are deliberately NOT reconstructed.
 * The original backup remains intact in legacy_imports for a later ledger migration.
 */
object LegacySnapshotImport {
    private fun data(payload:String):JSONObject {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup"){"يلزم ملف AlSafa Backup v2 الأصلي"}
        require(root.optInt("formatVersion")>=2){"إصدار النسخة القديمة غير مدعوم"}
        return root.getJSONObject("data")
    }
    fun preview(payload:String):String {
        val d=data(payload)
        val products=d.getJSONArray("products")
        val parts=d.getJSONArray("maintenanceParts")
        val suppliers=d.getJSONArray("suppliers")
        return "مخزون الساعات: ${products.length()}، قطع الصيانة: ${parts.length()}، الموردون: ${suppliers.length()}. " +
            "هذه عملية نقل مخزون حالي فقط؛ لن تُستعاد الفواتير أو الديون أو النقد أو أرباح الشركاء بهذه الخطوة."
    }
    fun import(store:Store,payload:String):String {
        val d=data(payload)
        val lists=listOf("products" to "watch","maintenanceParts" to "part")
        val suppliers=d.getJSONArray("suppliers")
        return store.transaction {
            require(store.scalar("SELECT count(*) FROM products")==0L &&
                store.scalar("SELECT count(*) FROM suppliers")==0L &&
                store.scalar("SELECT count(*) FROM events")==0L &&
                store.scalar("SELECT count(*) FROM invoices")==0L &&
                store.scalar("SELECT count(*) FROM journal")==0L){"يجب أن تكون قاعدة Native 6 المحاسبية فارغة"}
            val seen=HashSet<String>()
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
                    val price=Money.parse(item.optString("salePrice","0"),currency).minor
                    val cost=Money.parse(item.optString("cost","0"),currency).minor
                    val qty=item.getLong("stock")
                    require(qty>=0 && price>=0 && cost>=0 && name.isNotEmpty()){"بيانات مخزون غير صالحة: $id"}
                    // Preserve all legacy metadata and embedded photos as JSON details.
                    store.addProduct(name,barcode,currency,price,cost,qty,kind,item.toString(),id)
                }
            }
            for(i in 0 until suppliers.length()) {
                val s=suppliers.getJSONObject(i)
                val id=s.getString("id").trim()
                require(id.isNotEmpty() && seen.add(id)){"معرف مورد مكرر"}
                store.insert("suppliers","id" to id,"name" to s.getString("name"),"phone" to s.optString("phone"))
            }
            store.insert("settings","key" to "legacy_snapshot_import","value" to JSONObject()
                .put("scope","inventory_and_suppliers_only")
                .put("sourceAppVersion","3.9.x")
                .put("products",d.getJSONArray("products").length())
                .put("maintenanceParts",d.getJSONArray("maintenanceParts").length())
                .put("suppliers",suppliers.length()).toString())
            "نُقل المخزون الحالي والموردون فقط. الفواتير والأرصدة القديمة ما زالت في الأرشيف ولم تُرحّل."
        }
    }
}
