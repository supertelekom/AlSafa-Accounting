package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Operational expenses and family withdrawals.
 * Existing repository commits the source entry and cash movement atomically.
 * Historical imported entries are never modified here.
 */
class FinancialEntryActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val expenseMode=intent.getBooleanExtra("expense",true)
  title=if(expenseMode) "المصاريف" else "الشركاء والسحوبات"
  val content=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(20,20,20,24)
  }
  fun field(hint:String,initial:String=""):EditText=EditText(this).apply {
   this.hint=hint
   setText(initial)
   setSingleLine(true)
   content.addView(this,LinearLayout.LayoutParams(-1,-2))
  }
  fun choose(label:String,options:List<String>):Spinner {
   val caption=TextView(this).apply { text=label;textSize=17f }
   content.addView(caption)
   return Spinner(this).apply {
    adapter=ArrayAdapter(this@FinancialEntryActivity,android.R.layout.simple_spinner_dropdown_item,options)
    content.addView(this,LinearLayout.LayoutParams(-1,-2))
   }
  }
  val category=if(expenseMode) field("تصنيف المصروف") else null
  val partner=if(!expenseMode) choose("الشريك",listOf("الأب","عبد الكريم","يزن")) else null
  val amount=field("المبلغ")
  val currency=choose("العملة",listOf("USD","TRY","SYP"))
  val usdRate=field("قيمة وحدة العملة بالدولار (سعر الصرف وقت العملية)","1")
  val vault=choose("الخزنة",listOf("الدرج","الصندوق الخارجي","شام كاش","البنك")+
   if(expenseMode) listOf("دون خزنة") else emptyList())
  val notes=field("ملاحظات")
  val status=TextView(this).apply { textSize=16f }
  val save=Button(this).apply { text=if(expenseMode) "حفظ المصروف" else "حفظ السحب" }
  content.addView(save,LinearLayout.LayoutParams(-1,-2))
  content.addView(status,LinearLayout.LayoutParams(-1,-2))
  val history=TextView(this).apply { textSize=16f }
  val showHistory=Button(this).apply { text=if(expenseMode) "عرض المصاريف" else "عرض سحوبات الشركاء" }
  content.addView(showHistory,LinearLayout.LayoutParams(-1,-2))
  content.addView(history,LinearLayout.LayoutParams(-1,-2))
  setContentView(ScrollView(this).apply {
   isFillViewport=true
   addView(content,android.widget.FrameLayout.LayoutParams(-1,-2))
  })
  showHistory.setOnClickListener {
   showHistory.isEnabled=false
   scope.launch {
    try {
     history.text=withContext(Dispatchers.IO) {
      val dao=AppDatabase.get(applicationContext).operationalExpensePartnerDao()
      val from="0001-01-01"
      val until="9999-12-31"
      if(expenseMode) dao.expenses(from,until).joinToString("\n") {
       "${it.date} | ${it.category} | ${it.amount} ${it.currency} | ${it.payment_method}"
      } else dao.withdrawals(from,until).joinToString("\n") {
       val name=when(it.partner_id) {"father"->"الأب";"me"->"عبد الكريم";else->"يزن"}
       "${it.date} | $name | ${it.amount} ${it.currency}"
      }
     }.ifBlank { "لا توجد عمليات جديدة" }
    } catch(e:Exception) { history.text=e.message ?: "تعذر عرض السجلات" }
    finally { showHistory.isEnabled=true }
   }
  }
  var pendingId:String?=null
  var pendingAt:Long?=null
  save.setOnClickListener {
   val id=pendingId ?: UUID.randomUUID().toString().also { pendingId=it }
   val createdAt=pendingAt ?: System.currentTimeMillis().also { pendingAt=it }
   save.isEnabled=false
   status.text="جارٍ الحفظ..."
   scope.launch {
    try {
     val day=LocalDate.now().toString()
     val time=LocalTime.now().withNano(0).toString()
     val selected=vault.selectedItem.toString()
     val box=if(selected=="دون خزنة") null else UnifiedCashboxBalance.canonical(selected)
     val unit=currency.selectedItem.toString()
     val value=amount.text.toString().trim()
     val rate=usdRate.text.toString().trim()
     val note=notes.text.toString().trim()
     withContext(Dispatchers.IO) {
      val repo=OperationalExpensePartnerRepository(AppDatabase.get(applicationContext))
      if(expenseMode) {
       val method=when(box) {
        "drawer" -> "نقدي"
        "external" -> "الخزنة الخارجية"
        "shamcash" -> "شام كاش"
        "bank" -> "بنكي"
        else -> "آجل"
       }
       repo.saveExpense(OperationalExpenseEntity(id,day,time,category!!.text.toString().trim(),
        note,unit,value,method,box,rate,createdAt,
        if(box==null) null else "financial-"+id))
      } else {
       val partnerId=listOf("father","me","brother")[partner!!.selectedItemPosition]
       repo.saveWithdrawal(OperationalPartnerWithdrawalEntity(id,day,time,partnerId,
        unit,value,requireNotNull(box),rate,note,createdAt,"financial-"+id))
      }
     }
     status.text="تم الحفظ"
     finish()
    } catch(e:Exception) {
     status.text=e.message ?: "تعذر الحفظ"
     save.isEnabled=true
    }
   }
  }
 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
