package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** Creates a persistent draft only. Posting is deliberately unavailable. */
class PurchaseDraftActivity:Activity() {
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(16,16,16,16)
  }
  root.addView(TextView(this).apply { text="إنشاء فاتورة شراء — مسودة";textSize=19f })
  val supplier=Spinner(this)
  val currency=Spinner(this)
  val note=EditText(this).apply { hint="ملاحظات الفاتورة";textSize=15f }
  val status=TextView(this).apply { text="جاري تحميل الموردين...";textSize=15f }
  val save=Button(this).apply { text="حفظ المسودة";isEnabled=false }
  root.addView(supplier)
  currency.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("USD","TRY","SYP"))
  root.addView(currency)
  root.addView(note)
  root.addView(save)
  root.addView(status)
  root.addView(TextView(this).apply {
   text="هذه مسودة فقط؛ لا تغير المخزون أو الصناديق أو دين المورد."
   textSize=14f
  })
  setContentView(ScrollView(this).apply { addView(root) })
  Thread {
   try {
    val dao=AppDatabase.get(applicationContext).supplierPurchaseDao()
    val suppliers=runBlocking { dao.allSuppliers() }
    runOnUiThread {
     if(suppliers.isEmpty()) { status.text="لا يوجد موردون؛ استعد بيانات الموردين أولاً.";return@runOnUiThread }
     supplier.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
      suppliers.map { it.name.ifBlank { it.supplier_id } })
     save.isEnabled=true
     status.text="اختر المورد والعملة ثم احفظ المسودة."
     save.setOnClickListener {
      save.isEnabled=false
      val selected=suppliers[supplier.selectedItemPosition]
      val row=PurchaseDraftEntity(UUID.randomUUID().toString(),selected.supplier_id,
       currency.selectedItem.toString(),note.text.toString(),System.currentTimeMillis())
      Thread {
       try {
        runBlocking { dao.insertDraft(row) }
        runOnUiThread { status.text="تم حفظ المسودة: ${row.draft_id}";finish() }
       } catch(e:Exception) {
        runOnUiThread { status.text="تعذر حفظ المسودة: ${e.message}";save.isEnabled=true }
       }
      }.start()
     }
    }
   } catch(e:Exception) { runOnUiThread { status.text="تعذر تحميل الموردين: ${e.message}" } }
  }.start()
 }
}
