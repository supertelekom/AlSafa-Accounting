package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal

/** Supplier debt settlement for new posted purchases; historical balances remain read-only. */
class SupplierDebtActivity:Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val supplierId=intent.getStringExtra("supplier_id").orEmpty()
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(16,16,16,16) }
  val title=TextView(this).apply { text="ديون المورد — فواتير الشراء الجديدة";textSize=19f }
  root.addView(title)
  val invoice=Spinner(this)
  root.addView(invoice)
  val debt=TextView(this).apply { text="جاري تحميل الديون";textSize=16f }
  root.addView(debt)
  val amount=EditText(this).apply { hint="مبلغ الدفعة";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
  root.addView(amount)
  val vault=Spinner(this).apply { adapter=ArrayAdapter(this@SupplierDebtActivity,android.R.layout.simple_spinner_dropdown_item,listOf("الدرج","الصندوق الخارجي","شام كاش","البنك")) }
  root.addView(vault)
  val full=Button(this).apply { text="تسديد كامل المتبقي";isEnabled=false }
  val partial=Button(this).apply { text="تسديد المبلغ المحدد";isEnabled=false }
  root.addView(full);root.addView(partial)
  val status=TextView(this).apply { text="";textSize=14f }
  root.addView(status)
  setContentView(ScrollView(this).apply { addView(root) })
  data class Due(val id:String,val currency:String,val remaining:BigDecimal)
  var dues=emptyList<Due>()
  val db=AppDatabase.get(applicationContext)
  fun refresh() {
   Thread {
    try {
     val rows=runBlocking {
      val dao=db.supplierPurchaseDao()
      dao.allDrafts().filter { it.supplier_id==supplierId && it.status=="POSTED" }.mapNotNull { draft ->
       val total=dao.draftLines(draft.draft_id).fold(BigDecimal.ZERO) { s,l -> s+l.quantity_exact.toBigDecimal()*l.unit_cost_exact.toBigDecimal() }
       val paid=dao.newPayments(draft.draft_id).fold(BigDecimal.ZERO) { s,p -> s+p.amount_exact.toBigDecimal() }
       val remaining=total-paid
       if(remaining>BigDecimal.ZERO) Due(draft.draft_id,draft.currency,remaining) else null
      }
     }
     runOnUiThread {
      dues=rows
      invoice.adapter=ArrayAdapter(this@SupplierDebtActivity,android.R.layout.simple_spinner_dropdown_item,rows.map { "فاتورة "+it.id.take(8)+" — "+it.remaining.toPlainString()+" "+it.currency })
      full.isEnabled=rows.isNotEmpty();partial.isEnabled=rows.isNotEmpty()
      debt.text=if(rows.isEmpty()) "لا توجد ديون على فواتير الشراء الجديدة" else "اختر الفاتورة لتسديد المتبقي كاملاً أو جزئياً"
     }
    } catch(e:Exception) { runOnUiThread { status.text="تعذر تحميل الديون: "+e.message } }
   }.start()
  }
  fun pay(all:Boolean) {
   val due=dues.getOrNull(invoice.selectedItemPosition) ?: return
   val value=if(all) due.remaining else amount.text.toString().toBigDecimalOrNull()
   if(value==null || value<=BigDecimal.ZERO || value>due.remaining) { status.text="مبلغ غير صالح";return }
   val cashbox=listOf("drawer","external","shamcash","bank")[vault.selectedItemPosition]
   full.isEnabled=false;partial.isEnabled=false
   Thread {
    try {
     val left=runBlocking { SupplierPaymentService(db).pay(due.id,value,cashbox) }
     runOnUiThread { status.text="تم التسديد؛ المتبقي "+left.toPlainString()+" "+due.currency;amount.text.clear();refresh() }
    } catch(e:Exception) { runOnUiThread { status.text="فشل التسديد: "+e.message;full.isEnabled=true;partial.isEnabled=true } }
   }.start()
  }
  full.setOnClickListener { pay(true) }
  partial.setOnClickListener { pay(false) }
  refresh()
 }
}
