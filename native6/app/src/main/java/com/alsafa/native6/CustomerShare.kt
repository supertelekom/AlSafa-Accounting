package com.alsafa.native6

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares only files stored in the app's private photo or invoice export directories. */
object CustomerShare {
    fun send(activity:Activity,file:File,mime:String,caption:String) {
        require(file.isFile && file.length()>0) { "الملف غير موجود أو فارغ" }
        val allowedPhoto=File(activity.filesDir,"images").canonicalFile
        val allowedInvoice=File(activity.cacheDir,"shared_invoices").canonicalFile
        val parent=file.canonicalFile.parentFile
        require(parent==allowedPhoto || parent==allowedInvoice) { "المشاركة من مجلد غير مسموح" }
        val uri=FileProvider.getUriForFile(activity,activity.packageName+".share",file)
        val intent=Intent(Intent.ACTION_SEND).apply {
            type=mime
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_TEXT,caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData=android.content.ClipData.newUri(activity.contentResolver,"AlSafa",uri)
        }
        val whatsapp=Intent(intent).apply { setPackage("com.whatsapp") }
        val whatsappBusiness=Intent(intent).apply { setPackage("com.whatsapp.w4b") }
        val manager=activity.packageManager
        val target=when {
            whatsapp.resolveActivity(manager)!=null -> whatsapp
            whatsappBusiness.resolveActivity(manager)!=null -> whatsappBusiness
            else -> intent
        }
        activity.startActivity(Intent.createChooser(target,"مشاركة مع العميل"))
    }
}
