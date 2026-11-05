package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.LocalDate
import com.alsafa.native8.data.migration.LegacyStagingDatabase
import com.alsafa.native8.data.migration.LegacyReadRepository

/**
 * Partner profit statement. Until historical sales and repairs have passed
 * parity checks, do not present provisional shares as earned balances.
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
   text="حصة الأب 40%، عبد الكريم 35%، يزن 25%. مستحقات الأرباح النهائية قيد مطابقة الأرشيف والصيانة والمصاريف؛ لن تُعرض مبالغ غير معتمدة."
   textSize=17f
  }
  val result=TextView(this).apply { textSize=18f }
  val button=Button(this).apply { text="عرض سحوبات الشركاء" }
  listOf(heading,explanation,button,result).forEach {
   content.addView(it,LinearLayout.LayoutParams(-1,-2))
  }
  val scroll=ScrollView(this).apply {
   isFillViewport=true
   isHorizontalScrollBarEnabled=false
   addView(content,android.widget.FrameLayout.LayoutParams(-1,-2))
  }
  setContentView(scroll)
  button.setOnClickListener {
   button.isEnabled=false
   scope.launch {
    try {
     val snapshot=withContext(Dispatchers.IO) {
      val staging=LegacyStagingDatabase(applicationContext)
      try {
       UnifiedExpensePartnerReadRepository(
        LegacyReadRepository(staging),
        AppDatabase.get(applicationContext).operationalExpensePartnerDao()
       ).load("0001-01-01",LocalDate.now().toString())
      } finally { staging.close() }
     }
     val names=mapOf("father" to "الأب","me" to "عبد الكريم","brother" to "يزن")
     val lines=LegacyFinancialRules.partners.map { partner ->
      val withdrawn=snapshot.source.withdrawals.filter { it.partnerId==partner.id }
       .fold(BigDecimal.ZERO) { sum,row -> sum+row.money.usd() }
      "${names[partner.id]} (${partner.share}%): ${withdrawn.stripTrailingZeros().toPlainString()} USD سحوبات تراكمية"
     }
     val projection=withContext(Dispatchers.IO) {
      val staging=LegacyStagingDatabase(applicationContext)
      try {
       LegacyHistoricalProfitProjection(LegacyReadRepository(staging))
        .read("0001-01-01",LocalDate.now().toString())
      } finally { staging.close() }
     }
     val monthlyWithdrawals=snapshot.source.withdrawals.groupBy { it.date.take(7) }
      .toSortedMap().map { (month,entries) ->
       val byPartner=LegacyFinancialRules.partners.joinToString(" | ") { partner ->
        val sum=entries.filter { it.partnerId==partner.id }
         .fold(BigDecimal.ZERO) { total,entry -> total+entry.money.usd() }
        "${names[partner.id]}: ${sum.stripTrailingZeros().toPlainString()} USD"
       }
       "$month — $byPartner"
      }
     val historicalMonths=withContext(Dispatchers.IO) {
      val staging=LegacyStagingDatabase(applicationContext)
      try {
       val reader=LegacyHistoricalProfitProjection(LegacyReadRepository(staging))
       listOf("2026-09","2026-10").map { month ->
        month to reader.read("$month-01","$month-31")
       }
      } finally { staging.close() }
     }
     val monthlyAudit=historicalMonths.joinToString("\\n") { (month,p) ->
      val total=p.partialNetUsd.stripTrailingZeros().toPlainString()
      "$month: $total USD (تدقيق جزئي، غير معتمد) — " +
       "أونلاين: ${p.onlineProfitUsd} | محل: ${p.storeProfitUsd} | " +
       "صيانة: ${p.repairProfitUsd} | مرتجعات: ${p.returnImpactUsd} | " +
       "مصاريف: ${p.expensesUsd} | أخطاء: ${p.unresolved.size}"
     }
     result.text="المصاريف القديمة: ${snapshot.legacyExpenses} | الجديدة: ${snapshot.operationalExpenses}\n"+
      "سحوبات الشركاء القديمة: ${snapshot.legacyWithdrawals} | الجديدة: ${snapshot.operationalWithdrawals}\n"+
      lines.joinToString("\n")+"\n"+
      "ربح المحل التاريخي (تدقيق): ${projection.storeProfitUsd} USD\n"+
      "ربح الصيانة التاريخي (تدقيق): ${projection.repairProfitUsd} USD\n"+
      "تسويات المرتجعات التاريخية: ${projection.returnImpactUsd} USD\n"+
      "مصاريف تاريخية: ${projection.expensesUsd} USD\n"+
      "سجلات تحتاج مراجعة: ${projection.unresolved.size}\n"+
      "أرباح الأونلاين التاريخية والمستحقات النهائية: غير معتمدة حتى مطابقة التحصيلات."

    } catch(e:Exception) { result.text=e.message ?: "تعذر قراءة السحوبات" }
    finally { button.isEnabled=true }
   }
  }
 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
