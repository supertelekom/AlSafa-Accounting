package com.alsafa.native6

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Rebase app-private photo references when restoring on another Android device. */
object BackupMediaPaths {
    fun rebase(context:Context,data:JSONObject) {
        val imageDir=File(context.filesDir,"images").canonicalFile
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
                require(file.isAbsolute)
                val name=file.name
                require(name.endsWith(".jpg") && name.removeSuffix(".jpg").matches(Regex("[a-fA-F0-9-]{36}"))) {
                    "مسار صورة غير صالح في النسخة الاحتياطية"
                }
                val rebased=File(imageDir,name).canonicalFile
                require(rebased.parentFile==imageDir) { "مسار الصورة خارج المجلد المسموح" }
                details.put("imagePath",rebased.absolutePath)
                record.put("details",details.toString())
            }
        }
    }
}
