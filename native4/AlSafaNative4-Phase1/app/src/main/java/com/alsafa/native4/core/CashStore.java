package com.alsafa.native4.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.alsafa.native4.sync.SyncOperation;
import org.json.JSONObject;
import java.util.UUID;

/** Multi-currency cash ledger. Balances are derived from immutable movements, never overwritten snapshots. */
public final class CashStore {
 private final CoreStore.DbProvider provider; private final String deviceId;
 public CashStore(CoreStore.DbProvider provider,String deviceId){this.provider=provider;this.deviceId=deviceId;}
 public double balance(String drawer,String currency){String d=drawer(drawer),c=currency(currency);try(Cursor q=provider.db().rawQuery("SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0) FROM cash_moves WHERE drawer=? AND currency=?",new String[]{d,c})){return q.moveToFirst()?q.getDouble(0):0;}}
 public String transfer(String from,String to,double amount,String currency)throws Exception{String f=drawer(from),t=drawer(to),c=currency(currency);if(f.equals(t))throw new IllegalArgumentException("الصندوق المصدر والوجهة متطابقان");if(amount<=0||!Double.isFinite(amount))throw new IllegalArgumentException("المبلغ غير صالح");SQLiteDatabase db=provider.db();db.beginTransaction();try{if(balanceTx(db,f,c)+1e-9<amount)throw new IllegalArgumentException("الرصيد غير كافٍ");long now=System.currentTimeMillis();String id="transfer-"+UUID.randomUUID();insertMove(db,id+"-out","TRANSFER",id,"OUT",amount,c,"TRANSFER",f,now);insertMove(db,id+"-in","TRANSFER",id,"IN",amount,c,"TRANSFER",t,now);JSONObject p=new JSONObject().put("transferId",id).put("from",f).put("to",t).put("amount",amount).put("currency",c).put("createdAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.CASH_MOVEMENT,id,p.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 public String adjustment(String drawer,double amount,String currency,String direction,String reason)throws Exception{String d=drawer(drawer),c=currency(currency),dir="OUT".equalsIgnoreCase(direction)?"OUT":"IN";if(amount<=0||!Double.isFinite(amount))throw new IllegalArgumentException("المبلغ غير صالح");SQLiteDatabase db=provider.db();db.beginTransaction();try{if("OUT".equals(dir)&&balanceTx(db,d,c)+1e-9<amount)throw new IllegalArgumentException("الرصيد غير كافٍ");long now=System.currentTimeMillis();String id="cash-"+UUID.randomUUID();insertMove(db,id,"ADJUSTMENT",id,dir,amount,c,"CASH",d,now);JSONObject p=new JSONObject().put("movementId",id).put("drawer",d).put("direction",dir).put("amount",amount).put("currency",c).put("reason",trim(reason)).put("createdAt",now);provider.enqueue(db,SyncOperation.create(SyncOperation.Kind.CASH_MOVEMENT,id,p.toString(),deviceId));db.setTransactionSuccessful();return id;}finally{db.endTransaction();}}
 private static double balanceTx(SQLiteDatabase db,String d,String c){try(Cursor q=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN direction='IN' THEN amount ELSE -amount END),0) FROM cash_moves WHERE drawer=? AND currency=?",new String[]{d,c})){return q.moveToFirst()?q.getDouble(0):0;}}
 private static void insertMove(SQLiteDatabase db,String id,String type,String source,String dir,double amount,String cur,String payment,String drawer,long now){ContentValues v=new ContentValues();v.put("move_id",id);v.put("source_type",type);v.put("source_id",source);v.put("direction",dir);v.put("amount",amount);v.put("currency",cur);v.put("payment_method",payment);v.put("drawer",drawer);v.put("created_at",now);db.insertOrThrow("cash_moves",null,v);}
 private static String drawer(String v){String x=trim(v).toUpperCase();if(x.equals("EXTERNAL")||x.equals("SHAM_CASH")||x.equals("BANK"))return x;return "MAIN";}private static String currency(String v){String x=trim(v).toUpperCase();return x.equals("TRY")||x.equals("SYP")?x:"USD";}private static String trim(String v){return v==null?"":v.trim();}
}
