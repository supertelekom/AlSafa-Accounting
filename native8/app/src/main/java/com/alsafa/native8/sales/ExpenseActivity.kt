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
class ExpenseActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  title="المصاريف"
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
    adapter=ArrayAdapter(this@ExpenseActivity,android.R.layout.simple_spinner_dropdown_item,options)
    content.addView(this,LinearLayout.LayoutParams(-1,-2))
   }
  }
  val category=field("تصنيف المصروف")

  val amount=field("المبلغ")
  val currency=choose("العملة",listOf("USD","TRY","SYP"))
  val vault=choose("الخزنة",listOf("الدرج","الصندوق الخارجي","شام كاش","البنك","دون خزنة"))
  val notes=field("ملاحظات")
  val status=TextView(this).apply { textSize=16f }
  val save=Button(this).apply { text="حفظ المصروف" }
  content.addView(save,LinearLayout.LayoutParams(-1,-2))
  content.addView(status,LinearLayout.LayoutParams(-1,-2))
  val history=TextView(this).apply { textSize=16f }
  val showHistory=Button(this).apply { text="عرض المصاريف" }
  content.addView(showHistory,LinearLayout.LayoutParams(-1,-2))
  content.addView(history,LinearLayout.LayoutParams(-1,-2))
  // Fit the actual app window on the Honor tablet, including system bars.
  // The whole form scrolls vertically; no controls are placed outside it.
  val density=resources.displayMetrics.density
  val inset=(12*density+0.5f).toInt()
  content.setPadding(inset,inset,inset,inset)
  val scroll=ScrollView(this).apply {
   isFillViewport=true
   isHorizontalScrollBarEnabled=false
   isVerticalScrollBarEnabled=true
   clipToPadding=false
   addView(content,android.widget.FrameLayout.LayoutParams(-1,-2))
   setOnApplyWindowInsetsListener { view,insets ->
    val top:Int
    val bottom:Int
    if(android.os.Build.VERSION.SDK_INT>=30) {
     val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars())
     top=bars.top
     bottom=bars.bottom
    } else {
     top=insets.systemWindowInsetTop
     bottom=insets.systemWindowInsetBottom
    }
    view.setPadding(0,top,0,bottom)
    insets
   }
  }
  window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
  window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
   android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  setContentView(scroll)
  scroll.requestApplyInsets()
  content.isFocusableInTouchMode=true
  content.requestFocus()
  scroll.post { scroll.scrollTo(0,0) }
  showHistory.setOnClickListener {
   showHistory.isEnabled=false
   scope.launch {
    try {
     history.text=withContext(Dispatchers.IO) {
      val dao=AppDatabase.get(applicationContext).operationalExpensePartnerDao()
      val from="0001-01-01"
      val until="9999-12-31"
      dao.expenses(from,until).joinToString("\n") {
       "${it.date} | ${it.category} | ${it.amount} ${it.currency} | ${it.payment_method}"
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
     // Snapshot the configured direct rate at posting time; never recalculate saved entries.
     val rate=ExchangeRateSettings(applicationContext).rate(unit,"USD").toPlainString()
     val note=notes.text.toString().trim()
     withContext(Dispatchers.IO) {
      val repo=OperationalExpensePartnerRepository(AppDatabase.get(applicationContext))
      val method=when(box) {
       "drawer" -> "نقدي"
       "external" -> "الخزنة الخارجية"
       "shamcash" -> "شام كاش"
       "bank" -> "بنكي"
       else -> "آجل"
      }
      repo.saveExpense(OperationalExpenseEntity(id,day,time,category.text.toString().trim(),
       note,unit,value,method,box,rate,createdAt,
       if(box==null) null else "financial-"+id))
     }
     status.text="تم الحفظ"
     pendingId=null
     pendingAt=null
     android.app.AlertDialog.Builder(this@ExpenseActivity)
      .setTitle("المصاريف")
      .setMessage("تم حفظ المصروف بنجاح")
      .setPositiveButton("موافق") { dialog,_ ->
       dialog.dismiss()
       amount.setText("")
       notes.setText("")
       category.setText("")
       save.isEnabled=true
      }
      .setCancelable(false)
      .show()
    } catch(e:Exception) {
     status.text=e.message ?: "تعذر الحفظ"
     save.isEnabled=true
    }
   }
  }
 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
