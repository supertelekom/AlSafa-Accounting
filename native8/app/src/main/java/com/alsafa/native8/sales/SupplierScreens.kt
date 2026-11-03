package com.alsafa.native8.sales

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.runBlocking
import android.view.Gravity

/** Supplier archive browser: no historical ledger postings. */
class SuppliersActivity : Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(24,24,24,24)
  }
  root.addView(TextView(this).apply { text="الموردون";textSize=24f })
  val status=TextView(this).apply { text="جاري تحميل الموردين..." }
  status.textSize=18f
  status.setPadding(8,18,8,18)
  root.addView(status)
  root.addView(Button(this).apply {
   text="المشتريات"
   setOnClickListener { startActivity(Intent(this@SuppliersActivity,PurchasesActivity::class.java)) }
  })
  val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  root.addView(ScrollView(this).apply { addView(list) },
   LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  Thread {
   try {
    val db=AppDatabase.get(applicationContext)
    val rows=runBlocking {
     db.supplierPurchaseDao().allSuppliers()
    }
    runOnUiThread {
     status.text=if(rows.isEmpty()) "لا يوجد موردون في قاعدة Room حالياً. افتح «استعادة البيانات» من الرئيسية واختر النسخة الاحتياطية، ثم تحقق من سطر «أرشيف الموردين المنقول إلى Room». لن تظهر بيانات الموردين قبل نجاح النقل." else "عدد الموردين المستوردين: ${rows.size}"
     rows.forEach { supplier ->
      list.addView(Button(this).apply {
       text=supplier.name.ifBlank { supplier.supplier_id }
       textSize=20f
       minHeight=68
       isAllCaps=false
       setOnClickListener {
        startActivity(Intent(this@SuppliersActivity,SupplierDetailsActivity::class.java)
         .putExtra("supplier_id",supplier.supplier_id))
       }
      })
     }
    }
   } catch(e:Exception) { runOnUiThread { status.text="تعذر قراءة الموردين: ${e.message}" } }
  }.start()
 }
}

class SupplierDetailsActivity:Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val supplierId=intent.getStringExtra("supplier_id").orEmpty()
  val text=TextView(this).apply { text="جاري تحميل كشف المورد...";textSize=20f;setPadding(28,28,28,28) }
  setContentView(ScrollView(this).apply { addView(text) })
  if(supplierId.isBlank()) { text.text="معرف المورد مفقود";return }
  Thread {
   try {
    val dao=AppDatabase.get(applicationContext).supplierPurchaseDao()
    val purchases=runBlocking { dao.purchases(supplierId) }
    val ledger=runBlocking { dao.historicalLedger(supplierId) }
    val report=buildString {
     append("المورد: ").append(supplierId).append("\n")
     append("فواتير الشراء التاريخية: ").append(purchases.size).append("\n\n")
     purchases.forEach { invoice ->
      val due=invoice.total_exact.toBigDecimal().subtract(invoice.paid_at_import_exact.toBigDecimal())
      append(invoice.invoice_number).append(" | ").append(invoice.currency)
       .append(" | المتبقي وقت الاستيراد: ").append(due.toPlainString()).append("\n")
     }
     append("\nحركات الحساب السابق: ").append(ledger.size).append("\n")
     ledger.forEach { row ->
      append(row.kind).append(" | ").append(row.amount_exact).append(" ").append(row.currency).append("\n")
     }
     append("\nالأرصدة التاريخية معروضة بصورة مستقلة، ولم تُجمع تلقائياً.")
    }
    runOnUiThread { text.text=report }
   } catch(e:Exception) { runOnUiThread { text.text="تعذر تحميل الكشف: ${e.message}" } }
  }.start()
 }
}

class PurchasesActivity:Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val text=TextView(this).apply { text="جاري تحميل المشتريات...";textSize=18f;setPadding(24,24,24,24) }
  setContentView(ScrollView(this).apply { addView(text) })
  Thread {
   try {
    val rows=runBlocking { AppDatabase.get(applicationContext).supplierPurchaseDao().allPurchases() }
    val report=buildString {
     append("المشتريات — الأرشيف المستورد\n")
     append("عدد الفواتير: ").append(rows.size).append("\n\n")
     if(rows.isEmpty()) append("لا توجد فواتير شراء في Room حالياً. استخدم «استعادة البيانات» من الرئيسية واختر النسخة الاحتياطية، ثم تحقق من نتيجة استيراد أرشيف الموردين.\n\n")
     rows.forEach { row ->
      val due=row.total_exact.toBigDecimal().subtract(row.paid_at_import_exact.toBigDecimal())
      append(row.invoice_number).append(" | ").append(row.supplier_id)
       .append(" | ").append(row.invoice_date).append("\n")
       .append("الإجمالي: ").append(row.total_exact).append(" ").append(row.currency)
       .append(" | المدفوع: ").append(row.paid_at_import_exact)
       .append(" | المتبقي: ").append(due.toPlainString()).append("\n\n")
     }
     append("الأرشيف للعرض فقط؛ لم تُرحّل حركة مالية أو مخزنية جديدة.")
    }
    runOnUiThread { text.text=report }
   } catch(e:Exception) { runOnUiThread { text.text="تعذر قراءة المشتريات: ${e.message}" } }
  }.start()
 }
}
