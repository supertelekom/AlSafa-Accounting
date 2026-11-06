package com.alsafa.native8.stylekit
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class SafaWidgets(val style:SafaStyle) {
 val context:Context get()=style.context
 fun column(padding:Int=0)=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(style.dp(padding),style.dp(padding),style.dp(padding),style.dp(padding))}
 fun add(parent:LinearLayout,view:View,gap:Int=10){parent.addView(view,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=style.dp(gap)})}
 fun text(value:String,size:Float=13f,color:Int=SafaStyle.Palette.ink,bold:Boolean=false)=TextView(context).apply {
  text=value;textSize=size;setTextColor(color);typeface=if(bold)Typeface.create(style.font,Typeface.BOLD)else style.font
  layoutDirection=View.LAYOUT_DIRECTION_RTL;textDirection=View.TEXT_DIRECTION_FIRST_STRONG_RTL;setLineSpacing(0f,1.15f)
 }
 fun heading(parent:LinearLayout,title:String,subtitle:String){add(parent,text(title,25f,bold=true),5);add(parent,text(subtitle,12f,SafaStyle.Palette.muted),18)}
 fun label(parent:LinearLayout,value:String){add(parent,text(value,11f,SafaStyle.Palette.muted),6)}
 fun input(hint:String="",numeric:Boolean=false,multiline:Boolean=false)=EditText(context).apply {
  id=View.generateViewId();typeface=style.font;textSize=16f;setTextColor(SafaStyle.Palette.ink);setHintTextColor(SafaStyle.Palette.muted)
  this.hint=hint;background=style.inputBackground();minimumHeight=style.dp(if(multiline)78 else 42)
  setPadding(style.dp(10),style.dp(9),style.dp(10),style.dp(9));setSingleLine(!multiline)
  inputType=if(numeric)InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT or if(multiline)InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
  gravity=Gravity.TOP or Gravity.START
 }
 fun button(label:String,primary:Boolean=false,height:Int=44,onClick:()->Unit)=Button(context).apply {
  text=label;typeface=Typeface.create(style.font,Typeface.BOLD);textSize=13f;isAllCaps=false
  minHeight=style.dp(height);minimumHeight=style.dp(height);minWidth=0;minimumWidth=0
  setPadding(style.dp(15),style.dp(7),style.dp(15),style.dp(7));stateListAnimator=null
  background=if(primary)style.gradient("#b38332","#8b601f",12) else style.box(Color.parseColor("#f7eddd"),12,SafaStyle.Palette.sand2)
  setTextColor(if(primary)Color.WHITE else SafaStyle.Palette.deep)
  setOnClickListener {onClick()}
 }
 fun complete(label:String,action:()->Unit)=button(label,true,64,action).apply {textSize=19f;background=style.gradient("#a87527","#c69a51",17)}
 fun card(padding:Int=15)=column(padding).apply {background=style.box()}
 fun step(parent:LinearLayout,number:Int,title:String){
  val row=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
  row.addView(text(number.toString(),17f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=style.box(SafaStyle.Palette.gold,16,width=0)},LinearLayout.LayoutParams(style.dp(31),style.dp(31)).apply{marginEnd=style.dp(10)})
  row.addView(text(title,17f,bold=true),LinearLayout.LayoutParams(0,-2,1f));add(parent,row,12)
 }
 fun listen(field:EditText,onChange:(String)->Unit){field.addTextChangedListener(object:TextWatcher {
  override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
  override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){onChange(s?.toString().orEmpty())}
  override fun afterTextChanged(e:Editable?){}
 })}
 fun choice(options:List<Pair<String,String>>,selected:String,vertical:Boolean=false,onChanged:(String)->Unit):Choice {
  val grid=SafaGrid(context,{if(vertical)1 else options.size});val buttons=linkedMapOf<String,Button>()
  val choice=Choice(grid,selected,buttons)
  options.forEach { (id,label)->
   val b=button(label,false,70){choice.select(id);onChanged(id)}.apply{background=style.selection();textSize=17f;isSelected=id==selected}
   buttons[id]=b;grid.addView(b)
  };return choice
 }
 class Choice(val view:View,var value:String,private val buttons:Map<String,Button>) {
  fun activate(id:String){requireNotNull(buttons[id]).performClick()}
  fun select(id:String){require(id in buttons);value=id;buttons.forEach{(key,b)->b.isSelected=key==id}}
 }
 fun confirm(title:String,message:String,onConfirm:()->Unit):AlertDialog=AlertDialog.Builder(context).setTitle(title).setMessage(message)
  .setNegativeButton("إلغاء",null).setPositiveButton("موافق"){_,_->onConfirm()}.create().apply {
   setOnShowListener{window?.decorView?.layoutDirection=View.LAYOUT_DIRECTION_RTL;findViewById<TextView>(android.R.id.message)?.typeface=style.font}
  }
}
