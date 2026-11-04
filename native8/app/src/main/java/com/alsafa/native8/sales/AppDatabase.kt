package com.alsafa.native8.sales

import android.content.Context
import com.alsafa.native8.maintenance.MaintenanceRepairEntity
import com.alsafa.native8.maintenance.MaintenancePartEntity
import com.alsafa.native8.maintenance.MaintenanceDao
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.alsafa.native8.data.operational.SourceImportEntity
import com.alsafa.native8.data.operational.ProductEntity
import com.alsafa.native8.data.operational.ProductEditEntity
import com.alsafa.native8.data.operational.LocalProductEntity
import com.alsafa.native8.data.operational.ProductVariantEntity
import com.alsafa.native8.data.operational.ProductStockMovementEntity
import com.alsafa.native8.data.operational.OperationalInventoryReadDao
import com.alsafa.native8.data.operational.OperationalInventoryWriteDao

/** Unified Room schema for all 15 operational entities.
 * WARNING: legacy SQLiteOpenHelper call sites must be removed before release.
 * Never use destructive migration for financial or inventory data.
 */
@Database(
 entities=[
  LegacySaleEntity::class,RoomSale::class,RoomSaleLine::class,
  RoomSalePayment::class,RoomSaleReturn::class,RoomReceivable::class,
  RoomCashboxMovement::class,SaleProfitEvent::class,SaleRefundAllocation::class,SaleProfitReversal::class,RoomLegacySaleLine::class,RoomLegacySaleImport::class,
  SourceImportEntity::class,ProductEntity::class,ProductEditEntity::class,
  LocalProductEntity::class,ProductVariantEntity::class,ProductStockMovementEntity::class,
  SupplierArchiveEntity::class,PurchaseArchiveEntity::class,
  SupplierLedgerArchiveEntity::class,SupplierPaymentEntity::class,
  PurchaseDraftEntity::class,PurchaseDraftLineEntity::class,
  MaintenanceRepairEntity::class,MaintenancePartEntity::class,DailyClosingEntity::class,OperationalExpenseEntity::class,OperationalPartnerWithdrawalEntity::class,StoreSaleProfitEvent::class
 ],
 version=18,
 exportSchema=false
)
abstract class AppDatabase:RoomDatabase() {
 abstract fun operationalInventoryReadDao():OperationalInventoryReadDao
 abstract fun operationalInventoryWriteDao():OperationalInventoryWriteDao
 abstract fun maintenanceDao():MaintenanceDao
 abstract fun dailyClosingDao():DailyClosingDao
 abstract fun operationalExpensePartnerDao():OperationalExpensePartnerDao
 abstract fun supplierPurchaseDao():SupplierPurchaseDao
 abstract fun salesPostingDao():SalesPostingDao
 abstract fun salesFinancialAuditDao():SalesFinancialAuditDao
 abstract fun legacySaleDao():LegacySaleDao
 abstract fun legacyPaymentDao():LegacyPaymentDao
 abstract fun legacyReceivablesDao():LegacyReceivablesDao

 companion object {
  @Volatile private var instance:AppDatabase?=null
  @Volatile private var applicationContext:Context?=null
  fun cashContext():Context=applicationContext ?: error("قاعدة البيانات لم تهيأ بعد")
  fun get(context:Context):AppDatabase {
   applicationContext=context.applicationContext
   return instance ?: synchronized(this) {
    instance ?: run {
     Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,
      "alsafa_native8_operational.db")
      .addMigrations(OperationalMigration4To5, OperationalMigration5To6, OperationalMigration6To7, OperationalMigration7To8, OperationalMigration8To9, OperationalMigration9To10, OperationalMigration10To11,OperationalMigration11To12,OperationalMigration12To13,OperationalMigration13To14,OperationalMigration14To15,OperationalMigration15To16,OperationalMigration16To17,OperationalMigration17To18)
      .build().also { instance=it }
    }
   }
  }
 }
}
