package com.alsafa.native8.sales

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Imported source rows remain immutable snapshots. Never replay historical cash or stock. */
@Entity(tableName="supplier_archive", indices=[Index(value=["source_sha256","legacy_id"],unique=true),Index(value=["supplier_id"])])
data class SupplierArchiveEntity(
 @PrimaryKey val archive_key:String, val source_sha256:String,val legacy_id:String,
 val supplier_id:String,val name:String,val original_json:String,val imported_at:Long
)
@Entity(tableName="purchase_archive",indices=[Index(value=["source_sha256","legacy_id"],unique=true),Index(value=["supplier_id","currency"])])
data class PurchaseArchiveEntity(
 @PrimaryKey val archive_key:String,val source_sha256:String,val legacy_id:String,
 val supplier_id:String,val currency:String,val invoice_number:String,val invoice_date:String,
 val total_exact:String,val paid_at_import_exact:String,val original_json:String,val imported_at:Long
)
@Entity(tableName="supplier_ledger_archive",indices=[Index(value=["source_sha256","legacy_id"],unique=true),Index(value=["supplier_id","currency"])])
data class SupplierLedgerArchiveEntity(
 @PrimaryKey val archive_key:String,val source_sha256:String,val legacy_id:String,
 val supplier_id:String,val currency:String,val kind:String,val amount_exact:String,
 val reference_id:String?,val original_json:String,val imported_at:Long
)
@Entity(tableName="supplier_payments",indices=[Index(value=["purchase_archive_key"]),Index(value=["supplier_id","currency"])])
data class SupplierPaymentEntity(
 @PrimaryKey val payment_id:String,val purchase_archive_key:String?,val supplier_id:String,
 val currency:String,val amount_exact:String,val vault:String,val paid_at:Long,
 val cash_movement_id:String
)
