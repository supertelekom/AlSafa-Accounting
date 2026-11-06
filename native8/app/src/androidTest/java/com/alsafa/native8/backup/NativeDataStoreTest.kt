package com.alsafa.native8.backup

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.nio.file.Files

/** Isolated database fixture; never touches the user's operational data. */
@RunWith(AndroidJUnit4::class)
class NativeDataStoreTest {
 @Test fun sqliteRoundTripAndResetPreserveSavedExternalArchive() {
  val base=ApplicationProvider.getApplicationContext<Context>()
  val root=Files.createTempDirectory(base.cacheDir.toPath(),"native8-roundtrip").toFile()
  try {
   val home=File(root,"home").apply { mkdirs() }
   val isolated=object:ContextWrapper(base) {
    override fun getApplicationInfo()=ApplicationInfo(base.applicationInfo).apply { dataDir=home.path }
    override fun getNoBackupFilesDir()=File(home,"no_backup").apply { mkdirs() }
    override fun getCacheDir()=File(home,"cache").apply { mkdirs() }
   }
   val photo=File(root,"picked.png")
   photo.outputStream().use { android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
   val file=File(home,"databases/alsafa_native8_operational.db").apply { parentFile.mkdirs() }
   SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
    db.version=19
    db.execSQL("CREATE TABLE maintenance_repairs(id TEXT PRIMARY KEY, original_json TEXT NOT NULL)")
    db.execSQL("INSERT INTO maintenance_repairs VALUES(?,?)",arrayOf("repair-1",org.json.JSONObject().put("id","repair-1").put("watchPhoto",android.net.Uri.fromFile(photo).toString()).toString()))
    db.execSQL("CREATE TABLE cash(id TEXT PRIMARY KEY,amount TEXT)")
    db.execSQL("INSERT INTO cash VALUES('receipt-1','250')")
   }
   File(home,"shared_prefs/rates.xml").apply { parentFile.mkdirs();writeText("<map><string name=\"USD_TRY\">50</string></map>") }
   val store=NativeDataStore(isolated);val archive=File(root,"saved.zip")
   archive.outputStream().use { NativeArchive.write(store.snapshot(),it) }
   assertTrue(photo.delete())
   SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DELETE FROM cash") }
   val incoming=archive.inputStream().use { store.prepare(it) };store.replace(incoming)
   SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
    db.rawQuery("SELECT id,amount FROM cash",null).use { assertTrue(it.moveToFirst());assertEquals("receipt-1",it.getString(0));assertEquals("250",it.getString(1));assertFalse(it.moveToNext()) }
   }
   SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
    db.rawQuery("SELECT original_json FROM maintenance_repairs",null).use { c -> assertTrue(c.moveToFirst());assertTrue(org.json.JSONObject(c.getString(0)).getString("watchPhoto").startsWith("data:image/")) }
   }
   assertTrue(File(home,"shared_prefs/rates.xml").readText().contains("50"))
   // Avoid revoking real URI grants in this isolated instrumentation fixture.
   DataDirectorySwap(home,File(home,"no_backup/native8-maintenance")).replace(File(root,"empty").apply { mkdirs() })
   NativeArchive.roots.forEach { assertFalse(File(home,it).exists()) };assertTrue(archive.exists())
  } finally { root.deleteRecursively() }
 }
}
