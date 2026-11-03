package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SupplierPurchaseDao {
 @Query("SELECT * FROM purchase_drafts WHERE draft_id=:id LIMIT 1")
 suspend fun draftById(id:String):PurchaseDraftEntity?
 @Query("UPDATE purchase_drafts SET status='POSTED' WHERE draft_id=:id AND status='DRAFT'")
 suspend fun markDraftPosted(id:String):Int

 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertSupplier(row:SupplierArchiveEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertPurchase(row:PurchaseArchiveEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertLedger(row:SupplierLedgerArchiveEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertPayment(row:SupplierPaymentEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertCashMovement(row:RoomCashboxMovement)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertDraft(row:PurchaseDraftEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertDraftLines(rows:List<PurchaseDraftLineEntity>)
 @Query("SELECT * FROM purchase_draft_lines WHERE draft_id=:draftId ORDER BY line_id")
 suspend fun draftLines(draftId:String):List<PurchaseDraftLineEntity>
 @Query("SELECT * FROM purchase_drafts ORDER BY created_at DESC")
 suspend fun allDrafts():List<PurchaseDraftEntity>
 @Query("SELECT * FROM supplier_archive ORDER BY name")
 suspend fun allSuppliers():List<SupplierArchiveEntity>
 @Query("SELECT * FROM purchase_archive ORDER BY invoice_date DESC")
 suspend fun allPurchases():List<PurchaseArchiveEntity>
 @Query("SELECT * FROM supplier_archive WHERE source_sha256=:source ORDER BY name")
 suspend fun suppliers(source:String):List<SupplierArchiveEntity>
 @Query("SELECT * FROM purchase_archive WHERE supplier_id=:supplierId ORDER BY invoice_date DESC")
 suspend fun purchases(supplierId:String):List<PurchaseArchiveEntity>
 @Query("SELECT * FROM supplier_ledger_archive WHERE supplier_id=:supplierId ORDER BY archive_key")
 suspend fun historicalLedger(supplierId:String):List<SupplierLedgerArchiveEntity>
 @Query("SELECT * FROM supplier_payments WHERE purchase_archive_key=:purchaseKey ORDER BY paid_at")
 suspend fun newPayments(purchaseKey:String):List<SupplierPaymentEntity>
 @Query("SELECT COUNT(*) FROM supplier_archive WHERE source_sha256=:source")
 suspend fun supplierCount(source:String):Int
 @Query("SELECT COUNT(*) FROM supplier_ledger_archive WHERE source_sha256=:source")
 suspend fun ledgerCount(source:String):Int
 @Query("SELECT COUNT(*) FROM purchase_archive WHERE source_sha256=:source")
 suspend fun purchaseCount(source:String):Int
}
