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
 @Query("SELECT * FROM maintenance_parts WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY name LIMIT 500")
 suspend fun searchParts(query:String):List<MaintenancePartEntity>
 @Query("SELECT COUNT(*) FROM maintenance_parts WHERE name=:name COLLATE NOCASE AND id!=:exclude")
 suspend fun partNameConflict(name:String,exclude:String):Int
 @Query("SELECT COUNT(*) FROM maintenance_parts WHERE barcode=:barcode AND id!=:exclude")
 suspend fun partBarcodeConflict(barcode:String,exclude:String):Int
 @Query("SELECT COUNT(*) FROM products WHERE barcode=:barcode")
 suspend fun productBarcodeConflict(barcode:String):Int
 @Query("SELECT COUNT(*) FROM maintenance_repairs WHERE original_json LIKE '%' || :partId || '%'")
 suspend fun partReferenced(partId:String):Int
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertNewPart(part:MaintenancePartEntity)
 @Query("UPDATE maintenance_parts SET name=:name,category=:category,barcode=:barcode,stock=:stock,currency=:currency,cost=:cost,sale_price=:salePrice,original_json=:raw WHERE id=:id AND source_sha256='native8' AND original_json=:expected")
 suspend fun updatePartFields(id:String,name:String,category:String,barcode:String,stock:String,currency:String,cost:String,salePrice:String,raw:String,expected:String):Int
 suspend fun updateNewPart(part:MaintenancePartEntity,expected:String):Int =
  updatePartFields(part.id,part.name,part.category,part.barcode,part.stock,part.currency,part.cost,part.sale_price,part.original_json,expected)
 @Query("UPDATE maintenance_parts SET name=:name,category=:category,barcode=:barcode,stock=:stock,currency=:currency,cost=:cost,sale_price=:salePrice,original_json=:raw WHERE id=:id AND original_json=:expected")
 suspend fun updateImportedPartFields(id:String,name:String,category:String,barcode:String,stock:String,currency:String,cost:String,salePrice:String,raw:String,expected:String):Int
 @Query("DELETE FROM maintenance_parts WHERE id=:id AND source_sha256='native8'")
 suspend fun deleteNewPart(id:String):Int
 @Query("SELECT * FROM maintenance_parts ORDER BY name")
 suspend fun parts():List<MaintenancePartEntity>
 @Query("SELECT * FROM maintenance_parts WHERE id=:id LIMIT 1")
 suspend fun part(id:String):MaintenancePartEntity?
 @Query("UPDATE maintenance_parts SET stock=:stock WHERE id=:id AND stock=:expected")
 suspend fun changePartStock(id:String,expected:String,stock:String):Int
 @Query("UPDATE maintenance_repairs SET original_json=:raw WHERE id=:id AND source_sha256='native8' AND status!='تم التسليم' AND original_json=:expected")
 suspend fun updateParts(id:String,expected:String,raw:String):Int
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
 @Query("UPDATE maintenance_repairs SET status=:status,price=:price,received=:received,delivered_date=:deliveredDate,original_json=:raw WHERE id=:id AND source_sha256='native8' AND status!='تم التسليم' AND original_json=:expected")
 suspend fun finalizeDelivery(id:String,status:String,price:String,received:String,deliveredDate:String,raw:String,expected:String):Int
 @Insert(onConflict=OnConflictStrategy.IGNORE)
 suspend fun insertRepairs(values:List<MaintenanceRepairEntity>):List<Long>
 @Insert(onConflict=OnConflictStrategy.IGNORE)
 suspend fun insertParts(values:List<MaintenancePartEntity>):List<Long>
}
