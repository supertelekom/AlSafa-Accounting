package com.alsafa.native8.sales

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.room.withTransaction
import com.alsafa.native8.ProductEditorActivity
import com.alsafa.native8.data.operational.RoomInventoryRepository
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.util.UUID

/** Purchase drafts are non-posting: never modify stock, cashboxes or supplier balances here. */
class PurchaseDraftActivity:Activity() {
 private data class Pick(val source:String,val id:String,val name:String)
 private val lines=mutableListOf<Pair<Pick,Pair<String,String>>>()
 private var refreshProducts:(()->Unit)?=null
 private var pendingNewProductName:String?=null
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==7401 && resultCode==RESULT_OK) {
   pendingNewProductName=data?.getStringExtra("created_product_name")
   refreshProducts?.invoke()
  }
 }
 override fun onResume() { super.onResume();refreshProducts?.invoke() }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(8,8,8,8) }
  fun title(value:String)=TextView(this).apply { text=value;textSize=14f }
  root.addView(title("إنشاء فاتورة شراء — مسودة"))
  root.addView(title("اختيار المورد"))
  val supplier=Spinner(this)
  root.addView(TextView(this).apply { text="اختر المورد من القائمة";textSize=14f })
  root.addView(supplier)
  root.addView(title("عملة الفاتورة"))
  val currency=Spinner(this).apply { adapter=ArrayAdapter(this@PurchaseDraftActivity,android.R.layout.simple_spinner_dropdown_item,listOf("USD","TRY","SYP")) }
  root.addView(currency)
  root.addView(title("اختر منتجاً لإضافته إلى الفاتورة"))
  val productSearch=EditText(this).apply { hint="بحث عن منتج";textSize=14f }
  root.addView(productSearch)
  val product=Spinner(this)
  root.addView(product)
  val qty=EditText(this).apply { hint="الكمية";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;textSize=14f }
  val cost=EditText(this).apply { hint="تكلفة الوحدة";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;textSize=14f }
  root.addView(qty);root.addView(cost)
  val lineSummary=TextView(this).apply { text="لا توجد أصناف مضافة";textSize=13f }
  val add=Button(this).apply { text="إضافة الصنف إلى الفاتورة";isEnabled=false;textSize=13f }
  root.addView(add)
  root.addView(Button(this).apply { text="إنشاء منتج جديد";textSize=13f;setOnClickListener { startActivityForResult(Intent(this@PurchaseDraftActivity,ProductEditorActivity::class.java),7401) } })
  root.addView(lineSummary)
  val totals=TextView(this).apply { text="إجمالي الفاتورة: 0";textSize=15f }
  root.addView(totals)
  val note=EditText(this).apply { hint="ملاحظات الفاتورة";textSize=14f }
  root.addView(note)
  val save=Button(this).apply { text="حفظ مسودة الفاتورة";textSize=13f;isEnabled=false }
  root.addView(save)
  root.addView(title("طريقة الدفع"))
  val paymentType=Spinner(this).apply { adapter=ArrayAdapter(this@PurchaseDraftActivity,android.R.layout.simple_spinner_dropdown_item,listOf("آجل","نقدي","دفعة جزئية")) }
  root.addView(paymentType)
  val paid=EditText(this).apply { hint="المبلغ المدفوع";inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;visibility=View.GONE }
  root.addView(paid)
  val vault=Spinner(this).apply { adapter=ArrayAdapter(this@PurchaseDraftActivity,android.R.layout.simple_spinner_dropdown_item,listOf("الدرج","الصندوق الخارجي","شام كاش","البنك"));visibility=View.GONE }
  root.addView(vault)
  paymentType.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){
    paid.visibility=if(position==2) View.VISIBLE else View.GONE
    vault.visibility=if(position==0) View.GONE else View.VISIBLE
   }
  }
  var requestPost=false
  val post=Button(this).apply { text="اعتماد فاتورة الشراء (تجريبي)";textSize=12f;isEnabled=false }
  root.addView(post)
  post.setOnClickListener { requestPost=true;save.performClick() }
  val status=TextView(this).apply { text="جاري تحميل الموردين والمنتجات...";textSize=13f }
  root.addView(status)
  root.addView(title("المسودة لا تغيّر المخزون أو دين المورد أو الصناديق."))
  setContentView(ScrollView(this).apply { addView(root) })
  var products=emptyList<Pick>()
  var visibleProducts=emptyList<Pick>()
  val db=AppDatabase.get(applicationContext)
  val inventory=RoomInventoryRepository(db)
  refreshProducts={
   Thread {
    try {
     val rows=runBlocking {
      val source=inventory.latestSource()
      val imported=if(source==null) emptyList() else db.operationalInventoryWriteDao().productPage(source,500,0).map {
       Pick(source,it.legacy_id,org.json.JSONObject(it.original_json).optString("name").ifBlank { it.legacy_id })
      }
      val local=db.operationalInventoryWriteDao().localProducts().map {
       Pick("local",it.local_id,org.json.JSONObject(it.product_json).optString("name").ifBlank { it.local_id })
      }
      imported+local
     }
     runOnUiThread {
      products=rows
      visibleProducts=rows.filter { it.name.contains(productSearch.text.toString(),true) }
      product.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,visibleProducts.map { it.name })
      add.isEnabled=visibleProducts.isNotEmpty()
      pendingNewProductName?.let { created ->
       productSearch.setText(created)
       val index=visibleProducts.indexOfFirst { it.name==created && it.source=="local" }
       if(index>=0) {
        product.setSelection(index)
        status.text="تم تحديد المنتج الجديد. أدخل الكمية والتكلفة ثم أضفه."
       } else status.text="تم إنشاء المنتج؛ ابحث عن اسمه واختره."
       pendingNewProductName=null
      }
      if(rows.isEmpty()) status.text="لا توجد منتجات؛ أنشئ منتجاً جديداً أولاً."
     }
    } catch(e:Exception) { runOnUiThread { status.text="تعذر قراءة المنتجات: ${e.message}" } }
   }.start()
  }
  add.setOnClickListener {
   val selected=visibleProducts.getOrNull(product.selectedItemPosition) ?: return@setOnClickListener
   val q=qty.text.toString().toBigDecimalOrNull()
   val price=cost.text.toString().toBigDecimalOrNull()
   if(q==null || price==null || q<=BigDecimal.ZERO || price<BigDecimal.ZERO) {
    status.text="أدخل كمية موجبة وتكلفة صحيحة.";return@setOnClickListener
   }
   lines+=selected to (q.stripTrailingZeros().toPlainString() to price.stripTrailingZeros().toPlainString())
   lineSummary.text=lines.mapIndexed { index,item -> "${index+1}. ${item.first.name} | الكمية ${item.second.first} | التكلفة ${item.second.second}" }.joinToString("\n")
   val sum=lines.fold(BigDecimal.ZERO) { acc,item -> acc+item.second.first.toBigDecimal()*item.second.second.toBigDecimal() }
   totals.text="إجمالي الفاتورة: "+sum.stripTrailingZeros().toPlainString()+" "+currency.selectedItem.toString()
   qty.text.clear();cost.text.clear()
   status.text="تمت إضافة الصنف إلى المسودة."
  }
  Thread {
   try {
    val suppliers=runBlocking { db.supplierPurchaseDao().allSuppliers() }
    runOnUiThread {
     supplier.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,suppliers.map { it.name.ifBlank { it.supplier_id } })
     save.isEnabled=suppliers.isNotEmpty()
     post.isEnabled=suppliers.isNotEmpty()
     if(suppliers.isEmpty()) status.text="لا يوجد موردون مستوردون."
     save.setOnClickListener {
      val selected=suppliers.getOrNull(supplier.selectedItemPosition) ?: return@setOnClickListener
      if(lines.isEmpty()) { status.text="أضف صنفاً واحداً على الأقل قبل الحفظ.";return@setOnClickListener }
      val posting=requestPost
      val totalAmount=lines.fold(BigDecimal.ZERO) { sum,item -> sum+item.second.first.toBigDecimal()*item.second.second.toBigDecimal() }
      val paymentAmount=when(paymentType.selectedItemPosition) {
       0 -> "0"
       1 -> totalAmount.toPlainString()
       else -> paid.text.toString()
      }
      val selectedVault=if(paymentType.selectedItemPosition==0) null else when(vault.selectedItemPosition) {
       0 -> "drawer"
       1 -> "external"
       2 -> "shamcash"
       else -> "bank"
      }
      if(posting && (paymentAmount.toBigDecimalOrNull()==null || paymentAmount.toBigDecimal()<BigDecimal.ZERO || paymentAmount.toBigDecimal()>totalAmount)) {
       status.text="المبلغ المدفوع غير صالح";return@setOnClickListener
      }
      requestPost=false
      save.isEnabled=false
      post.isEnabled=false
      val id=UUID.randomUUID().toString()
      val draft=PurchaseDraftEntity(id,selected.supplier_id,currency.selectedItem.toString(),note.text.toString(),System.currentTimeMillis())
      val items=lines.map { (pick,amount) -> PurchaseDraftLineEntity(UUID.randomUUID().toString(),id,pick.source,pick.id,pick.name,amount.first,amount.second) }
      Thread {
       try {
        runBlocking { db.withTransaction {
         db.supplierPurchaseDao().insertDraft(draft)
         db.supplierPurchaseDao().insertDraftLines(items)
         if(posting) PurchasePostingService(db).post(id,paymentAmount,selectedVault)
        } }
        runOnUiThread { status.text=if(posting) "تم اعتماد الفاتورة وتحديث المخزون والدفع." else "حُفظت المسودة مع ${items.size} أصناف.";finish() }
       } catch(e:Exception) { runOnUiThread { status.text="فشل الحفظ أو الاعتماد: ${e.message}";save.isEnabled=true;post.isEnabled=true } }
      }.start()
     }
    }
   } catch(e:Exception) { runOnUiThread { status.text="تعذر قراءة الموردين: ${e.message}" } }
  }.start()
  productSearch.addTextChangedListener(object:android.text.TextWatcher {
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
    visibleProducts=products.filter { it.name.contains(s?.toString().orEmpty(),ignoreCase=true) }
    product.adapter=ArrayAdapter(this@PurchaseDraftActivity,android.R.layout.simple_spinner_dropdown_item,visibleProducts.map { it.name })
    add.isEnabled=visibleProducts.isNotEmpty()
   }
   override fun afterTextChanged(s:android.text.Editable?){}
  })
  refreshProducts?.invoke()
 }
}
