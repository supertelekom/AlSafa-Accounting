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
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(28,28,28,28)
  }
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
  val result=TextView(this).apply { textSize=18f }
  val detail=TextView(this).apply { textSize=15f }
  val showDetails=Button(this).apply { text="تفصيل القيود المالية" }
  val refresh=Button(this).apply { text="عرض مراجعة الأرباح" }
  listOf(title,warning,from,to,refresh,result,showDetails,detail).forEach {
   root.addView(it,LinearLayout.LayoutParams(-1,-2))
  }
  setContentView(ScrollView(this).apply { addView(root) })
  showDetails.setOnClickListener {
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
      val online=db.salesFinancialAuditDao().profitEventsInPeriod(w.startInclusive,w.endExclusive)
      val reverse=db.salesFinancialAuditDao().profitReversalsInPeriod(w.startInclusive,w.endExclusive)
      val lines=mutableListOf<String>()
      stores.forEach { lines+="فاتورة محل ${it.sale_id}: +${it.profit_usd} USD" }
      adjustments.forEach { lines+="تسوية محل ${it.sale_id} (${it.reason}): ${it.profit_delta_usd} USD" }
      online.forEach { lines+="تحصيل ${it.sale_id} / ${it.payment_id}: +${it.recognized_profit} USD" }
      reverse.forEach { lines+="عكس تحصيل ${it.sale_id} / ${it.payment_id}: -${it.reversed_profit} USD" }
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
      val online=sum(sales.profitEventsInPeriod(window.startInclusive,window.endExclusive).map { it.recognized_profit })
      val reversals=sum(sales.profitReversalsInPeriod(window.startInclusive,window.endExclusive).map { it.reversed_profit })
      val unverified=db.salesPostingDao().unverifiedChannelCount()
      listOf(storeProfit,storeAdjust,online,reversals,BigDecimal(unverified))
     }
     result.text="ربح فواتير المحل الجديدة: ${data[0]} USD\n"+
      "تسويات ربح المحل: ${data[1]} USD\n"+
      "أرباح تحصيلات الأونلاين الجديدة: ${data[2]} USD\n"+
      "عكس أرباح التحصيلات: ${data[3]} USD\n"+
      "فواتير جديدة غير مصنفة: ${data[4].toPlainString()}\n"+
      "المصاريف والصيانة والأرشيف والشركاء: لم تكتمل مطابقتها؛ لا يُعرض صافي نهائي."
    } catch(e:Exception) {
     result.text="تعذر استخراج المراجعة: "+(e.message ?: "خطأ غير معروف")
    } finally { refresh.isEnabled=true }
   }
  }
 }
 override fun onDestroy() {
  scope.cancel()
  super.onDestroy()
 }
}
