package com.alsafa.native8.stylekit

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Main action order and wording from legacy renderAdvancedHome/renderMore. */
class FullHomePage(private val ui:SafaWidgets,private val onRoute:(String)->Unit) {
 val host=SafaPageHost(ui,"الصفا للساعات")
 val view:View get()=host.view
 private val recent=ui.column()
 private val balances=ui.column()
 private val alerts=ui.column()
 private val shortcuts=ui.column()
 private val prefs=ui.context.getSharedPreferences("full_home",android.content.Context.MODE_PRIVATE)
 private var summary:com.alsafa.native8.home.HomeReadRepository.Summary?=null
 private val shortcutOptions=listOf("products" to "المنتجات والمخزون","parts" to "قطع الصيانة","sales" to "الفواتير السابقة","repairs" to "كل الصيانة","vaults" to "الخزائن والأرصدة","closing" to "إقفال اليوم","partners" to "الشركاء والسحوبات","reports" to "التقارير","rates" to "أسعار الصرف")
 init {
  ui.heading(host.body,"الوضع الكامل","كل عمليات المتجر والإدارة.")
  ui.add(host.body,ui.button("الوضع المبسط"){onRoute("simple")})
  ui.add(host.body,balances,8);ui.add(balances,ui.text("جارٍ تحميل ملخص اليوم…"))
  ui.add(host.body,alerts,8)
  ui.add(host.body,shortcuts,10);renderShortcuts()
  val actions=SafaGrid(ui.context,{if(it>=900)5 else if(it>=600)4 else 2},12)
  listOf(
   arrayOf("today","☀","اليوم","ملخص وإقفال اليوم"),
   arrayOf("sale","🛒","مبيع","شاشة المبيع الكاملة"),
   arrayOf("intake","🔧","استلام صيانة","طلب صيانة كامل"),
   arrayOf("delivery","✓","تسليم صيانة","الساعات الجاهزة للتسليم"),
   arrayOf("debt","💵","قبض دين","اختيار الفاتورة والتحصيل"),
   arrayOf("expense","📤","مصروف","تسجيل مصروف"),
   arrayOf("search","⌕","بحث","عميل، هاتف، طلب أو منتج")
  ).forEach { a -> actions.addView(tile(a[0],a[1],a[2],a[3])) }
  ui.add(host.body,actions,24)
  ui.heading(host.body,"آخر العمليات","أحدث فواتير المبيع وطلبات الصيانة")
  ui.add(host.body,recent,20)
  showRecent(emptyList(),"جارٍ تحميل آخر العمليات…")
  ui.heading(host.body,"الإدارة","المخزون والحسابات وسجلات المتجر")
  val manage=SafaGrid(ui.context,{if(it>=900)5 else if(it>=600)4 else 2},12)
  listOf(
   arrayOf("products","⌚","المنتجات والمخزون","الأسعار والكميات"),
   arrayOf("parts","🔋","قطع الصيانة","البطاريات والأحزمة وقطع الغيار"),
   arrayOf("purchases","📦","المشتريات والموردون","الفواتير وحسابات الموردين"),
   arrayOf("sales","🧾","الفواتير السابقة","التفاصيل والطباعة والتحصيل"),
   arrayOf("returns","↩","فواتير المرتجع","الاسترجاع والاستبدال"),
   arrayOf("repairs","🔧","كل الصيانة","الحالات والتسليم والمتابعة"),
   arrayOf("vaults","🏦","الخزائن والأرصدة","الدرج والصناديق وشام كاش"),
   arrayOf("closing","☾","إقفال اليوم","عدّ الدرج وتجهيز الغد"),
   arrayOf("expense","📤","المصاريف","المصاريف المسجلة"),
   arrayOf("reports","📊","التقارير","الأرباح والفترات"),
   arrayOf("partners","👥","الشركاء والسحوبات","الأرباح والمستحقات والسحوبات"),
   arrayOf("rates","⇄","أسعار الصرف","الدولار والتركي والسوري"),
   arrayOf("backup","▣","النسخ الاحتياطي","إنشاء نسخة واستعادة وتصفير"),
   arrayOf("import","↓","استعادة البيانات","استيراد النسخة القديمة")
  ).forEach { a -> manage.addView(tile(a[0],a[1],a[2],a[3])) }
  ui.add(host.body,manage,16)
  ui.add(host.body,ui.button("رجوع"){onRoute("back")})
 }
 private fun tile(route:String,icon:String,title:String,subtitle:String):View {
  val card=ui.card(10).apply {
   minimumHeight=ui.style.dp(90);gravity=Gravity.CENTER_VERTICAL
   background=ui.style.gradient("#fffdf9","#f3e8d6",18)
   foreground=RippleDrawable(ColorStateList.valueOf(Color.parseColor("#26a97929")),null,ui.style.box(Color.WHITE,18))
   clipToOutline=true;isClickable=true;isFocusable=true;contentDescription="$title، $subtitle"
   setOnClickListener{onRoute(route)}
  }
  ui.add(card,ui.text(icon,19f,SafaStyle.Palette.gold,true),6)
  ui.add(card,ui.text(title,15f,SafaStyle.Palette.ink,true),4)
  ui.add(card,ui.text(subtitle,10f,SafaStyle.Palette.muted),0)
  return card
 }
 fun restorePreferences(settings:org.json.JSONObject) {
  if(prefs.getBoolean("initialized",false))return
  val routes=mapOf("products" to "products","maintenance-parts" to "parts","sales" to "sales","repairs" to "repairs","vaults" to "vaults","daily-close" to "closing","partners" to "partners","reports" to "reports","exchange-rates" to "rates")
  val saved=settings.optJSONArray("modernShortcuts")
  val selected=(0 until (saved?.length() ?: 0)).mapNotNull {routes[saved!!.optString(it)]}.toSet()
  prefs.edit().putBoolean("initialized",true).putBoolean("hideAmounts",settings.optBoolean("hideHomeAmounts",false)).putStringSet("shortcuts",selected).apply()
  renderSummary();renderShortcuts()
 }
 fun showSummary(value:com.alsafa.native8.home.HomeReadRepository.Summary) {summary=value;renderSummary()}
 fun summaryError(){balances.removeAllViews();ui.add(balances,ui.text("تعذر تحميل ملخص اليوم؛ أعد فتح الرئيسية للمحاولة.",13f))}
 private fun renderSummary() {
  val value=summary ?: return
  balances.removeAllViews();val hidden=prefs.getBoolean("hideAmounts",false)
  ui.add(balances,ui.button(if(hidden)"إظهار المبالغ" else "إخفاء المبالغ"){prefs.edit().putBoolean("hideAmounts",!hidden).apply();renderSummary()},5)
  val grid=SafaGrid(ui.context,{if(it>=600)3 else 1},8)
  listOf("المدوّر من الإقفال السابق" to value.carry,"مبيعات اليوم" to value.sales,"صيانة اليوم" to value.repairs).forEach { (title,values) ->
   val card=ui.card(10);ui.add(card,ui.text(title,13f,bold=true),5)
   listOf("USD","SYP","TRY").forEach { unit ->ui.add(card,ui.text(if(hidden)"•••• $unit" else "${com.alsafa.native8.sales.InvoiceDisplay.amount((values[unit] ?: java.math.BigDecimal.ZERO).toPlainString())} $unit",14f),2) }
   grid.addView(card)
  };ui.add(balances,grid,3)
  if(value.warning.isNotBlank())ui.add(balances,ui.text(value.warning,12f))
 }
 fun showStock(count:Int) {alerts.removeAllViews();if(count>0)ui.add(alerts,ui.button("⚠ مخزون منخفض — $count"){onRoute("stock")})}
 fun stockError(){alerts.removeAllViews();ui.add(alerts,ui.button("تعذر فحص نقص المخزون — إعادة المحاولة"){onRoute("stock")})}
 private fun renderShortcuts() {
  shortcuts.removeAllViews()
  val ids=prefs.getStringSet("shortcuts",emptySet()).orEmpty()
  ui.add(shortcuts,ui.button("اختصاراتي — تخصيص"){editShortcuts()},3)
  val grid=SafaGrid(ui.context,{if(it>=600)4 else 2},8)
  shortcutOptions.filter { it.first in ids }.forEach { (route,title)->grid.addView(ui.button(title){onRoute(route)}) }
  if(ids.isNotEmpty())ui.add(shortcuts,grid,0)
 }
 private fun editShortcuts() {
  val ids=prefs.getStringSet("shortcuts",emptySet()).orEmpty().toMutableSet()
  val body=ui.card();ui.heading(body,"اختصاراتي","اختر الأزرار التي تريدها في الرئيسية")
  val dialog=android.app.AlertDialog.Builder(ui.context).create()
  shortcutOptions.forEach { (id,title)->ui.add(body,android.widget.CheckBox(ui.context).apply {
   text=title;typeface=ui.style.font;isChecked=id in ids;setOnCheckedChangeListener{_,checked->if(checked)ids.add(id) else ids.remove(id)}
  },0) }
  ui.add(body,ui.button("حفظ",true){prefs.edit().putStringSet("shortcuts",ids).apply();renderShortcuts();dialog.dismiss()})
  val scroll=android.widget.ScrollView(ui.context);scroll.addView(body);dialog.setView(scroll);dialog.show()
 }
 data class Recent(val title:String,val subtitle:String,val open:()->Unit)
 fun showRecent(rows:List<Recent>,empty:String="لا توجد عمليات مسجلة بعد.") {
  recent.removeAllViews()
  if(rows.isEmpty())ui.add(recent,ui.text(empty,14f,SafaStyle.Palette.muted))
  rows.forEach { row -> ui.add(recent,ui.button("${row.title}\n${row.subtitle}",height=64,onClick=row.open)) }
 }
}
