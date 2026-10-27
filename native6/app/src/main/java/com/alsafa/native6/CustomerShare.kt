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
        val allowedRestored=File(activity.cacheDir,"restored_invoices").canonicalFile
        val parent=file.canonicalFile.parentFile
        require(parent==allowedPhoto || parent==allowedInvoice || parent==allowedRestored) { "المشاركة من مجلد غير مسموح" }
        require(mime in setOf("image/jpeg","image/png","image/webp","application/pdf"))
        require((mime.startsWith("image/") && parent==allowedPhoto) || (mime=="application/pdf" && (parent==allowedInvoice || parent==allowedRestored)))
        val uri=FileProvider.getUriForFile(activity,activity.packageName+".share",file)
        val intent=Intent(Intent.ACTION_SEND).apply {
            type=mime
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_TEXT,caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData=android.content.ClipData.newUri(activity.contentResolver,"AlSafa",uri)
        }
        // The Android chooser exposes WhatsApp and WhatsApp Business when installed.
        // Do not rely on package visibility checks, which can fail on Android 11+.
        activity.startActivity(Intent.createChooser(intent,"مشاركة مع العميل"))

    }
}
