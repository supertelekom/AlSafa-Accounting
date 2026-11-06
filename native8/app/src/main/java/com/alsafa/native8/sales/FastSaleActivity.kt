package com.alsafa.native8.sales
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import com.alsafa.native8.stylekit.QuickSalePage
import com.alsafa.native8.stylekit.SafaStyle
import com.alsafa.native8.stylekit.SafaWidgets
import com.alsafa.native8.maintenance.QuickRepairRules
import kotlinx.coroutines.*
import java.math.RoundingMode
import java.util.UUID
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/** Original father-sale form; saves one paid store invoice through the existing Room engine. */
class FastSaleActivity:Activity() {
 private lateinit var page:QuickSalePage
 private val cart=mutableListOf<SaleChoice>()
 private fun baseCurrency()=cart.singleOrNull()?.currency ?: "USD"
 private var results=listOf<SaleChoice>()
 private var generation=0
 private val searchScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var searchJob:Job?=null
 private lateinit var catalog:SalesProductCatalog
 private var changing=false
 private var currency="USD"
 private var saving=false
 private var requestId=UUID.randomUUID().toString()
 override fun onCreate(state:Bundle?) {
  super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  catalog=SalesProductCatalog(applicationContext)
  catalog.prepare()
  requestId=state?.getString("requestId") ?: requestId
  window.statusBarColor=SafaStyle.Palette.cream
  page=QuickSalePage(SafaWidgets(SafaStyle(this)),"الصفا للساعات",object:QuickSalePage.Callbacks {
   override fun remove(index:Int){if(!saving && index in cart.indices){cart.removeAt(index);refreshCart()}}
   override fun back(){if(!saving) finish()}
   override fun search(query:String){searchProducts(query,false)}
   override fun scan(){android.app.AlertDialog.Builder(this@FastSaleActivity).setTitle("قراءة الباركود").setItems(arrayOf("الكاميرا","اختيار صورة")){_,i ->
    runCatching { if(i==0) startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),502) else startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type="image/*" },501) }.onFailure {page.showConversion("تعذر فتح الكاميرا أو الصور",true)}
   }.show()}
   override fun lookup(barcode:String){searchProducts(barcode,true)}
   override fun baseAmountChanged(amount:String){if(!changing) convert()}
   override fun amountChanged(amount:String,currency:String){if(this@FastSaleActivity.currency!=currency){this@FastSaleActivity.currency=currency;convert()}}
   override fun submit(draft:QuickSalePage.Draft){post(draft)}
  });setContentView(page.view)
  state?.getString("cart")?.let { text -> runCatching {
   val rows=org.json.JSONArray(text)
   for(i in 0 until rows.length()) {val j=rows.getJSONObject(i)
    cart+=SaleChoice(j.getString("source"),j.getString("id"),j.optString("variant").takeIf {it.isNotBlank()},j.getString("title"),j.getString("barcode"),j.getString("available"),j.getString("price"),j.getString("cost"),j.getString("currency"))
   }
   refreshCart();page.baseAmount.setText(state.getString("base"));page.money.selectCurrency(state.getString("currency") ?: "USD")
   page.money.amount.setText(state.getString("final"));page.payment.activate(state.getString("method") ?: "نقدي")
  } }

 }
 private fun searchProducts(query:String,barcode:Boolean) {
  val token=++generation
  searchJob?.cancel()
  results=emptyList();page.showProducts(emptyList()){}
  if(query.isBlank())return
  searchJob=searchScope.launch {
   if(!barcode)delay(120)
   try {
    val found=catalog.find(query,barcode)
    ensureActive()
    if(token!=generation || isDestroyed)return@launch
    results=found
    if(barcode && found.size==1)select(found.single()) else {
     page.showProducts(found.mapIndexed { i,p -> i.toString() to "${p.title} — ${p.price} ${p.currency} — المتوفر ${p.available}" }) { select(found[it.toInt()]) }
     if(found.isEmpty())page.showConversion("لا توجد نتائج",true)
    }
   } catch(e:CancellationException){throw e}
   catch(e:Exception){if(token==generation && !isDestroyed)page.showConversion(e.message.orEmpty(),true)}
  }
 }
 override fun onDestroy(){searchScope.cancel();if(::catalog.isInitialized)catalog.close();super.onDestroy()}
 private fun select(p:SaleChoice) {
  if(saving)return
  val count=cart.count {it.source==p.source && it.productId==p.productId && it.variantId==p.variantId}
  if(p.available.toBigDecimal()<java.math.BigDecimal(count+1)){page.showConversion("الكمية المطلوبة غير متوفرة",true);return}
  cart+=p;refreshCart()
 }
 private fun refreshCart() {
  page.showCart(cart.map {"${it.title} — ${it.price} ${it.currency}"})
  runCatching {
   cart.fold(java.math.BigDecimal.ZERO){total,p -> total+p.price.toBigDecimal()*ExchangeRateSettings(this).rate(p.currency,baseCurrency())}
  }.onSuccess {total -> changing=true;page.baseAmount.setText(total.stripTrailingZeros().toPlainString());changing=false;convert()}
   .onFailure {page.showConversion(it.message.orEmpty(),true)}
 }
 private fun convert() {
  if(cart.isEmpty()){page.money.amount.setText("");return}
  runCatching { QuickRepairRules.amount(page.baseAmount.text.toString())*ExchangeRateSettings(this).rate(baseCurrency(),page.money.currency) }
   .onSuccess { amount -> changing=true;page.money.amount.setText(amount.setScale(2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());changing=false;page.showConversion("سعر الأصناف بعملة ${baseCurrency()} — يمكن تعديل السعر النهائي بالعملة المختارة") }
   .onFailure {page.money.amount.setText("");page.showConversion("أدخل سعراً صحيحاً",true)}
 }
 private fun post(form:QuickSalePage.Draft) {
  if(saving) return
  if(cart.isEmpty()){page.showConversion("اختر الساعة أولاً",true);return}
  val items=cart.toList()
  saving=true;page.busy(true)
  Thread {
   val result=runCatching {
    val draft=FastSaleDraft.createMany(items.map {p -> FastSaleDraft.Item(p.source,p.productId,p.variantId,p.title,p.cost.toBigDecimal(),p.price.toBigDecimal(),p.currency)},QuickRepairRules.amount(form.finalAmount),form.currency,form.paymentMethod,ExchangeRateSettings(applicationContext)::rate)
    runBlocking { RoomSalesEngine(applicationContext).save(draft,SalesEngine(applicationContext).calculate(draft),requestId) }
   }
   runOnUiThread {saving=false;page.busy(false);result.fold(onSuccess={id ->
    Toast.makeText(this,"تم حفظ فاتورة المبيع",Toast.LENGTH_LONG).show()
    startActivity(Intent(this,SalePrintActivity::class.java).putExtra("sale_id",id).putExtra("after_save",true).putExtra("share_after_collection",true));finish()
   },onFailure={page.showConversion(it.message.orEmpty(),true)})}
  }.start()
 }
 override fun onActivityResult(request:Int,result:Int,data:Intent?) {
  super.onActivityResult(request,result,data)
  if(request !in listOf(501,502) || result!=RESULT_OK) return
  val input=runCatching {
   if(request==502) InputImage.fromBitmap(data?.extras?.get("data") as? android.graphics.Bitmap ?: error("لم تلتقط صورة"),0)
   else InputImage.fromFilePath(this,data?.data ?: error("لم تحدد صورة"))
  }.getOrElse {page.showConversion(it.message.orEmpty(),true);return}
  val scanner=BarcodeScanning.getClient()
  runCatching { scanner.process(input).addOnSuccessListener { codes ->
   val code=codes.firstOrNull()?.rawValue
   if(code==null) page.showConversion("لم يتم العثور على باركود",true) else {page.barcode.setText(code);searchProducts(code,true)}
  }.addOnFailureListener {page.showConversion(it.message.orEmpty(),true)}.addOnCompleteListener {scanner.close()} }.onFailure{scanner.close();page.showConversion(it.message.orEmpty(),true)}
 }
 override fun onSaveInstanceState(out:Bundle){
  out.putString("requestId",requestId)
  val rows=org.json.JSONArray()
  cart.forEach { p -> rows.put(org.json.JSONObject().put("source",p.source).put("id",p.productId).put("variant",p.variantId ?: "").put("title",p.title).put("barcode",p.barcode).put("available",p.available).put("price",p.price).put("cost",p.cost).put("currency",p.currency)) }
  out.putString("cart",rows.toString())
  if(::page.isInitialized){out.putString("base",page.baseAmount.text.toString());out.putString("final",page.money.amount.text.toString());out.putString("currency",page.money.currency);out.putString("method",page.payment.value)}
  super.onSaveInstanceState(out)
 }
 @Deprecated("Legacy back") override fun onBackPressed(){if(!saving) super.onBackPressed()}
}
