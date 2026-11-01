package com.alsafa.native8.sales

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.alsafa.native8.data.operational.OperationalDatabase

/**
 * Room access layer for sales tables in the EXISTING operational database.
 * Never use destructive migration: this file contains real inventory and money records.
 * OperationalDatabase remains the schema owner until its tables are migrated in full.
 */
@Database(
 entities=[
  LegacySaleEntity::class,RoomSale::class,RoomSaleLine::class,
  RoomSalePayment::class,RoomSaleReturn::class,RoomReceivable::class,
  RoomCashboxMovement::class,RoomLegacySaleLine::class,RoomLegacySaleImport::class
 ],
 version=5,
 exportSchema=false
)
abstract class AppDatabase:RoomDatabase() {
 abstract fun legacySaleDao():LegacySaleDao
 abstract fun legacyPaymentDao():LegacyPaymentDao
 abstract fun legacyReceivablesDao():LegacyReceivablesDao

 companion object {
  @Volatile private var instance:AppDatabase?=null
  fun get(context:Context):AppDatabase {
   return instance ?: synchronized(this) {
    instance ?: run {
     // Create the existing operational tables and legacy archive before Room validation.
     val helper=OperationalDatabase(context.applicationContext)
     try { LegacySalesArchiveSchema.ensure(helper.writableDatabase) }
     finally { helper.close() }
     Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,
      "alsafa_native8_operational.db")
      .build().also { instance=it }
    }
   }
  }
 }
}
