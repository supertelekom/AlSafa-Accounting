package com.alsafa.native8.sales
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName="legacy_sales_archive",indices=[Index(value=["invoice_number"],name="idx_legacy_sales_number")])
data class LegacySaleEntity(
 @PrimaryKey val legacy_id: String,
 val source_sha256: String,
 val invoice_number: Long,
 val created_at: Long,
 val customer: String,
 val phone: String,
 val currency: String,
 val total: String,
 val historical_paid: String,
 val due: String,
 val reconciliation: String,
 val status: String,
 val original_json: String
)
