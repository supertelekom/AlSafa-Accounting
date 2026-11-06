package com.alsafa.native8.maintenance

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import com.alsafa.native8.sales.*
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.math.RoundingMode

/** One delivery surface for restored/native repairs, separate from invoice details. */
object RepairDeliveryDialog {
 fun show(activity:Activity,row:MaintenanceRepairEntity,due:BigDecimal,onSaved:()->Unit) {
  val ui=SafaWidgets(SafaStyle(activity));val zero=due.signum()==0
  val dialog=Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
  val body=ui.column(22).apply{background=ui.style.box(SafaStyle.Palette.paper,24);clipToOutline=true}
  ui.heading(body,if(zero) "تسليم الساعة" else "تسليم الساعة وتحصيل المبلغ","فاتورة رقم ${row.invoice_number} — ${row.customer.ifBlank { "عميل" }}")
  val summary=ui.card(16).apply{background=ui.style.box(SafaStyle.Palette.cream,16)}
  ui.add(summary,ui.text(row.watch.ifBlank {row.problem},17f,bold=true),8)
  ui.add(summary,ui.text(if(zero) "لا يوجد مبلغ مستحق" else "المبلغ المتبقي",13f,SafaStyle.Palette.muted),6)
  ui.add(summary,ui.text("\u2066${due.setScale(2,RoundingMode.HALF_UP)} ${row.currency}\u2069",27f,SafaStyle.Palette.deep,true),0)
  ui.add(body,summary,18)
  var currency=row.currency;var vault="drawer"
  val amount=ui.input(numeric=true).apply{setText(InvoiceDisplay.amount(due));textSize=22f;gravity=Gravity.CENTER;minimumHeight=ui.style.dp(58)}
  val controls=ui.column()
  ui.label(controls,"المبلغ المقبوض الآن");ui.add(controls,amount,14)
  lateinit var currencyButton:Button
  currencyButton=ui.button("العملة: ${QuickFormFields.currencyLabel(currency)}",height=54) {
   val ids=arrayOf("USD","TRY","SYP")
   android.app.AlertDialog.Builder(activity).setTitle("عملة القبض").setItems(ids.map{QuickFormFields.currencyLabel(it)}.toTypedArray()){_,i ->
    currency=ids[i];currencyButton.text="العملة: ${QuickFormFields.currencyLabel(currency)}"
    runCatching {due*ExchangeRateSettings(activity).rate(row.currency,currency)}
     .onSuccess{amount.setText(it.setScale(2,RoundingMode.HALF_UP).toPlainString())}.onFailure{amount.setText("")}
   }.show()
  }
  lateinit var vaultButton:Button
  vaultButton=ui.button("الصندوق: الدرج",height=54) {
   val ids=arrayOf("drawer","external","shamcash","bank");val labels=arrayOf("الدرج","الصندوق الخارجي","شام كاش","البنك")
   android.app.AlertDialog.Builder(activity).setTitle("الصندوق").setItems(labels){_,i->vault=ids[i];vaultButton.text="الصندوق: ${labels[i]}"}.show()
  }
  val choices=SafaGrid(activity,{if(it>=420)2 else 1},12)
  choices.addView(currencyButton);choices.addView(vaultButton);ui.add(controls,choices,12)
  ui.add(controls,ui.text("يُسجل المبلغ المقبوض بالعملة والصندوق المختارين.",12f,SafaStyle.Palette.muted))
  if(!zero) ui.add(body,controls,12)
  else ui.add(body,ui.text("سيتم تأكيد التسليم دون تسجيل قبض جديد.",15f,SafaStyle.Palette.success,true),18)
  val error=ui.text("",14f,SafaStyle.Palette.danger).apply{visibility=View.GONE};ui.add(body,error,8)
  lateinit var confirm:Button
  val cancel=ui.button("إلغاء",height=52){dialog.dismiss()}
  confirm=ui.complete(if(zero) "✓ تأكيد التسليم" else "✓ تأكيد التسليم والقبض") {
   val actual=if(zero) BigDecimal.ZERO else amount.text.toString().toBigDecimalOrNull()
   if(actual==null || (!zero && actual.signum()<=0)) {error.text="أدخل المبلغ المقبوض";error.visibility=View.VISIBLE}
   else {
    val selectedCurrency=currency;val selectedVault=vault
    confirm.isEnabled=false;cancel.isEnabled=false;dialog.setCancelable(false)
    Thread {
     val result=runCatching {runBlocking {
      val db=AppDatabase.get(activity.applicationContext)
      if(row.source_sha256=="native8") {
       MaintenanceDeliveryRepository(db,activity.applicationContext).deliver(row.id,
        listOf(MaintenanceDeliveryRepository.PaymentLine(actual.toPlainString(),selectedCurrency,selectedVault,"")),
        expectedJson=row.original_json)
      } else ArchivedRepairDeliveryRepository(db,activity.applicationContext)
       .deliver(row.id,row.original_json,due,actual,selectedCurrency,selectedVault)
     }}
     activity.runOnUiThread {
      if(activity.isDestroyed) return@runOnUiThread
      result.fold(onSuccess={dialog.dismiss();Toast.makeText(activity,"تم تسليم الساعة",Toast.LENGTH_LONG).show();onSaved()
       activity.startActivity(android.content.Intent(activity,SalePrintActivity::class.java).putExtra("repair_id",row.id).putExtra("share_after_collection",true))
      },
       onFailure={error.text=it.message;error.visibility=View.VISIBLE;confirm.isEnabled=true;cancel.isEnabled=true;dialog.setCancelable(true)})
     }
    }.start()
   }
  }
  ui.add(body,confirm,10);ui.add(body,cancel,0)
  val scroll=ScrollView(activity).apply{isFillViewport=false;addView(body)}
  dialog.setContentView(scroll);dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
  dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  dialog.show()
  val metrics=activity.resources.displayMetrics
  val windowSize=if(android.os.Build.VERSION.SDK_INT>=30) activity.windowManager.currentWindowMetrics.bounds else null
  val width=windowSize?.width() ?: metrics.widthPixels;val height=windowSize?.height() ?: metrics.heightPixels
  val contentWidth=(width-ui.style.dp(32)).coerceAtLeast(1).coerceAtMost(ui.style.dp(620))
  scroll.measure(View.MeasureSpec.makeMeasureSpec(contentWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
  dialog.window?.setLayout(contentWidth,scroll.measuredHeight.coerceAtMost((height*0.85).toInt()))
 }
}
