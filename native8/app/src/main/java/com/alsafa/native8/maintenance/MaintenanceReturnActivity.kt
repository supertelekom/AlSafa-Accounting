package com.alsafa.native8.maintenance

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.alsafa.native8.sales.AppDatabase
import kotlinx.coroutines.*
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** The original repair-return form; all controls fit inside the tablet's scrolling viewport. */
class MaintenanceReturnActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private val db by lazy { AppDatabase.get(this) }
 private val repository by lazy { MaintenanceReturnRepository(db,this) }
 private lateinit var content:LinearLayout
 private lateinit var status:TextView
 private lateinit var font:Typeface
 private var repairId=""
 private var busy=false
 private val ui by lazy{com.alsafa.native8.stylekit.SafaWidgets(com.alsafa.native8.stylekit.SafaStyle(this))}
 private fun dp(n:Int)=(n*resources.displayMetrics.density+0.5f).toInt()
 private fun text(value:String)=TextView(this).apply { text=value;textSize=18f;typeface=font;content.addView(this);MaintenanceFormStyle.apply(this,ui) }
 private fun field(label:String,value:String,numeric:Boolean=false):EditText {
  text(label)
  return EditText(this).apply {
   typeface=font;setText(value);minHeight=dp(58);setSingleLine(true)
   if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
   content.addView(this,LinearLayout.LayoutParams(-1,-2));MaintenanceFormStyle.apply(this,ui)
  }
 }
 private fun choice(label:String,options:List<String>):Spinner {
  text(label)
  return Spinner(this).apply {
   minimumHeight=dp(58)
   adapter=object:ArrayAdapter<String>(this@MaintenanceReturnActivity,android.R.layout.simple_spinner_dropdown_item,options) {
    override fun getView(position:Int,convertView:View?,parent:ViewGroup):View=super.getView(position,convertView,parent).also { (it as? TextView)?.typeface=font }
    override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup):View=super.getDropDownView(position,convertView,parent).also { (it as? TextView)?.typeface=font }
   }
   content.addView(this,LinearLayout.LayoutParams(-1,-2));MaintenanceFormStyle.apply(this,ui)
  }
 }
 private fun button(label:String,action:()->Unit)=Button(this).apply {
  text=label;typeface=font;minHeight=dp(72);content.addView(this,LinearLayout.LayoutParams(-1,-2));MaintenanceFormStyle.apply(this,ui)
  setOnClickListener { if(!busy) action() }
 }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);window.statusBarColor=com.alsafa.native8.stylekit.SafaStyle.Palette.cream
  repairId=intent.getStringExtra("repair_id") ?: run { finish();return }
  font=Typeface.createFromAsset(assets,"fonts/Tajawal-Medium.ttf")
  title="مرتجع / استبدال صيانة"
  content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(com.alsafa.native8.stylekit.SafaStyle.Palette.cream);layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(16),dp(16),dp(16),dp(20)) }
  val scroll=ScrollView(this).apply {
   isFillViewport=true;clipToPadding=false;isHorizontalScrollBarEnabled=false
   addView(content,FrameLayout.LayoutParams(-1,-2))
   setOnApplyWindowInsetsListener { view,insets ->
    if(android.os.Build.VERSION.SDK_INT>=30) {
     val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
     view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
    } else view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
    insets
   }
  }
  window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
  window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  setContentView(scroll);scroll.requestApplyInsets();load()
 }
 private fun load(message:String="") {
  busy=true
  scope.launch {
   try {
    val data=withContext(Dispatchers.IO) { (db.maintenanceDao().repair(repairId) ?: error("الصيانة غير موجودة")) to db.maintenanceDao().parts() }
    require(data.first.source_sha256=="native8" && data.first.status=="تم التسليم") { "هذه العملية للصيانة الجديدة المسلّمة فقط" }
    render(JSONObject(data.first.original_json),data.second,message)
   } catch(e:Exception) { content.removeAllViews();text(e.message ?: "تعذر تحميل الصيانة") }
   finally { busy=false }
  }
 }
 private fun receipt(r:JSONObject):String {
  val unit=r.getString("currency")
  fun value(key:String)=r.optString(key,"0").toBigDecimal().setScale(2,java.math.RoundingMode.HALF_UP).toPlainString()+" "+unit
  return if(r.has("kind")) listOf("رقم العملية: ${r.getString("id")}","التاريخ: ${r.getString("date")} ${r.getString("time")}",
   "الإجمالي الحالي: ${value("currentTotalBefore")}","المقبوض الفعلي: ${value("paidBefore")}","الدين قبل العملية: ${value("debtBefore")}",
   "قيمة المسترجع: ${value("returnedValue")}","قيمة البديل/إعادة العمل: ${value("replacementValue")}",
   "الخصم من الدين: ${value("debtReduction")}","المبلغ الواجب رده نقداً: ${value("refundAmount")}",
   "فرق إضافي مستحق: ${value("extraDue")}","المقبوض الآن: ${value("extraCollected")}",
   "الإجمالي بعد العملية: ${value("newTotalAfter")}","المتبقي: ${value("remainingAfter")}",
   "إعادة القطعة للمخزون: ${if(r.optBoolean("returnedPartRestocked")) "نعم" else "لا"}",
   "الخزنة: ${com.alsafa.native8.sales.UnifiedCashboxBalance.displayName(r.getString("vault"))}","التفاصيل: ${r.getString("notes")}").joinToString("\n")
  else "تكملة الدفع: ${value("amount")}\nالتاريخ: ${r.getString("date")} ${r.getString("time")}\nالمتبقي: ${value("remainingAfter")}\nالخزنة: ${com.alsafa.native8.sales.UnifiedCashboxBalance.displayName(r.getString("vault"))}"
 }
 private fun render(raw:JSONObject,parts:List<MaintenancePartEntity>,message:String) {
  content.removeAllViews()
  text("مرتجع / استبدال صيانة — فاتورة ${raw.get("invoiceNo")}")
  val unit=raw.getString("currency")
  text("العملة: $unit — المتبقي: ${MaintenanceReturnRules.due(raw).stripTrailingZeros().toPlainString()}")
  status=text(message)
  val kind=choice("نوع العملية",listOf("استرجاع صيانة","استبدال / إعادة عمل"))
  val previous=MaintenanceReturnRules.rows(raw,"nativeReturns")
  val available=MaintenanceReturnRules.rows(raw,"usedParts").mapIndexedNotNull { i,p ->
   val remaining=MaintenanceReturnRules.n(p,"qty")-previous.filter { it.optInt("repairPartIndex",-1)==i }.fold(BigDecimal.ZERO) { sum,r -> sum+MaintenanceReturnRules.n(r,"returnedPartQty") }
   if(remaining.signum()>0) Triple(i,p,remaining) else null
  }
  val original=choice("القطعة المسترجعة",listOf("بدون قطعة — تسوية أجرة/مبلغ فقط")+available.map { "${it.second.optString("name")} · متاح ${it.third}" })
  val qty=field("كمية القطعة المسترجعة","1",true)
  val credit=field("قيمة الجزء المسترجع ($unit)",MaintenanceReturnRules.effective(raw).toPlainString(),true)
  val restock=CheckBox(this).apply { text="إعادة القطعة المسترجعة إلى المخزون الصالح للبيع";typeface=font;isChecked=false;content.addView(this) }
  val restockHelp=text("اتركه غير محدد إذا كانت البطارية/القطعة تالفة.")
  val replacements=parts.filter { it.stock.toBigDecimalOrNull()?.signum()==1 }
  val replacement=choice("القطعة البديلة",listOf("بدون قطعة — إعادة عمل/قيمة يدوية")+replacements.map { "${it.name} · متوفر ${it.stock} · ${it.sale_price} ${it.currency}" })
  val replacementQty=field("كمية البديل","1",true)
  val replacementAmount=field("قيمة العمل/البديل الجديد ($unit)","0",true)
  val extra=field("المقبوض الآن من فرق الاستبدال ($unit)","0",true)
  fun visibility(control:View,show:Boolean) {
   control.visibility=if(show) View.VISIBLE else View.GONE
   val index=content.indexOfChild(control)
   if(index>0) content.getChildAt(index-1).visibility=control.visibility
  }
  fun updateFields() {
   val withPart=original.selectedItemPosition>0
   val exchange=kind.selectedItemPosition==1
   visibility(qty,withPart);visibility(credit,!withPart)
   restock.visibility=if(withPart) View.VISIBLE else View.GONE;restockHelp.visibility=restock.visibility
   visibility(replacement,exchange);visibility(replacementQty,exchange && replacement.selectedItemPosition>0)
   visibility(replacementAmount,exchange && replacement.selectedItemPosition==0);visibility(extra,exchange)
  }
  val listener=object:AdapterView.OnItemSelectedListener {
   override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { updateFields() }
   override fun onNothingSelected(parent:AdapterView<*>?) {}
  }
  listOf(kind,original,replacement).forEach { it.onItemSelectedListener=listener };updateFields()
  val vault=choice("الخزنة",listOf("الدرج","الصندوق الخارجي","شام كاش","البنك"))
  val zone=ZoneId.of("Asia/Damascus")
  val date=field("التاريخ",LocalDate.now(zone).toString()).also{if(com.alsafa.native8.stylekit.SafaDateTime.fullMode(this))com.alsafa.native8.stylekit.SafaDateTime.bindDate(it)}
  val time=field("الوقت",LocalTime.now(zone).withSecond(0).withNano(0).toString()).also{if(com.alsafa.native8.stylekit.SafaDateTime.fullMode(this))com.alsafa.native8.stylekit.SafaDateTime.bindTime(it)}
  val notes=field("التفاصيل *","")
  fun number(v:EditText)=v.text.toString().trim().toBigDecimalOrNull() ?: error("أدخل مبلغاً أو كمية صحيحة")
  button("تنفيذ مرتجع / استبدال صيانة") {
   try {
    val q=MaintenanceReturnRules.Request(UUID.randomUUID().toString(),if(kind.selectedItemPosition==1) "exchange" else "return",
     available.getOrNull(original.selectedItemPosition-1)?.first ?: -1,number(qty),number(credit),restock.isChecked,
     replacements.getOrNull(replacement.selectedItemPosition-1)?.id ?: "",number(replacementQty),number(replacementAmount),number(extra),
     vault.selectedItem.toString(),date.text.toString().trim(),time.text.toString().trim(),notes.text.toString().trim())
    confirm({repository.preview(repairId,q)}) { expected,approved -> repository.save(repairId,q,expected,approved) }
   } catch(e:Exception) { status.text=e.message }
  }
  if(MaintenanceReturnRules.due(raw)>BigDecimal.ZERO) {
   val payment=field("تكملة الدفع ($unit)",MaintenanceReturnRules.due(raw).toPlainString(),true)
   button("تكملة الدفع") {
    try {
     val amount=number(payment);val box=vault.selectedItem.toString();val day=date.text.toString().trim();val clock=time.text.toString().trim();val memo=notes.text.toString().trim()
     val id=UUID.randomUUID().toString()
     confirm({repository.previewCollection(repairId,id,amount,box,day,clock,memo)}) { expected,approved -> repository.saveCollection(repairId,expected,approved) }
    } catch(e:Exception) { status.text=e.message }
   }
  }
  if(previous.isNotEmpty()) { text("المرتجعات والاستبدالات");previous.forEach { text(receipt(it)) } }
  MaintenanceReturnRules.rows(raw,"nativePayments").forEach { text(receipt(it)) }
 }
 private fun confirm(preview:suspend ()->Pair<String,JSONObject>,save:suspend (String,String)->JSONObject) {
  busy=true
  scope.launch {
   try {
    val (expected,record)=withContext(Dispatchers.IO) { preview() }
    if(isFinishing || isDestroyed) return@launch
    AlertDialog.Builder(this@MaintenanceReturnActivity).setTitle("تأكيد العملية").setMessage(receipt(record))
     .setNegativeButton("إلغاء") { _,_ -> busy=false }
     .setOnCancelListener { busy=false }
     .setPositiveButton("موافق") { _,_ ->
      scope.launch {
       try {
        val saved=withContext(Dispatchers.IO) { save(expected,record.toString()) }
        setResult(RESULT_OK,Intent().putExtra("repair_id",repairId))
        load("تم حفظ العملية بنجاح\n"+receipt(saved))
       } catch(e:Exception) { busy=false;status.text=e.message ?: "لم تُحفظ العملية" }
      }
     }.show().also { dialog ->
      dialog.findViewById<TextView>(android.R.id.message)?.typeface=font
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).typeface=font
      dialog.getButton(AlertDialog.BUTTON_NEGATIVE).typeface=font
      dialog.window?.decorView?.layoutDirection=View.LAYOUT_DIRECTION_RTL
     }
   } catch(e:Exception) { busy=false;status.text=e.message ?: "تعذرت المعاينة" }
  }
 }
 override fun onDestroy() { scope.cancel();super.onDestroy() }
}
