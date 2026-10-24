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
                val gallery=details.optJSONArray("imagePaths")
                if(table=="repairs") require(gallery==null) { "معرض الصور الإضافية مخصص للمنتجات" }
                if(gallery!=null) {
                    require(gallery.length()<=8) { "عدد صور المنتج يتجاوز الحد المسموح" }
                    val rebasedGallery=JSONArray()
                    val seen=mutableSetOf<String>()
                    for(index in 0 until gallery.length()) {
                        val oldGallery=gallery.getString(index)
                        val file=File(oldGallery)
                        require(file.isAbsolute && file.parentFile?.name=="images") { "مسار صورة إضافية غير صالح" }
                        val name=file.name
                        require(name.endsWith(".jpg") && name.removeSuffix(".jpg").matches(Regex("[a-fA-F0-9-]{36}"))) { "اسم صورة إضافية غير صالح" }
                        require(seen.add(name)) { "صورة مكررة في معرض المنتج" }
                        if(media!=null) require(name in available) { "صورة إضافية مفقودة من النسخة" }
                        val target=File(imageDir,name).canonicalFile
                        require(target.parentFile==imageDir) { "مسار صورة إضافية غير آمن" }
                        rebasedGallery.put(target.absolutePath)
                    }
                    details.put("imagePaths",rebasedGallery)
                    record.put("details",details.toString())
                }
                val old=details.optString("imagePath")
                if(old.isBlank()) {
                    val first=details.optJSONArray("imagePaths")?.optString(0).orEmpty()
                    if(first.isNotBlank()) details.put("imagePath",first)
                    record.put("details",details.toString())
                    continue
                }
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
