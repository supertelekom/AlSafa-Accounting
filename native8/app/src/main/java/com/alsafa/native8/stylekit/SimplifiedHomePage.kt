package com.alsafa.native8.stylekit
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

class SimplifiedHomePage(ui:SafaWidgets,onRoute:(String)->Unit) {
 val view:View
 init {
  val host=SafaPageHost(ui,"الصفا للساعات")
  ui.heading(host.body,"العمل السريع","اختر العملية فقط.")
  ui.add(host.body,ui.button("الوضع الكامل"){onRoute("full")})
  fun tile(title:String,icon:String,route:String,fill:String):View {
   val box=object:LinearLayout(ui.context) {
    override fun onMeasure(w:Int,h:Int){super.onMeasure(w,MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(w),MeasureSpec.EXACTLY))}
   }.apply {
    orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
    background=ui.style.gradient("#fffdf9",fill,24)
    foreground=RippleDrawable(ColorStateList.valueOf(Color.parseColor("#26a97929")),null,ui.style.box(Color.WHITE,24))
    elevation=ui.style.dp(2).toFloat();clipToOutline=true
    setPadding(ui.style.dp(6),ui.style.dp(8),ui.style.dp(6),ui.style.dp(8))
    isClickable=true;isFocusable=true;contentDescription=title;setOnClickListener{onRoute(route)}
   }
   val badge=ui.text(icon,30f).apply {
    gravity=Gravity.CENTER;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
    background=ui.style.box(SafaStyle.Palette.paper,32,SafaStyle.Palette.line)
   }
   box.addView(badge,LinearLayout.LayoutParams(ui.style.dp(60),ui.style.dp(60)).apply{bottomMargin=ui.style.dp(14)})
   ui.add(box,ui.text(title,20f,SafaStyle.Palette.ink,true).apply{gravity=Gravity.CENTER;maxLines=2},0)
   return box
  }
  // Three equal squares in one RTL row; compact even on a wide tablet.
  val actions=object:LinearLayout(ui.context) {
   override fun onMeasure(w:Int,h:Int) {
    val width=MeasureSpec.getSize(w).coerceAtMost(ui.style.dp(572))
    super.onMeasure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),h)
   }
  }.apply{orientation=LinearLayout.VERTICAL;clipChildren=false;clipToPadding=false}
  host.body.addView(actions,LinearLayout.LayoutParams(-1,-2).apply{
   gravity=Gravity.CENTER_HORIZONTAL;topMargin=ui.style.dp(20);bottomMargin=ui.style.dp(24)
  })
  val row=SafaGrid(ui.context,{3},16).apply{clipChildren=false;clipToPadding=false}
  row.addView(tile("مبيع سريع","🛒","sale","#f2e4cc"))
  row.addView(tile("صيانة سريعة","🔧","repair","#eee9df"))
  row.addView(tile("تسليم الساعات","⌚","delivery","#e8eee5"))
  ui.add(actions,row,0)
  ui.add(host.body,ui.button("رجوع"){onRoute("back")});view=host.view
 }
}
