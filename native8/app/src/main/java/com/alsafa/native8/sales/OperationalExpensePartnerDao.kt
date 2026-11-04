package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OperationalExpensePartnerDao {
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertExpense(row:OperationalExpenseEntity)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertWithdrawal(row:OperationalPartnerWithdrawalEntity)
 @Query("SELECT * FROM operational_expenses WHERE id=:id LIMIT 1")
 suspend fun expense(id:String):OperationalExpenseEntity?
 @Query("SELECT * FROM operational_partner_withdrawals WHERE id=:id LIMIT 1")
 suspend fun withdrawal(id:String):OperationalPartnerWithdrawalEntity?
 @Query("SELECT * FROM operational_expenses WHERE date>=:from AND date<=:until ORDER BY date,time,id")
 suspend fun expenses(from:String,until:String):List<OperationalExpenseEntity>
 @Query("SELECT * FROM operational_partner_withdrawals WHERE date>=:from AND date<=:until ORDER BY date,time,id")
 suspend fun withdrawals(from:String,until:String):List<OperationalPartnerWithdrawalEntity>
}
