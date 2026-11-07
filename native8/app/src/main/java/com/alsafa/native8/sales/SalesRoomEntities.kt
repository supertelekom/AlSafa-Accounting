package com.alsafa.native8.sales

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(tableName="sales",indices=[Index(value=["created_at"],name="idx_sales_created"),Index(value=["number"],unique=true,name="idx_sales_number_unique")])
data class RoomSale(@PrimaryKey val id:String,val number:Long,val created_at:Long,
 val customer:String,val phone:String,val total:String,val discount:String,val paid:String,val status:String,@ColumnInfo(defaultValue="NULL") val channel:String?=null,
 @ColumnInfo(defaultValue="'{}'") val metadata_json:String="{}")

@Entity(tableName="sale_lines")
data class RoomSaleLine(@PrimaryKey val id:String,val sale_id:String,val source:String,
 val product_id:String,val variant_id:String?,val title:String,val quantity:String,
 val unit_price:String,val unit_cost:String,val discount:String,@ColumnInfo(defaultValue="'0'") val returned_qty:String="0")

@Entity(tableName="sale_payments")
data class RoomSalePayment(@PrimaryKey val id:String,val sale_id:String,val cashbox:String,
 val currency:String,val amount:String,val created_at:Long,
 val invoice_currency:String?=null,val fx_rate:String?=null,val credited_amount:String?=null)

@Entity(tableName="sale_returns")
data class RoomSaleReturn(@PrimaryKey val id:String,val sale_id:String,val line_id:String,
 val quantity:String,val created_at:Long,val reason:String)

@Entity(tableName="customer_receivables")
data class RoomReceivable(@PrimaryKey val sale_id:String,val customer:String,val phone:String,val balance:String)

@Entity(tableName="cashbox_movements")
data class RoomCashboxMovement(@PrimaryKey val id:String,val cashbox:String,val currency:String,
 val amount:String,val sale_id:String,val created_at:Long,val note:String)

@Entity(tableName="legacy_sales_lines")
data class RoomLegacySaleLine(@PrimaryKey val id:String,val sale_id:String,val ordinal:Int,val original_json:String)

@Entity(tableName="legacy_sales_imports")
data class RoomLegacySaleImport(@PrimaryKey val source_sha256:String,val imported_at:Long,
 val invoice_count:Int,val review_count:Int)
