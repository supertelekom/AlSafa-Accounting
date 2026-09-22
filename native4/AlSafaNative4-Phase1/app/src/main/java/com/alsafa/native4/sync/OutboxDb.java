package com.alsafa.native4.sync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local SQLite authority for the isolated Native 4 sale test. */
public final class OutboxDb extends SQLiteOpenHelper {
 public static final class Item { public final String id,kind,recordId,payload; public final int attempts; Item(String i,String k,String r,String p,int a){id=i;kind=k;recordId=r;payload=p;attempts=a;} }
 public static final class SaleResult { public final String saleId,invoiceNo,productName; public final int remainingQty; SaleResult(String s,String i,String p,int q){saleId=s;invoiceNo=i;productName=p;remainingQty=q;} }
 public OutboxDb(Context c){super(c,"alsafa_native4.db",null,5);}
 @Override public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE outbox(operation_id TEXT PRIMARY KEY,operation_kind TEXT NOT NULL,record_id TEXT NOT NULL,payload TEXT NOT NULL,created_at INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'PENDING',attempts INTEGER NOT NULL DEFAULT 0,last_attempt_at INTEGER,last_error TEXT)");
  db.execSQL("CREATE TABLE applied(operation_id TEXT PRIMARY KEY,applied_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE sync_checkpoint(checkpoint_key TEXT PRIMARY KEY,checkpoint_value TEXT NOT NULL,updated_at INTEGER NOT NULL)"); createBusinessTables(db);
 }
 @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){ if(oldVersion<5)createBusinessTables(db); }
 private static void createBusinessTables(SQLiteDatabase db){
  db.execSQL("CREATE TABLE IF NOT EXISTS test_products(product_id TEXT PRIMARY KEY,name TEXT NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>=0),price REAL NOT NULL CHECK(price>=0),created_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS test_sales(sale_id TEXT PRIMARY KEY,invoice_no TEXT NOT NULL UNIQUE,product_id TEXT NOT NULL,product_name TEXT NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>0),unit_price REAL NOT NULL CHECK(unit_price>=0),total REAL NOT NULL CHECK(total>=0),payment_method TEXT NOT NULL,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS test_stock_moves(move_id TEXT PRIMARY KEY,sale_id TEXT NOT NULL,product_id TEXT NOT NULL,before_qty INTEGER NOT NULL,after_qty INTEGER NOT NULL,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS test_cash_moves(move_id TEXT PRIMARY KEY,sale_id TEXT NOT NULL,amount REAL NOT NULL,payment_method TEXT NOT NULL,created_at INTEGER NOT NULL)");
  db.execSQL("CREATE TABLE IF NOT EXISTS audit_log(id INTEGER PRIMARY KEY AUTOINCREMENT,happened_at INTEGER NOT NULL,direction TEXT NOT NULL,operation_id TEXT NOT NULL,operation_kind TEXT NOT NULL,record_id TEXT NOT NULL,detail TEXT NOT NULL)");
 }
 public synchronized String addTestProduct(String name,int quantity,double price){
  String safe=String.valueOf(name).trim();if(safe.isEmpty()||safe.length()>140)throw new IllegalArgumentException("اسم المنتج غير صالح");if(quantity<0)throw new IllegalArgumentException("الكمية لا يمكن أن تكون سالبة");if(!Double.isFinite(price)||price<0)throw new IllegalArgumentException("السعر غير صالح");
  String id="product-"+UUID.randomUUID();ContentValues v=new ContentValues();v.put("product_id",id);v.put("name",safe);v.put("quantity",quantity);v.put("price",price);v.put("created_at",System.currentTimeMillis());writable().insertOrThrow("test_products",null,v);audit("LOCAL_ONLY",id,"PRODUCT_CREATE",id,"منتج تجريبي محفوظ محلياً فقط");return id;
 }
 /** Transaction rule: invoice, stock, cash and audit either all commit or none commit. */
 public synchronized SaleResult recordTestSale(String productId,int quantity,String paymentMethod){
  if(productId==null||productId.trim().isEmpty())throw new IllegalArgumentException("اختر المنتج أولاً");if(quantity<=0)throw new IllegalArgumentException("الكمية يجب أن تكون أكبر من صفر");SQLiteDatabase db=writable();db.beginTransaction();
  try{
   String name;int before;double price;try(Cursor c=db.rawQuery("SELECT name,quantity,price FROM test_products WHERE product_id=?",new String[]{productId})){if(!c.moveToFirst())throw new IllegalArgumentException("المنتج غير موجود");name=c.getString(0);before=c.getInt(1);price=c.getDouble(2);}if(before<quantity)throw new IllegalArgumentException("كمية المخزون غير كافية");
   int after=before-quantity;long now=System.currentTimeMillis();String saleId="sale-"+UUID.randomUUID(),invoice="T-"+now,payment=safePayment(paymentMethod);double total=price*quantity;
   ContentValues sale=new ContentValues();sale.put("sale_id",saleId);sale.put("invoice_no",invoice);sale.put("product_id",productId);sale.put("product_name",name);sale.put("quantity",quantity);sale.put("unit_price",price);sale.put("total",total);sale.put("payment_method",payment);sale.put("created_at",now);db.insertOrThrow("test_sales",null,sale);
   ContentValues product=new ContentValues();product.put("quantity",after);if(db.update("test_products",product,"product_id=? AND quantity>=?",new String[]{productId,String.valueOf(quantity)})!=1)throw new IllegalStateException("تعذر تثبيت خصم المخزون");
   ContentValues stock=new ContentValues();stock.put("move_id","stock-"+UUID.randomUUID());stock.put("sale_id",saleId);stock.put("product_id",productId);stock.put("before_qty",before);stock.put("after_qty",after);stock.put("created_at",now);db.insertOrThrow("test_stock_moves",null,stock);
   ContentValues cash=new ContentValues();cash.put("move_id","cash-"+UUID.randomUUID());cash.put("sale_id",saleId);cash.put("amount",total);cash.put("payment_method",payment);cash.put("created_at",now);db.insertOrThrow("test_cash_moves",null,cash);
   audit(db,"LOCAL_CONFIRMED",saleId,"SALE",saleId,"فاتورة "+invoice+" ثبتت محلياً مع المخزون والخزنة");db.setTransactionSuccessful();return new SaleResult(saleId,invoice,name,after);
  }finally{db.endTransaction();}
 }
 public synchronized String testProductsSummary(){StringBuilder out=new StringBuilder();try(Cursor c=writable().rawQuery("SELECT product_id,name,quantity,price FROM test_products ORDER BY created_at DESC LIMIT 8",null)){while(c.moveToNext())out.append(c.getString(1)).append(" · ").append(c.getInt(2)).append(" قطعة · ").append(c.getDouble(3)).append("\n").append(c.getString(0)).append("\n\n");}return out.length()==0?"لا توجد منتجات تجريبية محفوظة":out.toString();}
 public synchronized String salesSummary(){StringBuilder out=new StringBuilder();try(Cursor c=writable().rawQuery("SELECT invoice_no,product_name,quantity,total,payment_method FROM test_sales ORDER BY created_at DESC LIMIT 8",null)){while(c.moveToNext())out.append("فاتورة ").append(c.getString(0)).append(" · ").append(c.getString(1)).append(" × ").append(c.getInt(2)).append(" · ").append(c.getDouble(3)).append(" · ").append(c.getString(4)).append("\n");}return out.length()==0?"لا توجد فواتير تجريبية محفوظة":out.toString();}
 public synchronized void enqueue(SyncOperation o){o.validate();ContentValues v=new ContentValues();v.put("operation_id",o.id);v.put("operation_kind",o.kind.name());v.put("record_id",o.recordId);v.put("payload",o.payload);v.put("created_at",o.createdAt);v.put("state","PENDING");writable().insertWithOnConflict("outbox",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 public synchronized List<Item> pending(){ArrayList<Item> a=new ArrayList<>();try(Cursor c=writable().rawQuery("SELECT operation_id,operation_kind,record_id,payload,attempts FROM outbox WHERE state='PENDING' ORDER BY created_at",null)){while(c.moveToNext())a.add(new Item(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)));}return a;}
 public synchronized void markAttempt(String id){writable().execSQL("UPDATE outbox SET attempts=attempts+1,last_attempt_at=? WHERE operation_id=?",new Object[]{System.currentTimeMillis(),id});}
 public synchronized void retryLater(String id,String error){ContentValues v=new ContentValues();v.put("state","PENDING");v.put("last_error",safeError(error));writable().update("outbox",v,"operation_id=?",new String[]{id});}
 public synchronized void confirm(String id){ContentValues v=new ContentValues();v.put("state","CONFIRMED");v.putNull("last_error");writable().update("outbox",v,"operation_id=?",new String[]{id});}
 public synchronized boolean wasApplied(String id){try(Cursor c=writable().rawQuery("SELECT 1 FROM applied WHERE operation_id=? LIMIT 1",new String[]{id})){return c.moveToFirst();}}
 public synchronized void markApplied(String id){ContentValues v=new ContentValues();v.put("operation_id",id);v.put("applied_at",System.currentTimeMillis());writable().insertWithOnConflict("applied",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 public synchronized void saveCheckpoint(String k,String value){ContentValues v=new ContentValues();v.put("checkpoint_key",k);v.put("checkpoint_value",value);v.put("updated_at",System.currentTimeMillis());writable().insertWithOnConflict("sync_checkpoint",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 public synchronized String checkpoint(String k){try(Cursor c=writable().rawQuery("SELECT checkpoint_value FROM sync_checkpoint WHERE checkpoint_key=?",new String[]{k})){return c.moveToFirst()?c.getString(0):"0";}}
 public synchronized int pendingCount(){try(Cursor c=writable().rawQuery("SELECT COUNT(*) FROM outbox WHERE state='PENDING'",null)){return c.moveToFirst()?c.getInt(0):0;}}
 private SQLiteDatabase writable(){return getWritableDatabase();}
 private void audit(String d,String op,String k,String r,String detail){audit(writable(),d,op,k,r,detail);}private void audit(SQLiteDatabase db,String d,String op,String k,String r,String detail){ContentValues v=new ContentValues();v.put("happened_at",System.currentTimeMillis());v.put("direction",d);v.put("operation_id",op);v.put("operation_kind",k);v.put("record_id",r);v.put("detail",detail);db.insertOrThrow("audit_log",null,v);}
 private static String safePayment(String value){String x=String.valueOf(value).trim();return x.isEmpty()?"نقدي":x.substring(0,Math.min(40,x.length()));}private static String safeError(String e){String x=String.valueOf(e);return x.substring(0,Math.min(500,x.length()));}
}
