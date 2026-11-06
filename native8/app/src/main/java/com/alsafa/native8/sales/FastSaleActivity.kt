package com.alsafa.native8.sales
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import com.alsafa.native8.stylekit.QuickSalePage
import com.alsafa.native8.stylekit.SafaStyle
import com.alsafa.native8.stylekit.SafaWidgets
import com.alsafa.native8.maintenance.QuickRepairRules
import kotlinx.coroutines.runBlocking
import java.math.RoundingMode
import java.util.UUID
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/** Original father-sale form; saves one paid store invoice through the existing Room engine. */
class FastSaleActivity:Activity() {
 private lateinit var page:QuickSalePage
 private var choice:SaleChoice?=null
 private var results=listOf<SaleChoice>()
 private var generation=0
 private var changing=false
 private var currency="USD"
 private var saving=false
 private var requestId=UUID.randomUUID().toString()
 override fun onCreate(state:Bundle?) {
  super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  requestId=state?.getString("requestId") ?: requestId
  window.statusBarColor=SafaStyle.Palette.cream
  page=QuickSalePage(SafaWidgets(SafaStyle(this)),"الصفا للساعات",object:QuickSalePage.Callbacks {
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
  state?.getString("choice")?.let { text -> runCatching {
   val j=org.json.JSONObject(text)
   select(SaleChoice(j.getString("source"),j.getString("id"),j.optString("variant").takeIf { it.isNotBlank() },j.getString("title"),j.getString("barcode"),j.getString("available"),j.getString("price"),j.getString("cost"),j.getString("currency")))
   page.baseAmount.setText(state.getString("base"));page.money.selectCurrency(state.getString("currency") ?: "USD")
   page.money.amount.setText(state.getString("final"));page.payment.activate(state.getString("method") ?: "نقدي")
  } }
 }
 private fun searchProducts(query:String,barcode:Boolean) {
  val token=++generation
  Thread { val found=runCatching { runBlocking { SalesProductCatalog(applicationContext).find(query) } }
   runOnUiThread {
    if(token!=generation || isDestroyed) return@runOnUiThread
    found.fold(onSuccess={ rows ->
     results=if(barcode) rows.filter { it.barcode==query.trim() } else rows
     if(barcode && results.size==1) select(results.single()) else {
      page.showProducts(results.mapIndexed { i,p -> i.toString() to "${p.title} — ${p.price} ${p.currency} — المتوفر ${p.available}" }) { select(results[it.toInt()]) }
      if(results.isEmpty()) page.showConversion("لا توجد نتائج",true)
     }
    },onFailure={page.showConversion(it.message.orEmpty(),true)})
   }
  }.start()
 }
 private fun select(p:SaleChoice) {
  if(p.available.toBigDecimal()<java.math.BigDecimal.ONE){page.showConversion("المنتج غير متوفر للبيع",true);return}
  choice=p;page.selectProduct(p.productId,"${p.title} — ${p.price} ${p.currency}")
  changing=true;page.baseAmount.setText(p.price);changing=false;convert()
 }
 private fun convert() {
  val p=choice ?: return
  runCatching { QuickRepairRules.amount(page.baseAmount.text.toString())*ExchangeRateSettings(this).rate(p.currency,page.money.currency) }
   .onSuccess { amount -> changing=true;page.money.amount.setText(amount.setScale(2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());changing=false;page.showConversion("يمكن تعديل السعر النهائي بالعملة المختارة") }
   .onFailure {page.money.amount.setText("");page.showConversion("أدخل سعراً صحيحاً",true)}
 }
 private fun post(form:QuickSalePage.Draft) {
  if(saving) return
  val p=choice ?: run {page.showConversion("اختر الساعة أولاً",true);return}
  saving=true;page.busy(true)
  Thread {
   val result=runCatching {
    val draft=FastSaleDraft.create(p.source,p.productId,p.variantId,p.title,p.cost.toBigDecimal(),p.currency,QuickRepairRules.amount(form.finalAmount),form.currency,form.paymentMethod,ExchangeRateSettings(applicationContext)::rate)
    runBlocking { RoomSalesEngine(applicationContext).save(draft,SalesEngine(applicationContext).calculate(draft),requestId) }
   }
   runOnUiThread {saving=false;page.busy(false);result.fold(onSuccess={id ->
    Toast.makeText(this,"تم حفظ فاتورة المبيع",Toast.LENGTH_LONG).show()
    startActivity(Intent(this,SalePrintActivity::class.java).putExtra("sale_id",id));finish()
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
  choice?.let { p -> out.putString("choice",org.json.JSONObject().put("source",p.source).put("id",p.productId).put("variant",p.variantId ?: "").put("title",p.title).put("barcode",p.barcode).put("available",p.available).put("price",p.price).put("cost",p.cost).put("currency",p.currency).toString()) }
  if(::page.isInitialized){out.putString("base",page.baseAmount.text.toString());out.putString("final",page.money.amount.text.toString());out.putString("currency",page.money.currency);out.putString("method",page.payment.value)}
  super.onSaveInstanceState(out)
 }
 @Deprecated("Legacy back") override fun onBackPressed(){if(!saving) super.onBackPressed()}
}
