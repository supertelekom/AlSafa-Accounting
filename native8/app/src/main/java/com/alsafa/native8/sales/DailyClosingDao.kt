package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyClosingDao {
 @Query("SELECT * FROM daily_closings_native8 WHERE date=:date LIMIT 1")
 suspend fun byDate(date:String):DailyClosingEntity?
 @Query("SELECT * FROM daily_closings_native8 WHERE date<:date ORDER BY date DESC LIMIT 1")
 suspend fun previous(date:String):DailyClosingEntity?
 @Query("SELECT * FROM daily_closings_native8 ORDER BY date DESC LIMIT 100")
 suspend fun recent():List<DailyClosingEntity>
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insert(row:DailyClosingEntity)
}
