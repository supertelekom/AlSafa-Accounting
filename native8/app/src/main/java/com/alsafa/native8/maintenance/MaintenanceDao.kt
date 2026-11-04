package com.alsafa.native8.maintenance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MaintenanceDao {
 @Query("SELECT * FROM maintenance_repairs WHERE customer LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR CAST(invoice_number AS TEXT) LIKE '%' || :query || '%' OR watch LIKE '%' || :query || '%' ORDER BY created_at DESC LIMIT :limit")
 suspend fun search(query:String,limit:Int=250):List<MaintenanceRepairEntity>
 @Query("SELECT * FROM maintenance_repairs WHERE id=:id LIMIT 1")
 suspend fun repair(id:String):MaintenanceRepairEntity?
 @Query("SELECT * FROM maintenance_parts ORDER BY name")
 suspend fun parts():List<MaintenancePartEntity>
 @Query("SELECT COUNT(*) FROM maintenance_repairs WHERE source_sha256=:source")
 suspend fun repairCount(source:String):Int
 @Query("SELECT COALESCE(MAX(invoice_number),0) FROM maintenance_repairs")
 suspend fun maxRepairInvoice():Long
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertNewRepair(value:MaintenanceRepairEntity)
 @Query("UPDATE maintenance_repairs SET customer=:customer, phone=:phone, watch=:watch, model=:model, problem=:problem, status=:status, original_json=:raw WHERE id=:id AND source_sha256='native8'")
 suspend fun editNewRepair(id:String,customer:String,phone:String,watch:String,model:String,problem:String,status:String,raw:String):Int
 @Query("UPDATE maintenance_repairs SET status=:status, original_json=:raw WHERE id=:id")
 suspend fun updateRepairStatus(id:String,status:String,raw:String):Int
 @Insert(onConflict=OnConflictStrategy.IGNORE)
 suspend fun insertRepairs(values:List<MaintenanceRepairEntity>):List<Long>
 @Insert(onConflict=OnConflictStrategy.IGNORE)
 suspend fun insertParts(values:List<MaintenancePartEntity>):List<Long>
}
