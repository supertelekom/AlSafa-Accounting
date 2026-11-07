package com.alsafa.native8.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Base64
import java.io.File
import org.json.JSONObject

/** Called only while the process-wide exclusive data lock is held. */
class NativeDataStore(private val context:Context) {
 private val home=File(context.applicationInfo.dataDir)
 private val work=File(context.noBackupFilesDir,"native8-maintenance").apply { mkdirs() }
 private val swap=DataDirectorySwap(home,work)
 fun recover()=swap.recover()
 private fun clean(name:String)=File(work,name).apply { check(deleteRecursively());check(mkdirs()) }
 fun snapshot():File {
  recover();val stage=clean("export")
  for(name in NativeArchive.roots) { val source=File(home,name);if(source.exists()) source.copyRecursively(File(stage,name),true) }
  validateDatabases(stage)
  // Embed external selected photos in the COPY, not the operational database.
  val dbFile=File(stage,"databases/alsafa_native8_operational.db")
  if(dbFile.exists()) SQLiteDatabase.openDatabase(dbFile.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
   val updates=mutableListOf<Pair<String,String>>()
   db.rawQuery("SELECT id, original_json FROM maintenance_repairs",null).use { c ->
    while(c.moveToNext()) {
     val raw=JSONObject(c.getString(1));val photo=raw.optString("watchPhoto")
     if(photo.startsWith("content://") || photo.startsWith("file://")) {
      val uri=Uri.parse(photo)
      val data=context.contentResolver.openInputStream(uri)?.use { stream ->
       val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(65536)
       while(true) { val n=stream.read(buffer);if(n<0) break;require(out.size()+n<=32*1024*1024) { "صورة الصيانة كبيرة جداً" };out.write(buffer,0,n) };out.toByteArray()
      } ?: error("تعذر نسخ صورة الصيانة ${c.getString(0)}")
      val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
      android.graphics.BitmapFactory.decodeByteArray(data,0,data.size,options)
      require(options.outWidth>0 && options.outHeight>0) { "صورة الصيانة غير صالحة" }
      raw.put("watchPhoto","data:image/*;base64,"+Base64.encodeToString(data,Base64.NO_WRAP))
      updates+=c.getString(0) to raw.toString()
     }
    }
   }
   db.beginTransaction();try { updates.forEach { (id,raw) -> db.execSQL("UPDATE maintenance_repairs SET original_json=? WHERE id=?",arrayOf(raw,id)) };db.setTransactionSuccessful() } finally { db.endTransaction() }
   db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null).use { it.moveToFirst();check(it.getInt(0)==0) }
  }
  return stage
 }
 fun prepare(input:java.io.InputStream):File {
  val stage=clean("incoming");NativeArchive.read(input,stage);validateDatabases(stage);return stage
 }
 fun validateDatabases(stage:File) {
  File(stage,"databases").listFiles().orEmpty().filter { it.isFile && !it.name.endsWith("-wal") && !it.name.endsWith("-shm") && !it.name.endsWith("-journal") }.forEach { file ->
   SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
    db.rawQuery("PRAGMA integrity_check",null).use { c -> check(c.moveToFirst() && c.getString(0)=="ok") { "قاعدة بيانات تالفة" } }
    db.rawQuery("PRAGMA foreign_key_check",null).use { check(!it.moveToFirst()) { "روابط البيانات غير سليمة" } }
    if(file.name=="alsafa_native8_operational.db") require(db.version in 19..20) { "إصدار قاعدة البيانات غير متوافق" }
    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null).use { it.moveToFirst();check(it.getInt(0)==0) }
   }
  }
 }
 fun replace(stage:File) { swap.replace(stage);context.cacheDir.deleteRecursively();clearTemporary() }
 fun clearTemporary() { listOf("export","incoming","empty","old").forEach { File(work,it).deleteRecursively() } }
 fun reset() { replace(clean("empty"));context.contentResolver.persistedUriPermissions.forEach { permission ->
  runCatching { context.contentResolver.releasePersistableUriPermission(permission.uri,(if(permission.isReadPermission) 1 else 0) or (if(permission.isWritePermission) 2 else 0)) }
 } }
}
