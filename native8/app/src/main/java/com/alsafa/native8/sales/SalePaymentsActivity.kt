package com.alsafa.native8.sales
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.*
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.math.RoundingMode

/** Existing invoice collections. Classifies by database records, never ID prefixes. */
class SalePaymentsActivity:Activity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(state:Bundle?){super.onCreate(state);requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  val id=intent.getStringExtra("sale_id").orEmpty()
  if(id.isBlank()){startActivity(Intent(this,FullSaleActivity::class.java).putExtra("resume_shared_draft",true));finish();return}
  val ui=SafaWidgets(SafaStyle(this));val host=SafaPageHost(ui,"الصفا للساعات");val body=host.body;ui.heading(body,"تكملة الدفع","قبض دفعة للفاتورة")
  val summary=ui.text("جارٍ قراءة الرصيد…",20f,bold=true);ui.add(body,summary)
  ui.label(body,"المبلغ المقبوض");val amount=ui.input(numeric=true);ui.add(body,amount)
  val units=ui.choice(listOf("USD" to "دولار","TRY" to "تركي","SYP" to "سوري"),"USD"){};ui.add(body,units.view)
  val boxes=ui.choice(listOf("drawer" to "الدرج","external" to "الخارجي","shamcash" to "شام كاش","bank" to "البنك"),"drawer",true){};ui.add(body,boxes.view)
  val error=ui.text("",14f,SafaStyle.Palette.danger);ui.add(body,error)
  val save=ui.button("تأكيد القبض",true){};save.isEnabled=false;ui.add(body,save);ui.add(body,ui.button("رجوع"){finish()});setContentView(host.view)
  var historical=false;var base="USD";var due=BigDecimal.ZERO
  scope.launch{try{val db=AppDatabase.get(applicationContext);withContext(Dispatchers.IO){val current=db.salesPostingDao().sale(id);historical=current==null
   val old=if(historical)requireNotNull(db.legacySaleDao().legacyById(id)){"الفاتورة غير موجودة"} else null;base=old?.currency ?: "USD"
   due=db.legacyReceivablesDao().bySaleId(id)?.balance?.toBigDecimal() ?: BigDecimal.ZERO
  };summary.text="المتبقي: ${InvoiceDisplay.amount(due)} $base";units.select(base);save.isEnabled=due>BigDecimal.ZERO
  if(historical)ui.add(body,ui.text("تحصيل الفاتورة القديمة بنفس عملتها: $base"))
  }catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message}}
  save.setOnClickListener{try{val value=amount.text.toString().toBigDecimal();require(value>BigDecimal.ZERO){"أدخل مبلغاً موجباً"};val currency=units.value;val box=boxes.value;require(box!="shamcash"||currency!="TRY"){"شام كاش لا يدعم التركية"};require(!historical||currency==base){"اختر عملة الفاتورة $base"}
   val fx=ExchangeRateSettings(this).rate(currency,"USD");val credit=(value*fx).setScale(2,RoundingMode.HALF_UP);require((if(historical)value else credit)<=due){"المبلغ أكبر من المتبقي"}
   save.isEnabled=false;scope.launch{try{withContext(Dispatchers.IO){if(historical)SalesRoomRepository(applicationContext).collectConfirmedHistorical(id,box,currency,value) else RoomSalesEngine(applicationContext).collect(id,SalePayment(box,currency,value,"USD",fx,credit))};startActivity(Intent(this@SalePaymentsActivity,SalePrintActivity::class.java).putExtra("sale_id",id));finish()}catch(e:CancellationException){throw e}catch(e:Exception){error.text=e.message;save.isEnabled=true}}
  }catch(e:Exception){error.text=e.message}}
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
