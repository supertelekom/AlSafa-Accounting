package com.alsafa.native8.stylekit
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.WindowInsets
import android.widget.*

/** A View factory only: no Activity, navigation, database or manifest registration. */
class SafaPageHost(val ui:SafaWidgets,shopName:String,logo:Drawable?=null,handleInsets:Boolean=true) {
 val body=ui.column(16)
 val view=ScrollView(ui.context).apply {
  isFillViewport=true;isHorizontalScrollBarEnabled=false;clipToPadding=false
  layoutDirection=View.LAYOUT_DIRECTION_RTL;setBackgroundColor(SafaStyle.Palette.cream)
  val stack=ui.column()
  val header=ui.column(16).apply {background=ui.style.gradient("#f9f3e8","#ead9b9",0)}
  val brand=LinearLayout(ui.context).apply{gravity=android.view.Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
  if(logo!=null)brand.addView(ImageView(ui.context).apply{setImageDrawable(logo);adjustViewBounds=true;contentDescription=shopName},LinearLayout.LayoutParams(ui.style.dp(80),ui.style.dp(54)).apply{marginEnd=ui.style.dp(12)})
  val names=ui.column();ui.add(names,ui.text(shopName,19f,SafaStyle.Palette.deep,true),3)
  ui.add(names,ui.text("مبيعات · مخزون · صيانة · حسابات",11f,SafaStyle.Palette.muted),0)
  brand.addView(names,LinearLayout.LayoutParams(0,-2,1f));header.addView(brand)
  stack.addView(header,LinearLayout.LayoutParams(-1,-2));stack.addView(body,LinearLayout.LayoutParams(-1,-2))
  addView(stack,FrameLayout.LayoutParams(-1,-2))
  if(handleInsets)setOnApplyWindowInsetsListener { v,i ->
   if(android.os.Build.VERSION.SDK_INT>=30){val b=i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom)}
   else v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom)
   v.post { revealFocusedField() }
   i
  }
  // React to measured window width; later insets/keyboard changes remain within the scroll viewport.
  addOnLayoutChangeListener{_,left,_,right,_,oldLeft,_,oldRight,_->
   if(right-left!=oldRight-oldLeft){val w=(right-left)/resources.displayMetrics.density;val p=ui.style.dp((w*.022f).coerceIn(10f,28f));body.setPadding(p,p,p,p)}
  }
 }
 fun revealFocusedField(extraBelow:Int?=null) {
  val field=view.findFocus() ?: return
  if(field !is EditText)return
  SafaKeyboard.reveal(field,extraBelow)
 }
 init {view.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
  override fun onViewAttachedToWindow(v:View){v.requestApplyInsets()}
  override fun onViewDetachedFromWindow(v:View){}
 })}
}
