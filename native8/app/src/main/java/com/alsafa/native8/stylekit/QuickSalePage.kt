package com.alsafa.native8.stylekit
import android.view.View
import android.widget.*

/** renderFatherSale, main.js:3038. The controller owns lookup, totals and submission. */
class QuickSalePage(private val ui:SafaWidgets,shopName:String,private val callbacks:Callbacks) {
 data class Draft(val productId:String,val finalAmount:String,val currency:String,val paymentMethod:String)
 interface Callbacks {
  fun back();fun search(query:String);fun scan();fun lookup(barcode:String)
  fun baseAmountChanged(amount:String);fun amountChanged(amount:String,currency:String);fun submit(draft:Draft)
 }
 val host=SafaPageHost(ui,shopName);val view:View get()=host.view
 val search=ui.input("مثال: CASIO أو MTP أو 002")
 val barcode=ui.input("امسح أو اكتب الباركود").apply{minimumHeight=ui.style.dp(58);textSize=18f;gravity=android.view.Gravity.CENTER}
 val baseAmount=ui.input(numeric=true)
 private val priceFields=ui.column().apply{visibility=View.GONE}
 val results=ui.column()
 val selected=ui.text("لم يتم اختيار ساعة بعد",17f,SafaStyle.Palette.muted).apply{background=ui.style.box();setPadding(ui.style.dp(15),ui.style.dp(15),ui.style.dp(15),ui.style.dp(15))}
 private var selectedId=""
 val money:QuickFormFields
 val payment:SafaWidgets.Choice
 val submit:Button
 init {
  val body=host.body;ui.heading(body,"مبيع سريع","ابحث عن الساعة أو امسح الباركود، ثم اختر طريقة الدفع وعملة البيع.")
  ui.add(body,ui.button("رجوع"){callbacks.back()})
  val form=ui.card();ui.add(body,form)
  ui.step(form,1,"اختر الساعة");ui.label(form,"بحث بالاسم أو REF أو الرقم أو الباركود")
  ui.add(form,search);ui.add(form,results);ui.add(form,barcode)
  ui.add(form,ui.button("📷 قراءة الباركود",true,54){callbacks.scan()})
  ui.add(form,ui.button("إضافة",height=54){callbacks.lookup(barcode.text.toString())})
  ui.add(form,selected)
  ui.label(priceFields,"سعر البيع لهذه الفاتورة");ui.add(priceFields,baseAmount);ui.add(form,priceFields,21)
  ui.listen(baseAmount,callbacks::baseAmountChanged)
  ui.step(form,2,"سعر البيع النهائي");money=QuickFormFields(ui,form,"USD",callbacks::amountChanged)
  ui.step(form,3,"طريقة الدفع");payment=money.payment(form)
  submit=ui.complete("✓ إتمام الفاتورة"){callbacks.submit(Draft(selectedId,money.amount.text.toString(),money.currency,payment.value))}
  ui.add(form,submit);ui.listen(search,callbacks::search)
 }
 fun showProducts(rows:List<Pair<String,String>>,onChoose:(String)->Unit){results.removeAllViews();rows.forEach{(id,label)->ui.add(results,ui.button(label){onChoose(id)})}}
 fun selectProduct(id:String,description:String){selectedId=id;selected.text=description;priceFields.visibility=View.VISIBLE;results.removeAllViews()}
 fun showConversion(message:String,error:Boolean=false){money.conversion.text=message;money.conversion.setTextColor(if(error)SafaStyle.Palette.danger else SafaStyle.Palette.deep)}
 fun busy(value:Boolean){submit.isEnabled=!value}
}
