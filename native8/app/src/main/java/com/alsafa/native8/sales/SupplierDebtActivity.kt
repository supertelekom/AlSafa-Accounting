package com.alsafa.native8.sales

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal

/** Each posted purchase has its own settlement action. Historical debts are not reposted. */
class SupplierDebtActivity : Activity() {
 private data class Due(val id:String,val currency:String,val total:BigDecimal,val paid:BigDecimal) {
  val remaining:BigDecimal get()=total-paid
 }
 private lateinit var list:LinearLayout
 private lateinit var summary:TextView
 private lateinit var status:TextView
 private val db by lazy { AppDatabase.get(applicationContext) }
 private val supplierId by lazy { intent.getStringExtra("supplier_id").orEmpty() }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(16,16,16,16) }
  root.addView(TextView(this).apply { text="ديون المورد — فواتير الشراء";textSize=20f })
  summary=TextView(this).apply { text="جاري تحميل الفواتير...";textSize=16f }
  root.addView(summary)
  list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  root.addView(list)
  status=TextView(this).apply { textSize=15f }
  root.addView(status)
  setContentView(ScrollView(this).apply { addView(root) })
  refresh()
 }
 private fun refresh() {
  Thread {
   try {
    val rows=runBlocking {
     val dao=db.supplierPurchaseDao()
     dao.allDrafts().filter { it.supplier_id==supplierId && it.status=="POSTED" }.map { draft ->
      val total=dao.draftLines(draft.draft_id).fold(BigDecimal.ZERO) { s,l -> s+l.quantity_exact.toBigDecimal()*l.unit_cost_exact.toBigDecimal() }
      val paid=dao.newPayments(draft.draft_id).fold(BigDecimal.ZERO) { s,p -> s+p.amount_exact.toBigDecimal() }
      Due(draft.draft_id,draft.currency,total,paid)
     }
    }
    runOnUiThread {
     list.removeAllViews()
     val pending=rows.filter { it.remaining>BigDecimal.ZERO }
     summary.text=if(rows.isEmpty()) "لا توجد فواتير شراء جديدة لهذا المورد" else "الفواتير: ${rows.size} | فواتير عليها دين: ${pending.size}"
     rows.forEach { due ->
      val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(12,12,12,12) }
      card.addView(TextView(this).apply {
       text="فاتورة شراء ${due.id.take(8)}\nالإجمالي: ${due.total.toPlainString()} ${due.currency} | المدفوع: ${due.paid.toPlainString()} | المتبقي: ${due.remaining.toPlainString()}"
       textSize=16f
      })
      card.addView(Button(this).apply {
       text=if(due.remaining>BigDecimal.ZERO) "تسديد هذه الفاتورة" else "مسددة بالكامل"
       isEnabled=due.remaining>BigDecimal.ZERO
       setOnClickListener { showPayment(due) }
      })
      list.addView(card)
     }
    }
   } catch(e:Exception) { runOnUiThread { status.text="تعذر قراءة الفواتير: ${e.message}" } }
  }.start()
 }
 private fun showPayment(due:Due) {
  val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(20,12,20,8) }
  content.addView(TextView(this).apply { text="الفاتورة ${due.id.take(8)} — المتبقي ${due.remaining.toPlainString()} ${due.currency}" })
  val type=Spinner(this).apply { adapter=ArrayAdapter(this@SupplierDebtActivity,android.R.layout.simple_spinner_dropdown_item,listOf("تسديد كامل المتبقي","تسديد جزء من المتبقي")) }
  content.addView(type)
  val amount=EditText(this).apply { hint="مبلغ الدفعة الجزئية";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;visibility=View.GONE }
  content.addView(amount)
  type.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(parent:AdapterView<*>?){}
   override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { amount.visibility=if(position==1) View.VISIBLE else View.GONE }
  }
  val vault=Spinner(this).apply { adapter=ArrayAdapter(this@SupplierDebtActivity,android.R.layout.simple_spinner_dropdown_item,listOf("الدرج","الصندوق الخارجي","شام كاش","البنك")) }
  content.addView(vault)
  val dialog=AlertDialog.Builder(this).setTitle("تسديد فاتورة شراء").setView(content)
   .setNegativeButton("إلغاء",null).setPositiveButton("تأكيد التسديد",null).create()
  dialog.setOnShowListener {
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { button ->
    val value=if(type.selectedItemPosition==0) due.remaining else amount.text.toString().toBigDecimalOrNull()
    if(value==null || value<=BigDecimal.ZERO || value>due.remaining) { amount.error="مبلغ غير صالح";return@setOnClickListener }
    val cashbox=listOf("drawer","external","shamcash","bank")[vault.selectedItemPosition]
    button.isEnabled=false
    Thread {
     try {
      val left=runBlocking { SupplierPaymentService(db).pay(due.id,value,cashbox) }
      runOnUiThread {
       dialog.dismiss()
       status.text="تم تسديد فاتورة ${due.id.take(8)}؛ المتبقي لهذه الفاتورة ${left.toPlainString()} ${due.currency}"
       refresh()
      }
     } catch(e:Exception) { runOnUiThread { status.text="فشل التسديد: ${e.message}";button.isEnabled=true } }
    }.start()
   }
  }
  dialog.show()
 }
}
