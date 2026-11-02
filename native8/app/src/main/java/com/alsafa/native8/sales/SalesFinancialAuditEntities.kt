package com.alsafa.native8.sales

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/** Append-only profit recognition per actual collection, never per invoice creation date alone. */
@Entity(tableName="sale_profit_events", indices=[Index(value=["sale_id"]), Index(value=["collected_at"])])
data class SaleProfitEvent(
 @PrimaryKey val payment_id:String,
 val sale_id:String,
 val collected_at:Long,
 val credited_base:String,
 val invoice_net:String,
 val cost_basis:String,
 val recognized_profit:String
)

/** Append-only, explicit FX snapshot for every refunded cash movement. */
@Entity(tableName="sale_refund_allocations",indices=[Index(value=["sale_id"]),Index(value=["payment_id"])])
data class SaleRefundAllocation(
 @PrimaryKey val id:String,
 val sale_id:String,
 val payment_id:String,
 val movement_id:String,
 val cashbox:String,
 val currency:String,
 val refunded_amount:String,
 val credited_base:String,
 val exchange_rate_to_base:String,
 val refunded_at:Long
)

/** Compensating profit entries; the original collection event remains immutable. */
@Entity(tableName="sale_profit_reversals", indices=[Index(value=["sale_id"]),Index(value=["payment_id"])])
data class SaleProfitReversal(
 @PrimaryKey val id:String,
 val sale_id:String,
 val payment_id:String,
 val refund_movement_id:String,
 val reversed_base:String,
 val reversed_profit:String,
 val reversed_at:Long
)
