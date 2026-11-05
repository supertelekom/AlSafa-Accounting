package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StoreSaleProfitDao {
 @Insert(onConflict = OnConflictStrategy.ABORT)
 suspend fun insert(event: StoreSaleProfitEvent)
 @Query("SELECT * FROM store_sale_profit_events WHERE sale_id=:saleId LIMIT 1")
 suspend fun bySale(saleId:String):StoreSaleProfitEvent?
 @Query("SELECT * FROM store_sale_profit_events WHERE recognized_at>=:fromMillis AND recognized_at<:untilExclusive ORDER BY recognized_at,sale_id")
 suspend fun inPeriod(fromMillis:Long,untilExclusive:Long):List<StoreSaleProfitEvent>
}
