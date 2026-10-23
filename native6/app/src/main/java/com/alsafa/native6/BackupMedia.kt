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

    /** Reject files that have a JPG name but are not actually JPEG images. */
    private fun validJpeg(bytes:ByteArray):Boolean =
        bytes.size>=4 && (bytes[0].toInt() and 255)==0xFF &&
            (bytes[1].toInt() and 255)==0xD8 &&
            (bytes[bytes.lastIndex-1].toInt() and 255)==0xFF &&
            (bytes[bytes.lastIndex].toInt() and 255)==0xD9

    fun collect(context:Context):JSONArray {
        val root=File(context.filesDir,"images").canonicalFile
        val result=JSONArray()
        require(root.parentFile==context.filesDir.canonicalFile) { "مجلد الصور خارج تخزين التطبيق" }
        require(root.isDirectory || !root.exists()) { "مسار مجلد الصور غير صالح" }
        var total=0L
        val files=if(root.exists()) requireNotNull(root.listFiles()) { "تعذر قراءة مجلد الصور" }.toList() else emptyList()
        require(files.none { it.isFile && it.extension.lowercase() in setOf("jpeg","png","webp") }) { "توجد صور بصيغة غير مدعومة في النسخة" }
        require(files.none { it.isDirectory }) { "مجلد فرعي غير متوقع ضمن الصور" }
        require(files.size<=10000) { "عدد ملفات الصور يتجاوز الحد الآمن" }
        require(files.none { it.isFile && it.extension.lowercase() !in setOf("jpg","jpeg","png","webp") }) { "توجد ملفات غير معروفة في مجلد الصور" }
        files.filter { it.isFile && it.extension.lowercase()=="jpg" }.sortedBy { it.name }.forEach { file ->
            require(file.canonicalFile.parentFile==root && file.length()<=MAX_FILE) { "ملف صورة غير صالح للنسخ" }
            require(file.name.removeSuffix(".jpg").matches(Regex("[a-fA-F0-9-]{36}"))) { "اسم صورة غير صالح" }
            require(file.length()>0L) { "صورة فارغة في النسخة" }
            total+=file.length()
            require(total<=MAX_TOTAL) { "حجم الصور يتجاوز حد النسخة الاحتياطية" }
            val bytes=file.readBytes()
            require(bytes.size.toLong()==file.length() && bytes.size.toLong()<=MAX_FILE) { "تغير حجم الصورة أثناء النسخ" }
            require(validJpeg(bytes)) { "ملف الصورة ليس JPEG صالحاً" }
            result.put(JSONObject().put("name",file.name).put("sha256",Backup.sha(bytes))
                .put("base64",Base64.encodeToString(bytes,Base64.NO_WRAP)))
        }
        return result
    }

    /** Restore only to a fresh images directory; never overwrite an existing photo. */
    fun restore(context:Context,entries:JSONArray):List<File> {
        val dir=File(context.filesDir,"images").canonicalFile
        require(dir.parentFile==context.filesDir.canonicalFile) { "مسار استعادة الصور غير آمن" }
        var total=0L
        val names=mutableSetOf<String>()
        require(entries.length()<=10000) { "عدد الصور في النسخة كبير جداً" }
        // Validate metadata first without keeping every decoded image in memory.
        for(i in 0 until entries.length()) {
            val row=entries.getJSONObject(i)
            val name=row.getString("name")
            require(name.endsWith(".jpg") && name.removeSuffix(".jpg").matches(Regex("[a-fA-F0-9-]{36}")) && names.add(name)) { "اسم صورة غير صالح أو مكرر" }
            val dest=File(dir,name).canonicalFile
            require(dest.parentFile==dir && !dest.exists()) { "الصورة موجودة مسبقاً؛ لن تُستبدل" }
            val encoded=row.getString("base64")
            require(encoded.length<=((MAX_FILE+2)/3*4).toInt()+8) { "حجم الصورة المشفرة أكبر من الحد" }
            require(encoded.length%4==0 && encoded.all { (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9') || it=='+' || it=='/' || it=='=' }) { "ترميز الصورة غير صالح" }
            total+=encoded.length.toLong()/4L*3L
            require(total<=MAX_TOTAL+entries.length()*2L) { "حجم الصور يتجاوز الحد" }
        }
        require(dir.isDirectory || dir.mkdirs()) { "تعذر إنشاء مجلد الصور" }
        require(dir.canonicalFile.parentFile==context.filesDir.canonicalFile) { "تغير مسار الصور أثناء الاستعادة" }
        val created=mutableListOf<File>()
        var decodedTotal=0L
        try {
            for(i in 0 until entries.length()) {
                val row=entries.getJSONObject(i)
                val dest=File(dir,row.getString("name")).canonicalFile
                require(dest.parentFile==dir.canonicalFile && !dest.exists()) { "مسار صورة غير آمن" }
                val bytes=Base64.decode(row.getString("base64"),Base64.NO_WRAP)
                decodedTotal+=bytes.size.toLong()
                require(bytes.isNotEmpty() && bytes.size<=MAX_FILE && decodedTotal<=MAX_TOTAL) { "حجم الصور غير صالح" }
                require(validJpeg(bytes)) { "محتوى الصورة ليس JPEG صالحاً" }
                require(Backup.sha(bytes)==row.getString("sha256")) { "صورة تالفة في النسخة الاحتياطية" }
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
