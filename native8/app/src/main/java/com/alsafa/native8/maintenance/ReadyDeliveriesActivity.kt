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
  val pending=intent.getBooleanExtra("pending",false)
  val ui=SafaWidgets(SafaStyle(this));val host=SafaPageHost(ui,"الصفا للساعات")
  ui.heading(host.body,if(pending)"قيد الصيانة" else "تسليم الساعات",if(pending)"الساعات قيد الصيانة وبانتظار القطعة — افتح التفاصيل للتعديل أو اعتماد الجاهزية." else "الساعات الجاهزة أو المتبقية لدينا بانتظار العميل.")
  ui.add(host.body,ui.button("رجوع"){finish()})
  ui.add(host.body,ui.button("سجلات الصيانة — تحديث الجاهزية"){startActivity(Intent(this,MaintenanceActivity::class.java))})
  val list=ui.column();ui.add(host.body,list);ui.add(list,ui.text("جاري القراءة…"));setContentView(host.view)
  val token=++generation
  Thread { val result=runCatching {
   val rows=runBlocking { AppDatabase.get(applicationContext).maintenanceDao().let{if(pending)it.pendingRepairs() else it.readyDeliveries()} }
   if(pending) rows.map { it to (null as BigDecimal?) } else {
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
  }
   runOnUiThread {
    if(isDestroyed || token!=generation) return@runOnUiThread
    list.removeAllViews()
    result.fold(onSuccess={ rows ->
     if(rows.isEmpty()) ui.add(list,ui.text(if(pending)"لا توجد ساعات قيد الصيانة أو بانتظار القطعة." else "لا توجد ساعات بانتظار التسليم."))
     rows.forEach { (r,due) ->
      val card=ui.card();ui.add(list,card,10)
      ui.add(card,ui.text("فاتورة رقم ${r.invoice_number} — ${r.status}",13f))
      ui.add(card,ui.text("⌚ "+r.customer.ifBlank { "عميل" },20f,bold=true),6)
      ui.add(card,ui.text(r.watch.ifBlank { r.problem },14f),8)
      if(!pending) {
      ui.add(card,ui.text("المطلوب عند التسليم",12f),4)
      ui.add(card,ui.text(if(due==null) "راجع تفاصيل المبلغ" else due.max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP).toPlainString()+" "+r.currency,22f,bold=true))
      ui.add(card,ui.button(if(due!=null && due>BigDecimal.ZERO) "✓ تسليم وتحصيل" else "✓ تسليم الساعة",true){
       if(due!=null) RepairDeliveryDialog.show(this,r,due){load()}
      }.apply{isEnabled=due!=null})
      ui.add(card,ui.button("عرض الفاتورة / PDF / طباعة"){
       startActivity(Intent(this,com.alsafa.native8.sales.SalePrintActivity::class.java).putExtra("repair_id",r.id))
      })
      }
      ui.add(card,ui.button(if(pending)"التفاصيل / تعديل حالة الصيانة" else "عرض تفاصيل الساعة"){
       startActivity(Intent(this,MaintenanceActivity::class.java).putExtra("repair_id",r.id))
      })
     }
    },onFailure={ui.add(list,ui.text("تعذر قراءة الساعات: ${it.message}"))})
   }
  }.start()
 }

}
