package com.alsafa.native8.backup

import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

/** Recoverable replacement of all app data roots; never merges financial records. */
class DataDirectorySwap(private val home:File,private val work:File) {
 private val pending=File(work,"pending.json")
 fun recover() {
  if(!pending.exists()) return
  val original=JSONObject(pending.readText())
  for(name in NativeArchive.roots) {
   val old=File(work,"old/$name");val live=File(home,name)
   if(old.exists()) { check(live.deleteRecursively());check(old.renameTo(live)) }
   else if(!original.getBoolean(name)) check(live.deleteRecursively())
  }
  check(pending.delete())
 }
 fun replace(stage:File) {
  recover();val old=File(work,"old");check(old.deleteRecursively());check(old.mkdirs())
  val state=JSONObject();NativeArchive.roots.forEach { state.put(it,File(home,it).exists()) }
  val temp=File(work,"pending.tmp")
  FileOutputStream(temp).use { it.write(state.toString().toByteArray());it.fd.sync() };check(temp.renameTo(pending))
  try {
   for(name in NativeArchive.roots) {
    val live=File(home,name);if(live.exists()) check(live.renameTo(File(old,name)))
    val incoming=File(stage,name);if(incoming.exists()) check(incoming.renameTo(live))
   }
   check(pending.delete())
  } catch(e:Exception) { recover();throw e }
  old.deleteRecursively()
 }
}
