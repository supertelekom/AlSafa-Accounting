package com.alsafa.native8.sales

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Append-only compensating profit, never edits the original invoice profit. */
@Entity(tableName="store_profit_adjustments",indices=[Index(value=["sale_id"]),Index(value=["created_at"])])
data class StoreProfitAdjustment(
 @PrimaryKey val id:String,
 val sale_id:String,
 val created_at:Long,
 val profit_delta_usd:String,
 val reason:String
)
