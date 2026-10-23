package com.alsafa.native6

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** App-private JPEG files are part of a complete local backup, not just their database paths. */
object BackupMedia {
    private const val MAX_FILE=12L*1024*1024
    private const val MAX_TOTAL=80L*1024*1024

    fun collect(context:Context):JSONArray {
        val root=File(context.filesDir,"images").canonicalFile
        val result=JSONArray()
        var total=0L
        root.listFiles()?.filter { it.isFile && it.extension.lowercase()=="jpg" }?.sortedBy { it.name }?.forEach { file ->
            require(file.canonicalFile.parentFile==root && file.length()<=MAX_FILE) { "ملف صورة غير صالح للنسخ" }
            total+=file.length()
            require(total<=MAX_TOTAL) { "حجم الصور يتجاوز حد النسخة الاحتياطية" }
            val bytes=file.readBytes()
            result.put(JSONObject().put("name",file.name).put("sha256",Backup.sha(bytes))
                .put("base64",Base64.encodeToString(bytes,Base64.NO_WRAP)))
        }
        return result
    }

    /** Restore only to a fresh images directory; never overwrite an existing photo. */
    fun restore(context:Context,entries:JSONArray):List<File> {
        val dir=File(context.filesDir,"images").canonicalFile
        val pending=mutableListOf<Pair<File,ByteArray>>()
        var total=0L
        val names=mutableSetOf<String>()
        for(i in 0 until entries.length()) {
            val row=entries.getJSONObject(i)
            val name=row.getString("name")
            require(name.endsWith(".jpg") && name.removeSuffix(".jpg").matches(Regex("[a-fA-F0-9-]{36}")) && names.add(name)) { "اسم صورة غير صالح أو مكرر" }
            val dest=File(dir,name).canonicalFile
            require(dest.parentFile==dir && !dest.exists()) { "الصورة موجودة مسبقاً؛ لن تُستبدل" }
            val bytes=Base64.decode(row.getString("base64"),Base64.DEFAULT)
            total+=bytes.size
            require(bytes.isNotEmpty() && bytes.size<=MAX_FILE && total<=MAX_TOTAL) { "حجم الصور غير صالح" }
            require(Backup.sha(bytes)==row.getString("sha256")) { "صورة تالفة في النسخة الاحتياطية" }
            pending.add(dest to bytes)
        }
        require(dir.isDirectory || dir.mkdirs()) { "تعذر إنشاء مجلد الصور" }
        val created=mutableListOf<File>()
        try {
            for((dest,bytes) in pending) {
                require(dest.createNewFile()) { "تعذر إنشاء ملف الصورة" }
                created.add(dest)
                dest.outputStream().use { it.write(bytes) }
            }
        }catch(e:Exception) {
            created.forEach { it.delete() }
            throw e
        }
        return created
    }
}
