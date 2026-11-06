package com.alsafa.native8.sales
import androidx.room.Dao
import androidx.room.Query

/** Only original invoice-edit fields. No amounts, stock or payment updates. */
data class InvoiceMetadataProjection(val id:String,val number:Long,val date:String,val time:String,val customer:String,val phone:String)
@Dao
interface InvoiceMetadataDao {
 @Query("""SELECT (SELECT COUNT(*) FROM sales WHERE number=:number)+
 (SELECT COUNT(*) FROM legacy_sales_archive WHERE invoice_number=:number AND NOT (:kind='sale' AND legacy_id=:id))+
 (SELECT COUNT(*) FROM maintenance_repairs WHERE invoice_number=:number AND NOT (:kind='repair' AND id=:id))""")
 suspend fun duplicates(kind:String,id:String,number:Long):Int
 @Query("UPDATE legacy_sales_archive SET invoice_number=:number,created_at=:created,customer=:customer,phone=:phone,original_json=:raw WHERE legacy_id=:id AND original_json=:expected")
 suspend fun sale(id:String,expected:String,number:Long,created:Long,customer:String,phone:String,raw:String):Int
 @Query("UPDATE maintenance_repairs SET invoice_number=:number,date=:date,time=:time,customer=:customer,phone=:phone,original_json=:raw WHERE id=:id AND original_json=:expected")
 suspend fun repair(id:String,expected:String,number:Long,date:String,time:String,customer:String,phone:String,raw:String):Int
 @Query("""SELECT legacy_id AS id,invoice_number AS number,strftime('%Y-%m-%d',created_at/1000,'unixepoch','localtime') AS date,strftime('%H:%M:%S',created_at/1000,'unixepoch','localtime') AS time,customer,phone FROM legacy_sales_archive WHERE source_sha256=:source AND instr(original_json,'"nativeInvoiceMetadata"')>0""")
 suspend fun editedSales(source:String):List<InvoiceMetadataProjection>
 @Query("""SELECT id,invoice_number AS number,date,time,customer,phone FROM maintenance_repairs WHERE source_sha256=:source AND instr(original_json,'"nativeInvoiceMetadata"')>0""")
 suspend fun editedRepairs(source:String):List<InvoiceMetadataProjection>
}
