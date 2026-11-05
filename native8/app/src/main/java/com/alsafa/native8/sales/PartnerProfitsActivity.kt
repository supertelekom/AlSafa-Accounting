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
   text="حصة الأب 40%، عبد الكريم 35%، يزن 25%. الأرباح والمستحقات غير معتمدة حتى اكتمال مطابقة التحصيلات التاريخية."
   textSize=17f
  }
  val result=TextView(this).apply { textSize=18f }
  val button=Button(this).apply { text="عرض حالة أرباح الشركاء" }
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
   result.text="الأب 40% | عبد الكريم 35% | يزن 25%\\n"+
    "لا يمكن عرض مستحقات نهائية قبل اعتماد أرباح المبيعات والصيانة والتحصيلات التاريخية.\\n"+
    "تفاصيل السحوبات القديمة والجديدة موجودة في صفحة سحوبات الشركاء المستقلة."
  }
 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
