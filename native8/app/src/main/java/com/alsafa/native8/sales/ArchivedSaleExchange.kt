package com.alsafa.native8.sales

import android.content.Context
import androidx.room.withTransaction
import com.alsafa.native8.data.operational.RoomProductStockRepository
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class ArchivedSaleExchange(private val context:Context){
 private val db=AppDatabase.get(context)
 private val stock=RoomProductStockRepository(db)
 private val zero=BigDecimal.ZERO
 private val mc=MathContext.DECIMAL128
 private fun money(v:BigDecimal)=v.setScale(2,RoundingMode.HALF_UP)
 private data class Plan(val row:LegacySaleEntity,val original:JSONObject,val line:JSONObject,val index:Int,
  val balance:ArchivedSaleBalance,val fx:BigDecimal,val preview:RoomSalesEngine.ExchangePreview,
  val returnedNet:BigDecimal,val replacementNet:BigDecimal)

 private suspend fun plan(q:RoomSalesEngine.ExchangeRequest,quote:Boolean):Plan {
  val row=requireNotNull(db.legacySaleDao().legacyById(q.saleId)){"الفاتورة المستعادة غير موجودة"}
  require(row.status!="ملغاة"){"الفاتورة ملغاة"}
  val raw=JSONObject(row.original_json)
  val items=raw.getJSONArray("items")
  val index=q.lineId.substringAfterLast('#').toInt()
  require(q.lineId=="${row.legacy_id}#${index}")
  val line=items.getJSONObject(index)
  val balance=SalesRoomRepository(context).archivedBalance(row)
  val left=line.getStringNumber("qty")-(balance.returnedByLine[index] ?: zero)
  require(q.quantity>zero && q.quantity<=left){"تجاوزت الكمية المتاحة بعد المرتجعات السابقة"}
  val r=q.replacement
  require(r.quantity>zero && r.unitPrice>=zero && r.unitCost>=zero){"راجع سعر وكمية البديل"}
  val same=row.source_sha256==r.productSource && line.optString("productId")==r.productId && r.variantId==null
  if(r.productSource!=MANUAL_SALE_SOURCE)require(stock.currentStock(r.productSource,r.productId,r.variantId).toBigDecimal()+(if(same)q.quantity else zero)>=r.quantity){"مخزون البديل غير كافٍ"}
  val rates=ExchangeRateSettings(context)
  val fx=rates.rate(row.currency,"USD")
  require(q.rate==rates.rate(q.currency,"USD") && fx>zero && q.rate>zero){"تغيّر سعر الصرف"}
  require(UnifiedCashboxBalance.canonical(q.cashbox)!=null && !(q.cashbox=="shamcash" && q.currency=="TRY")){"الصندوق لا يدعم العملة المختارة"}
  val subtotal=(0 until items.length()).sumOf{val x=items.getJSONObject(it);x.getStringNumber("qty")*x.getStringNumber("price")}
  val gross=line.getStringNumber("price")*q.quantity
  val discount=raw.optString("discount","0").toBigDecimal()
  val returnedNet=if(subtotal.signum()==0)zero else gross-(gross*discount).divide(subtotal,mc)
  val replacementNet=(r.unitPrice*r.quantity).divide(fx,mc)
  val taxFactor=BigDecimal.ONE+raw.optString("taxRate","0").toBigDecimal().movePointLeft(2)
  val returned=money(returnedNet*taxFactor);val replacement=money(replacementNet*taxFactor)
  // Prior collection discounts settle debt without becoming refundable cash.
  val effective=(balance.effective-returned+replacement).max(zero)
  val allowance=(balance.effective-balance.paid-balance.due).max(zero)
  val refund=(balance.paid-effective).max(zero)
  val due=(effective-balance.paid-allowance).max(zero)
  val collect=if(quote)zero else q.collect
  val credit=money((collect*q.rate).divide(fx,mc))
  require(collect>=zero && credit<=due && (collect.signum()==0 || credit>zero)){"المقبوض يتجاوز المتبقي أو يقل عن دقة العملة"}
  val settle=SaleExchangeMath.Settlement(effective*fx, money(refund*fx),due*fx,(balance.paid-refund+credit)*fx,(due-credit)*fx)
  val stamp=listOf(row.original_json,balance,fx,q).toString()
  return Plan(row,raw,line,index,balance,fx,RoomSalesEngine.ExchangePreview(stamp,returned*fx,replacement*fx,settle),returnedNet,replacementNet)
 }
 private fun JSONObject.getStringNumber(key:String)=get(key).toString().toBigDecimal()
 suspend fun preview(q:RoomSalesEngine.ExchangeRequest,quote:Boolean)=db.withTransaction{plan(q,quote).preview}
 suspend fun save(q:RoomSalesEngine.ExchangeRequest,expected:String)=db.withTransaction{
  val key="archive-exchange:"+q.requestId
  val current=requireNotNull(db.legacySaleDao().legacyById(q.saleId))
  if(JSONObject(current.original_json).archiveExchanges().any{it.optString("id")==key})return@withTransaction
  require(q.reason.isNotBlank()){"اكتب سبب التسوية"}
  val p=plan(q,false);require(p.preview.fingerprint==expected){"تغيّرت الفاتورة، أعد المعاينة"}
  val s=p.preview.settlement;val fx=p.fx;val row=p.row;val r=q.replacement
  val cashRefund=if(s.refund.signum()>0)RefundAllocationMath.convertExact(s.refund,q.rate) else zero
  if(cashRefund>zero){
   val b=UnifiedCashboxBalance(context,db.supplierPurchaseDao()).calculate(q.cashbox,q.currency)
   require(b.verified && b.total>=cashRefund){"رصيد الصندوق غير كافٍ أو غير مطابق"}
  }
  val pid=p.line.optString("productId")
  val sameStock=pid.isNotBlank() && row.source_sha256==r.productSource && pid==r.productId && r.variantId==null
  if(sameStock){
   val delta=q.quantity-r.quantity
   if(delta.signum()!=0)stock.adjust(row.source_sha256,pid,null,delta.toPlainString(),"تصحيح كمية فاتورة ${row.invoice_number}")
  }else{
  if(pid.isNotBlank() && pid!="null")stock.adjust(row.source_sha256,pid,null,q.quantity.toPlainString(),"مرتجع استبدال فاتورة ${row.invoice_number}")
  if(r.productSource!=MANUAL_SALE_SOURCE)stock.adjust(r.productSource,r.productId,r.variantId,r.quantity.negate().toPlainString(),"بديل فاتورة ${row.invoice_number}")
  }
  val now=System.currentTimeMillis();val day=java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Asia/Damascus"))
  val cash=q.collect-cashRefund
  if(cash.signum()!=0)db.legacyPaymentDao().insertCashboxMovement(RoomCashboxMovement(key,q.cashbox,q.currency,cash.toPlainString(),q.saleId,now,"فرق استبدال فاتورة ${row.invoice_number}"))
  val remaining=money(s.due.divide(fx,mc)).toPlainString()
  val debts=db.legacyReceivablesDao();val debt=debts.bySaleId(q.saleId)
  if(debt==null)debts.insert(RoomReceivable(q.saleId,row.customer,row.phone,remaining))
  else check(debts.compareAndSetBalance(q.saleId,debt.balance,remaining)==1)
  val originalTotal=p.original.getStringNumber("total")
  val online=LegacySaleChannel.isOnline(row.original_json)
  val ratio=if(!online)BigDecimal.ONE else if(originalTotal>zero)p.balance.paid.divide(originalTotal,mc).coerceIn(zero,BigDecimal.ONE) else zero
  // Recognize the change in earned margin once, including cash received in this settlement.
  val prior=SalesRoomRepository(context).archivedReturns(row)+p.original.archiveExchanges()
  val items=p.original.getJSONArray("items")
  val originalCost=(0 until items.length()).sumOf{val i=items.getJSONObject(it);i.getStringNumber("qty")*i.getStringNumber("cost")}
  val costBefore=originalCost+prior.sumOf{it.getStringNumber("replacementCost")-it.getStringNumber("returnedCost")}
  val netBefore=originalTotal-p.original.optString("tax","0").toBigDecimal()-prior.sumOf{it.optString("netCreditAmount",it.optString("creditAmount")).toBigDecimal()}
  val returnedCost=p.line.getStringNumber("cost")*q.quantity
  val replacementCost=(r.unitCost*r.quantity).divide(fx,mc)
  val marginBefore=netBefore-costBefore
  val marginAfter=marginBefore+p.replacementNet-p.returnedNet+returnedCost-replacementCost
  fun earned(margin:BigDecimal,paid:BigDecimal,total:BigDecimal)=if(!online)margin else if(total.signum()==0)zero else margin*paid.divide(total,mc).coerceIn(zero,BigDecimal.ONE)
  val deltaUsd=(earned(marginAfter,s.paid,s.total)-earned(marginBefore,p.balance.paid,p.balance.effective))*fx
  val doc=JSONObject().put("nativeProfitDeltaUsd",deltaUsd.toPlainString()).put("id",key).put("referenceId",q.saleId).put("referenceType","sale").put("kind","exchange")
   .put("itemIndex",p.index).put("qty",q.quantity).put("itemName",p.line.optString("name")).put("replacementName",r.title).put("replacementQty",r.quantity)
   .put("date",day.toLocalDate().toString()).put("time",day.toLocalTime().withNano(0).toString()).put("currency",row.currency)
   .put("returnedValue",money(p.preview.returned.divide(fx,mc))).put("replacementValue",money(p.preview.replacement.divide(fx,mc)))
   .put("creditAmount",money((p.preview.returned-p.preview.replacement).divide(fx,mc))).put("netCreditAmount",p.returnedNet-p.replacementNet)
   .put("returnedCost",p.line.getStringNumber("cost")*q.quantity).put("replacementCost",(r.unitCost*r.quantity).divide(fx,mc))
   .put("recognizedRatio",ratio).put("rateSnapshot",JSONObject().put(row.currency,BigDecimal.ONE.divide(fx,mc)))
   .put("refundAmount",money((cashRefund*q.rate).divide(fx,mc))).put("extraCollected",money((q.collect*q.rate).divide(fx,mc)))
   .put("cashCurrency",q.currency).put("cashCollected",q.collect).put("cashRefunded",cashRefund).put("cashRate",q.rate)
   .put("remainingAfter",remaining).put("vault",q.cashbox).put("reason",q.reason)
  val entries=p.original.optJSONArray("nativeArchiveExchanges") ?: JSONArray()
  p.original.put("nativeArchiveExchanges",entries.put(doc))
  check(db.legacySaleDao().writeExchange(q.saleId,row.original_json,p.original.toString())==1)
 }
}
