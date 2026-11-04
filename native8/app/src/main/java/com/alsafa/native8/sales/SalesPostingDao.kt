package com.alsafa.native8.sales

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** All financial writes are invoked inside AppDatabase.withTransaction. */
@Dao
interface SalesPostingDao {
    @Query("SELECT COALESCE(MAX(number),0) FROM sales")
    suspend fun maxNewNumber(): Long

    @Query("UPDATE sales SET channel=:channel WHERE id=:id AND channel IS NULL AND status!='ملغاة'")
    suspend fun classifyUnverified(id:String,channel:String):Int

    @Query("SELECT COUNT(*) FROM sales WHERE channel IS NULL AND status!='ملغاة'")
    suspend fun unverifiedChannelCount():Int

    @Query("SELECT * FROM sales WHERE id=:id LIMIT 1")
    suspend fun sale(id: String): RoomSale?

    @Query("SELECT * FROM sale_lines WHERE id=:lineId AND sale_id=:saleId LIMIT 1")
    suspend fun line(saleId: String, lineId: String): RoomSaleLine?

    @Query("SELECT * FROM sale_lines WHERE sale_id=:saleId ORDER BY rowid")
    suspend fun lines(saleId: String): List<RoomSaleLine>

    @Query("UPDATE sale_lines SET quantity=:quantity, unit_price=:price WHERE id=:lineId AND sale_id=:saleId AND returned_qty='0'")
    suspend fun correctUnpaidLine(saleId:String,lineId:String,quantity:String,price:String):Int

    @Query("UPDATE sales SET total=:total, discount=:discount WHERE id=:saleId AND paid='0' AND status!='ملغاة'")
    suspend fun correctUnpaidTotal(saleId:String,total:String,discount:String):Int

    @Query("UPDATE sales SET paid=:paid, status=:status WHERE id=:id")
    suspend fun updatePayment(id: String, paid: String, status: String): Int

    @Query("UPDATE sales SET customer=:customer, phone=:phone WHERE id=:id AND status!='ملغاة'")
    suspend fun updateCustomer(id:String, customer:String, phone:String):Int

    @Query("UPDATE sale_lines SET returned_qty=:quantity WHERE id=:id")
    suspend fun updateReturned(id: String, quantity: String): Int

    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insertSale(value: RoomSale)

    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insertLine(value: RoomSaleLine)

    @Insert(onConflict=OnConflictStrategy.ABORT)
    suspend fun insertReturn(value: RoomSaleReturn)
}
