package com.alsafa.native8.sales

import android.content.ContentValues
import android.content.Context
import com.alsafa.native8.data.operational.OperationalDatabase
import java.math.BigDecimal
import java.util.UUID

/** Collect confirmed historical debt without editing original invoice or replaying old cash. */
class LegacyReceivablesCollector(private val context: Context) {
 fun collect(id: String, payment: SalePayment): String {
  val helper=OperationalDatabase(context)
  val db=helper.writableDatabase
  db.beginTransaction()
  try {
   LegacySalesArchiveSchema.ensure(db)
   val currency=db.rawQuery("SELECT currency FROM legacy_sales_archive WHERE legacy_id=? AND reconciliation='confirmed'",arrayOf(id)).use {
    require(it.moveToFirst()) { "الفاتورة غير مطابقة" };it.getString(0)
   }
   require(currency==payment.currency) { "عملة القبض تختلف عن عملة الفاتورة" }
   require(payment.amount>BigDecimal.ZERO && payment.cashbox.isNotBlank())
   val balance=db.rawQuery("SELECT balance FROM customer_receivables WHERE sale_id=?",arrayOf(id)).use {
    require(it.moveToFirst()) { "لا توجد ذمة" };it.getString(0).toBigDecimal()
   }
   require(payment.amount<=balance) { "المبلغ أكبر من الدين" }
   db.execSQL("UPDATE customer_receivables SET balance=? WHERE sale_id=?",
    arrayOf((balance-payment.amount).toPlainString(),id))
   val receiptId=UUID.randomUUID().toString()
   val now=System.currentTimeMillis()
   db.insertOrThrow("sale_payments",null,ContentValues().apply {
    put("id",receiptId);put("sale_id",id);put("cashbox",payment.cashbox)
    put("currency",payment.currency);put("amount",payment.amount.toPlainString());put("created_at",now)
   })
   db.insertOrThrow("cashbox_movements",null,ContentValues().apply {
    put("id",UUID.randomUUID().toString());put("cashbox",payment.cashbox)
    put("currency",payment.currency);put("amount",payment.amount.toPlainString())
    put("sale_id",id);put("created_at",now);put("note","تحصيل ذمة فاتورة تاريخية")
   })
   db.setTransactionSuccessful()
   return receiptId
  } finally {db.endTransaction();helper.close()}
 }
}
