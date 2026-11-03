package com.alsafa.native8.sales

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Unposted purchase draft: no stock, payable, or cash movement. */
@Entity(tableName="purchase_drafts",indices=[Index(value=["supplier_id"]),Index(value=["created_at"])])
data class PurchaseDraftEntity(
 @PrimaryKey val draft_id:String,
 val supplier_id:String,
 val currency:String,
 val note:String,
 val created_at:Long,
 val status:String="DRAFT"
)
