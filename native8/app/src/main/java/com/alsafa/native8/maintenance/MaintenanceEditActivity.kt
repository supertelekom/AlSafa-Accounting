package com.alsafa.native8.maintenance

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.*
import androidx.room.withTransaction
import com.alsafa.native8.sales.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Edits intake details only. Imported historical records remain unchanged. */
class MaintenanceEditActivity:Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val id=intent.getStringExtra("repair_id") ?: run { finish();return }
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(24,24,24,24)
  }
  val status=TextView(this)
  root.addView(status)
  fun field(label:String):EditText=EditText(this).also {
   it.hint=label;root.addView(it)
  }
  val customer=field("العميل")
  val phone=field("الهاتف")
  val watch=field("الساعة")
  val model=field("الموديل")
  val problem=field("العطل")
  val diagnosis=field("التشخيص")
  val workDone=field("العمل المنجز")
  val notes=field("ملاحظات")
  val save=Button(this).apply { text="حفظ التعديلات";isEnabled=false }
  root.addView(save)
  setContentView(ScrollView(this).apply { addView(root) })
  Thread {
   val result=runCatching { runBlocking { AppDatabase.get(applicationContext).maintenanceDao().repair(id) } }
   runOnUiThread {
    val repair=result.getOrNull()
    if(repair==null || repair.source_sha256!="native8" || repair.status=="تم التسليم") {
     status.text="التعديل غير متاح لهذا السجل؛ الأرشيف والتسليم النهائي محميان."
     return@runOnUiThread
    }
    val json=JSONObject(repair.original_json)
    customer.setText(repair.customer)
    phone.setText(repair.phone)
    watch.setText(repair.watch)
    model.setText(repair.model)
    problem.setText(repair.problem)
    diagnosis.setText(json.optString("diagnosis"))
    workDone.setText(json.optString("workDone"))
    notes.setText(json.optString("notes"))
    status.text="تعديل الصيانة رقم "+repair.invoice_number
    save.isEnabled=true
    save.setOnClickListener {
     if(customer.text.isBlank() || watch.text.isBlank() || problem.text.isBlank()) {
      Toast.makeText(this,"العميل والساعة والعطل مطلوبة",Toast.LENGTH_LONG).show()
      return@setOnClickListener
     }
     save.isEnabled=false
     val values=listOf(customer,phone,watch,model,problem,diagnosis,workDone,notes)
      .map { it.text.toString().trim() }
     Thread {
      val saved=runCatching {
       runBlocking {
        val db=AppDatabase.get(applicationContext)
        db.withTransaction {
         val latest=db.maintenanceDao().repair(id) ?: error("الصيانة غير موجودة")
         require(latest.source_sha256=="native8" && latest.status!="تم التسليم") { "لا يمكن تعديل السجل" }
         val raw=JSONObject(latest.original_json)
         listOf("customer","phone","watch","model","problem","diagnosis","workDone","notes")
          .forEachIndexed { index,key -> raw.put(key,values[index]) }
         check(db.maintenanceDao().editNewRepair(id,values[0],values[1],values[2],
          values[3],values[4],latest.status,raw.toString())==1)
        }
       }
      }
      runOnUiThread {
       if(saved.isSuccess) { setResult(RESULT_OK,Intent().putExtra("repair_id",id));finish() }
       else { save.isEnabled=true;Toast.makeText(this,saved.exceptionOrNull()?.message ?: "فشل الحفظ",Toast.LENGTH_LONG).show() }
      }
     }.start()
    }
   }
  }.start()
 }
}
