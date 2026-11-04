package com.alsafa.native8.sales

import androidx.activity.ComponentActivity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate

/** Manual closing with counted amounts; no automatic guessed cash counts. */
class DailyClosingActivity:ComponentActivity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val layout=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(24,24,24,24)
  }
  fun title(value:String)=TextView(this).apply { text=value;textSize=19f;layout.addView(this) }
  fun field(label:String)=EditText(this).apply {
   hint=label
   inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
   layout.addView(this)
  }
  title("الإغلاق اليومي — "+LocalDate.now())
  val info=title("جاري حساب الرصيد المتوقع...")
  val drawer=linkedMapOf<String,EditText>()
  val transfer=linkedMapOf<String,EditText>()
  val sham=linkedMapOf<String,EditText>()
  for(unit in listOf("USD","SYP","TRY")) {
   title("الدرج — "+unit)
   drawer[unit]=field("المبلغ المعدود فعلياً — "+unit)
   transfer[unit]=field("المنقول إلى الصندوق الخارجي — "+unit).apply {setText("0")}
  }
  for(unit in listOf("USD","SYP")) {
   title("شام كاش — "+unit)
   sham[unit]=field("الرصيد الفعلي — "+unit)
  }
  val notes=EditText(this).apply {hint="ملاحظات";layout.addView(this)}
  val save=Button(this).apply {text="حفظ الإغلاق اليومي";isEnabled=false;layout.addView(this)}
  setContentView(ScrollView(this).apply {addView(layout)})
  val service=DailyClosingService(AppDatabase.get(applicationContext))
  lifecycleScope.launch {
   try {
    val preview=withContext(Dispatchers.IO) {service.preview()}
    info.text="الرصيد المتوقع — الدرج: "+preview.drawerExpected.entries.joinToString("، ") {it.key+": "+it.value.toPlainString()}+
     "\nشام كاش: "+preview.shamExpected.entries.joinToString("، ") {it.key+": "+it.value.toPlainString()}
    drawer.forEach { (unit,input)->input.setText(preview.drawerExpected.getValue(unit).toPlainString()) }
    sham.forEach { (unit,input)->input.setText(preview.shamExpected.getValue(unit).toPlainString()) }
    save.isEnabled=true
   } catch(e:Exception) {info.text="تعذر تحميل الرصيد: "+(e.message ?: "")}
  }
  save.setOnClickListener {
   val counts=runCatching {
    fun parse(input:EditText)=input.text.toString().trim().toBigDecimal()
    DailyClosingService.Counts(
     drawer.mapValues {parse(it.value)},
     transfer.mapValues {parse(it.value)},
     sham.mapValues {parse(it.value)},notes.text.toString()
    )
   }.getOrElse {info.text="أدخل مبالغ صحيحة لجميع العملات";return@setOnClickListener}
   val message=buildString {
    append("تأكيد إغلاق اليوم؟\n")
    for(unit in listOf("USD","SYP","TRY")) {
     append(unit+": المعدود "+counts.drawerActual.getValue(unit)+
      "، المنقول "+counts.drawerTransfer.getValue(unit)+"\n")
    }
    append("لا يمكن تنفيذ الإغلاق مرتين لليوم نفسه.")
   }
   AlertDialog.Builder(this).setTitle("تأكيد الإغلاق").setMessage(message)
    .setNegativeButton("رجوع",null).setPositiveButton("تأكيد") {_,_->
     save.isEnabled=false
     lifecycleScope.launch {
      try {
       withContext(Dispatchers.IO) {service.close(counts)}
       info.text="تم حفظ الإغلاق بنجاح"
       finish()
      } catch(e:Exception) {
       info.text="لم يتم الإغلاق: "+(e.message ?: "خطأ")
       save.isEnabled=true
      }
     }
    }.show()
  }
 }
}
