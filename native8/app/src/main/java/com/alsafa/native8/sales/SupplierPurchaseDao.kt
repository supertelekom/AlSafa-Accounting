package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SupplierPurchaseDao {
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertSupplier(row:SupplierArchiveEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertPurchase(row:PurchaseArchiveEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertLedger(row:SupplierLedgerArchiveEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertPayment(row:SupplierPaymentEntity)
 @Query("SELECT * FROM supplier_archive WHERE source_sha256=:source ORDER BY name")
 suspend fun suppliers(source:String):List<SupplierArchiveEntity>
 @Query("SELECT * FROM purchase_archive WHERE supplier_id=:supplierId ORDER BY invoice_date DESC")
 suspend fun purchases(supplierId:String):List<PurchaseArchiveEntity>
 @Query("SELECT * FROM supplier_ledger_archive WHERE supplier_id=:supplierId ORDER BY archive_key")
 suspend fun historicalLedger(supplierId:String):List<SupplierLedgerArchiveEntity>
 @Query("SELECT * FROM supplier_payments WHERE purchase_archive_key=:purchaseKey ORDER BY paid_at")
 suspend fun newPayments(purchaseKey:String):List<SupplierPaymentEntity>
 @Query("SELECT COUNT(*) FROM purchase_archive WHERE source_sha256=:source")
 suspend fun purchaseCount(source:String):Int
}
