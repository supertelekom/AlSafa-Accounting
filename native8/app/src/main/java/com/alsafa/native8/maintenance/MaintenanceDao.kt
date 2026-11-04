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
 @Insert(onConflict=OnConflictStrategy.IGNORE)
 suspend fun insertRepairs(values:List<MaintenanceRepairEntity>):List<Long>
 @Insert(onConflict=OnConflictStrategy.IGNORE)
 suspend fun insertParts(values:List<MaintenancePartEntity>):List<Long>
}
