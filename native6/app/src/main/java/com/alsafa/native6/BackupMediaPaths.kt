package com.alsafa.native6

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** Rebase app-private photo references when restoring on another Android device. */
object BackupMediaPaths {
    fun rebase(context:Context,data:JSONObject,media:JSONArray?=null) {
        val available=mutableSetOf<String>()
        if(media!=null) require(media.length()<=10000) { "فهرس الصور يتجاوز الحد الآمن" }
        if(media!=null) for(i in 0 until media.length()) {
            val name=media.getJSONObject(i).getString("name")
            require(name.endsWith(".jpg") && name.removeSuffix(".jpg").matches(Regex("[a-fA-F0-9-]{36}"))) { "اسم صورة غير صالح في الفهرس" }
            require(available.add(name)) { "اسم صورة مكرر في النسخة" }
        }
        val imageDir=File(context.filesDir,"images").canonicalFile
        require(imageDir.parentFile==context.filesDir.canonicalFile) { "مجلد الصور خارج تخزين التطبيق" }
        for(table in listOf("products","repairs")) {
            val records=data.getJSONArray(table)
            for(i in 0 until records.length()) {
                val record=records.getJSONObject(i)
                val raw=record.optString("details","{}")
                if(raw.isBlank()) continue
                val details=JSONObject(raw)
                val old=details.optString("imagePath")
                if(old.isBlank()) continue
                val file=File(old)
                require(file.isAbsolute && file.parentFile?.name=="images") { "مسار الصورة الأصلي غير صالح" }
                val name=file.name
                require(name.endsWith(".jpg") && name.removeSuffix(".jpg").matches(Regex("[a-fA-F0-9-]{36}"))) {
                    "مسار صورة غير صالح في النسخة الاحتياطية"
                }
                if(media!=null) require(name in available) { "الصورة المشار إليها غير موجودة في النسخة الاحتياطية" }
                val rebased=File(imageDir,name).canonicalFile
                require(rebased.parentFile==imageDir) { "مسار الصورة خارج المجلد المسموح" }
                details.put("imagePath",rebased.absolutePath)
                record.put("details",details.toString())
            }
        }
    }
}
