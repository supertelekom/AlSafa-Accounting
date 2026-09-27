package com.alsafa.native4.core;

import java.util.List;

/** UI-facing purchase coordinator. Keeps Activity free of accounting rules and manual stored codes. */
public final class PurchaseUiController {
 private final CoreProductCatalog catalog;
 private final PurchaseStore store;
 private CoreProductCatalog.ProductHit selected;

 public PurchaseUiController(CoreStore.DbProvider provider,String deviceId){
  catalog=new CoreProductCatalog(provider);
  store=new PurchaseStore(provider,deviceId);
 }

 public List<CoreProductCatalog.ProductHit> search(String query){return catalog.search(query,20);}
 public CoreProductCatalog.ProductHit select(String productId){selected=catalog.get(productId);if(selected==null)throw new IllegalArgumentException("المنتج غير موجود");return selected;}
 public CoreProductCatalog.ProductHit selected(){return selected;}
 public void clearSelection(){selected=null;}

 public PurchaseStore.Result save(int quantity,double unitCost,int currencyPosition,int sourcePosition,String supplierId,String invoiceRef)throws Exception{
  if(selected==null)throw new IllegalArgumentException("اختر المنتج أولاً");
  String currency=AccountingChoices.codeAt(AccountingChoices.CURRENCY_CODES,currencyPosition);
  String source=AccountingChoices.codeAt(AccountingChoices.PURCHASE_SOURCE_CODES,sourcePosition);
  PurchaseStore.Result result=store.record(selected.id,quantity,unitCost,currency,supplierId,invoiceRef,source);
  selected=catalog.get(selected.id);
  return result;
 }
}
