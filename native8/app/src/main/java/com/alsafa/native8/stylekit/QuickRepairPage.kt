package com.alsafa.native8.stylekit
import android.content.res.Configuration
import android.view.View
import android.widget.*

/** renderFatherRepair, main.js:3057. No cost, profit, currency conversion or stock logic. */
class QuickRepairPage(private val ui:SafaWidgets,shopName:String,services:List<Service>,private val callbacks:Callbacks) {
 data class Service(val id:String,val label:String,val icon:String,val priceLabel:String,val manual:Boolean,val stockLabel:String="")
 data class Draft(val serviceId:String,val baseAmount:String,val workDone:String,val deliverNow:Boolean,val customer:String,val phone:String,val finalAmount:String,val currency:String,val paymentMethod:String)
 interface Callbacks {fun back();fun selectService(id:String);fun amountChanged(amount:String,currency:String);fun baseAmountChanged(amount:String);fun submit(draft:Draft)}
 val host=SafaPageHost(ui,shopName);val view:View get()=host.view
 private var serviceId="";private var deliverNow=true
 val baseAmount=ui.input("اكتب المبلغ",numeric=true)
 val workDone=ui.input("مثال: تبديل ماكينة، تنظيف الساعة، تثبيت العقارب...",multiline=true)
 val customer=ui.input();val phone=ui.input().apply{inputType=android.text.InputType.TYPE_CLASS_PHONE}
 val selected=ui.text("اختر خدمة من الأعلى",17f)
 val delivery:SafaWidgets.Choice
 val money:QuickFormFields;val payment:SafaWidgets.Choice;val submit:Button
 private val serviceButtons=linkedMapOf<String,View>()
 init {
  val body=host.body;ui.heading(body,"صيانة سريعة","اضغط على الخدمة ثم اختر الدفع والعملة.");ui.add(body,ui.button("رجوع"){callbacks.back()})
  val form=ui.card();ui.add(body,form)
  val manual=ui.column().apply{visibility=View.GONE}
  val grid=SafaGrid(ui.context,{width->when {width>=1024->4;width>=900&&ui.context.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE->4;width>=720->3;else->2}},7)
  services.forEach {service ->
   val b=ui.column(7).apply {
    minimumHeight=ui.style.dp(78);background=ui.style.selection(14);isClickable=true;isFocusable=true
    contentDescription=listOf(service.label,if(service.manual)"مبلغ يدوي" else service.priceLabel,service.stockLabel).joinToString("، ")
    setOnClickListener {
     serviceId=service.id;serviceButtons.forEach{(id,button)->button.isSelected=id==service.id}
     manual.visibility=if(service.manual)View.VISIBLE else View.GONE
     selected.text=service.label;callbacks.selectService(service.id)
    }
   }
   ui.add(b,ui.text(service.icon,23f).apply{gravity=android.view.Gravity.CENTER},3)
   ui.add(b,ui.text(service.label,16f,bold=true).apply{gravity=android.view.Gravity.CENTER},3)
   ui.add(b,ui.text(if(service.manual)"مبلغ يدوي" else service.priceLabel,15f,bold=true).apply{gravity=android.view.Gravity.CENTER},0)
   if(service.stockLabel.isNotBlank())ui.add(b,ui.text(service.stockLabel,11f).apply{gravity=android.view.Gravity.CENTER},0)
   serviceButtons[service.id]=b;grid.addView(b)
  };ui.add(form,grid,12)
  ui.label(manual,"سعر الصيانة الأساسي (ثم اختر عملة الصيانة بالأسفل)")
  val manualRow=LinearLayout(ui.context).apply{layoutDirection=View.LAYOUT_DIRECTION_RTL;gravity=android.view.Gravity.CENTER_VERTICAL}
  manualRow.addView(baseAmount,LinearLayout.LayoutParams(0,-2,1f));manualRow.addView(ui.text("₺",18f),LinearLayout.LayoutParams(ui.style.dp(42),-2));ui.add(manual,manualRow);ui.label(manual,"العمل الذي تم *");ui.add(manual,workDone)
  ui.add(form,manual);ui.add(form,selected,21)
  val customerFields=ui.column().apply{visibility=View.GONE}
  ui.label(customerFields,"اسم العميل *");ui.add(customerFields,customer);ui.label(customerFields,"رقم الهاتف");ui.add(customerFields,phone)
  ui.step(form,2,"حالة تسليم الساعة")
  delivery=ui.choice(listOf("1" to "تم تسليم الساعة","0" to "ترك الساعة لدينا"),"1",vertical=true){id->
   deliverNow=id=="1";customerFields.visibility=if(deliverNow)View.GONE else View.VISIBLE
   if(deliverNow){customer.setText("");phone.setText("")}
  };ui.add(form,delivery.view);ui.add(form,customerFields)
  ui.step(form,3,"مبلغ الصيانة النهائي");money=QuickFormFields(ui,form,"TRY",callbacks::amountChanged)
  ui.step(form,4,"طريقة الدفع");payment=money.payment(form)
  submit=ui.complete("✓ إتمام العملية"){callbacks.submit(snapshot())}
  ui.add(form,submit);ui.listen(baseAmount,callbacks::baseAmountChanged)
 }
 fun snapshot()=Draft(serviceId,baseAmount.text.toString(),workDone.text.toString(),deliverNow,customer.text.toString(),phone.text.toString(),money.amount.text.toString(),money.currency,payment.value)
 fun restore(d:Draft){serviceButtons[d.serviceId]?.performClick();delivery.activate(if(d.deliverNow) "1" else "0");baseAmount.setText(d.baseAmount);workDone.setText(d.workDone);customer.setText(d.customer);phone.setText(d.phone);money.selectCurrency(d.currency);money.amount.setText(d.finalAmount);payment.activate(d.paymentMethod)}
 fun showConversion(text:String,error:Boolean=false){money.conversion.text=text;money.conversion.setTextColor(if(error)SafaStyle.Palette.danger else SafaStyle.Palette.deep)}
 fun busy(value:Boolean){submit.isEnabled=!value}
}
