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
import android.graphics.Color
import android.view.Gravity

/** Supplier archive browser: no historical ledger postings. */
class SuppliersActivity : Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(8,8,8,8)
   fitsSystemWindows=true
  }
  root.addView(TextView(this).apply { text="الموردون";textSize=16f;setTextColor(Color.BLACK) })
  val status=TextView(this).apply { text="جاري تحميل الموردين..." }
  status.textSize=13f
  status.setPadding(8,8,8,8)
  root.addView(status)
  root.addView(Button(this).apply {
   text="المشتريات / إنشاء فاتورة شراء";textSize=13f;minHeight=40;minimumHeight=40
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
       textSize=13f
       minHeight=38
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
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(8,8,8,8);fitsSystemWindows=true }
  root.addView(TextView(this).apply { text="كشف حساب المورد";textSize=17f;setTextColor(Color.BLACK) })
  val text=TextView(this).apply { this.text="جاري تحميل كشف المورد...";textSize=15f;setTextColor(Color.BLACK);setPadding(8,8,8,8) }
  root.addView(ScrollView(this).apply { addView(text) },LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  if(supplierId.isBlank()) { text.text="معرف المورد مفقود";return }
  Thread {
   try {
    val dao=AppDatabase.get(applicationContext).supplierPurchaseDao()
    val review=runBlocking { SupplierBalanceReview(dao).inspect(supplierId) }
    val report=buildString {
     append("المورد: ").append(supplierId).append("\\n")
     append("فواتير الشراء التاريخية: ").append(review.invoices.size).append("\\n\\n")
     review.invoices.forEach { invoice ->
      append(invoice.number).append(" | ").append(invoice.currency)
       .append(" | الإجمالي: ").append(invoice.total.toPlainString())
       .append(" | المدفوع: ").append(invoice.paid.toPlainString())
       .append(" | المتبقي: ").append(invoice.remaining.toPlainString()).append("\\n")
     }
     append("\\nالحساب السابق (منفصل عن الفواتير):\\n")
     review.ledger.forEach { ledger ->
      append(ledger.currency).append(" | دين افتتاحي: ").append(ledger.opening.toPlainString())
       .append(" | دفعات تاريخية: ").append(ledger.payments.toPlainString()).append("\\n")
     }
     if(review.warnings.isNotEmpty()) {
      append("\\nتنبيهات التدقيق:\\n")
      review.warnings.forEach { append("• ").append(it).append("\\n") }
     }
     append("\\nغير معتمد للترحيل المالي حتى مطابقة التداخل مع المصدر الأصلي.")
    }
    runOnUiThread { text.text=report }
   } catch(e:Exception) { runOnUiThread { text.text="تعذر تحميل الكشف: ${e.message}" } }
  }.start()
 }
}

class PurchasesActivity:Activity() {
 override fun onRestart() {
  super.onRestart()
  recreate() // Refresh saved drafts after returning from the draft editor.
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val purchaseReportView=TextView(this).apply { text="جاري تحميل المشتريات...";textSize=13f;setTextColor(Color.BLACK);setPadding(8,8,8,8) }
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(8,8,8,8);fitsSystemWindows=true }
  root.addView(Button(this).apply {
   text="إنشاء فاتورة شراء";textSize=13f;minHeight=40;minimumHeight=40
   setOnClickListener { startActivity(Intent(this@PurchasesActivity,PurchaseDraftActivity::class.java)) }
  })
  val scroll=ScrollView(this).apply { isFillViewport=true;addView(purchaseReportView) }
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  Thread {
   try {
    val dao=AppDatabase.get(applicationContext).supplierPurchaseDao()
    val rows=runBlocking { dao.allPurchases() }
    val supplierNames=runBlocking { dao.allSuppliers() }.associate { it.supplier_id to it.name }
    val drafts=runBlocking { dao.allDrafts() }
    val report=buildString {
     append("المشتريات — الأرشيف المستورد\n")
     append("عدد الفواتير: ").append(rows.size).append("\n")
     append("المسودات المحفوظة: ").append(drafts.size).append("\n")
     drafts.forEach { append("مسودة: ").append(it.supplier_id).append(" | ").append(it.currency).append(" | ").append(it.note).append("\n") }
     append("\n")
     if(rows.isEmpty()) append("لا توجد فواتير شراء في Room حالياً. استخدم «استعادة البيانات» من الرئيسية واختر النسخة الاحتياطية، ثم تحقق من نتيجة استيراد أرشيف الموردين.\n\n")
     rows.forEach { row ->
      val supplierName=supplierNames[row.supplier_id]?.takeIf { it.isNotBlank() } ?: row.supplier_id
      val due=row.total_exact.toBigDecimal().subtract(row.paid_at_import_exact.toBigDecimal())
      append(row.invoice_number.ifBlank { "فاتورة شراء" }).append(" | ").append(supplierName)
       .append(" | ").append(row.invoice_date).append("\n")
       .append("الإجمالي: ").append(row.total_exact).append(" ").append(row.currency)
       .append(" | المدفوع: ").append(row.paid_at_import_exact)
       .append(" | المتبقي: ").append(due.toPlainString()).append("\n\n")
     }
     append("الأرشيف للعرض فقط؛ لم تُرحّل حركة مالية أو مخزنية جديدة.")
    }
    runOnUiThread { purchaseReportView.text=report }
   } catch(e:Exception) { runOnUiThread { purchaseReportView.text="تعذر قراءة المشتريات: ${e.message}" } }
  }.start()
 }
}
