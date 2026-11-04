package com.alsafa.native8.maintenance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.content.ActivityNotFoundException
import android.widget.*
import android.view.View
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
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   layoutDirection=View.LAYOUT_DIRECTION_RTL
   setPadding(24,24,24,24)
  }
  fun field(hint:String):EditText=EditText(this).also {
   it.hint=hint
   root.addView(it,LinearLayout.LayoutParams(-1,-2))
  }
  root.addView(TextView(this).apply { text="استلام صيانة";textSize=25f })
  val customer=field("اسم العميل *")
  val phone=field("رقم الهاتف")
  val watch=field("الماركة / نوع الساعة *")
  val model=field("الموديل أو الرقم")
  val problem=field("العطل *")
  val diagnosis=field("التشخيص")
  val notes=field("ملاحظات")
  val selectedParts=mutableListOf<MaintenancePartsRepository.SelectedPart>()
  val partSummary=TextView(this).apply { text="قطع الصيانة: لا توجد قطع مضافة";textSize=17f }
  root.addView(TextView(this).apply { text="قطع الصيانة";textSize=20f })
  root.addView(Button(this).apply {
   text="إضافة قطع من المستودع"
   setOnClickListener {
    Thread {
     val result=runCatching { runBlocking { MaintenancePartsRepository(AppDatabase.get(applicationContext)).list() } }
     runOnUiThread {
      val parts=result.getOrNull().orEmpty()
      if(parts.isEmpty()) { Toast.makeText(this@MaintenanceIntakeActivity,"لا توجد قطع في المستودع",Toast.LENGTH_LONG).show();return@runOnUiThread }
      val choices=parts.map { "${it.name} — متاح: ${it.stock} — ${it.sale_price} ${it.currency}" }.toTypedArray()
      android.app.AlertDialog.Builder(this@MaintenanceIntakeActivity).setTitle("اختيار قطعة صيانة")
       .setItems(choices) { _,index ->
        val part=parts[index]
        val form=LinearLayout(this@MaintenanceIntakeActivity).apply {
         orientation=LinearLayout.VERTICAL;setPadding(32,12,32,12)
        }
        val quantity=EditText(this@MaintenanceIntakeActivity).apply {
         hint="الكمية";setText("1");inputType=android.text.InputType.TYPE_CLASS_NUMBER
        }
        val price=EditText(this@MaintenanceIntakeActivity).apply {
         hint="سعر القطعة";setText(part.sale_price)
         inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        form.addView(quantity);form.addView(price)
        android.app.AlertDialog.Builder(this@MaintenanceIntakeActivity)
         .setTitle(part.name).setView(form).setNegativeButton("إلغاء",null)
         .setPositiveButton("إضافة") { _,_ ->
          val qty=quantity.text.toString().toBigDecimalOrNull()
          val available=part.stock.toBigDecimalOrNull()
          val sale=price.text.toString().toBigDecimalOrNull()
          if(qty==null||available==null||sale==null||qty<=BigDecimal.ZERO||qty>available||sale<BigDecimal.ZERO||qty.stripTrailingZeros().scale()>0) {
           Toast.makeText(this@MaintenanceIntakeActivity,"تحقق من الكمية والسعر والمخزون",Toast.LENGTH_LONG).show()
          } else {
           selectedParts.removeAll { it.partId==part.id }
           selectedParts.add(MaintenancePartsRepository.SelectedPart(part.id,qty.toPlainString(),sale.toPlainString()))
           partSummary.text="قطع الصيانة: ${selectedParts.size} — "+selectedParts.joinToString("، ") { line ->
            val p=parts.firstOrNull { it.id==line.partId }; "${p?.name ?: line.partId} × ${line.qty}"
           }
          }
         }.show()
       }.show()
     }
    }.start()
   }
  })
  root.addView(partSummary)
  root.addView(Button(this).apply {
   text="مسح القطع المختارة"
   setOnClickListener { selectedParts.clear();partSummary.text="قطع الصيانة: لا توجد قطع مضافة" }
  })
  val photoLabel=TextView(this).apply { text="صورة الساعة: لم تُحدد" }
  root.addView(Button(this).apply {
   text="اختيار صورة الساعة"
   setOnClickListener {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
     type="image/*"
     addCategory(Intent.CATEGORY_OPENABLE)
     addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    },701)
   }
  })
  root.addView(photoLabel)
  photoPreview=ImageView(this).apply {
   adjustViewBounds=true
   maxHeight=500
   visibility=View.GONE
  }
  root.addView(photoPreview,LinearLayout.LayoutParams(-1,-2))
  root.addView(Button(this).apply {
   text="حفظ طلب الصيانة"
   setOnClickListener {
    if(customer.text.isBlank()||watch.text.isBlank()||problem.text.isBlank()) {
     Toast.makeText(this@MaintenanceIntakeActivity,"أكمل الحقول المطلوبة",Toast.LENGTH_LONG).show()
     return@setOnClickListener
    }
    val customerValue=customer.text.toString().trim()
    val phoneValue=phone.text.toString().trim()
    val watchValue=watch.text.toString().trim()
    val modelValue=model.text.toString().trim()
    val problemValue=problem.text.toString().trim()
    val diagnosisValue=diagnosis.text.toString().trim()
    val notesValue=notes.text.toString().trim()
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
         put("status","بانتظار التسليم")
         put("customer",customer.text.toString().trim());put("phone",phone.text.toString().trim())
         put("watch",watch.text.toString().trim());put("model",model.text.toString().trim())
         put("problem",problem.text.toString().trim());put("diagnosis",diagnosis.text.toString().trim())
         put("notes",notes.text.toString().trim());put("watchPhoto",photoUri)
         put("currency","USD");put("price",0);put("received",0)
         put("partsTotal",0);put("partsCost",0);put("cost",0)
         put("usedParts",org.json.JSONArray());put("partsStockApplied",false)
         put("createdAt",now)
        }
        db.maintenanceDao().insertNewRepair(MaintenanceRepairEntity(
         id,"native8",number,date,time,"بانتظار التسليم",
         customer.text.toString().trim(),phone.text.toString().trim(),
         watch.text.toString().trim(),model.text.toString().trim(),
         problem.text.toString().trim(),"USD","0","0","",now,raw.toString()
        ))
        if(requestedParts.isNotEmpty()) {
         // Same Room transaction as intake; no stock deduction until delivery.
         val used=JSONArray();var total=BigDecimal.ZERO;var costs=BigDecimal.ZERO
         for(line in requestedParts) {
          val part=db.maintenanceDao().part(line.partId) ?: error("قطعة الصيانة غير موجودة")
          val qty=line.qty.toBigDecimal();val sale=line.price.toBigDecimal()
          require(qty>BigDecimal.ZERO && qty<=part.stock.toBigDecimal() && qty.stripTrailingZeros().scale()<=0) { "الكمية غير متوفرة: ${part.name}" }
          used.put(JSONObject().put("partId",part.id).put("name",part.name).put("qty",line.qty).put("price",line.price).put("cost",part.cost).put("currency",part.currency))
          total=total.add(qty.multiply(sale));costs=costs.add(qty.multiply(part.cost.toBigDecimal()))
         }
         val current=db.maintenanceDao().repair(id) ?: error("لم تحفظ الفاتورة")
         val changed=JSONObject(current.original_json).apply { put("usedParts",used);put("partsTotal",total.toPlainString());put("partsCost",costs.toPlainString()) }
         check(db.maintenanceDao().updateParts(id,current.original_json,changed.toString())==1)
        }
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
  setContentView(ScrollView(this).apply { addView(root) })
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
  if(requestCode==701 && resultCode==RESULT_OK) {
   val uri=data?.data ?: return
   try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
   catch(_:SecurityException) { Toast.makeText(this@MaintenanceIntakeActivity,"تعذر الاحتفاظ بإذن الصورة",Toast.LENGTH_LONG).show();return }
   photoUri=uri.toString()
   photoPreview.setImageURI(uri)
   photoPreview.visibility=View.VISIBLE
   Toast.makeText(this@MaintenanceIntakeActivity,"تم اختيار صورة الساعة",Toast.LENGTH_SHORT).show()
  }
 }
}
