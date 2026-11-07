package com.alsafa.native8.sales
import com.alsafa.native8.backup.NativeArchive
import com.alsafa.native8.backup.DataDirectorySwap
import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.nio.file.Files
import java.util.zip.*
import org.json.JSONObject

class NativeArchiveTest {
 private fun directory()=Files.createTempDirectory("native8-backup-test").toFile()
 private fun put(root:File,path:String,text:String) { File(root,path).apply { parentFile.mkdirs();writeText(text) } }
 @Test fun roundTripPreservesAllRootsAndReplacementDoesNotMerge() {
  val root=directory()
  try {
   val source=File(root,"source");put(source,"databases/test.db","old+new ledger");put(source,"shared_prefs/rates.xml","50");put(source,"files/photo.jpg","photo")
   val bytes=ByteArrayOutputStream();NativeArchive.write(source,bytes)
   val incoming=File(root,"incoming");NativeArchive.read(ByteArrayInputStream(bytes.toByteArray()),incoming)
   val live=File(root,"live");put(live,"files/stale","remove")
   DataDirectorySwap(live,File(root,"work").apply { mkdirs() }).replace(incoming)
   assertEquals("old+new ledger",File(live,"databases/test.db").readText());assertEquals("50",File(live,"shared_prefs/rates.xml").readText())
   assertEquals("photo",File(live,"files/photo.jpg").readText());assertFalse(File(live,"files/stale").exists())
  } finally { root.deleteRecursively() }
 }
 @Test fun rejectsTraversalBeforeWritingOutsideStage() {
  val root=directory();try {
   val bytes=ByteArrayOutputStream();ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry("files/../../escape"));it.write(1);it.closeEntry() }
   try { NativeArchive.read(ByteArrayInputStream(bytes.toByteArray()),File(root,"incoming"));fail() } catch(_:IllegalArgumentException) {}
   assertFalse(File(root,"escape").exists())
  } finally { root.deleteRecursively() }
 }
 @Test fun rejectsChangedFileWithOriginalManifest() {
  val root=directory();try {
   put(root,"files/a","original");val bytes=ByteArrayOutputStream();NativeArchive.write(root,bytes)
   val tampered=ByteArrayOutputStream()
   ZipOutputStream(tampered).use { out -> ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
    while(true) { val entry=input.nextEntry ?: break;out.putNextEntry(ZipEntry(entry.name));out.write(if(entry.name=="files/a") "changed".toByteArray() else input.readBytes());out.closeEntry() }
   } }
   try { NativeArchive.read(ByteArrayInputStream(tampered.toByteArray()),File(root,"incoming"));fail() } catch(_:IllegalArgumentException) {}
  } finally { root.deleteRecursively() }
 }
 @Test fun interruptedReplacementRecoversAllRootsAndCanRepeatRecovery() {
  val root=directory();try {
   val home=File(root,"live").apply { mkdirs() };val work=File(root,"work").apply { mkdirs() }
   put(work,"old/databases/db","original");put(home,"databases/db","replacement");put(home,"files/photo","original photo")
   File(work,"pending.json").writeText(JSONObject().put("databases",true).put("files",true).put("shared_prefs",false).toString())
   put(home,"shared_prefs/new.xml","new setting")
   val swap=DataDirectorySwap(home,work);swap.recover();swap.recover()
   assertEquals("original",File(home,"databases/db").readText());assertEquals("original photo",File(home,"files/photo").readText());assertFalse(File(home,"shared_prefs").exists())
  } finally { root.deleteRecursively() }
 }
 @Test fun resetRemovesOnlyDataRoots() {
  val root=directory();try {
   val home=File(root,"live");put(home,"databases/db","data");put(home,"files/photo","image");put(home,"shared_prefs/rates.xml","rate");put(home,"outside.zip","saved backup")
   DataDirectorySwap(home,File(root,"work").apply { mkdirs() }).replace(File(root,"empty").apply { mkdirs() })
   NativeArchive.roots.forEach { assertFalse(File(home,it).exists()) };assertTrue(File(home,"outside.zip").exists())
  } finally { root.deleteRecursively() }
 }
 @Test fun acceptsPreviousSchemaBackupForMigration() {
  val root=directory();try {
   put(root,"files/a","saved data");val bytes=ByteArrayOutputStream();NativeArchive.write(root,bytes)
   val previous=ByteArrayOutputStream()
   ZipOutputStream(previous).use { out -> ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
    while(true){val entry=input.nextEntry ?: break;val content=input.readBytes();out.putNextEntry(ZipEntry(entry.name))
     out.write(if(entry.name.endsWith("manifest.json"))JSONObject(String(content,Charsets.UTF_8)).put("schema",19).toString().toByteArray() else content);out.closeEntry()}
   } }
   val restored=File(root,"restored");NativeArchive.read(ByteArrayInputStream(previous.toByteArray()),restored)
   assertEquals("saved data",File(restored,"files/a").readText())
  } finally {root.deleteRecursively()}
 }
}
