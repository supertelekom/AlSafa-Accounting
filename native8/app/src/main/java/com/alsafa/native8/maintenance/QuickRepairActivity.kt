package com.alsafa.native8.maintenance

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import android.widget.TextView
import com.alsafa.native8.stylekit.*
import com.alsafa.native8.sales.AppDatabase
import com.alsafa.native8.sales.ExchangeRateSettings
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.math.RoundingMode
import java.util.UUID

class QuickRepairActivity:Activity() {
 private lateinit var page:QuickRepairPage
 private lateinit var buttons:List<JSONObject>
 private var selected:JSONObject?=null
 private var changing=false
 private var previousCurrency="TRY"
 private var saving=false
 private var requestId="repair-"+UUID.randomUUID()
 override fun onCreate(state:Bundle?) {
  super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);window.statusBarColor=SafaStyle.Palette.cream;window.navigationBarColor=SafaStyle.Palette.cream;requestId=state?.getString("requestId") ?: requestId
  setContentView(TextView(this).apply { text="جاري قراءة أزرار الصيانة…" })
  Thread {
   val result=runCatching {
    val settings=QuickRepairRepository(applicationContext).settings()
    val parts=runBlocking { AppDatabase.get(applicationContext).maintenanceDao().parts() }.associateBy { it.id }
    Triple(settings,QuickRepairRules.buttons(settings),parts)
   }
   runOnUiThread {
    if(isFinishing || isDestroyed) return@runOnUiThread
    result.fold(onSuccess={ (settings,rows,parts) ->
     buttons=rows
     page=QuickRepairPage(SafaWidgets(SafaStyle(this)),settings.optString("shopName","الصفا للساعات"),rows.map { b ->
      QuickRepairPage.Service(b.getString("id"),b.getString("label"),b.optString("icon","🔧"),b.optString("price","0")+" TRY",b.optString("priceMode")=="manual",parts[b.optString("partId")]?.let { "متوفر ${it.stock}" }.orEmpty())
     },object:QuickRepairPage.Callbacks {
      override fun back(){if(!saving) finish()}
      override fun selectService(id:String){
       selected=buttons.single { it.getString("id")==id }
       changing=true;page.baseAmount.setText(if(selected!!.optString("priceMode")=="manual") "" else selected!!.optString("price"));page.workDone.setText("");changing=false
       convertBase()
      }
      override fun amountChanged(amount:String,currency:String){
       if(changing) return
       if(currency!=previousCurrency) { previousCurrency=currency;convertBase() }
      }
      override fun baseAmountChanged(amount:String){if(!changing) convertBase()}
      override fun submit(draft:QuickRepairPage.Draft){save(draft)}
     })
     setContentView(page.view)
     state?.getString("draft")?.let { value -> runCatching {
      val d=JSONObject(value);page.restore(QuickRepairPage.Draft(d.getString("id"),d.getString("base"),d.getString("work"),d.getBoolean("deliver"),d.getString("customer"),d.getString("phone"),d.getString("amount"),d.getString("currency"),d.getString("method")))
     } }
    },onFailure={ e -> setContentView(TextView(this).apply { text="تعذر قراءة أزرار الصيانة: ${e.message}" }) })
   }
  }.start()
 }
 private fun convertBase() {
  if(!::page.isInitialized) return
  val amount=runCatching { QuickRepairRules.amount(page.baseAmount.text.toString()) }.getOrNull()
  changing=true
  if(amount==null) { page.money.amount.setText("");page.showConversion("اكتب مبلغ الصيانة الأساسي") }
  else runCatching {
   val final=amount.multiply(ExchangeRateSettings(this).rate("TRY",page.money.currency)).setScale(2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
   page.money.amount.setText(final);page.showConversion("$final ${page.money.currency} — يمكن تعديل المبلغ النهائي")
  }.onFailure { page.money.amount.setText("");page.showConversion(it.message.orEmpty(),true) }
  changing=false
 }
 private fun save(d:QuickRepairPage.Draft) {
  if(saving) return
  saving=true;page.busy(true)
  Thread {
   val result=runCatching { runBlocking {
    QuickRepairRepository(applicationContext).save(requestId,d.serviceId,d.baseAmount,d.finalAmount,d.currency,d.paymentMethod,d.workDone,d.deliverNow,d.customer,d.phone)
   } }
   runOnUiThread {
    saving=false;page.busy(false)
    result.fold(onSuccess={ number ->
     Toast.makeText(this,"تم تسجيل ${selected?.optString("label")} — فاتورة رقم $number",Toast.LENGTH_LONG).show()
     startActivity(Intent(this,MaintenanceActivity::class.java));finish()
    },onFailure={page.showConversion(it.message ?: "تعذر حفظ الصيانة",true);Toast.makeText(this,it.message,Toast.LENGTH_LONG).show()})
   }
  }.start()
 }
 override fun onSaveInstanceState(out:Bundle) {
  out.putString("requestId",requestId)
  if(::page.isInitialized) { val d=page.snapshot();out.putString("draft",JSONObject().put("id",d.serviceId).put("base",d.baseAmount).put("work",d.workDone).put("deliver",d.deliverNow).put("customer",d.customer).put("phone",d.phone).put("amount",d.finalAmount).put("currency",d.currency).put("method",d.paymentMethod).toString()) }
  super.onSaveInstanceState(out)
 }
 @Deprecated("Legacy back callback") override fun onBackPressed(){if(!saving) super.onBackPressed()}
}
