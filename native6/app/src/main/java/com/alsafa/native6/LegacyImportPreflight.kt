package com.alsafa.native6

import org.json.JSONObject
import java.time.LocalDate

/** Read-only validation; never mutates the active store. */
object LegacyImportPreflight {
    fun inspect(payload:String):List<String> {
        val root=JSONObject(payload)
        require(root.optString("format")=="alsafa-accounting-backup")
        val data=root.getJSONObject("data")
        val problems=mutableListOf<String>()
        val ids=HashSet<String>()
        val barcodes=HashSet<String>()
        for(section in listOf("products","maintenanceParts","suppliers","repairs")) {
            val items=data.optJSONArray(section)
            if(items==null) {
                problems.add("Missing section: $section")
                continue
            }
            for(i in 0 until items.length()) {
                val item=items.optJSONObject(i)
                if(item==null) { problems.add("$section[$i]: invalid record");continue }
                val id=item.optString("id").trim()
                if(id.isEmpty() || !ids.add(id)) problems.add("$section[$i]: missing or duplicate ID")
                if(section=="products" || section=="maintenanceParts") {
                    val barcode=item.optString("barcode").trim()
                    if(barcode.isNotEmpty() && !barcodes.add(barcode))
                        problems.add("$section[$i]: duplicate barcode")
                    if(item.optString("currency") !in setOf("USD","TRY","SYP"))
                        problems.add("$section[$i]: unsupported currency")
                    val qty=item.optString("stock").toLongOrNull()
                    if(qty==null || qty<0) problems.add("$section[$i]: invalid stock")
                    if(item.optString("name").isBlank())
                        problems.add("$section[$i]: missing product name")
                    for(field in listOf("cost","salePrice")) {
                        val amount=item.optString(field,"0").toBigDecimalOrNull()
                        if(amount==null || amount.signum()<0)
                            problems.add("$section[$i]: invalid $field")
                    }
                }
                if(section=="repairs") {
                    val date=item.optString("date")
                    val valid=runCatching { !LocalDate.parse(date).isAfter(LocalDate.now()) }.getOrDefault(false)
                    if(!valid) problems.add("repairs[$i]: invalid intake date")
                    val status=item.optString("status")
                    if(status.isNotBlank() && status !in setOf(
                        "تم التسليم","delivered","قيد الصيانة","in_progress",
                        "جاهزة","جاهز","جاهز للاستلام","بانتظار التسليم","ready",
                        "مستلمة","تم الاستلام","قيد الانتظار","received"))
                        problems.add("repairs[$i]: unsupported status")
                }
            }
        }
        return problems
    }
}
