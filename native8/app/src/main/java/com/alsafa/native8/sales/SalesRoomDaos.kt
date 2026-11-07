package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class UnifiedSaleRow(
 val id:String,val number:Long,val created_at:Long,val customer:String,
 val phone:String,val total:String,val paid:String,val status:String,val historical:Int,
 val channel:String?,val original_json:String?
)

@Dao
interface LegacySaleDao {
 @Query("SELECT * FROM sale_returns ORDER BY created_at DESC")
 suspend fun allOperationalReturns():List<RoomSaleReturn>
 @Query("SELECT * FROM legacy_sales_archive WHERE created_at>=:fromMillis AND created_at<:untilExclusive ORDER BY created_at,invoice_number")
 suspend fun historicalInPeriod(fromMillis:Long,untilExclusive:Long):List<LegacySaleEntity>

 @Query("""SELECT id,number,created_at,customer,phone,total,paid,status,0 AS historical,channel,metadata_json AS original_json FROM sales
 WHERE (customer LIKE '%' || :search || '%' OR phone LIKE '%' || :search || '%'
 OR CAST(number AS TEXT) LIKE '%' || :search || '%' OR EXISTS(SELECT 1 FROM sale_lines l WHERE l.sale_id=sales.id AND l.title LIKE '%' || :search || '%')) AND created_at BETWEEN :from AND :to
 UNION ALL
 SELECT legacy_id AS id,invoice_number AS number,created_at,customer,phone,total,
 historical_paid AS paid,status,1 AS historical,NULL AS channel,original_json FROM legacy_sales_archive
 WHERE (customer LIKE '%' || :search || '%' OR phone LIKE '%' || :search || '%'
 OR CAST(invoice_number AS TEXT) LIKE '%' || :search || '%' OR original_json LIKE '%' || :search || '%') AND created_at BETWEEN :from AND :to
 ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset""")
 suspend fun unifiedArchive(search:String,from:Long=0,to:Long=Long.MAX_VALUE,limit:Int=100,offset:Int=0):List<UnifiedSaleRow>

 @Query("SELECT * FROM legacy_sales_archive WHERE source_sha256=:source ORDER BY invoice_number")
 suspend fun legacyBySource(source:String):List<LegacySaleEntity>

 @Query("SELECT * FROM legacy_sales_archive WHERE legacy_id=:id")
 suspend fun legacyById(id:String):LegacySaleEntity?

 @Query("SELECT * FROM legacy_sales_lines WHERE sale_id=:id ORDER BY ordinal")
 suspend fun legacyLines(id:String):List<RoomLegacySaleLine>

 @Query("SELECT COALESCE(MAX(invoice_number),0) FROM legacy_sales_archive")
 suspend fun maxLegacyInvoiceNumber():Long

 @Query("SELECT * FROM legacy_sales_imports WHERE source_sha256=:source LIMIT 1")
 suspend fun importForSource(source:String):RoomLegacySaleImport?

 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertLegacyLine(line:RoomLegacySaleLine)

 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertLegacyImport(value:RoomLegacySaleImport)

 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertLegacy(sale:LegacySaleEntity)
}

@Dao
interface LegacyPaymentDao {
 @Query("SELECT * FROM cashbox_movements ORDER BY created_at DESC LIMIT 1000")
 suspend fun recentCashboxMovements():List<RoomCashboxMovement>
 @Query("SELECT * FROM sale_payments WHERE sale_id=:saleId ORDER BY created_at")
 suspend fun payments(saleId:String):List<RoomSalePayment>

 @Query("SELECT * FROM cashbox_movements WHERE sale_id=:saleId ORDER BY created_at")
 suspend fun cashboxMovements(saleId:String):List<RoomCashboxMovement>

 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertPayment(payment:RoomSalePayment)

 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertCashboxMovement(movement:RoomCashboxMovement)
}

@Dao
interface LegacyReceivablesDao {
 @Query("UPDATE customer_receivables SET customer=:customer, phone=:phone WHERE sale_id=:saleId")
 suspend fun updateCustomer(saleId:String, customer:String, phone:String):Int
 @Query("SELECT * FROM customer_receivables WHERE sale_id=:saleId")
 suspend fun bySaleId(saleId:String):RoomReceivable?

 @Query("SELECT * FROM customer_receivables WHERE balance!='0' AND (customer LIKE '%' || :search || '%' OR phone LIKE '%' || :search || '%')")
 suspend fun searchOutstanding(search:String):List<RoomReceivable>

 @Query("UPDATE customer_receivables SET balance=:newBalance WHERE sale_id=:saleId AND balance=:expectedBalance")
 suspend fun compareAndSetBalance(saleId:String,expectedBalance:String,newBalance:String):Int

 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insert(receivable:RoomReceivable)
}
