package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** Visible financial audit, NOT an approved profit or partner statement. */
class FinancialReviewActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val title=TextView(this).apply {
   text="الأرباح — مراجعة الحسابات"
   textSize=23f
   gravity=Gravity.CENTER
  }
  val warning=TextView(this).apply {
   text="النتائج التالية تشغيلية جزئية، وليست صافي الربح المعتمد. لا تشمل بعد جميع بيانات الأرشيف والصيانة."
   textSize=17f
   setTextColor(Color.rgb(150,65,25))
  }
  val from=EditText(this).apply {
   hint="من تاريخ YYYY-MM-DD"
   setSingleLine()
   setText(LocalDate.now().withDayOfMonth(1).toString())
  }
  val to=EditText(this).apply {
   hint="إلى تاريخ YYYY-MM-DD"
   setSingleLine()
   setText(LocalDate.now().toString())
  }
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
  listOf(title,warning,from,to,refresh,result,showDetails,detail,checkSources,sourceStatus).forEach {
   content.addView(it,LinearLayout.LayoutParams(
    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
    android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
  }
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
     val status=withContext(Dispatchers.IO) {
      val db=AppDatabase.get(applicationContext)
      val missing=db.salesPostingDao().unverifiedChannelCount()
      val suspect=db.salesFinancialAuditDao().nonOnlineProfitEventCount()
      "الفواتير الجديدة غير المصنفة: "+missing+"\\n"+
       "أحداث ربح قبض تحتاج تدقيق نوع الفاتورة: "+suspect+"\\n"+
       "مبيعات المحل الجديدة: متصلة بجدول الأرباح\\n"+
       "تحصيلات الأونلاين الجديدة: متصلة بقيود القبض\\n"+
       "الأرشيف القديم: لم يُدمج ربحه في هذا التقرير\\n"+
       "الصيانة: لم تُدمج المقبوضات والتكلفة في هذا التقرير\\n"+
       "المصاريف والشركاء: لم تُعتمد نتائجها النهائية"
     }
     sourceStatus.text=status.replace("\\n","\n")
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
      val w=FinancialPeriodGuard.window(a.toString(),b.toString(),ZoneId.of("Asia/Damascus"))
      val stores=db.storeSaleProfitDao().inPeriod(w.startInclusive,w.endExclusive)
      val adjustments=db.storeSaleProfitDao().adjustmentsInPeriod(w.startInclusive,w.endExclusive)
      val online=db.salesFinancialAuditDao().verifiedOnlineProfitsInPeriod(w.startInclusive,w.endExclusive)
      val reverse=db.salesFinancialAuditDao().verifiedOnlineReversalsInPeriod(w.startInclusive,w.endExclusive)
      val expenses=db.operationalExpensePartnerDao().expenses(a.toString(),b.toString())
      val withdrawals=db.operationalExpensePartnerDao().withdrawals(a.toString(),b.toString())
      val lines=mutableListOf<String>()
      val cache=mutableMapOf<String,RoomSale?>()
      suspend fun invoice(id:String):String {
       val sale=if(cache.containsKey(id)) cache[id] else db.salesPostingDao().sale(id).also { cache[id]=it }
       return if(sale!=null) "فاتورة رقم ${sale.number} — ${sale.customer.ifBlank { "عميل غير مسمى" }}"
       else "فاتورة غير موجودة في المبيعات الجديدة (مرجع ${id.take(8)})"
      }
      fun day(at:Long)=java.time.Instant.ofEpochMilli(at)
       .atZone(ZoneId.of("Asia/Damascus")).toLocalDate().toString()
      lines+="مبيعات المحل"
      for(entry in stores) lines+="${day(entry.recognized_at)} | ${invoice(entry.sale_id)} | ربح ${entry.profit_usd} USD"
      for(entry in adjustments) lines+="${day(entry.created_at)} | ${invoice(entry.sale_id)} | ${entry.reason}: ${entry.profit_delta_usd} USD"
      lines+="تحصيلات الأونلاين"
      for(entry in online) lines+="${day(entry.collected_at)} | ${invoice(entry.sale_id)} | المقبوض ${entry.credited_base} USD | الربح ${entry.recognized_profit} USD"
      lines+="المرتجعات وعكس الأرباح"
      for(entry in reverse) lines+="${day(entry.reversed_at)} | ${invoice(entry.sale_id)} | عكس ربح ${entry.reversed_profit} USD"
      lines+="المصاريف التشغيلية الجديدة (لا تشمل الأرشيف)"
      for(entry in expenses) lines+="${entry.date} | ${entry.category} | ${entry.amount} ${entry.currency} | ${entry.notes}"
      lines+="سحوبات الشركاء الجديدة (ليست مصروفاً تشغيلياً)"
      for(entry in withdrawals) lines+="${entry.date} | ${entry.partner_id} | ${entry.amount} ${entry.currency}"
      lines
     }
     detail.text=if(rows.isEmpty()) "لا توجد قيود في هذه الفترة" else rows.joinToString("\n")
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
      val window=FinancialPeriodGuard.window(a.toString(),b.toString(),ZoneId.of("Asia/Damascus"))
      val store=db.storeSaleProfitDao()
      val sales=db.salesFinancialAuditDao()
      fun sum(values:List<String>)=values.fold(BigDecimal.ZERO) { acc,s -> acc+s.toBigDecimal() }
      val storeProfit=sum(store.inPeriod(window.startInclusive,window.endExclusive).map { it.profit_usd })
      val storeAdjust=sum(store.adjustmentsInPeriod(window.startInclusive,window.endExclusive).map { it.profit_delta_usd })
      val online=sum(sales.verifiedOnlineProfitsInPeriod(window.startInclusive,window.endExclusive).map { it.recognized_profit })
      val reversals=sum(sales.verifiedOnlineReversalsInPeriod(window.startInclusive,window.endExclusive).map { it.reversed_profit })
      val unverified=db.salesPostingDao().unverifiedChannelCount()
      val archived=LegacySalesProfitAudit.inspect(db.legacySaleDao().historicalInPeriod(
       window.startInclusive,window.endExclusive))
      val storeEvents=store.inPeriod(window.startInclusive,window.endExclusive)
      val storeRevenue=sum(storeEvents.map { it.net_revenue_usd })
      val storeCapital=sum(storeEvents.map { it.cost_usd })
      val onlineInvoices=db.salesPostingDao().onlineInvoicesInPeriod(
       window.startInclusive,window.endExclusive)
      var onlineStockCapital=BigDecimal.ZERO
      for(invoice in onlineInvoices) {
       onlineStockCapital+=OnlineCapitalReport.stockCost(db.salesPostingDao().lines(invoice.id))
      }
      val recognizedOnlineCapital=OnlineCapitalReport.recognizedCost(
       sales.verifiedOnlineProfitsInPeriod(window.startInclusive,window.endExclusive))
      val suspect=sales.nonOnlineProfitEventCount()
      listOf(storeProfit,storeAdjust,online,reversals,BigDecimal(unverified),
       BigDecimal(archived.storeInvoices),BigDecimal(archived.onlineInvoices),
       BigDecimal(archived.unresolvedIds.size),storeRevenue,storeCapital,BigDecimal(suspect),onlineStockCapital,recognizedOnlineCapital)
     }
     result.text="مبيعات المحل الجديدة بعد الخصومات: ${data[8]} USD\n"+
      "رأس مال ساعات المحل المباعة: ${data[9]} USD\n"+
      "رأس مال ساعات الأونلاين الخارجة من المخزون بتاريخ البيع: ${data[11]} USD\n"+
      "تكلفة الأونلاين المقابلة للتحصيلات خلال الفترة: ${data[12]} USD\n"+
      "فواتير المحل التاريخية في الفترة: ${data[5].toPlainString()} (ربحها قيد المطابقة)\n"+
      "فواتير الأونلاين التاريخية في الفترة: ${data[6].toPlainString()} (تحصيلاتها قيد المطابقة)\n"+
      "فواتير تاريخية تحتاج مراجعة التصنيف: ${data[7].toPlainString()}\n"+
      "ربح فواتير المحل الجديدة: ${data[0]} USD\n"+
      "تسويات ربح المحل: ${data[1]} USD\n"+
      "أرباح تحصيلات الأونلاين الجديدة: ${data[2]} USD\n"+
      "عكس أرباح التحصيلات: ${data[3]} USD\n"+
      "فواتير جديدة غير مصنفة: ${data[4].toPlainString()}\n"+
      "أحداث تحصيل قديمة غير معتمدة كأونلاين: ${data[10].toPlainString()}\n"+
      "المصاريف والصيانة والأرشيف والشركاء: لم تكتمل مطابقتها؛ لا يُعرض صافي نهائي."
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
