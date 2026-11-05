package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.LocalDate

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
     val rows=withContext(Dispatchers.IO) {
      AppDatabase.get(applicationContext).operationalExpensePartnerDao()
       .withdrawals("0001-01-01","9999-12-31")
     }
     val names=mapOf("father" to "الأب","me" to "عبد الكريم","brother" to "يزن")
     val lines=rows.groupBy { it.partner_id }.map { (id,entries) ->
      val sum=entries.fold(BigDecimal.ZERO) { a,e ->
       a+e.amount.toBigDecimal()*e.usd_per_unit.toBigDecimal()
      }
      "${names[id] ?: id}: سحوبات جديدة ${sum.stripTrailingZeros().toPlainString()} USD"
     }
     result.text=(if(lines.isEmpty()) "لا توجد سحوبات جديدة" else lines.joinToString("\n"))+
      "\nالسحوبات التاريخية والأرباح التراكمية: لم تُعتمد بعد."
    } catch(e:Exception) { result.text=e.message ?: "تعذر قراءة السحوبات" }
    finally { button.isEnabled=true }
   }
  }
 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
