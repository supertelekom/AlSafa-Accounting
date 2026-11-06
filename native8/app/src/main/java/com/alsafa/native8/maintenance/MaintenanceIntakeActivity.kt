package com.alsafa.native8.maintenance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.content.ActivityNotFoundException
import android.widget.*
import android.view.View
import com.alsafa.native8.stylekit.*
import kotlinx.coroutines.*
import androidx.core.content.FileProvider
import java.io.File
import com.alsafa.native8.sales.AppDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** New intake, saved to the same Room database; no payment or stock movement on intake. */
class MaintenanceIntakeActivity:Activity() {
 private var photoUri:String=""
 private lateinit var photoPreview:ImageView
 private lateinit var photoLabel:TextView
 private var cameraUri:String=""
 private var stateParts:(()->String)?=null
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onDestroy(){scope.cancel();super.onDestroy()}
 override fun onSaveInstanceState(out:Bundle){out.putString("parts",stateParts?.invoke());out.putString("photo",photoUri);out.putString("camera",cameraUri);super.onSaveInstanceState(out)}
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
  val ui=com.alsafa.native8.stylekit.SafaWidgets(com.alsafa.native8.stylekit.SafaStyle(this))
  val host=com.alsafa.native8.stylekit.SafaPageHost(ui,"الصفا للساعات")
  val root=host.body
  ui.heading(root,"استلام صيانة","")
  ui.add(root,ui.button("رجوع"){finish()})
  photoUri=savedInstanceState?.getString("photo").orEmpty();cameraUri=savedInstanceState?.getString("camera").orEmpty()
  fun pair():SafaGrid=SafaGrid(this,{if(it>=520)2 else 1},14).also{ui.add(root,it,14)}
  fun field(parent:android.view.ViewGroup,label:String,multiline:Boolean=false):EditText {
   val box=ui.column();ui.label(box,label)
   val input=ui.input(multiline=multiline);ui.add(box,input,0);parent.addView(box);return input
  }
  val client=pair()
  val customer=field(client,"اسم العميل *").apply{id=4101}
  val phone=field(client,"رقم الهاتف / واتساب").apply{id=4102;inputType=android.text.InputType.TYPE_CLASS_PHONE}
  val watchRow=pair()
  val watch=field(watchRow,"الماركة / نوع الساعة *").apply{id=4103}
  val problem=field(watchRow,"العطل أو المطلوب *").apply{id=4104}
  val notes=field(root,"ملاحظات",true).apply{id=4105}
  val amounts=pair()
  val labor=field(amounts,"أجرة الإصلاح فقط — دون ثمن القطع").apply{id=4106;inputType=8194;setText("0")}
  val moneyBox=ui.column();ui.label(moneyBox,"عملة الأجرة والفاتورة")
  val currency=Spinner(this).apply {id=4107;adapter=ArrayAdapter(this@MaintenanceIntakeActivity,android.R.layout.simple_spinner_dropdown_item,listOf("USD","TRY","SYP"))};ui.add(moneyBox,currency);amounts.addView(moneyBox)
  ui.add(root,ui.text("يمكن إبقاء الأجرة صفرًا وإضافة الأجرة والقطع بعد الفحص.",12f,SafaStyle.Palette.muted))
  val selectedParts=mutableListOf<MaintenancePartsRepository.SelectedPart>()
  val names=mutableMapOf<String,String>()
  savedInstanceState?.getString("parts")?.let { val a=JSONArray(it);for(i in 0 until a.length()){val r=a.getJSONObject(i);selectedParts.add(MaintenancePartsRepository.SelectedPart(r.getString("id"),r.getString("qty"),r.getString("price")));names[r.getString("id")]=r.optString("name")} }
  stateParts={JSONArray().apply{selectedParts.forEach{put(JSONObject().put("id",it.partId).put("qty",it.qty).put("price",it.price).put("name",names[it.partId]))}}.toString()}
  val partRows=ui.column()
  fun refreshParts(){partRows.removeAllViews();if(selectedParts.isEmpty())ui.add(partRows,ui.text("لا توجد قطع مضافة"))
   selectedParts.toList().forEach{line->ui.add(partRows,ui.button("${names[line.partId]} × ${line.qty} — حذف"){selectedParts.remove(line);refreshParts()})}}
  ui.step(root,1,"قطع الصيانة — اختياري")
  ui.add(root,ui.button("بحث وإضافة قطع من المستودع"){
   val body=ui.column(16);ui.heading(body,"اختيار قطعة صيانة","")
   val search=ui.input("بحث بالاسم أو الباركود");ui.add(body,search)
   val notice=ui.text("");ui.add(body,notice)
   val list=ListView(this);body.addView(list,LinearLayout.LayoutParams(-1,ui.style.dp(300)))
   val dialog=android.app.AlertDialog.Builder(this).setView(body).setNegativeButton("إغلاق",null).create()
   var job:Job?=null;var parts=listOf<MaintenancePartEntity>()
   fun load(q:String){job?.cancel();job=scope.launch{try{delay(200);parts=withContext(Dispatchers.IO){MaintenancePartsRepository(AppDatabase.get(applicationContext)).list(q)}
    list.adapter=ArrayAdapter(this@MaintenanceIntakeActivity,android.R.layout.simple_list_item_1,parts.map{"${it.name} — المتاح ${it.stock} — ${com.alsafa.native8.sales.InvoiceDisplay.amount(it.sale_price)} ${it.currency}"});notice.text=if(parts.isEmpty())"لا توجد قطع مطابقة" else "${parts.size} قطعة"
   }catch(e:CancellationException){throw e}catch(e:Exception){notice.text=e.message ?: "تعذر قراءة القطع"}}}
   ui.listen(search){load(it)};dialog.setOnDismissListener{job?.cancel()};dialog.show();load("")
   list.setOnItemClickListener{_,_,index,_->
    val part=parts[index];val existing=selectedParts.firstOrNull{it.partId==part.id};val form=ui.column(16)
    ui.label(form,"الكمية");val quantity=ui.input(numeric=true).apply{setText(existing?.qty ?: "1")};ui.add(form,quantity)
    ui.label(form,"سعر القطعة — ${part.currency}");val price=ui.input(numeric=true).apply{setText(existing?.price ?: com.alsafa.native8.sales.InvoiceDisplay.amount(part.sale_price))};ui.add(form,price)
    val error=ui.text("",14f,SafaStyle.Palette.danger);ui.add(form,error)
    val picker=android.app.AlertDialog.Builder(this).setTitle(part.name).setView(form).setNegativeButton("إلغاء",null).setPositiveButton("إضافة",null).create()
    picker.setOnShowListener{picker.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
     val qty=quantity.text.toString().toBigDecimalOrNull();val sale=price.text.toString().toBigDecimalOrNull();val available=part.stock.toBigDecimalOrNull()
     if(qty==null||sale==null||available==null||qty<=BigDecimal.ZERO||qty>available||qty.stripTrailingZeros().scale()>0||sale<BigDecimal.ZERO)error.text="تحقق من الكمية والسعر والمخزون"
     else {selectedParts.removeAll{it.partId==part.id};selectedParts.add(MaintenancePartsRepository.SelectedPart(part.id,qty.toPlainString(),sale.toPlainString()));names[part.id]=part.name;refreshParts();picker.dismiss();dialog.dismiss()}
    }};picker.show()
   }
  });ui.add(root,partRows);refreshParts()
  ui.step(root,2,"صورة الساعة")
  val photos=pair()
  photos.addView(ui.button("تصوير بالكاميرا"){
   try {
    val dir=File(filesDir,"repair_photos").apply{mkdirs()};val file=File.createTempFile("watch-",".jpg",dir)
    val uri=FileProvider.getUriForFile(this,packageName+".receipts",file);cameraUri=uri.toString()
    startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply{putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri);clipData=android.content.ClipData.newRawUri("صورة الساعة",uri);addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)},702)
   }catch(e:Exception){Toast.makeText(this,"تعذر فتح الكاميرا: ${e.message ?: "لا يوجد تطبيق كاميرا"}",Toast.LENGTH_LONG).show()}
  })
  photos.addView(ui.button("اختيار من الصور"){
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},701)
  })
  photoLabel=ui.text("");ui.add(root,photoLabel)
  photoPreview=ImageView(this).apply{adjustViewBounds=true;maxHeight=ui.style.dp(240);visibility=View.GONE};ui.add(root,photoPreview);showPhoto()
  ui.add(root,ui.button("حفظ طلب الصيانة",true){}.apply {
   setOnClickListener {
    if(customer.text.isBlank()||watch.text.isBlank()||problem.text.isBlank()) {
     Toast.makeText(this@MaintenanceIntakeActivity,"أكمل الحقول المطلوبة",Toast.LENGTH_LONG).show()
     return@setOnClickListener
    }
    val customerValue=customer.text.toString().trim()
    val phoneValue=phone.text.toString().trim()
    val watchValue=watch.text.toString().trim()
    val modelValue=""
    val problemValue=problem.text.toString().trim()
    val diagnosisValue=""
    val notesValue=notes.text.toString().trim()
    val invoiceCurrency=currency.selectedItem.toString()
    val laborValue=labor.text.toString().toBigDecimalOrNull()
    if(laborValue==null || laborValue<BigDecimal.ZERO) { Toast.makeText(this@MaintenanceIntakeActivity,"أجرة العمل غير صحيحة",Toast.LENGTH_LONG).show();return@setOnClickListener }
    val photoValue=photoUri
    val requestedParts=selectedParts.toList()
    isEnabled=false
    Thread {
     val result=runCatching {
      val db=AppDatabase.get(applicationContext)
      val now=System.currentTimeMillis()
      val date=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(now))
      val time=SimpleDateFormat("HH:mm",Locale.US).format(Date(now))
      val id="repair-"+now+"-"+UUID.randomUUID().toString().take(6)
      val savedNumber=runBlocking {
       db.withTransaction {
        val number=maxOf(db.maintenanceDao().maxRepairInvoice(),
         db.salesPostingDao().maxNewNumber(),db.legacySaleDao().maxLegacyInvoiceNumber())+1
        val raw=JSONObject().apply {
         put("id",id);put("invoiceNo",number);put("date",date);put("time",time)
         put("status","قيد الصيانة")
         put("customer",customerValue);put("phone",phoneValue)
         put("watch",watchValue);put("model",modelValue)
         put("problem",problemValue);put("diagnosis",diagnosisValue)
         put("notes",notesValue);put("watchPhoto",photoValue)
         put("currency",invoiceCurrency);put("laborPrice",laborValue.toPlainString());put("price",laborValue.toPlainString());put("received",0)
         put("partsTotal",0);put("partsCost",0);put("cost",0)
         put("usedParts",org.json.JSONArray());put("partsStockApplied",false)
         put("createdAt",now)
        }
        db.maintenanceDao().insertNewRepair(MaintenanceRepairEntity(
         id,"native8",number,date,time,"قيد الصيانة",
         customerValue,phoneValue,
         watchValue,modelValue,
         problemValue,invoiceCurrency,laborValue.toPlainString(),"0","",now,raw.toString()
        ))
        if(requestedParts.isNotEmpty()) {
         // Same Room transaction as intake; no stock deduction until delivery.
         val used=JSONArray();var total=BigDecimal.ZERO;var costs=BigDecimal.ZERO
         for(line in requestedParts) {
          val part=db.maintenanceDao().part(line.partId) ?: error("قطعة الصيانة غير موجودة")
          val qty=line.qty.toBigDecimal();val sale=line.price.toBigDecimal().multiply(com.alsafa.native8.sales.ExchangeRateSettings(applicationContext).rate(part.currency,invoiceCurrency))
          require(qty>BigDecimal.ZERO && qty<=part.stock.toBigDecimal() && qty.stripTrailingZeros().scale()<=0) { "الكمية غير متوفرة: ${part.name}" }
          val costRate=com.alsafa.native8.sales.ExchangeRateSettings(applicationContext).rate(part.currency,invoiceCurrency)
          val convertedCost=part.cost.toBigDecimal().multiply(costRate)
          used.put(JSONObject().put("partId",part.id).put("name",part.name).put("qty",line.qty).put("price",sale.toPlainString()).put("cost",convertedCost.toPlainString()).put("currency",invoiceCurrency)
           .put("costSnapshotVerified",true).put("sourceUnitCost",part.cost)
           .put("sourceCostCurrency",part.currency).put("costExchangeRate",costRate.toPlainString()))
          total=total.add(qty.multiply(sale));costs=costs.add(qty.multiply(convertedCost))
         }
         val current=db.maintenanceDao().repair(id) ?: error("لم تحفظ الفاتورة")
         val changed=JSONObject(current.original_json).apply { put("usedParts",used);put("partsTotal",total.toPlainString());put("partsCost",costs.toPlainString()) }
         check(db.maintenanceDao().updateParts(id,current.original_json,changed.toString())==1)
        }
        MaintenancePartsRepository(db).refreshDraftTotal(id)
        number
       }
      }
      savedNumber
     }
     runOnUiThread {
      if(result.isSuccess) {
       Toast.makeText(this@MaintenanceIntakeActivity,"تم حفظ طلب الصيانة",Toast.LENGTH_SHORT).show()
       val number=result.getOrThrow()
       setResult(RESULT_OK,Intent().putExtra("maintenance_invoice_number",number))
       if(photoUri.isNotBlank() && phone.text.isNotBlank()) {
        shareWithWhatsAppBusiness(phone.text.toString(),number)
       }
       finish()
      }
      else { isEnabled=true;Toast.makeText(this@MaintenanceIntakeActivity,result.exceptionOrNull()?.message ?: "تعذر الحفظ",Toast.LENGTH_LONG).show() }
     }
    }.start()
   }
  })
  setContentView(host.view)
 }
 private fun showPhoto(){
  photoLabel.text=if(photoUri.isBlank())"لم تُحدد صورة" else "صورة الساعة محفوظة"
  if(photoUri.isBlank())return
  try{val uri=Uri.parse(photoUri);val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
   contentResolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,bounds)}
   var sample=1;while(bounds.outWidth/sample>1200 || bounds.outHeight/sample>1200)sample*=2
   val options=android.graphics.BitmapFactory.Options().apply{inSampleSize=sample}
   val bitmap=contentResolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,options)}
   photoPreview.setImageBitmap(bitmap);photoPreview.visibility=View.VISIBLE
  }catch(e:Exception){photoLabel.text="تعذر عرض الصورة؛ اخترها مجدداً"}
 }
 private fun shareWithWhatsAppBusiness(phone:String,number:Long) {
  val digits=phone.filter { it.isDigit() }
  if(digits.isBlank()) return
  val image=Uri.parse(photoUri)
  val send=Intent(Intent.ACTION_SEND).apply {
   type="image/*"
   setPackage("com.whatsapp.w4b")
   putExtra(Intent.EXTRA_STREAM,image)
   putExtra(Intent.EXTRA_TEXT,"الصفا للساعات - تم استلام ساعتكم للصيانة، رقم الصيانة: "+number)
   putExtra("jid",digits+"@s.whatsapp.net")
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   clipData=android.content.ClipData.newUri(contentResolver,"صورة الصيانة",image)
  }
  try { startActivity(send) }
  catch(_:ActivityNotFoundException) {
   Toast.makeText(this,"تعذر فتح واتساب Business؛ يمكنك مشاركة الصورة من تفاصيل الصيانة",Toast.LENGTH_LONG).show()
  } catch(e:SecurityException) {
   Toast.makeText(this,"تعذر منح واتساب صلاحية الصورة",Toast.LENGTH_LONG).show()
  }
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==702){if(resultCode==RESULT_OK && cameraUri.isNotBlank()){photoUri=cameraUri;showPhoto()};cameraUri="";return}
  if(requestCode==701 && resultCode==RESULT_OK) {
   val uri=data?.data ?: return
   try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
   catch(_:SecurityException) { Toast.makeText(this@MaintenanceIntakeActivity,"تعذر الاحتفاظ بإذن الصورة",Toast.LENGTH_LONG).show();return }
   photoUri=uri.toString()
   showPhoto()
   Toast.makeText(this@MaintenanceIntakeActivity,"تم اختيار صورة الساعة",Toast.LENGTH_SHORT).show()
  }
 }
}
