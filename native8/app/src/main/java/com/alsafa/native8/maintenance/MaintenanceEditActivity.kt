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
import org.json.JSONArray
import java.math.BigDecimal

/** Edits undelivered repair drafts while preserving historical payment and cost evidence. */
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
  root.addView(TextView(this).apply { text="عملة الفاتورة (يمكن تغييرها للصيانة دون قطع)" })
  val currency=Spinner(this).apply { adapter=ArrayAdapter(this@MaintenanceEditActivity,android.R.layout.simple_spinner_dropdown_item,listOf("USD","TRY","SYP")) };root.addView(currency)
  val labor=field("أجرة العمل — بعملة الفاتورة").apply { inputType=8194 }
  val partsButton=Button(this).apply { text="تحديث العطل واختيار قطع الصيانة" }
  root.addView(partsButton)
  val partsSummary=TextView(this).apply { text="القطع المختارة: لا توجد قطع" }
  root.addView(partsSummary)
  val save=Button(this).apply { text="حفظ التعديلات";isEnabled=false }
  root.addView(save)
  setContentView(ScrollView(this).apply { addView(root) })
  Thread {
   val result=runCatching { runBlocking { AppDatabase.get(applicationContext).maintenanceDao().repair(id) } }
   runOnUiThread {
    val repair=result.getOrNull()
    if(repair==null || repair.status=="تم التسليم") {
     status.text="لا يمكن تعديل صيانة تم تسليمها."
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
    currency.setSelection(listOf("USD","TRY","SYP").indexOf(repair.currency).coerceAtLeast(0))
    labor.setText(com.alsafa.native8.sales.InvoiceDisplay.amount(json.optString("laborPrice",json.optString("price","0"))))
    var pendingParts:MutableList<MaintenancePartsRepository.SelectedPart>?=null
    val currentParts=json.optJSONArray("usedParts") ?: JSONArray()
    val existing=mutableListOf<MaintenancePartsRepository.SelectedPart>()
    for(i in 0 until currentParts.length()) {
     val row=currentParts.optJSONObject(i) ?: continue
     val partId=row.optString("partId")
     if(partId.isNotBlank()) existing.add(MaintenancePartsRepository.SelectedPart(partId,row.optString("qty","1"),row.optString("price","0")))
    }
    val partNames=mutableMapOf<String,String>()
    for(i in 0 until currentParts.length()) {
     val row=currentParts.optJSONObject(i) ?: continue
     val partId=row.optString("partId")
     val name=row.optString("name")
     if(partId.isNotBlank() && name.isNotBlank()) partNames[partId]=name
    }
    fun showParts(lines:List<MaintenancePartsRepository.SelectedPart>) {
     partsSummary.text=if(lines.isEmpty()) "القطع المختارة: لا توجد قطع" else
      "القطع المختارة: "+lines.joinToString("، ") { "${partNames[it.partId] ?: "قطعة صيانة"} × ${it.qty}" }
    }
    showParts(existing)
    partsButton.setOnClickListener {
     Thread {
      val result=runCatching { runBlocking { MaintenancePartsRepository(AppDatabase.get(applicationContext)).list() } }
      runOnUiThread {
       val parts=result.getOrNull().orEmpty()
       if(parts.isEmpty()) { Toast.makeText(this,"مستودع قطع الصيانة فارغ",Toast.LENGTH_LONG).show();return@runOnUiThread }
       parts.forEach { partNames[it.id]=it.name }
       showParts(pendingParts ?: existing)
       val labels=parts.map { "${it.category} / ${it.name} — ${it.stock} متاح" }.toTypedArray()
       android.app.AlertDialog.Builder(this).setTitle("القطع المقترحة / المراد تركيبها")
        .setItems(labels) { _,index ->
         val part=parts[index]
         val fields=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,8,24,8) }
         val qty=EditText(this).apply { hint="الكمية";setText("1");inputType=android.text.InputType.TYPE_CLASS_NUMBER }
         val price=EditText(this).apply { hint="سعر العميل — ${currency.selectedItem}";setText(runCatching { com.alsafa.native8.sales.InvoiceDisplay.amount(part.sale_price.toBigDecimal()*com.alsafa.native8.sales.ExchangeRateSettings(this@MaintenanceEditActivity).rate(part.currency,currency.selectedItem.toString())) }.getOrDefault(""));inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
         fields.addView(qty);fields.addView(price)
         android.app.AlertDialog.Builder(this).setTitle(part.name).setView(fields).setNegativeButton("إلغاء",null)
          .setPositiveButton("إضافة القطعة للصيانة") { _,_ ->
           val q=qty.text.toString().toBigDecimalOrNull()
           val s=price.text.toString().toBigDecimalOrNull()
           val available=part.stock.toBigDecimalOrNull()
           if(q==null||s==null||available==null||q<=BigDecimal.ZERO||q>available||q.stripTrailingZeros().scale()>0||s<BigDecimal.ZERO) {
            Toast.makeText(this,"الكمية أو السعر غير صحيح",Toast.LENGTH_LONG).show()
           } else {
            val chosen=pendingParts ?: existing.toMutableList()
            chosen.removeAll { it.partId==part.id }
            chosen.add(MaintenancePartsRepository.SelectedPart(part.id,q.toPlainString(),s.toPlainString()))
            pendingParts=chosen;showParts(chosen)
           }
          }.show()
        }.setNeutralButton("مسح القطع") { _,_ -> pendingParts=mutableListOf();showParts(emptyList()) }.show()
      }
     }.start()
    }
    status.text="تعديل الصيانة رقم "+repair.invoice_number
    save.isEnabled=true
    save.setOnClickListener {
     if(customer.text.isBlank() || watch.text.isBlank() || problem.text.isBlank()) {
      Toast.makeText(this,"العميل والساعة والعطل مطلوبة",Toast.LENGTH_LONG).show()
      return@setOnClickListener
     }
     val invoiceCurrency=currency.selectedItem.toString()
     val laborValue=labor.text.toString().toBigDecimalOrNull()
     if(laborValue==null || laborValue<BigDecimal.ZERO) { Toast.makeText(this,"أجرة العمل غير صحيحة",Toast.LENGTH_LONG).show();return@setOnClickListener }
     save.isEnabled=false
     val requestedParts=pendingParts?.toList()
     val values=listOf(customer,phone,watch,model,problem,diagnosis,workDone,notes)
      .map { it.text.toString().trim() }
     Thread {
      val saved=runCatching {
       runBlocking {
        val db=AppDatabase.get(applicationContext)
        db.withTransaction {
         val latest=db.maintenanceDao().repair(id) ?: error("الصيانة غير موجودة")
         require(latest.status!="تم التسليم" && !JSONObject(latest.original_json).has("nativeArchiveDelivery")) { "لا يمكن تعديل السجل" }
         require(latest.original_json==repair.original_json) {"تغيرت الفاتورة؛ أعد فتح التعديل"}
         val raw=JSONObject(latest.original_json).put("laborPrice",laborValue.toPlainString())
         if(latest.source_sha256!="native8" && !raw.has("nativeDraftOriginal"))raw.put("nativeDraftOriginal",latest.original_json)
         if(invoiceCurrency!=latest.currency) {
          require(latest.source_sha256=="native8"){"عملة الفاتورة المستعادة محفوظة مع دفعاتها وأسعار صرفها؛ أضف الأجرة والقطع بعملتها الحالية"}
          require((raw.optJSONArray("usedParts")?.length() ?: 0)==0 && requestedParts.isNullOrEmpty() && raw.optString("manualCost","0").toBigDecimal().signum()==0) { "تغيير العملة هنا متاح للصيانة دون قطع أو تكلفة إضافية فقط" }
          raw.put("currency",invoiceCurrency)
          check(db.maintenanceDao().updateDraftCurrency(id,latest.original_json,raw.toString(),invoiceCurrency)==1)
         }
         listOf("customer","phone","watch","model","problem","diagnosis","workDone","notes")
          .forEachIndexed { index,key -> raw.put(key,values[index]) }
         check(db.maintenanceDao().editNewRepair(id,values[0],values[1],values[2],
          values[3],values[4],latest.status,raw.toString(),if(invoiceCurrency!=latest.currency) db.maintenanceDao().repair(id)!!.original_json else latest.original_json)==1)
         if(requestedParts!=null) MaintenancePartsRepository(db).saveDraft(id,requestedParts)
         MaintenancePartsRepository(db).refreshDraftTotal(id)
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
