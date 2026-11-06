package com.alsafa.native8.stylekit

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue

/** Presentation only. CSS px→dp is a starting mapping, pending physical-window comparison. */
class SafaStyle(val context:Context, val font:Typeface=Typeface.createFromAsset(context.assets,"fonts/Tajawal-Medium.ttf")) {
 object Palette {
  val cream=Color.parseColor("#f8f3eb");val paper=Color.parseColor("#fffdf9")
  val sand=Color.parseColor("#efe4d0");val sand2=Color.parseColor("#dfc9a4")
  val gold=Color.parseColor("#a97929");val deep=Color.parseColor("#7a5118")
  val ink=Color.parseColor("#3e3429");val muted=Color.parseColor("#8a7b6c")
  val line=Color.parseColor("#eadfce");val danger=Color.parseColor("#b8584b")
  val success=Color.parseColor("#5a8a67")
 }
 fun dp(n:Float)=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,n,context.resources.displayMetrics).toInt()
 fun dp(n:Int)=dp(n.toFloat())
 fun box(fill:Int=Palette.paper,radius:Int=17,stroke:Int=Palette.line,width:Int=1)=GradientDrawable().apply {
  setColor(fill);cornerRadius=dp(radius).toFloat();if(width>0)setStroke(dp(width),stroke)
 }
 fun gradient(a:String,b:String,radius:Int)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor(a),Color.parseColor(b))).apply {cornerRadius=dp(radius).toFloat()}
 fun selection(radius:Int=16)=StateListDrawable().apply {
  addState(intArrayOf(android.R.attr.state_selected),box(Color.parseColor("#f4e6cd"),radius,Color.parseColor("#b78a3e"),2))
  addState(intArrayOf(),box(Color.WHITE,radius,Palette.line,2))
 }
 fun inputBackground()=StateListDrawable().apply {
  addState(intArrayOf(android.R.attr.state_focused),box(Color.parseColor("#fffcf7"),10,Palette.gold))
  addState(intArrayOf(),box(Color.parseColor("#fffcf7"),10))
 }
}
