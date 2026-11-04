package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SalesFinancialAuditDao {
 @Query("SELECT * FROM sale_profit_events WHERE collected_at>=:fromMillis AND collected_at<:untilExclusive ORDER BY collected_at,payment_id")
 suspend fun profitEventsInPeriod(fromMillis:Long,untilExclusive:Long):List<SaleProfitEvent>
 @Query("SELECT * FROM sale_profit_reversals WHERE reversed_at>=:fromMillis AND reversed_at<:untilExclusive ORDER BY reversed_at,id")
 suspend fun profitReversalsInPeriod(fromMillis:Long,untilExclusive:Long):List<SaleProfitReversal>
 @Query("SELECT * FROM sale_refund_allocations WHERE refunded_at>=:fromMillis AND refunded_at<:untilExclusive ORDER BY refunded_at,id")
 suspend fun refundAllocationsInPeriod(fromMillis:Long,untilExclusive:Long):List<SaleRefundAllocation>

 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertProfit(event:SaleProfitEvent)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertRefund(allocation:SaleRefundAllocation)
 @Insert(onConflict=OnConflictStrategy.ABORT)
 suspend fun insertReversal(value:SaleProfitReversal)
 @Query("SELECT * FROM sale_profit_reversals WHERE sale_id=:saleId ORDER BY reversed_at,id")
 suspend fun reversals(saleId:String):List<SaleProfitReversal>
 @Query("SELECT * FROM sale_profit_events WHERE sale_id=:saleId ORDER BY collected_at,payment_id")
 suspend fun profits(saleId:String):List<SaleProfitEvent>
 @Query("SELECT * FROM sale_refund_allocations WHERE sale_id=:saleId ORDER BY refunded_at,id")
 suspend fun refunds(saleId:String):List<SaleRefundAllocation>
 @Query("SELECT * FROM sale_refund_allocations WHERE payment_id=:paymentId")
 suspend fun refundsForPayment(paymentId:String):List<SaleRefundAllocation>
}
