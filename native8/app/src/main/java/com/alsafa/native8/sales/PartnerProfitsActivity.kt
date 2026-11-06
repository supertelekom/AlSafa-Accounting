package com.alsafa.native8.sales

import android.app.Activity
import android.app.AlertDialog
import androidx.room.withTransaction
import java.time.ZoneId
import java.math.RoundingMode
import android.os.Bundle
import android.view.View
import android.widget.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.LocalDate
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.data.migration.LegacyReadRepository

/**
 * Period and cumulative statements use the same source-verified financial repository.
 */
class PartnerProfitsActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  title="أرباح الشركاء"
  val content=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(20,20,20,24)
   isFocusableInTouchMode=true
  }
  val heading=TextView(this).apply { text="أرباح الشركاء";textSize=24f }
  val explanation=TextView(this).apply {
   text="حصة الأب 40%، عبد الكريم 35%، يزن 25%. المستحق المتبقي محسوب من بداية السجلات حتى نهاية الفترة، بعد طرح السحوبات التراكمية."
   textSize=17f
  }
  val result=TextView(this).apply { textSize=18f;gravity=android.view.Gravity.RIGHT;textDirection=View.TEXT_DIRECTION_RTL;setHorizontallyScrolling(false);setLineSpacing(8f,1.1f) }
  val button=Button(this).apply { text="عرض حالة أرباح الشركاء" }
  listOf(heading,explanation,button,result).forEach {
   content.addView(it,LinearLayout.LayoutParams(-1,-2))
  }
  val scroll=ScrollView(this).apply {
   isFillViewport=true
   isHorizontalScrollBarEnabled=false
   addView(content,android.widget.FrameLayout.LayoutParams(-1,-2))
  }
  scroll.setOnApplyWindowInsetsListener { view,insets ->
   if(android.os.Build.VERSION.SDK_INT>=30) {
    val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());view.setPadding(0,bars.top,0,bars.bottom)
   } else view.setPadding(0,insets.systemWindowInsetTop,0,insets.systemWindowInsetBottom)
   insets
  }
  window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
  window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  setContentView(scroll)
  scroll.requestApplyInsets()
  val font=android.graphics.Typeface.createFromAsset(assets,"fonts/Tajawal-Medium.ttf")
  fun applyFont(view:View) {
   if(view is TextView) view.typeface=font
   if(view is android.view.ViewGroup) for(i in 0 until view.childCount) applyFont(view.getChildAt(i))
  }
  applyFont(content)
  fun money(value:BigDecimal)=value.setScale(2,RoundingMode.HALF_UP).toPlainString()+" USD"
  fun load(first:String,last:String) {
   button.isEnabled=false;result.text="جارٍ قراءة الأرباح والسحوبات..."
   scope.launch {
    try {
     val statement=withContext(Dispatchers.IO) {
      val db=AppDatabase.get(applicationContext)
      val staging=LegacyStagingDatabase(applicationContext)
      try {
       val legacy=LegacyReadRepository(staging)
       val run=legacy.latestRunId()
       db.withTransaction {
        val repository=UnifiedFinancialPeriodRepository(db.salesFinancialAuditDao(),
         UnifiedExpensePartnerReadRepository(legacy,db.operationalExpensePartnerDao()),ZoneId.of("Asia/Damascus"),
         db.storeSaleProfitDao(),LegacyHistoricalProfitProjection(legacy),NativeMaintenanceProfitRepository(db.maintenanceDao(),db.legacyPaymentDao()))
        repository.statement(first,last).also { check(legacy.latestRunId()==run) { "تغيرت النسخة المستعادة أثناء القراءة" } }
       }
      } finally { staging.close() }
     }
     if(statement.unresolved.isNotEmpty() || !statement.period.complete || !statement.cumulative.complete) {
      result.text="الفترة: $first — $last\nلا يمكن اعتماد صافي الربح أو المستحقات حتى مراجعة العمليات التالية:\n"+
       statement.unresolved.joinToString("\n\n")+
       "\n\nأرباح المصادر المستخرجة للفترة (ليست مستحقات معتمدة):\n"+
       "أرباح المحل: ${money(statement.period.totalStoreProfit)}\n"+
       "أرباح الأونلاين: ${money(statement.period.totalOnlineProfit)}\n"+
       "أرباح الصيانة: ${money(statement.period.maintenanceProfit)}\n"+
       "المصاريف: ${money(statement.period.operatingExpenses)}"
     } else {
      val names=mapOf("father" to "الأب","me" to "عبد الكريم","brother" to "يزن")
      val period=statement.period
      result.text="الفترة: $first — $last\n"+
       "أرباح مبيعات المحل: ${money(period.totalStoreProfit)}\n"+
       "أرباح الأونلاين: ${money(period.totalOnlineProfit)}\n"+
       "أرباح الصيانة: ${money(period.maintenanceProfit)}\n"+
       "أثر المرتجعات والتسويات: ${money(period.totalAdjustments)}\n"+
       "المصاريف التشغيلية: ${money(period.operatingExpenses)}\n"+
       "صافي ربح الفترة: ${money(statement.period.approvedNet())}\n"+
       "صافي الربح التراكمي حتى $last: ${money(statement.cumulative.approvedNet())}\n\n"+
       statement.partners().joinToString("\n\n") { row ->
        "${names[row.partnerId]} — ${row.percent}%\n"+
         "أرباح الفترة: ${money(row.periodProfit)}\n"+
         "الأرباح التراكمية: ${money(row.cumulativeProfit)}\n"+
         "سحوبات الفترة: ${money(row.periodWithdrawals)}\n"+
         "السحوبات التراكمية: ${money(row.cumulativeWithdrawals)}\n"+
         "المستحق المتبقي: ${money(row.due)}"
       }
     }
    } catch(e:Exception) { result.text="تعذر اعتماد كشف الأرباح: "+(e.message ?: "خطأ في البيانات") }
    finally { button.isEnabled=true }
   }
  }
  button.setOnClickListener {
   val today=LocalDate.now(ZoneId.of("Asia/Damascus"))
   val fields=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(24,16,24,16) }
   val first=EditText(this).apply { hint="من تاريخ";setText(today.withDayOfMonth(1).toString());isSingleLine=true }
   val last=EditText(this).apply { hint="إلى تاريخ";setText(today.toString());isSingleLine=true }
   fields.addView(first,LinearLayout.LayoutParams(-1,(58*resources.displayMetrics.density).toInt()))
   fields.addView(last,LinearLayout.LayoutParams(-1,(58*resources.displayMetrics.density).toInt()))
   applyFont(fields)
   AlertDialog.Builder(this).setTitle("فترة الأرباح").setView(fields)
    .setNegativeButton("إلغاء",null).setPositiveButton("عرض") { _,_ ->
     val a=first.text.toString().trim();val b=last.text.toString().trim()
     try { require(!LocalDate.parse(b).isBefore(LocalDate.parse(a)));load(a,b) }
     catch(_:Exception) { result.text="أدخل فترة صحيحة بصيغة YYYY-MM-DD" }
    }.show()
  }

 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
