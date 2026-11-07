package com.alsafa.native8.sales

import android.content.Context
import androidx.room.withTransaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Room-backed archive and debt queries. Monetary writes must share a single transaction.
 * Inventory posting still belongs to the existing operational SalesEngine until full migration.
 */
class SalesRoomRepository(private val context:Context) {
 private val db=AppDatabase.get(context)
 suspend fun searchInvoices(query:String,from:Long=0,to:Long=Long.MAX_VALUE):List<UnifiedSaleRow> =
  db.legacySaleDao().unifiedArchive(query.trim(),from,to)

 suspend fun invoiceLines(id:String):List<RoomLegacySaleLine> =
  db.legacySaleDao().legacyLines(id)

 suspend fun debt(id:String):RoomReceivable?=db.legacyReceivablesDao().bySaleId(id)

 suspend fun payments(id:String):List<RoomSalePayment> =db.legacyPaymentDao().payments(id)

 fun archivedReturns(invoice:LegacySaleEntity):List<org.json.JSONObject> =
  com.alsafa.native8.data.migration.LegacyStagingDatabase(context).use{staging->
   val run=staging.readableDatabase.rawQuery("SELECT id FROM import_runs WHERE source_sha256=?",arrayOf(invoice.source_sha256)).use{c->check(c.moveToFirst());c.getLong(0)}
   com.alsafa.native8.data.migration.LegacyReadRepository(staging).financialRows(run,"returns").filter{it.optString("referenceType")=="sale" && it.optString("referenceId")==invoice.legacy_id}
  }

 suspend fun archivedBalance(invoice:LegacySaleEntity):ArchivedSaleBalance =
    com.alsafa.native8.data.migration.LegacyStagingDatabase(context).use{staging->
     val run=staging.readableDatabase.rawQuery("SELECT id FROM import_runs WHERE source_sha256=?",arrayOf(invoice.source_sha256)).use{cursor->
      check(cursor.moveToFirst()){"تعذر العثور على مصدر الفاتورة"};cursor.getLong(0)
     }
     val reader=com.alsafa.native8.data.migration.LegacyReadRepository(staging)
     val newPaid=db.legacyPaymentDao().payments(invoice.legacy_id).sumOf{p->
      require(p.currency==invoice.currency){"تعذر مطابقة عملة التحصيل"};p.amount.toBigDecimal()
     }
     ArchivedSaleBalance.calculate(org.json.JSONObject(invoice.original_json),reader.financialRows(run,"payments"),reader.financialRows(run,"returns"),newPaid)
    }

 suspend fun collectConfirmedHistorical(id:String,cashbox:String,currency:String,amount:BigDecimal):String =
  db.withTransaction {
   require(amount>BigDecimal.ZERO && cashbox.isNotBlank())
   val invoice=requireNotNull(db.legacySaleDao().legacyById(id)) { "الفاتورة التاريخية غير موجودة" }
   require(invoice.reconciliation=="confirmed") { "الفاتورة تحتاج مطابقة محاسبية" }
   require(invoice.currency==currency) { "عملة التحصيل مختلفة" }
   require(invoice.status!="ملغاة"){"الفاتورة ملغاة"}
   val balance=archivedBalance(invoice).due
   val debt=db.legacyReceivablesDao().bySaleId(id)
   require(amount<=balance) { "المبلغ أكبر من الرصيد" }
   val remaining=(balance-amount).toPlainString()
   if(debt==null)db.legacyReceivablesDao().insert(RoomReceivable(id,invoice.customer,invoice.phone,remaining))
   else check(db.legacyReceivablesDao().compareAndSetBalance(id,debt.balance,remaining)==1) { "تغير الرصيد أثناء التحصيل" }
   val receipt=UUID.randomUUID().toString()
   val now=System.currentTimeMillis()
   db.legacyPaymentDao().insertPayment(RoomSalePayment(receipt,id,cashbox,currency,
    amount.toPlainString(),now))
   db.legacyPaymentDao().insertCashboxMovement(RoomCashboxMovement(UUID.randomUUID().toString(),
    cashbox,currency,amount.toPlainString(),id,now,"تحصيل ذمة فاتورة تاريخية"))
   receipt
  }
}
