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
  title="سحوبات الشركاء"
  val content=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(20,20,20,24)
   isFocusableInTouchMode=true
  }
  val heading=TextView(this).apply { text="سحوبات الشركاء";textSize=24f }
  val explanation=TextView(this).apply {
   text="السحوبات القديمة والجديدة لكل شريك، شهرياً وتراكمياً."
   textSize=17f
  }
  val result=TextView(this).apply { textSize=18f }
  val button=Button(this).apply { text="عرض السحوبات" }
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
     val monthlyWithdrawals=snapshot.source.withdrawals.groupBy { it.date.take(7) }
      .toSortedMap().map { (month,entries) ->
       val byPartner=LegacyFinancialRules.partners.joinToString(" | ") { partner ->
        val sum=entries.filter { it.partnerId==partner.id }
         .fold(BigDecimal.ZERO) { total,entry -> total+entry.money.usd() }
        "${names[partner.id]}: ${sum.stripTrailingZeros().toPlainString()} USD"
       }
       "$month — $byPartner"
      }
     result.text="سحوبات الشركاء القديمة: ${snapshot.legacyWithdrawals} | الجديدة: ${snapshot.operationalWithdrawals}\\n"+
      lines.joinToString("\\n")+"\\n"+
      "السحوبات حسب الشهر:\\n"+monthlyWithdrawals.joinToString("\\n")


    } catch(e:Exception) { result.text=e.message ?: "تعذر قراءة السحوبات" }
    finally { button.isEnabled=true }
   }
  }
 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
