package com.alsafa.native8.maintenance

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject

/** Shares a customer-facing maintenance update through WhatsApp Business only. */
object MaintenanceMessaging {
 fun share(context:Context,repair:MaintenanceRepairEntity) {
  val digits=repair.phone.filter { it.isDigit() }
  if(digits.isBlank()) {
   val ui=com.alsafa.native8.stylekit.SafaWidgets(com.alsafa.native8.stylekit.SafaStyle(context))
   val body=ui.column(20)
   ui.heading(body,"رقم الهاتف غير موجود","أضف رقم هاتف العميل من «تعديل بيانات الفاتورة»، ثم أعد الإرسال عبر واتساب أعمال.")
   val dialog=android.app.AlertDialog.Builder(context).setView(body).create()
   ui.add(body,ui.button("حسناً",true){dialog.dismiss()})
   dialog.show()
   return
  }
  val raw=JSONObject(repair.original_json)
  val text=buildString {
   append("الصفا للساعات\n")
   append("رقم الصيانة: ").append(repair.invoice_number).append("\n")
   append("الساعة: ").append(repair.watch).append("\n")
   append("العطل: ").append(repair.problem).append("\n")
   append("الحالة: ").append(repair.status).append("\n")
   raw.optString("diagnosis").takeIf { it.isNotBlank() }?.let { append("التشخيص: ").append(it).append("\n") }
   raw.optString("workDone").takeIf { it.isNotBlank() }?.let { append("العمل المنجز: ").append(it).append("\n") }
  }
  val photo=raw.optString("watchPhoto")
  val uri=photo.takeIf { it.startsWith("content://") }?.let(Uri::parse)
  val intent=Intent(Intent.ACTION_SEND).apply {
   setPackage("com.whatsapp.w4b")
   type=if(uri==null) "text/plain" else "image/*"
   putExtra(Intent.EXTRA_TEXT,text)
   putExtra("jid",digits+"@s.whatsapp.net")
   if(uri!=null) {
    putExtra(Intent.EXTRA_STREAM,uri)
    clipData=android.content.ClipData.newUri(context.contentResolver,"صورة الصيانة",uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   }
  }
  try { context.startActivity(intent) }
  catch(e:Exception) {
   Toast.makeText(context,"تعذر فتح واتساب أعمال: "+(e.message ?: ""),Toast.LENGTH_LONG).show()
  }
 }
}
