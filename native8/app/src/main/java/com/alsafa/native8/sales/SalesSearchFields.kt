package com.alsafa.native8.sales
import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader

/** Search metadata only: skip image strings/arrays without materialising them. */
object SalesSearchFields {
 private val keys=setOf("name","color","brand","model","barcode","ref","reference","sku","id","stock","salePrice","cost","currency")
 fun read(raw:String):Map<String,String> {
  val fields=mutableMapOf<String,String>()
  JsonReader(StringReader(raw)).use { reader ->
   reader.beginObject()
   while(reader.hasNext()) {
    val key=reader.nextName()
    if(key in keys && reader.peek() in setOf(JsonToken.STRING,JsonToken.NUMBER)) fields[key]=reader.nextString()
    else reader.skipValue()
   }
   reader.endObject()
  }
  return fields
 }
 fun matches(fields:Map<String,String>,query:String,exactBarcode:Boolean=false):Boolean {
  val q=query.trim();if(q.isEmpty())return false
  return if(exactBarcode) fields["barcode"]==q else
   listOf("name","color","model","brand","barcode","ref","reference","sku","id").joinToString(" "){fields[it].orEmpty()}.contains(q,true)
 }
}
