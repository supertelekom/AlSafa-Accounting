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
class PartnersActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  title="سحوبات الشركاء"
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
    adapter=ArrayAdapter(this@PartnersActivity,android.R.layout.simple_spinner_dropdown_item,options)
    content.addView(this,LinearLayout.LayoutParams(-1,-2))
   }
  }

  val partner=choose("الشريك",listOf("الأب","عبد الكريم","يزن"))
  val amount=field("المبلغ")
  val currency=choose("العملة",listOf("USD","TRY","SYP"))
  val vault=choose("الخزنة",listOf("الدرج","الصندوق الخارجي","شام كاش","البنك"))
  val notes=field("ملاحظات")
  val status=TextView(this).apply { textSize=16f }
  val save=Button(this).apply { text="حفظ السحب" }
  content.addView(save,LinearLayout.LayoutParams(-1,-2))
  content.addView(status,LinearLayout.LayoutParams(-1,-2))
  val history=TextView(this).apply { textSize=16f }
  val showHistory=Button(this).apply { text="عرض سحوبات الشركاء" }
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
      val staging=com.alsafa.native8.data.migration.LegacyStagingDatabase(applicationContext)
      try {
       val legacy=com.alsafa.native8.data.migration.LegacyReadRepository(staging)
       val unified=UnifiedExpensePartnerReadRepository(
        legacy,AppDatabase.get(applicationContext).operationalExpensePartnerDao())
       val snapshot=unified.load("0001-01-01","9999-12-31")
       val old=LegacyFinancialHistoryDisplay.legacy(legacy,"partnerWithdrawals")
       val fresh=AppDatabase.get(applicationContext).operationalExpensePartnerDao()
        .withdrawals("0001-01-01","9999-12-31").map { e ->
         val name=when(e.partner_id) {
          "father"->"الأب";"me"->"عبد الكريم";"brother"->"يزن";else->e.partner_id
         }
         LegacyFinancialHistoryDisplay.Entry(e.date,name,e.amount,e.currency,"","جديد")
        }
       "السحوبات القديمة: ${snapshot.legacyWithdrawals} | الجديدة: ${snapshot.operationalWithdrawals}\\n\\n"+
        LegacyFinancialHistoryDisplay.render(old+fresh)
      } finally { staging.close() }
     }.ifBlank { "لا توجد سحوبات" }
    } catch(e:Exception) { history.text=e.message ?: "تعذر عرض السجلات" }
    finally { showHistory.isEnabled=true }
   }
  }
  var pendingId:String?=null
  var pendingAt:Long?=null
  val performConfirmedSave:()->Unit = {

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
      val partnerId=listOf("father","me","brother")[partner.selectedItemPosition]
      repo.saveWithdrawal(OperationalPartnerWithdrawalEntity(id,day,time,partnerId,
       unit,value,requireNotNull(box),rate,note,createdAt,"financial-"+id))
     }
     status.text="تم الحفظ"
     pendingId=null
     pendingAt=null
     android.app.AlertDialog.Builder(this@PartnersActivity)
      .setTitle("سحوبات الشركاء")
      .setMessage("تم حفظ سحب الشريك بنجاح")
      .setPositiveButton("موافق") { dialog,_ ->
       dialog.dismiss()
       amount.setText("")
       notes.setText("")

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
  save.setOnClickListener {
   val entered=amount.text.toString().trim().toBigDecimalOrNull()
   if(entered==null || entered<=java.math.BigDecimal.ZERO) {
    status.text="أدخل مبلغاً موجباً صحيحاً"
    return@setOnClickListener
   }
   val unit=currency.selectedItem.toString()
   val selectedVault=vault.selectedItem.toString()
   val subject=partner.selectedItem.toString()
   if(subject.isBlank()) {
    status.text="اختر الشريك"
    return@setOnClickListener
   }
   val memo=notes.text.toString().trim()
   val description="هل توافق على سحب " + entered.toPlainString() + " " + unit +
    " للشريك " + subject + " من " + selectedVault +
    (if(memo.isBlank()) "" else "\nملاحظات: " + memo) + "؟"
   android.app.AlertDialog.Builder(this)
    .setTitle("تأكيد سحب الشريك")
    .setMessage(description)
    .setNegativeButton("إلغاء") { dialog,_ -> dialog.dismiss() }
    .setPositiveButton("موافق") { dialog,_ ->
     dialog.dismiss()
     performConfirmedSave.invoke()
    }
    .show()
  }

 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
