package com.alsafa.native8.stylekit
import android.view.Gravity
import android.widget.*

/** Display strings only; exchange conversion and money validation remain in the host repository. */
class QuickFormFields(private val ui:SafaWidgets,parent:LinearLayout,initialCurrency:String,private val onAmountCurrency:(String,String)->Unit) {
 val amount=ui.input("أدخل المبلغ النهائي",numeric=true).apply{minimumHeight=ui.style.dp(58);textSize=22f;gravity=Gravity.CENTER}
 var currency=initialCurrency;private set
 val conversion=ui.text("اختر الصنف أو الخدمة أولاً",14f,SafaStyle.Palette.muted,true).apply{setPadding(ui.style.dp(9),ui.style.dp(13),ui.style.dp(9),ui.style.dp(13));background=ui.style.box(SafaStyle.Palette.cream,13);gravity=Gravity.CENTER}
 val currencyButton=ui.button(currencyLabel(initialCurrency),height=58){
  val ids=listOf("TRY","USD","SYP")
  android.app.AlertDialog.Builder(ui.context).setTitle("عملة التحصيل").setSingleChoiceItems(ids.map(::currencyLabel).toTypedArray(),ids.indexOf(currency)){dialog,index->
   currency=ids[index];currencyButtonText();dialog.dismiss();onAmountCurrency(amount.text.toString(),currency)
  }.setNegativeButton("إلغاء",null).show()
 }
 fun selectCurrency(id:String){require(id in listOf("TRY","USD","SYP"));currency=id;currencyButtonText();onAmountCurrency(amount.text.toString(),currency)}
 private fun currencyButtonText(){currencyButton.text=currencyLabel(currency)}
 init {
  ui.label(parent,"المبلغ النهائي وعملته")
  val row=LinearLayout(ui.context).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=android.view.View.LAYOUT_DIRECTION_RTL}
  row.addView(amount,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=ui.style.dp(8)})
  row.addView(currencyButton,LinearLayout.LayoutParams(-2,-2));ui.add(parent,row,5)
  ui.add(parent,ui.text("يمكن تعديل السعر بعد تحويله. يُسجّل المبلغ النهائي بالعملة المختارة.",10f,SafaStyle.Palette.muted))
  ui.add(parent,conversion,18);ui.listen(amount){onAmountCurrency(it,currency)}
 }
 fun payment(parent:LinearLayout):SafaWidgets.Choice=ui.choice(listOf("نقدي" to "💵 نقدي","شام كاش" to "📱 شام كاش"),"نقدي"){}.also{ui.add(parent,it.view,18)}
 companion object {fun currencyLabel(id:String)=when(id){"TRY"->"₺ تركية";"USD"->"$ دولار";"SYP"->"ل.س سورية";else->error("Unsupported currency")}}
}
