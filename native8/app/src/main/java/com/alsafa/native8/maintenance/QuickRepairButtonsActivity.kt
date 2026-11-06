package com.alsafa.native8.maintenance
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import com.alsafa.native8.stylekit.*
import com.alsafa.native8.sales.AppDatabase
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.util.UUID

/** Legacy quick-repair-button form: fixed/manual price, part, quantity and ordering. */
class QuickRepairButtonsActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var ui:SafaWidgets
 private var rows=listOf<JSONObject>();private var parts=listOf<MaintenancePartEntity>()
 private val prefs by lazy {getSharedPreferences("quick_repair_buttons",MODE_PRIVATE)}
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);ui=SafaWidgets(SafaStyle(this));window.statusBarColor=SafaStyle.Palette.cream;load()}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
 private fun load(){scope.launch{
  try {
   withContext(Dispatchers.IO){rows=QuickRepairRules.buttons(QuickRepairRepository(applicationContext).settings());parts=AppDatabase.get(applicationContext).maintenanceDao().parts()};render()
  }catch(e:Exception){val host=SafaPageHost(ui,"الصفا للساعات");ui.heading(host.body,"أزرار الصيانة السريعة",e.message ?: "تعذر التحميل");ui.add(host.body,ui.button("إعادة المحاولة"){load()});setContentView(host.view)}
 }}
 private fun save(next:List<JSONObject>,done:()->Unit) {scope.launch{
  try {withContext(Dispatchers.IO){check(prefs.edit().putString("rows",JSONArray(next).toString()).commit()){ "تعذر حفظ الأزرار" }};rows=next;done();render()}
  catch(e:CancellationException){throw e}
  catch(e:Exception){done();ui.confirm("تعذر الحفظ",e.message ?: "حاول مجدداً"){}.show()}
 }}
 private fun render() {
  val host=SafaPageHost(ui,"الصفا للساعات");ui.heading(host.body,"أزرار الصيانة السريعة","إنشاء الخدمات وربطها بقطع المستودع")
  ui.add(host.body,ui.button("＋ زر جديد",true){edit(null)})
  rows.sortedBy{it.optInt("order")}.forEach { row ->
   val card=ui.card();val part=parts.firstOrNull{it.id==row.optString("partId")}
   ui.add(card,ui.text("${row.optString("icon","🔧")} ${row.getString("label")}",18f,bold=true))
   ui.add(card,ui.text(if(row.optString("priceMode")=="manual")"المبلغ: يدوي عند الاستخدام" else "${row.optString("price")} TRY"))
   ui.add(card,ui.text(if(part==null)"خدمة فقط — بدون قطعة" else "${part.name} × ${row.optInt("qty",1)} — التكلفة من المخزون"))
   ui.add(card,ui.button("تعديل"){edit(row)})
   if(row.getString("id")!="manual-problem")ui.add(card,ui.button("حذف"){ui.confirm("حذف الزر","حذف ${row.getString("label")}؟ تبقى القطعة والفواتير محفوظة."){save(rows.filter{it.getString("id")!=row.getString("id")}){}}.show()})
   ui.add(host.body,card)
  }
  ui.add(host.body,ui.button("رجوع"){finish()});setContentView(host.view)
 }
 private fun edit(old:JSONObject?) {
  val host=SafaPageHost(ui,"الصفا للساعات");val body=host.body;ui.heading(body,if(old==null)"إضافة زر صيانة سريعة" else "تعديل زر الصيانة","")
  fun field(label:String,value:String,numeric:Boolean=false):EditText {ui.label(body,label);return ui.input(numeric=numeric).apply{setText(value);ui.add(body,this)}}
  val label=field("اسم الزر الظاهر *",old?.optString("label").orEmpty())
  val icon=field("رمز الزر",old?.optString("icon") ?: "🔧")
  val mode=ui.choice(listOf("fixed" to "سعر ثابت","manual" to "مبلغ يدوي"),old?.optString("priceMode") ?: "fixed"){}
  ui.add(body,mode.view)
  val price=field("سعر المبيع بالتركية",old?.optString("price") ?: "0",true)
  val qty=field("الكمية التي تخصم من القطعة",old?.optString("qty") ?: "1",true)
  val order=field("ترتيب الزر",old?.optString("order") ?: rows.size.toString(),true)
  var partId=old?.optString("partId").orEmpty()
  val picked=ui.text("");fun showPart(){picked.text=parts.firstOrNull{it.id==partId}?.name ?: "خدمة فقط — بدون قطعة"};showPart();ui.add(body,picked)
  val search=field("بحث القطعة بالاسم أو الباركود","")
  val results=ui.column();ui.add(body,results)
  ui.listen(search){q ->results.removeAllViews();if(q.isNotBlank())parts.filter{it.name.contains(q,true)||it.barcode.contains(q,true)}.take(20).forEach { p->ui.add(results,ui.button("${p.name} — ${p.stock}"){partId=p.id;showPart();search.setText("")})}}
  ui.add(body,ui.button("خدمة فقط — إلغاء ربط القطعة"){partId="";showPart()})
  val error=ui.text("",14f,SafaStyle.Palette.danger);ui.add(body,error)
  val button=ui.button("حفظ الزر",true){};ui.add(body,button)
  button.setOnClickListener{
   try {
    val value=QuickRepairButtonRules.validate(JSONObject(old?.toString() ?: "{}").put("id",old?.getString("id") ?: "quick-"+UUID.randomUUID()).put("label",label.text.toString()).put("icon",icon.text.toString()).put("priceMode",mode.value).put("price",price.text.toString().ifBlank{"0"}).put("qty",qty.text.toString()).put("order",order.text.toString()).put("partId",partId))
    require(partId.isBlank() || parts.any{it.id==partId}){"القطعة المرتبطة غير موجودة"}
    val next=rows.filter{it.getString("id")!=value.getString("id")}+value
    button.isEnabled=false;save(next){button.isEnabled=true}
   }catch(e:Exception){error.text=e.message ?: "راجع بيانات الزر"}
  }
  ui.add(body,ui.button("إلغاء"){render()});setContentView(host.view)
 }
}
