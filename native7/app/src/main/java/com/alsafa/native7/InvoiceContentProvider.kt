package com.alsafa.native7

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** Read-only access to generated PDFs; never expose arbitrary filesystem paths. */
class InvoiceContentProvider:ContentProvider() {
    override fun onCreate()=true
    override fun getType(uri:Uri)="application/pdf"
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,
        selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,
        selectionArgs:Array<out String>?)=0
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        require(mode=="r") { "للقراءة فقط" }
        require(uri.authority==context?.packageName+".invoices")
        val name=uri.lastPathSegment ?: error("اسم الملف مفقود")
        require(Regex("sale-[A-Za-z0-9_-]+(?:-[0-9]{2,3}mm)?\\.pdf").matches(name)) { "اسم ملف غير صالح" }
        val dir=File(requireNotNull(context).cacheDir,"invoices").canonicalFile
        val file=File(dir,name).canonicalFile
        require(file.parentFile==dir && file.isFile) { "الفاتورة غير موجودة" }
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
