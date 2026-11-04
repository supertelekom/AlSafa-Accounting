package com.alsafa.native8.sales

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One immutable daily snapshot per local calendar date. */
@Entity(tableName="daily_closings_native8")
data class DailyClosingEntity(
 @PrimaryKey val date:String,
 val created_at:Long,
 val drawer_actual_json:String,
 val drawer_transfer_json:String,
 val drawer_next_json:String,
 val drawer_difference_json:String,
 val sham_actual_json:String,
 val sham_difference_json:String,
 val notes:String
)
