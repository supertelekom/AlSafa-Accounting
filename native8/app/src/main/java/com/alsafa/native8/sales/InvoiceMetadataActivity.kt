package com.alsafa.native8.sales
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.room.withTransaction
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class InvoiceMetadataActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 override fun onCreate(state:Bundle?) {
  super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  val id=intent.getStringExtra("invoice_id") ?: run{finish();return}
  val kind=intent.getStringExtra("kind") ?: run{finish();return}
  val ui=SafaWidgets(SafaStyle(this));val host=SafaPageHost(ui,"الصفا للساعات");setContentView(host.view)
  ui.heading(host.body,"تعديل بيانات الفاتورة", "تعديل بيانات الفاتورة لا يغيّر المبالغ أو المخزون.")
  val error=ui.text("");ui.add(host.body,error)
  scope.launch {
   try {
    val db=AppDatabase.get(applicationContext)
    val original=withContext(Dispatchers.IO){when(kind){"sale"->requireNotNull(db.legacySaleDao().legacyById(id)).original_json;"repair"->requireNotNull(db.maintenanceDao().repair(id)).original_json;else->error("نوع فاتورة غير معروف")}}
    val raw=JSONObject(original)
    fun field(label:String,key:String,numeric:Boolean=false)=ui.input(label,numeric,key=="notes").apply{setText(raw.optString(key));ui.label(host.body,label);ui.add(host.body,this)}
    val number=field("رقم الفاتورة","invoiceNo",true);val date=field("التاريخ","date").also{if(SafaDateTime.fullMode(this@InvoiceMetadataActivity))SafaDateTime.bindDate(it)};val time=field("الوقت","time").apply{if(text.isBlank())setText("00:00")}.also{if(SafaDateTime.fullMode(this@InvoiceMetadataActivity))SafaDateTime.bindTime(it)}
    val customer=field("اسم العميل","customer");val phone=field("رقم الهاتف","phone");val notes=field("ملاحظات","notes")
    lateinit var save:android.widget.Button
    save=ui.button("حفظ التعديل",true){
     val edit=runCatching {InvoiceMetadataEdit(number.text.toString().toLong(),date.text.toString().trim(),time.text.toString().trim(),customer.text.toString(),phone.text.toString(),notes.text.toString())}
     val value=edit.getOrElse{error.text="تحقق من رقم الفاتورة";return@button}
     save.isEnabled=false
     scope.launch {try {
      val updated=value.apply(original)
      withContext(Dispatchers.IO){db.withTransaction {
       val dao=db.invoiceMetadataDao()
       require(dao.duplicates(kind,id,value.number)==0){"رقم الفاتورة مستخدم لفاتورة أخرى"}
       val changed=if(kind=="sale")dao.sale(id,original,value.number,LocalDate.parse(value.date).atTime(LocalTime.parse(value.time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),value.customer.trim(),value.phone.trim(),updated)
        else dao.repair(id,original,value.number,value.date,value.time,value.customer.trim(),value.phone.trim(),updated)
       check(changed==1){"تغيرت الفاتورة؛ أعد فتح التعديل"}
      }}
      Toast.makeText(this@InvoiceMetadataActivity,"تم تعديل بيانات الفاتورة",Toast.LENGTH_LONG).show()
      setResult(RESULT_OK,Intent().putExtra("repair_id",id));finish()
     }catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message ?: "تعذر حفظ التعديل"}finally{save.isEnabled=true}}
    }
    ui.add(host.body,save);ui.add(host.body,ui.button("إلغاء"){finish()})
   }catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message ?: "تعذر قراءة الفاتورة"}
  }
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
