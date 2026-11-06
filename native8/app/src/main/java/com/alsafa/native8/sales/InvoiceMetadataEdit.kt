package com.alsafa.native8.sales
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** invoiceEditModal/saveInvoiceEdit: six metadata fields only. */
data class InvoiceMetadataEdit(val number:Long,val date:String,val time:String,val customer:String,val phone:String,val notes:String) {
 fun apply(original:String):String {
  require(number>0){"رقم الفاتورة يجب أن يكون موجباً"}
  LocalDate.parse(date);LocalTime.parse(time)
  val raw=JSONObject(original)
  if(!raw.has("nativeInvoiceMetadataOriginal")) {
   val before=JSONObject();listOf("invoiceNo","date","time","customer","phone","notes").forEach{key ->if(raw.has(key))before.put(key,raw.get(key))}
   raw.put("nativeInvoiceMetadataOriginal",before)
  }
  raw.put("invoiceNo",number).put("date",date).put("time",time).put("customer",customer.trim()).put("phone",phone.trim()).put("notes",notes.trim()).put("nativeInvoiceMetadata",true)
  return raw.toString()
 }
}
