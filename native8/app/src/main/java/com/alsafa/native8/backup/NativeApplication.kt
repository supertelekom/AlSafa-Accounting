package com.alsafa.native8.backup

import android.app.Application
import android.os.Process
import android.widget.Toast
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/** One live process owns the data, including recovery before Room/preferences open. */
class NativeApplication:Application() {
 private lateinit var handle:RandomAccessFile
 private var lock:FileLock?=null
 override fun onCreate() {
  super.onCreate()
  handle=RandomAccessFile(java.io.File(noBackupFilesDir,"native8-data.lock"),"rw")
  val attempts=40
  repeat(attempts) {
   if(lock==null) { lock=handle.channel.tryLock();if(lock==null) Thread.sleep(50) }
  }
  if(lock==null) {
   Toast.makeText(this,"أغلق صفحة النسخ والاستعادة قبل فتح التطبيق",Toast.LENGTH_LONG).show()
   Process.killProcess(Process.myPid());return
  }
  NativeDataStore(this).recover()
 }
}
