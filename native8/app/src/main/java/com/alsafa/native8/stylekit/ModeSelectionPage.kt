package com.alsafa.native8.stylekit

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*

/** renderDashboard mode-main-grid; the requested two-mode entry only. */
class ModeSelectionPage(ui:SafaWidgets,onMode:(String)->Unit) {
 val view:View
 init {
  val host=SafaPageHost(ui,"الصفا للساعات")
  val body=host.body
  ui.add(body,ui.text("ALSAFA WATCHES",10f,Color.parseColor("#9b8463"),true).apply { gravity=Gravity.CENTER;letterSpacing=.24f },8)
  ui.add(body,ui.text("كيف تريد أن تبدأ؟",28f,Color.parseColor("#241f1b"),true).apply { gravity=Gravity.CENTER },7)
  ui.add(body,ui.text("اختر بيئة العمل المناسبة",14f,Color.parseColor("#7d7268")).apply { gravity=Gravity.CENTER },18)
  val row=LinearLayout(ui.context).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
  fun card(mode:String,title:String,subtitle:String,icon:String,dark:Boolean) {
   val ink=if(dark) Color.WHITE else Color.parseColor("#3e3429")
   val tile=ui.column(20).apply {
    minimumHeight=ui.style.dp(142);gravity=Gravity.CENTER_VERTICAL
    background=if(dark) ui.style.gradient("#25211e","#403730",26) else ui.style.gradient("#fffaf2","#f8edd9",26)
    isClickable=true;isFocusable=true;contentDescription="$title، $subtitle، دخول"
    setOnClickListener { onMode(mode) }
   }
   ui.add(tile,ui.text(icon,22f,if(dark) Color.parseColor("#ead9c0") else ink).apply { gravity=Gravity.CENTER },10)
   ui.add(tile,ui.text(title,20f,ink,true).apply { gravity=Gravity.CENTER },7)
   ui.add(tile,ui.text(subtitle,11f,if(dark) Color.parseColor("#d5c9bb") else ink).apply { gravity=Gravity.CENTER },12)
   ui.add(tile,ui.text("دخول",10f,if(dark) Color.parseColor("#d5b98f") else Color.parseColor("#8c7657"),true).apply { gravity=Gravity.END },0)
   row.addView(tile,LinearLayout.LayoutParams(0,-1,1f).apply { if(row.childCount>0) marginStart=ui.style.dp(14) })
  }
  card("simple","الوضع المبسط","بيع وصيانة وتسليم سريع","⚡",false)
  card("full","الوضع الكامل","الإدارة والمخزون والحسابات","◆",true)
  ui.add(body,row,18);view=host.view
 }
}
