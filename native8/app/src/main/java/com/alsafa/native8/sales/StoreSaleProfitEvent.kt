package com.alsafa.native8.sales

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One immutable invoice-date profit entry per confirmed STORE sale. */
@Entity(tableName="store_sale_profit_events",indices=[Index(value=["recognized_at"])])
data class StoreSaleProfitEvent(
 @PrimaryKey val sale_id:String,
 val recognized_at:Long,
 val net_revenue_usd:String,
 val cost_usd:String,
 val profit_usd:String
)
