package com.alsafa.native8.sales

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName="purchase_draft_lines",indices=[Index(value=["draft_id"])])
data class PurchaseDraftLineEntity(
 @PrimaryKey val line_id:String,
 val draft_id:String,
 val product_source:String,
 val product_id:String,
 val product_name:String,
 val quantity_exact:String,
 val unit_cost_exact:String
)
