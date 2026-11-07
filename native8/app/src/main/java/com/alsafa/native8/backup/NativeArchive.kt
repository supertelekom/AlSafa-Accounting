package com.alsafa.native8.backup

import java.io.*
import java.security.MessageDigest
import java.util.zip.*
import org.json.JSONObject
import org.json.JSONArray

/** Portable archive with strict names, bounded expansion and per-file integrity. */
object NativeArchive {
 val roots=listOf("databases","shared_prefs","files")
 private const val limit=2L*1024*1024*1024
 private fun digest(f:File):String {
  val md=MessageDigest.getInstance("SHA-256")
  f.inputStream().use { input -> val b=ByteArray(65536);while(true) { val n=input.read(b);if(n<0) break;md.update(b,0,n) } }
  return md.digest().joinToString("") { "%02x".format(it) }
 }
 fun write(folder:File,out:OutputStream) {
  val rows=JSONArray()
  val files=roots.flatMap { name -> File(folder,name).walkTopDown().filter { it.isFile }.toList() }
  require(files.sumOf { it.length() }<=limit) { "حجم النسخة أكبر من الحد المدعوم" }
  files.forEach { f -> rows.put(JSONObject().put("path",f.relativeTo(folder).invariantSeparatorsPath).put("size",f.length()).put("sha256",digest(f))) }
  val manifest=JSONObject().put("format","alsafa-native8-full").put("version",1).put("schema",20).put("files",rows)
  ZipOutputStream(BufferedOutputStream(out)).use { zip ->
   zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString().toByteArray(Charsets.UTF_8));zip.closeEntry()
   files.forEach { f -> zip.putNextEntry(ZipEntry(f.relativeTo(folder).invariantSeparatorsPath));f.inputStream().use { it.copyTo(zip) };zip.closeEntry() }
  }
 }
 fun read(input:InputStream,folder:File) {
  require(!folder.exists() || folder.listFiles().isNullOrEmpty())
  folder.mkdirs();val seen=mutableSetOf<String>();var bytes=0L
  ZipInputStream(BufferedInputStream(input)).use { zip ->
   while(true) {
    val entry=zip.nextEntry ?: break;val name=entry.name
    require(!entry.isDirectory && seen.add(name) && seen.size<=100000) { "ملف مكرر أو غير صالح" }
    val segments=name.split('/')
    require(name=="manifest.json" || (segments.size>1 && segments.first() in roots && segments.all { it.isNotBlank() && it!="." && it!=".." && !it.contains('\\') && !it.contains(':') })) { "مسار غير صالح داخل النسخة" }
    val dest=File(folder,name);dest.parentFile!!.mkdirs()
    FileOutputStream(dest).use { out ->
     val buffer=ByteArray(65536);var entryBytes=0L
     while(true) { val n=zip.read(buffer);if(n<0) break;bytes+=n;entryBytes+=n
      require(bytes<=limit && (name!="manifest.json" || entryBytes<=16*1024*1024)) { "حجم النسخة غير مدعوم" };out.write(buffer,0,n) }
     out.fd.sync()
    }
   }
  }
  val manifest=JSONObject(File(folder,"manifest.json").readText())
  require(manifest.getString("format")=="alsafa-native8-full" && manifest.getInt("version")==1 && manifest.getInt("schema") in 19..20) { "هذه ليست نسخة كاملة متوافقة مع Native 8" }
  val rows=manifest.getJSONArray("files");val expected=mutableSetOf<String>()
  for(i in 0 until rows.length()) { val r=rows.getJSONObject(i);val path=r.getString("path")
   require(path!="manifest.json" && path in seen && expected.add(path))
   val f=File(folder,path);require(f.length()==r.getLong("size") && digest(f)==r.getString("sha256")) { "النسخة تالفة: $path" }
  }
  require(seen==expected+"manifest.json") { "محتويات النسخة لا تطابق سجلها" }
 }
}
