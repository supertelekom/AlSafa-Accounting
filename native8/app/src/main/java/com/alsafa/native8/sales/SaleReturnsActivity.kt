package com.alsafa.native8.sales
import android.app.Activity
import android.os.Bundle
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.*
import java.math.BigDecimal

class SaleReturnsActivity:Activity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  val id=intent.getStringExtra("sale_id").orEmpty();val line=intent.getStringExtra("line_id").orEmpty();val ui=SafaWidgets(SafaStyle(this));val host=SafaPageHost(ui,"الصفا للساعات");val body=host.body;ui.heading(body,"مرتجع مبيع","")
  val summary=ui.text("جارٍ تحميل البند…",18f,bold=true);ui.add(body,summary)
  ui.label(body,"الكمية المرتجعة");val qty=ui.input(numeric=true).apply{setText("1")};ui.add(body,qty)
  ui.label(body,"السبب");val reason=ui.input(multiline=true);ui.add(body,reason)
  ui.label(body,"الصندوق الذي يرد منه المبلغ")
  val box=ui.choice(listOf("drawer" to "الدرج","external" to "الخارجي","shamcash" to "شام كاش","bank" to "البنك"),"drawer",true){};ui.add(body,box.view)
  val unit=ui.choice(listOf("USD" to "دولار","TRY" to "تركي","SYP" to "سوري"),"USD"){};ui.add(body,unit.view)
  val error=ui.text("",14f,SafaStyle.Palette.danger);ui.add(body,error);val save=ui.button("تأكيد المرتجع",true){};save.isEnabled=false;ui.add(body,save);ui.add(body,ui.button("رجوع"){finish()});setContentView(host.view)
  var available=BigDecimal.ZERO;var submitting=false
  scope.launch{try{val item=withContext(Dispatchers.IO){RoomSalesEngine(applicationContext).lines(id).firstOrNull{it.id==line}} ?: error("اختر بنداً من تفاصيل الفاتورة")
   val originals=withContext(Dispatchers.IO){AppDatabase.get(applicationContext).legacyPaymentDao().payments(id)}
   if(originals.size==1){val receipt=originals.single();box.select(UnifiedCashboxBalance.canonical(receipt.cashbox) ?: "drawer");unit.select(receipt.currency)}
   available=item.quantity.toBigDecimal()-item.returned.toBigDecimal();summary.text="${item.title}\nالكمية القابلة للإرجاع: ${InvoiceDisplay.amount(available)}";save.isEnabled=available>BigDecimal.ZERO
  }catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message}}
  save.setOnClickListener{val q=qty.text.toString().toBigDecimalOrNull();if(q==null||q<=BigDecimal.ZERO||q>available){error.text="الكمية أكبر من المتاح أو غير صحيحة";return@setOnClickListener};val note=reason.text.toString();val cashbox=box.value;val currency=unit.value
   ui.confirm("تأكيد المرتجع","إرجاع ${InvoiceDisplay.amount(q)} من البند وتسوية المبلغ حسب الدفعات المحفوظة؟"){
    if(submitting)return@confirm;submitting=true;save.isEnabled=false;scope.launch{try{withContext(Dispatchers.IO){SalesEngine(applicationContext).returnLine(id,line,q,note,cashbox,currency)};finish()}catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message;submitting=false;save.isEnabled=true}}
   }.show()
  }
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
