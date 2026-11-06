package com.alsafa.native8.stylekit
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

class SimplifiedHomePage(ui:SafaWidgets,onRoute:(String)->Unit) {
 val view:View
 init {
  val host=SafaPageHost(ui,"الصفا للساعات")
  ui.heading(host.body,"العمل السريع","اختر العملية فقط.")
  ui.add(host.body,ui.button("الوضع الكامل"){onRoute("full")})
  fun tile(title:String,icon:String,route:String):View {
   val box=object:LinearLayout(ui.context) {
    override fun onMeasure(w:Int,h:Int){super.onMeasure(w,MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(w),MeasureSpec.EXACTLY))}
   }.apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=ui.style.box();isClickable=true;isFocusable=true;contentDescription=title;setOnClickListener{onRoute(route)} }
   ui.add(box,ui.text(icon,38f).apply{gravity=Gravity.CENTER},10)
   ui.add(box,ui.text(title,22f,bold=true).apply{gravity=Gravity.CENTER},0)
   return box
  }
  // Keep tablet actions compact; use available width when the window is narrower.
  val actions=object:LinearLayout(ui.context) {
   override fun onMeasure(w:Int,h:Int) {
    val width=MeasureSpec.getSize(w).coerceAtMost(ui.style.dp(374))
    super.onMeasure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),h)
   }
  }.apply{orientation=LinearLayout.VERTICAL}
  host.body.addView(actions,LinearLayout.LayoutParams(-1,-2).apply{gravity=Gravity.CENTER_HORIZONTAL})
  val top=SafaGrid(ui.context,{2},14);top.addView(tile("مبيع سريع","🛒","sale"));top.addView(tile("صيانة سريعة","🔧","repair"));ui.add(actions,top,14)
  val bottom=LinearLayout(ui.context).apply{gravity=Gravity.CENTER}
  val delivery=tile("تسليم الساعات","⌚","delivery")
  bottom.addView(delivery,LinearLayout.LayoutParams(0,-2,1f))
  // Match one upper square's width and centre it in the full-width row.
  bottom.addOnLayoutChangeListener{_,l,_,r,_,_,_,_,_-> val size=((r-l-ui.style.dp(14))/2).coerceAtLeast(0);val p=delivery.layoutParams as LinearLayout.LayoutParams;if(p.width!=size){p.width=size;p.weight=0f;delivery.layoutParams=p} }
  ui.add(actions,bottom,14);ui.add(host.body,ui.button("رجوع"){onRoute("back")});view=host.view
 }
}
