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
 init {
  ui.heading(host.body,"الوضع الكامل","كل عمليات المتجر والإدارة.")
  ui.add(host.body,ui.button("الوضع المبسط"){onRoute("simple")})
  val actions=SafaGrid(ui.context,{if(it>=900)4 else if(it>=600)3 else 2},12)
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
  val manage=SafaGrid(ui.context,{if(it>=900)4 else if(it>=600)3 else 2},12)
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
  val card=ui.card(16).apply {
   minimumHeight=ui.style.dp(138);gravity=Gravity.CENTER_VERTICAL
   background=ui.style.gradient("#fffdf9","#f3e8d6",18)
   foreground=RippleDrawable(ColorStateList.valueOf(Color.parseColor("#26a97929")),null,ui.style.box(Color.WHITE,18))
   clipToOutline=true;isClickable=true;isFocusable=true;contentDescription="$title، $subtitle"
   setOnClickListener{onRoute(route)}
  }
  ui.add(card,ui.text(icon,25f,SafaStyle.Palette.gold,true),6)
  ui.add(card,ui.text(title,18f,SafaStyle.Palette.ink,true),4)
  ui.add(card,ui.text(subtitle,12f,SafaStyle.Palette.muted),0)
  return card
 }
 data class Recent(val title:String,val subtitle:String,val open:()->Unit)
 fun showRecent(rows:List<Recent>,empty:String="لا توجد عمليات مسجلة بعد.") {
  recent.removeAllViews()
  if(rows.isEmpty())ui.add(recent,ui.text(empty,14f,SafaStyle.Palette.muted))
  rows.forEach { row -> ui.add(recent,ui.button("${row.title}\n${row.subtitle}",height=64,onClick=row.open)) }
 }
}
