package com.alsafa.native8.maintenance

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Exact original backup JSON is retained; source IDs never change. */
@Entity(tableName="maintenance_repairs", indices=[Index(value=["invoice_number"]),Index(value=["source_sha256"])])
data class MaintenanceRepairEntity(
 @PrimaryKey val id:String,
 val source_sha256:String,
 val invoice_number:Long,
 val date:String,
 val time:String,
 val status:String,
 val customer:String,
 val phone:String,
 val watch:String,
 val model:String,
 val problem:String,
 val currency:String,
 val price:String,
 val received:String,
 val delivered_date:String,
 val created_at:Long,
 val original_json:String
)

@Entity(tableName="maintenance_parts",indices=[Index(value=["source_sha256"])])
data class MaintenancePartEntity(
 @PrimaryKey val id:String,
 val source_sha256:String,
 val name:String,
 val category:String,
 val barcode:String,
 val stock:String,
 val currency:String,
 val cost:String,
 val sale_price:String,
 val created_at:Long,
 val original_json:String
)
