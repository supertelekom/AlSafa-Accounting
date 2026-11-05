package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SalesFinancialAuditDao {
 @Query("SELECT e.* FROM sale_profit_events e INNER JOIN sales s ON s.id=e.sale_id WHERE s.channel='online' AND e.collected_at>=:fromMillis AND e.collected_at<:untilExclusive ORDER BY e.collected_at,e.payment_id")
 suspend fun verifiedOnlineProfitsInPeriod(fromMillis:Long,untilExclusive:Long):List<SaleProfitEvent>
 @Query("SELECT r.* FROM sale_profit_reversals r INNER JOIN sales s ON s.id=r.sale_id WHERE s.channel='online' AND r.reversed_at>=:fromMillis AND r.reversed_at<:untilExclusive ORDER BY r.reversed_at,r.id")
 suspend fun verifiedOnlineReversalsInPeriod(fromMillis:Long,untilExclusive:Long):List<SaleProfitReversal>

 @Query("SELECT COUNT(*) FROM sale_profit_events e LEFT JOIN sales s ON s.id=e.sale_id WHERE s.id IS NULL OR s.channel IS NULL OR s.channel!='online'")
 suspend fun nonOnlineProfitEventCount():Int

 @Query("SELECT * FROM sale_profit_events WHERE payment_id=:paymentId LIMIT 1")
 suspend fun profitForPayment(paymentId:String):SaleProfitEvent?

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
