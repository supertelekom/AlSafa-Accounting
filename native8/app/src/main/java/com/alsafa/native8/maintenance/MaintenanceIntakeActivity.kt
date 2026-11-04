package com.alsafa.native8.maintenance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import android.view.View
import com.alsafa.native8.sales.AppDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** New intake, saved to the same Room database; no payment or stock movement on intake. */
class MaintenanceIntakeActivity:Activity() {
 private var photoUri:String=""
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(24,24,24,24)
  }
  fun field(hint:String):EditText=EditText(this).also {
   it.hint=hint
   root.addView(it,LinearLayout.LayoutParams(-1,-2))
  }
  root.addView(TextView(this).apply { text="استلام صيانة";textSize=25f })
  val customer=field("اسم العميل *")
  val phone=field("رقم الهاتف")
  val watch=field("الماركة / نوع الساعة *")
  val model=field("الموديل أو الرقم")
  val problem=field("العطل *")
  val diagnosis=field("التشخيص")
  val notes=field("ملاحظات")
  val photoLabel=TextView(this).apply { text="صورة الساعة: لم تُحدد" }
  root.addView(Button(this).apply {
   text="اختيار صورة الساعة"
   setOnClickListener {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
     type="image/*"
     addCategory(Intent.CATEGORY_OPENABLE)
     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    },701)
   }
  })
  root.addView(photoLabel)
  root.addView(Button(this).apply {
   text="حفظ طلب الصيانة"
   setOnClickListener {
    if(customer.text.isBlank()||watch.text.isBlank()||problem.text.isBlank()) {
     Toast.makeText(this@MaintenanceIntakeActivity,"أكمل الحقول المطلوبة",Toast.LENGTH_LONG).show()
     return@setOnClickListener
    }
    isEnabled=false
    Thread {
     val result=runCatching {
      val db=AppDatabase.get(applicationContext)
      val now=System.currentTimeMillis()
      val date=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(now))
      val time=SimpleDateFormat("HH:mm",Locale.US).format(Date(now))
      val id="repair-"+now+"-"+UUID.randomUUID().toString().take(6)
      runBlocking {
       db.withTransaction {
        val number=db.maintenanceDao().maxRepairInvoice()+1
        val raw=JSONObject().apply {
         put("id",id);put("invoiceNo",number);put("date",date);put("time",time)
         put("status","بانتظار التسليم")
         put("customer",customer.text.toString().trim());put("phone",phone.text.toString().trim())
         put("watch",watch.text.toString().trim());put("model",model.text.toString().trim())
         put("problem",problem.text.toString().trim());put("diagnosis",diagnosis.text.toString().trim())
         put("notes",notes.text.toString().trim());put("watchPhoto",photoUri)
         put("currency","USD");put("price",0);put("received",0)
         put("partsTotal",0);put("partsCost",0);put("cost",0)
         put("usedParts",org.json.JSONArray());put("partsStockApplied",false)
         put("createdAt",now)
        }
        db.maintenanceDao().insertNewRepair(MaintenanceRepairEntity(
         id,"native8",number,date,time,"بانتظار التسليم",
         customer.text.toString().trim(),phone.text.toString().trim(),
         watch.text.toString().trim(),model.text.toString().trim(),
         problem.text.toString().trim(),"USD","0","0","",now,raw.toString()
        ))
       }
      }
     }
     runOnUiThread {
      if(result.isSuccess) { Toast.makeText(this,"تم حفظ طلب الصيانة",Toast.LENGTH_LONG).show();finish() }
      else { isEnabled=true;Toast.makeText(this,result.exceptionOrNull()?.message ?: "تعذر الحفظ",Toast.LENGTH_LONG).show() }
     }
    }.start()
   }
  })
  setContentView(ScrollView(this).apply { addView(root) })
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==701 && resultCode==RESULT_OK) {
   val uri=data?.data ?: return
   try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
   catch(_:SecurityException) { Toast.makeText(this,"تعذر الاحتفاظ بإذن الصورة",Toast.LENGTH_LONG).show();return }
   photoUri=uri.toString()
   Toast.makeText(this,"تم اختيار صورة الساعة",Toast.LENGTH_SHORT).show()
  }
 }
}
