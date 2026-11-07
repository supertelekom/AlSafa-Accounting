package com.alsafa.native8.data.operational
import com.alsafa.native8.sales.AppDatabase
class RoomProductStockRepository(val db:AppDatabase){fun key(s:String,p:String,v:String?)="$s/$p/$v";suspend fun currentStock(s:String,p:String,v:String?)=db.stock[key(s,p,v)] ?: "0";suspend fun adjust(s:String,p:String,v:String?,q:String,n:String){if(db.failStock && q.toBigDecimal().signum()<0)error("injected stock failure");val k=key(s,p,v);db.stock[k]=((db.stock[k] ?: "0").toBigDecimal()+q.toBigDecimal()).toPlainString()}}
