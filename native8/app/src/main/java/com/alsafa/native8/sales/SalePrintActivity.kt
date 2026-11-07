package com.alsafa.native8.sales

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import android.widget.Button
import android.widget.CheckBox
import androidx.core.content.FileProvider
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.*
import java.io.File

/** Receipt export/preview/share and explicit printer actions; never posts accounting data. */
class SalePrintActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private lateinit var ui:SafaWidgets
 private lateinit var host:SafaPageHost
 private lateinit var message:android.widget.TextView
 private var pdf:File?=null
 private var receipt:InvoiceReceipt?=null
 private var printer=""
 private var width=80
 private var printing=false
 private var automaticShareHandled=false
 private val preferences by lazy {getSharedPreferences("receipt_print",MODE_PRIVATE)}
 override fun onCreate(state:Bundle?) {
  super.onCreate(state);automaticShareHandled=state?.getBoolean("automaticShareHandled",false) ?: false;requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  ui=SafaWidgets(SafaStyle(this));host=SafaPageHost(ui,"الصفا للساعات");setContentView(host.view)
  ui.heading(host.body,"طباعة ومشاركة الفاتورة","قالب الصفا — الفواتير والمرتجعات والاستبدال")
  ui.add(host.body,ui.button("رجوع"){finish()})
  message=ui.text("جارٍ تجهيز الفاتورة…");ui.add(host.body,message)
  scope.launch {
   try {
    val returnKey=intent.getStringExtra("return_key")
    val data=if(returnKey!=null)ReturnHistoryRepository(applicationContext).receipt(returnKey) else InvoiceReceiptRepository(applicationContext).load(intent.getStringExtra("sale_id"),intent.getStringExtra("repair_id"))
    val file=withContext(Dispatchers.IO){data.pdf(applicationContext)}
    receipt=data;pdf=file;showActions(data);message.text="الفاتورة جاهزة"
    if(intent.getBooleanExtra("share_after_collection",false) && !automaticShareHandled) {
     automaticShareHandled=true
     if(InvoiceDisplay.phone(data.phone).isNotBlank())shareReceipt(data)
    }
    if(intent.getBooleanExtra("after_save",false) && preferences.getBoolean("auto",false) && printer.isNotBlank())printReceipt(null)
   } catch(e:CancellationException){throw e}
   catch(e:Exception){message.text=e.message ?: "تعذر تجهيز الفاتورة"}
  }
 }
 private fun uri():android.net.Uri {
  val authority=packageName+".receipts"
  val provider=packageManager.resolveContentProvider(authority,PackageManager.GET_META_DATA)
  check(provider?.metaData?.containsKey("android.support.FILE_PROVIDER_PATHS")==true) {
   "إعداد مشاركة PDF غير مكتمل في هذه النسخة. حدّث AndroidManifest.xml وملف res/xml/receipt_paths.xml ثم أعد بناء التطبيق. يمكنك حفظ PDF الآن."
  }
  return FileProvider.getUriForFile(this,authority,requireNotNull(pdf))
 }
 private fun launchSafely(action:()->Unit){try{action()}catch(e:android.content.ActivityNotFoundException){message.text="لا يوجد تطبيق مناسب لفتح الملف، أو واتساب أعمال غير مثبت"}catch(e:Exception){message.text=e.message ?: "التطبيق المطلوب غير متاح"}}
 private fun showActions(data:InvoiceReceipt) {
  val grid=SafaGrid(this,{if(it>=600)3 else 1});ui.add(host.body,grid)
  grid.addView(ui.button("عرض الفاتورة"){launchSafely{startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(),"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}})
  grid.addView(ui.button("طباعة عبر أندرويد"){launchSafely {
   val file=requireNotNull(pdf)
   val adapter=object:android.print.PrintDocumentAdapter() {
    override fun onLayout(old:android.print.PrintAttributes?,new:android.print.PrintAttributes?,signal:android.os.CancellationSignal,callback:LayoutResultCallback,extras:Bundle?) {
     if(signal.isCanceled)callback.onLayoutCancelled() else callback.onLayoutFinished(android.print.PrintDocumentInfo.Builder("AlSafa-${data.number}.pdf").setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),true)
    }
    override fun onWrite(pages:Array<out android.print.PageRange>,destination:android.os.ParcelFileDescriptor,signal:android.os.CancellationSignal,callback:WriteResultCallback) {
     scope.launch {
      try {
       withContext(Dispatchers.IO){file.inputStream().use{input -> java.io.FileOutputStream(destination.fileDescriptor).use{out -> input.copyTo(out)}}}
       if(signal.isCanceled)callback.onWriteCancelled() else callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
      }catch(e:Exception){callback.onWriteFailed(e.message)}
     }
    }
   }
   (getSystemService(PRINT_SERVICE) as android.print.PrintManager).print("AlSafa-${data.number}",adapter,android.print.PrintAttributes.Builder().build())
  }})
  grid.addView(ui.button("حفظ PDF"){launchSafely{startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="application/pdf";addCategory(Intent.CATEGORY_OPENABLE);putExtra(Intent.EXTRA_TITLE,"AlSafa-${data.number}.pdf")},610)}})
  grid.addView(ui.button("واتساب أعمال",true){shareReceipt(data)})
  val preview=ui.card();ui.add(host.body,preview);ui.add(preview,ui.text(data.plain,15f))
  ui.heading(host.body,"طابعة حرارية مقترنة","")
  printer=preferences.getString("address","").orEmpty();width=preferences.getInt("width",80).takeIf{it==58 || it==80} ?: 80
  val devices=ui.column();ui.add(host.body,ui.button("عرض الطابعات المقترنة") {
   if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
    requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT),611);message.text="بعد منح الإذن اضغط عرض الطابعات المقترنة"
   } else launchSafely {
    devices.removeAllViews()
    val paired=ThermalBluetoothPrinter(this).paired()
    if(paired.isEmpty())message.text="أقرن الطابعة أولاً من إعدادات البلوتوث"
    paired.forEach {p -> ui.add(devices,ui.button(p.name){printer=p.address;preferences.edit().putString("address",printer).apply();message.text="الطابعة: ${p.name}"})}
   }
  });ui.add(host.body,devices)
  ui.add(host.body,ui.choice(listOf("58" to "58 مم","80" to "80 مم"),width.toString()){width=it.toInt();preferences.edit().putInt("width",width).apply()}.view)
  lateinit var button:Button
  button=ui.button("طباعة بلوتوث",true){printReceipt(button)};ui.add(host.body,button)
  ui.add(host.body,CheckBox(this).apply {text="طباعة تلقائية بعد حفظ المبيع السريع";typeface=ui.style.font;isChecked=preferences.getBoolean("auto",false);setOnCheckedChangeListener{_,yes ->preferences.edit().putBoolean("auto",yes).apply()}})
 }
 private fun shareReceipt(data:InvoiceReceipt)=launchSafely {
  val phone=InvoiceDisplay.phone(data.phone)
  require(phone.isNotBlank()) {"لا يوجد رقم هاتف للعميل. أضف رقم هاتف من تعديل بيانات الفاتورة للإرسال عبر واتساب أعمال."}
  val content=uri()
  startActivity(Intent(Intent.ACTION_SEND).apply {
   type="application/pdf";setPackage("com.whatsapp.w4b")
   putExtra(Intent.EXTRA_STREAM,content)
   putExtra(Intent.EXTRA_TEXT,"الصفا للساعات — ${data.title} رقم ${data.number}\nشكراً لثقتكم بالصفا للساعات، يسعدنا خدمتكم دائماً.")
   putExtra("jid",phone+"@s.whatsapp.net")
   clipData=ClipData.newUri(contentResolver,"فاتورة الصفا",content)
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  })
 }
 override fun onSaveInstanceState(out:Bundle){out.putBoolean("automaticShareHandled",automaticShareHandled);super.onSaveInstanceState(out)}
 private fun printReceipt(button:Button?) {
  if(printing)return
  if(printer.isBlank()){message.text="اختر الطابعة أولاً";return}
  printing=true
  button?.isEnabled=false
  scope.launch {
   try {withContext(Dispatchers.IO){ThermalBluetoothPrinter(applicationContext).printReceipt(printer,requireNotNull(receipt),width)};message.text="أُرسلت الفاتورة للطابعة"}
   catch(e:CancellationException){throw e}
   catch(e:Exception){message.text=e.message ?: "تعذرت الطباعة"}
   finally{printing=false;button?.isEnabled=true}
  }
 }
 override fun onActivityResult(request:Int,result:Int,data:Intent?) {
  super.onActivityResult(request,result,data)
  if(request!=610 || result!=RESULT_OK)return
  val destination=data?.data ?: return
  scope.launch {try {
   withContext(Dispatchers.IO){requireNotNull(contentResolver.openOutputStream(destination)).use{out -> requireNotNull(pdf).inputStream().use{it.copyTo(out)}}}
   message.text="تم حفظ ملف PDF"
  }catch(e:CancellationException){throw e}catch(e:Exception){message.text="تعذر حفظ PDF: ${e.message}"}}
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
