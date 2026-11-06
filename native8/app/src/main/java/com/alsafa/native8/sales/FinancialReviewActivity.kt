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
 private fun money(value:BigDecimal)=value.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString()
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
   text="المراجعة والتفاصيل حسب الفترة المختارة. فحص المصادر يعرض الأرشيف كاملاً؛ لا يعتمد صافي الربح أو مستحقات الشركاء عند وجود عمليات تحتاج مراجعة."
   textSize=17f
   setTextColor(Color.rgb(150,65,25))
  }
  val from=EditText(this).apply {
   hint="من تاريخ YYYY-MM-DD"
   setSingleLine()
   setText(LocalDate.now(ZoneId.of("Asia/Damascus")).withDayOfMonth(1).toString())
  }
  val to=EditText(this).apply {
   hint="إلى تاريخ YYYY-MM-DD"
   setSingleLine()
   setText(LocalDate.now(ZoneId.of("Asia/Damascus")).toString())
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
     val status=withContext(Dispatchers.IO) {
      val db=AppDatabase.get(applicationContext)
      val missing=db.salesPostingDao().unverifiedChannelCount()
      val suspect=db.salesFinancialAuditDao().nonOnlineProfitEventCount()
      val repairs=db.maintenanceDao().allDelivered()
      val nativeMaintenance=NativeMaintenanceProfitRepository(db.maintenanceDao(),db.legacyPaymentDao()).read()
      val staged=LegacyStagingDatabase(applicationContext)
      val (historical,historicalFinance)=try {
       val original=LegacyReadRepository(staged)
       LegacyHistoricalSalesProfitRepository(db.legacySaleDao(),original).inspect() to
        LegacyHistoricalProfitProjection(original).read("0001-01-01",LocalDate.now(ZoneId.of("Asia/Damascus")).toString())
      } finally { staged.close() }
      val storeHistorical=historical.events.filter {
       it.stream==FinancialSourceReconciliation.Stream.LEGACY_STORE
      }.fold(BigDecimal.ZERO) { sum,event -> sum+event.profitUsd }
      val onlineHistorical=historical.events.filter {
       it.stream==FinancialSourceReconciliation.Stream.LEGACY_ONLINE
      }.fold(BigDecimal.ZERO) { sum,event -> sum+event.profitUsd }
      "فحص شامل للأرشيف — غير مقيد بفترة المراجعة\nالفواتير الجديدة غير المصنفة: "+missing+"\n"+
       "أحداث ربح قبض تحتاج تدقيق نوع الفاتورة: "+suspect+"\n"+
       "مبيعات المحل الجديدة: متصلة بجدول الأرباح\n"+
       "تحصيلات الأونلاين الجديدة: متصلة بقيود القبض\n"+
       "أحداث أرباح المحل التاريخية المستخرجة: "+historical.events.count { it.stream==FinancialSourceReconciliation.Stream.LEGACY_STORE }+"\n"+
       "أحداث تحصيل الأونلاين التاريخية المستخرجة: "+historical.events.count { it.stream==FinancialSourceReconciliation.Stream.LEGACY_ONLINE }+"\n"+
       "عمليات تاريخية تحتاج مطابقة: "+historical.unresolved.size+"\n"+
       "ربح المحل التاريخي وفق السورس الأصلي: "+money(storeHistorical)+" USD\n"+
       "ربح الأونلاين التاريخي وفق السورس الأصلي: "+money(onlineHistorical)+" USD\n"+
       "الصيانة المسلّمة: "+repairs.size+" (مستعادة "+repairs.count { it.source_sha256!="native8" }+"، جديدة "+repairs.count { it.source_sha256=="native8" }+")\n"+
       "عمليات الصيانة الجديدة التي تحتاج استكمال أدلة الربح: "+nativeMaintenance.audit.unresolved.size+"\n"+
       "ربح الصيانة التاريخية: "+money(historicalFinance.repairProfitUsd)+" USD\n"+
       "مشكلات الصيانة التاريخية: "+historicalFinance.repairUnresolved.size+"\n"+
       "المصاريف والسحوبات وصافي الربح متصلة بالتقرير الموحد؛ المستحقات التراكمية في صفحة أرباح الشركاء."
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
      val staging=LegacyStagingDatabase(applicationContext)
      try {
       val legacy=LegacyReadRepository(staging)
       val history=LegacyHistoricalSalesProfitRepository(db.legacySaleDao(),legacy).inspect()
       lines+="الفترة: $a — $b"
       lines+="المبيعات الأرشيفية — الربح بتاريخ البيع للمحل والتحصيل للأونلاين"
       val selected=history.events.filter { !it.recognizedOn.isBefore(a) && !it.recognizedOn.isAfter(b) }.sortedWith(compareBy({it.recognizedOn},{it.sourceKey}))
       for(stream in listOf(FinancialSourceReconciliation.Stream.LEGACY_STORE,FinancialSourceReconciliation.Stream.LEGACY_ONLINE,FinancialSourceReconciliation.Stream.RETURN)) {
        lines+=when(stream) { FinancialSourceReconciliation.Stream.LEGACY_STORE -> "مبيعات المحل الأرشيفية";FinancialSourceReconciliation.Stream.LEGACY_ONLINE -> "تحصيلات الأونلاين الأرشيفية";else -> "المرتجعات الأرشيفية" }
        val entries=selected.filter { it.stream==stream }
        if(entries.isEmpty()) lines+="لا توجد قيود في الفترة المختارة"
        for(entry in entries) lines+="${entry.recognizedOn} | ${entry.sourceKey} | ربح ${money(entry.profitUsd)} USD"
       }
       lines+=history.unresolved
       val costs=UnifiedExpensePartnerReadRepository(legacy,db.operationalExpensePartnerDao()).load(a.toString(),b.toString(),allowIncomplete=true)
       lines+="المصاريف والسحوبات الأرشيفية"
       for(entry in costs.historicalEvidence.sortedWith(compareBy({it.date},{it.time},{it.id}))) {
        val label=if(entry.partnerId==null) entry.category else mapOf("father" to "الأب","me" to "عبد الكريم","brother" to "يزن")[entry.partnerId].orEmpty()
        lines+="${entry.date} | $label | ${money(entry.amount)} ${entry.currency} | ${entry.id}"
       }
       lines+=costs.unresolved
      } finally { staging.close() }
      lines+="مبيعات المحل"
      for(entry in stores) lines+="${day(entry.recognized_at)} | ${invoice(entry.sale_id)} | ربح ${money(entry.profit_usd)} USD"
      for(entry in adjustments) lines+="${day(entry.created_at)} | ${invoice(entry.sale_id)} | ${entry.reason}: ${money(entry.profit_delta_usd)} USD"
      lines+="تحصيلات الأونلاين"
      for(entry in online) lines+="${day(entry.collected_at)} | ${invoice(entry.sale_id)} | المقبوض ${money(entry.credited_base)} USD | الربح ${money(entry.recognized_profit)} USD"
      lines+="المرتجعات وعكس الأرباح"
      for(entry in reverse) lines+="${day(entry.reversed_at)} | ${invoice(entry.sale_id)} | عكس ربح ${money(entry.reversed_profit)} USD"
      lines+="المصاريف التشغيلية الجديدة (لا تشمل الأرشيف)"
      for(entry in expenses) lines+="${entry.date} | ${entry.category} | ${money(entry.amount)} ${entry.currency} | ${entry.notes}"
      lines+="سحوبات الشركاء الجديدة (ليست مصروفاً تشغيلياً)"
      for(entry in withdrawals) lines+="${entry.date} | ${mapOf("father" to "الأب","me" to "عبد الكريم","brother" to "يزن")[entry.partner_id] ?: entry.partner_id} | ${money(entry.amount)} ${entry.currency}"
      lines
     }
     detail.text=if(rows.isEmpty()) "لا توجد قيود في هذه الفترة" else rows.joinToString("\n\n")
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
     val (data,consolidationError)=withContext(Dispatchers.IO) {
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
      val staging=com.alsafa.native8.data.migration.LegacyStagingDatabase(applicationContext)
      val (historical,unifiedAttempt)=try {
       val legacy=com.alsafa.native8.data.migration.LegacyReadRepository(staging)
       val projection=LegacyHistoricalProfitProjection(legacy)
       db.withTransaction { projection.read(a.toString(),b.toString()) to runCatching { UnifiedFinancialPeriodRepository(
        sales,UnifiedExpensePartnerReadRepository(legacy,db.operationalExpensePartnerDao()),
        ZoneId.of("Asia/Damascus"),store,projection,NativeMaintenanceProfitRepository(db.maintenanceDao(),db.legacyPaymentDao())).read(a.toString(),b.toString()) } }
      } finally { staging.close() }
      val unified=unifiedAttempt.getOrNull()
      val suspect=sales.nonOnlineProfitEventCount()
      listOf(storeProfit,storeAdjust,online,reversals,BigDecimal(unverified),
       BigDecimal(historical.saleEvents.count { it.stream==FinancialSourceReconciliation.Stream.LEGACY_STORE }),BigDecimal(historical.saleEvents.count { it.stream==FinancialSourceReconciliation.Stream.LEGACY_ONLINE }),
       BigDecimal(historical.salesUnresolved.size),storeRevenue,storeCapital,BigDecimal(suspect),onlineStockCapital,recognizedOnlineCapital,
       historical.storeProfitUsd,historical.onlineProfitUsd,historical.repairProfitUsd,
       historical.returnImpactUsd,historical.expensesUsd,BigDecimal(historical.unresolved.size),historical.storeCapitalUsd,BigDecimal(historical.salesUnresolved.size),unified?.partialNet ?: BigDecimal.ZERO,
       if(unified?.historicalSalesIncluded==true) BigDecimal.ONE else BigDecimal.ZERO,BigDecimal(unified?.unresolvedHistorical?.size ?: 0),
       if(unified?.complete==true) BigDecimal.ONE else BigDecimal.ZERO,
       unified?.maintenanceProfit ?: BigDecimal.ZERO,unified?.maintenanceReturnImpact ?: BigDecimal.ZERO) to
       unifiedAttempt.exceptionOrNull()?.let { it.message ?: "تعذر التجميع المالي" }
     }
     result.text="الفترة: $a — $b\nمبيعات المحل الجديدة بعد الخصومات: ${money(data[8])} USD\n"+
      "رأس مال ساعات المحل المباعة: ${money(data[9])} USD\n"+
      "رأس مال ساعات الأونلاين الخارجة من المخزون بتاريخ البيع: ${money(data[11])} USD\n"+
      "تكلفة الأونلاين المقابلة للتحصيلات خلال الفترة: ${money(data[12])} USD\n"+
      "أحداث أرباح المحل التاريخية في الفترة: ${data[5].toPlainString()}\n"+
      "ربح المحل التاريخي: ${money(data[13])} USD\n"+
      "رأس مال المحل التاريخي القابل للاستخراج: ${money(data[19])} USD\n"+
      "فواتير تاريخية تعذر تدقيق ربحها: ${data[20].toPlainString()}\n"+
      "أحداث تحصيل الأونلاين التاريخية في الفترة: ${data[6].toPlainString()}\n"+
      "فواتير تاريخية تحتاج مراجعة التصنيف: ${data[7].toPlainString()}\n"+
      "ربح فواتير المحل الجديدة: ${money(data[0])} USD\n"+
      "تسويات ربح المحل: ${money(data[1])} USD\n"+
      "أرباح تحصيلات الأونلاين الجديدة: ${money(data[2])} USD\n"+
      "عكس أرباح التحصيلات: ${money(data[3])} USD\n"+
      "فواتير جديدة غير مصنفة: ${data[4].toPlainString()}\n"+
      "أحداث تحصيل قديمة غير معتمدة كأونلاين: ${data[10].toPlainString()}\n"+
      "ربح المحل التاريخي (تدقيق): ${money(data[13])} USD\n"+
      "ربح تحصيلات الأونلاين التاريخية (تدقيق): ${money(data[14])} USD\n"+
      "ربح الصيانة التاريخية (تدقيق): ${money(data[15])} USD\n"+
      "أثر المرتجعات التاريخية (تدقيق): ${money(data[16])} USD\n"+
      "المصاريف التاريخية (تدقيق، لا تُخصم مرة ثانية): ${money(data[17])} USD\n"+
      "مشكلات القراءة التاريخية: ${data[18].toPlainString()}\n"+
      (if(consolidationError==null) "${if(data[24]==BigDecimal.ONE) "صافي الربح" else "مجموع غير معتمد لوجود عمليات تحتاج مراجعة"}: ${money(data[21])} USD\n"
       else "تعذر التجميع المالي: $consolidationError\n")+
      "دمج المبيعات التاريخية: ${if(data[22]==BigDecimal.ONE) "تم" else "موقوف للمراجعة"}\n"+
      "مشكلات التجميع: ${data[23].toPlainString()}\n"+
      "أرباح الصيانة القديمة والجديدة: ${money(data[25])} USD\n"+
      "أثر مرتجعات الصيانة: ${money(data[26])} USD\n"+
      (if(data[24]==BigDecimal.ONE) "المصادر مكتملة لهذه القراءة؛ مستحقات الشركاء في صفحة أرباح الشركاء."
       else "لا يعتمد هذا المجموع لمستحقات الشركاء حتى استكمال العمليات التي تحتاج مراجعة.")
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
