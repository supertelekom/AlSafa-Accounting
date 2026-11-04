package com.alsafa.native8.sales

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Immutable accounting events. IDs must be supplied by the caller for retry safety. */
@Entity(tableName="operational_expenses")
data class OperationalExpenseEntity(
 @PrimaryKey val id:String,
 val date:String,val time:String,val category:String,val notes:String,
 val currency:String,val amount:String,val payment_method:String,
 val vault:String,val usd_per_unit:String,val created_at:Long,
 val cash_movement_id:String
)
@Entity(tableName="operational_partner_withdrawals")
data class OperationalPartnerWithdrawalEntity(
 @PrimaryKey val id:String,
 val date:String,val time:String,val partner_id:String,
 val currency:String,val amount:String,val vault:String,
 val usd_per_unit:String,val notes:String,val created_at:Long,
 val cash_movement_id:String
)
