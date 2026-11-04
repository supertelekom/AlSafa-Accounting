package com.alsafa.native8.maintenance

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView

/** Original backups may contain base64 data URLs, not Android content URIs. */
object MaintenancePhoto {
 fun display(view:ImageView,raw:String):Boolean {
  if(raw.isBlank()) return false
  return try {
   if(raw.startsWith("data:image/",true)) {
    val encoded=raw.substringAfter(',',"")
    if(encoded.isBlank()) return false
    val bytes=Base64.decode(encoded,Base64.DEFAULT)
    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: return false
    view.setImageBitmap(bitmap)
   } else if(raw.startsWith("content://") || raw.startsWith("file://")) {
    view.setImageURI(Uri.parse(raw))
   } else return false
   true
  } catch(_:Exception) { false }
 }
}
