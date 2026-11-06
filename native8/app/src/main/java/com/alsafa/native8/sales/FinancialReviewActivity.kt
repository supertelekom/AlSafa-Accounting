package com.alsafa.native8.sales

import android.app.Activity
import androidx.room.withTransaction
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.data.migration.LegacyReadRepository

/** Visible financial audit, NOT an approved profit or partner statement. */
class FinancialReviewActivity:Activity() {
 private fun money(value:BigDecimal)= "\u2066"+value.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString()+"\u2069"
 private fun money(value:String)=money(value.toBigDecimal())
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val title=TextView(this).apply {
   text="الأرباح — مراجعة الحسابات"
   textSize=23f
   gravity=Gravity.CENTER
  }
  val warning=TextView(this).apply {
   text="جميع النتائج والتفاصيل ومصادر الأرباح تخص الفترة المختارة، وتشمل جميع العمليات دون تكرار."
   textSize=17f
   setTextColor(Color.rgb(150,65,25))
  }
  val from=EditText(this).apply {
   hint="من تاريخ"
   setSingleLine()
   setText(LocalDate.now(ZoneId.of("Asia/Damascus")).withDayOfMonth(1).toString())
  }
  val to=EditText(this).apply {
   hint="إلى تاريخ"
   setSingleLine()
   setText(LocalDate.now(ZoneId.of("Asia/Damascus")).toString())
  }
  com.alsafa.native8.stylekit.SafaDateTime.bindDate(from)
  com.alsafa.native8.stylekit.SafaDateTime.bindDate(to)
  val result=TextView(this).apply {
   textSize=17f
   setHorizontallyScrolling(false)
  }
  val detail=TextView(this).apply {
   textSize=15f
   visibility=View.GONE
   setHorizontallyScrolling(false)
   setSingleLine(false)
   breakStrategy=android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
  }
  val showDetails=Button(this).apply { text="تفصيل القيود المالية" }
  val sourceStatus=TextView(this).apply {
   textSize=16f
   visibility=View.GONE
   setHorizontallyScrolling(false)
  }
  val checkSources=Button(this).apply { text="فحص مصادر الأرباح" }
  val allDates=Button(this).apply { text="من بداية السجلات" }
  allDates.setOnClickListener { from.setText("0001-01-01");to.setText(LocalDate.now(ZoneId.of("Asia/Damascus")).toString());detail.text="";detail.visibility=View.GONE;result.text="اضغط عرض مراجعة الأرباح لتحديث الفترة" }
  val refresh=Button(this).apply { text="عرض مراجعة الأرباح" }
  // One vertical scroll container owns the WHOLE page, including title and dates.
  // No fixed header, nested scroll, hard-coded screen height or horizontal scrolling.
  val content=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(16,16,16,24)
   isFocusableInTouchMode=true
   descendantFocusability=android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
  }
  listOf(title,warning,from,to,allDates,refresh,result,showDetails,detail,checkSources,sourceStatus).forEach {
   content.addView(it,LinearLayout.LayoutParams(
    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
    android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
  }
  com.alsafa.native8.stylekit.SafaDateTime.presets(com.alsafa.native8.stylekit.SafaWidgets(com.alsafa.native8.stylekit.SafaStyle(this)),content,from,to)
  val scroll=ScrollView(this).apply {
   isFillViewport=true
   isHorizontalScrollBarEnabled=false
   isVerticalScrollBarEnabled=true
   overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS
   addView(content,android.widget.FrameLayout.LayoutParams(-1,-2))
  }
  // Respect the tablet's real system-bar insets. A ScrollView starting at y=0
  // can otherwise draw its first controls behind the status bar.
  val density=resources.displayMetrics.density
  val side=(16*density+0.5f).toInt()
  val vertical=(12*density+0.5f).toInt()
  scroll.clipToPadding=false
  scroll.setOnApplyWindowInsetsListener { view,insets ->
   val bars=if(android.os.Build.VERSION.SDK_INT>=30)
    insets.getInsets(android.view.WindowInsets.Type.systemBars())
   else null
   val top=bars?.top ?: insets.systemWindowInsetTop
   val bottom=bars?.bottom ?: insets.systemWindowInsetBottom
   view.setPadding(0,top,0,bottom)
   insets
  }
  content.setPadding(side,vertical,side,vertical)
  window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
  window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
   android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  val font=android.graphics.Typeface.createFromAsset(assets,"fonts/Tajawal-Medium.ttf")
  fun style(v:View) {
   if(v is TextView) { v.typeface=font;v.textDirection=View.TEXT_DIRECTION_RTL;v.gravity=Gravity.RIGHT;v.setHorizontallyScrolling(false);v.setLineSpacing(6*density,1.12f) }
   if(v is android.view.ViewGroup) for(i in 0 until v.childCount) style(v.getChildAt(i))
  }
  style(content)
  setContentView(scroll)
  scroll.requestApplyInsets()
  content.requestFocus()
  scroll.post { scroll.scrollTo(0,0) }
  checkSources.setOnClickListener {
   sourceStatus.visibility=View.VISIBLE
   checkSources.isEnabled=false
   sourceStatus.text="جارٍ فحص المصادر..."
   scope.launch {
    try {
     val a=LocalDate.parse(from.text.toString().trim());val b=LocalDate.parse(to.text.toString().trim())
     require(!b.isBefore(a)) { "فترة غير صحيحة" }
     val status=withContext(Dispatchers.IO) {
      val db=AppDatabase.get(applicationContext);val staging=LegacyStagingDatabase(applicationContext)
      try {
       val legacy=LegacyReadRepository(staging).withInvoiceMetadata(db);val run=legacy.latestRunId()
       db.withTransaction {
        val data=UnifiedFinancialPeriodRepository(db.salesFinancialAuditDao(),UnifiedExpensePartnerReadRepository(legacy,db.operationalExpensePartnerDao()),ZoneId.of("Asia/Damascus"),db.storeSaleProfitDao(),LegacyHistoricalProfitProjection(legacy),NativeMaintenanceProfitRepository(db.maintenanceDao(),db.legacyPaymentDao())).read(a.toString(),b.toString())
        check(legacy.latestRunId()==run) { "تغيرت النسخة المستعادة أثناء القراءة" }
        "مصادر أرباح الفترة: $a — $b\n\n"+
         "مبيعات المحل: ${money(data.totalStoreProfit)} USD\n\n"+
         "تحصيلات الأونلاين: ${money(data.totalOnlineProfit)} USD\n\n"+
         "الصيانة: ${money(data.maintenanceProfit)} USD\n\n"+
         "المرتجعات والتسويات: ${money(data.totalAdjustments)} USD\n\n"+
         "المصاريف التشغيلية: ${money(data.operatingExpenses)} USD\n\n"+
         (if(data.complete) "صافي ربح الفترة: ${money(data.approvedNet())} USD" else "صافي الربح غير متاح حتى استكمال العمليات التالية:\n"+data.unresolvedHistorical.distinct().joinToString("\n\n"))+
         "\n\nالسحوبات تخصم من مستحق الشريك، ولا تخصم من صافي الربح."
       }
      } finally { staging.close() }
     }
     sourceStatus.text=status
    } catch(e:Exception) { sourceStatus.text=e.message ?: "تعذر فحص المصادر" }
    finally { checkSources.isEnabled=true }
   }
  }
  showDetails.setOnClickListener {
   if(detail.visibility==View.VISIBLE && detail.text.isNotBlank()) {
    detail.visibility=View.GONE
    showDetails.text="تفصيل القيود المالية"
    return@setOnClickListener
   }
   detail.visibility=View.VISIBLE
   showDetails.text="إخفاء تفاصيل القيود"
   showDetails.isEnabled=false
   detail.text="جارٍ تحميل تفاصيل القيود..."
   scope.launch {
    try {
     val a=LocalDate.parse(from.text.toString().trim())
     val b=LocalDate.parse(to.text.toString().trim())
     val rows=withContext(Dispatchers.IO) {
      val db=AppDatabase.get(applicationContext)
      val staging=LegacyStagingDatabase(applicationContext)
      try {
       db.withTransaction { UnifiedFinancialLedgerRepository(db,LegacyReadRepository(staging).withInvoiceMetadata(db)).rows(a.toString(),b.toString()) }
      } finally { staging.close() }
     }
     detail.text="الفترة: $a — $b\n\n"+FinancialLedgerPresentation.render(rows)
    } catch(e:Exception) { detail.text="تعذر عرض التفاصيل: "+(e.message ?: "خطأ") }
    finally { showDetails.isEnabled=true }
   }
  }
  refresh.setOnClickListener {
   refresh.isEnabled=false
   result.text="جارٍ قراءة القيود..."
   scope.launch {
    try {
     val a=LocalDate.parse(from.text.toString().trim())
     val b=LocalDate.parse(to.text.toString().trim())
     require(!b.isBefore(a)) { "فترة غير صحيحة" }
     val data=withContext(Dispatchers.IO) {
      val db=AppDatabase.get(applicationContext)
      val staging=LegacyStagingDatabase(applicationContext)
      try {
       val legacy=LegacyReadRepository(staging).withInvoiceMetadata(db);val run=legacy.latestRunId()
       db.withTransaction {
        UnifiedFinancialPeriodRepository(db.salesFinancialAuditDao(),UnifiedExpensePartnerReadRepository(legacy,db.operationalExpensePartnerDao()),
         ZoneId.of("Asia/Damascus"),db.storeSaleProfitDao(),LegacyHistoricalProfitProjection(legacy),
         NativeMaintenanceProfitRepository(db.maintenanceDao(),db.legacyPaymentDao())).read(a.toString(),b.toString())
         .also { check(legacy.latestRunId()==run) { "تغيرت النسخة المستعادة أثناء القراءة" } }
       }
      } finally { staging.close() }
     }
     result.text="الفترة: $a — $b\n"+
      "أرباح مبيعات المحل: ${money(data.totalStoreProfit)} USD\n"+
      "أرباح تحصيلات الأونلاين: ${money(data.totalOnlineProfit)} USD\n"+
      "أرباح الصيانة: ${money(data.maintenanceProfit)} USD\n"+
      "أثر المرتجعات والتسويات: ${money(data.totalAdjustments)} USD\n"+
      "المصاريف التشغيلية: ${money(data.operatingExpenses)} USD\n"+
      "سحوبات الشركاء (لا تخصم من الربح): ${money(data.withdrawals.values.fold(BigDecimal.ZERO) { sum,value -> sum+value })} USD\n"+
      (if(data.complete) "صافي الربح: ${money(data.approvedNet())} USD" else
       "صافي الربح غير متاح حتى استكمال العمليات التالية:\n"+data.unresolvedHistorical.joinToString("\n"))
     detail.text="";detail.visibility=View.GONE;showDetails.text="تفصيل القيود المالية"

    } catch(e:Exception) {
     result.text="تعذر استخراج المراجعة: "+(e.message ?: "خطأ غير معروف")
    } finally {
     refresh.isEnabled=true
     scroll.post { scroll.scrollTo(0,0) }
    }
   }
  }
 }
 override fun onDestroy() {
  scope.cancel()
  super.onDestroy()
 }
}
