package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StoreSaleProfitDao {
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertAdjustment(event:StoreProfitAdjustment)
 @Query("SELECT * FROM store_profit_adjustments WHERE sale_id=:saleId ORDER BY created_at,id")
 suspend fun adjustments(saleId:String):List<StoreProfitAdjustment>
 @Query("SELECT * FROM store_profit_adjustments WHERE created_at>=:fromMillis AND created_at<:untilExclusive ORDER BY created_at,id")
 suspend fun adjustmentsInPeriod(fromMillis:Long,untilExclusive:Long):List<StoreProfitAdjustment>

 @Insert(onConflict = OnConflictStrategy.ABORT)
 suspend fun insert(event: StoreSaleProfitEvent)
 @Query("SELECT * FROM store_sale_profit_events WHERE sale_id=:saleId LIMIT 1")
 suspend fun bySale(saleId:String):StoreSaleProfitEvent?
 @Query("SELECT * FROM store_sale_profit_events WHERE recognized_at>=:fromMillis AND recognized_at<:untilExclusive ORDER BY recognized_at,sale_id")
 suspend fun inPeriod(fromMillis:Long,untilExclusive:Long):List<StoreSaleProfitEvent>
}
