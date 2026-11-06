package com.alsafa.native8.maintenance
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import com.alsafa.native8.sales.AppDatabase
import com.alsafa.native8.sales.LegacyMaintenanceProfitReader
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/** renderFatherDeliveries: all pending/ready records, including restored records. */
class ReadyDeliveriesActivity:Activity() {
 private var generation=0
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)}
 override fun onResume(){super.onResume();load()}
 private fun load(){
  val ui=SafaWidgets(SafaStyle(this));val host=SafaPageHost(ui,"الصفا للساعات")
  ui.heading(host.body,"تسليم الساعات","الساعات الجاهزة أو المتبقية لدينا بانتظار العميل.")
  ui.add(host.body,ui.button("رجوع"){finish()})
  val list=ui.column();ui.add(host.body,list);ui.add(list,ui.text("جاري القراءة…"));setContentView(host.view)
  val token=++generation
  Thread { val result=runCatching {
   val rows=runBlocking { AppDatabase.get(applicationContext).maintenanceDao().readyDeliveries() }
   val staging=com.alsafa.native8.data.migration.LegacyStagingDatabase(applicationContext)
   try {
    val reader=com.alsafa.native8.data.migration.LegacyReadRepository(staging)
    val histories=rows.filter { it.source_sha256!="native8" }.map { it.source_sha256 }.distinct().associateWith { source ->
     val run=staging.readableDatabase.rawQuery("SELECT id FROM import_runs WHERE source_sha256=? ORDER BY id DESC LIMIT 1",arrayOf(source)).use { if(it.moveToFirst()) it.getLong(0) else null }
     if(run==null) null else reader.financialRows(run,"payments") to reader.financialRows(run,"returns")
    }
    rows.map { r -> r to runCatching {
     val raw=JSONObject(r.original_json)
     if(r.source_sha256=="native8") MaintenanceReturnRules.due(raw) else {
      val history=requireNotNull(histories[r.source_sha256])
      com.alsafa.native8.sales.ArchivedRepairDeliveryRules.due(raw,history.first,history.second)
     }
    }.getOrNull() }
   } finally {staging.close()}
  }
   runOnUiThread {
    if(isDestroyed || token!=generation) return@runOnUiThread
    list.removeAllViews()
    result.fold(onSuccess={ rows ->
     if(rows.isEmpty()) ui.add(list,ui.text("لا توجد ساعات بانتظار التسليم."))
     rows.forEach { (r,due) ->
      val card=ui.card();ui.add(list,card,10)
      ui.add(card,ui.text("⌚ "+r.customer.ifBlank { "عميل" },20f,bold=true),6)
      ui.add(card,ui.text(r.watch.ifBlank { r.problem },14f),8)
      ui.add(card,ui.text("المطلوب عند التسليم",12f),4)
      ui.add(card,ui.text(if(due==null) "راجع تفاصيل المبلغ" else due.max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP).toPlainString()+" "+r.currency,22f,bold=true))
      ui.add(card,ui.button(if(due!=null && due>BigDecimal.ZERO) "✓ تسليم وتحصيل" else "✓ تسليم الساعة",true){
       if(r.source_sha256=="native8") startActivity(Intent(this,MaintenanceActivity::class.java).putExtra("repair_id",r.id).putExtra("open_delivery",true))
       else if(due!=null) archiveDelivery(r,due)
      }.apply{isEnabled=due!=null})
      ui.add(card,ui.button("عرض تفاصيل الساعة"){
       startActivity(Intent(this,MaintenanceActivity::class.java).putExtra("repair_id",r.id))
      })
     }
    },onFailure={ui.add(list,ui.text("تعذر قراءة الساعات: ${it.message}"))})
   }
  }.start()
 }
 private fun archiveDelivery(row:MaintenanceRepairEntity,due:BigDecimal) {
  val ui=SafaWidgets(SafaStyle(this));val body=ui.column()
  ui.add(body,ui.text("${row.customer.ifBlank { "عميل" }} — فاتورة رقم ${row.invoice_number}"))
  ui.add(body,ui.text("المتبقي: ${due.setScale(2,RoundingMode.HALF_UP)} ${row.currency}"))
  var currency=row.currency;var vault="drawer"
  val amount=ui.input(numeric=true).apply{setText(due.toPlainString());isEnabled=due.signum()>0}
  ui.label(body,"المبلغ المقبوض الآن");ui.add(body,amount)
  lateinit var currencyButton:android.widget.Button
  currencyButton=ui.button("العملة: $currency") {
   val ids=arrayOf("USD","TRY","SYP")
   android.app.AlertDialog.Builder(this).setTitle("عملة القبض").setItems(ids){_,i ->
    currency=ids[i];currencyButton.text="العملة: $currency"
    runCatching {due*com.alsafa.native8.sales.ExchangeRateSettings(this).rate(row.currency,currency)}
     .onSuccess{amount.setText(it.setScale(2,RoundingMode.HALF_UP).toPlainString())}
     .onFailure{amount.setText("")}
   }.show()
  };ui.add(body,currencyButton)
  lateinit var vaultButton:android.widget.Button
  vaultButton=ui.button("الصندوق: الدرج") {
   val ids=arrayOf("drawer","external","shamcash","bank");val labels=arrayOf("الدرج","الصندوق الخارجي","شام كاش","البنك")
   android.app.AlertDialog.Builder(this).setTitle("الصندوق").setItems(labels){_,i->vault=ids[i];vaultButton.text="الصندوق: ${labels[i]}"}.show()
  };ui.add(body,vaultButton)
  ui.add(body,ui.text("يُحفظ المقبوض الفعلي بالعملة المختارة، وتُسوّى بقية الفاتورة كما في النظام الأصلي."))
  val error=ui.text("");ui.add(body,error)
  val scroll=android.widget.ScrollView(this).apply{addView(body)}
  val dialog=android.app.AlertDialog.Builder(this).setTitle("تسليم الساعة وتحصيل المبلغ").setView(scroll)
   .setNegativeButton("إلغاء",null).setPositiveButton("تأكيد التسليم",null).create()
  dialog.setOnShowListener {
   dialog.getButton(-1).setOnClickListener {
    val actual=amount.text.toString().toBigDecimalOrNull()
    if(actual==null){error.text="أدخل مبلغاً صحيحاً";return@setOnClickListener}
    val selectedCurrency=currency;val selectedVault=vault
    dialog.getButton(-1).isEnabled=false;dialog.getButton(-2).isEnabled=false;dialog.setCancelable(false)
    Thread {
     val result=runCatching {runBlocking {
      ArchivedRepairDeliveryRepository(AppDatabase.get(applicationContext),applicationContext)
       .deliver(row.id,row.original_json,due,actual,selectedCurrency,selectedVault)
     }}
     runOnUiThread {
      if(isDestroyed) return@runOnUiThread
      result.fold(onSuccess={dialog.dismiss();android.widget.Toast.makeText(this,"تم تسليم الساعة",android.widget.Toast.LENGTH_LONG).show();load()},
       onFailure={error.text=it.message;dialog.getButton(-1).isEnabled=true;dialog.getButton(-2).isEnabled=true;dialog.setCancelable(true)})
     }
    }.start()
   }
  };dialog.show()
  dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.9).toInt(),(resources.displayMetrics.heightPixels*0.8).toInt())
 }

}
