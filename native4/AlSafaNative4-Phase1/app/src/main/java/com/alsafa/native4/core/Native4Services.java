package com.alsafa.native4.core;

/**
 * Single composition root for the migrated 3.9.308 business modules.
 * UI code receives one object instead of constructing stores independently,
 * keeping every module on the same CoreDb and device identity.
 */
public final class Native4Services {
 public final CoreDb db;
 public final CustomerStore customers;
 public final CashTransferStore cash;
 public final RepairPartStore repairParts;
 public final RepairWorkflowStore repairs;
 public final ReservationStore reservations;
 public final PartnerStore partners;
 public final AlsafaCareStore care;
 public final ProductMediaStore productMedia;
 public final AdvancedSaleStore sales;
 public final SupplierStore suppliers;
 public final PurchaseStore purchases;
 public final ReportStore reports;
 public final DailyClosingStore closing;
 public final NextDayOpeningStore nextDayOpening;

 public Native4Services(CoreDb db,String deviceId){
  if(db==null)throw new IllegalArgumentException("CoreDb مطلوب");
  String id=deviceId==null?"":deviceId.trim();
  if(id.isEmpty())throw new IllegalArgumentException("Device ID مطلوب");
  this.db=db;
  customers=new CustomerStore(db,id);
  cash=new CashTransferStore(db,id);
  repairParts=new RepairPartStore(db,id);
  repairs=new RepairWorkflowStore(db,id);
  reservations=new ReservationStore(db,id);
  partners=new PartnerStore(db,id);
  care=new AlsafaCareStore(db,id);
  productMedia=new ProductMediaStore(db,id);
  sales=new AdvancedSaleStore(db,id);
  suppliers=new SupplierStore(db,id);
  purchases=new PurchaseStore(db,id);
  reports=new ReportStore(db);
  closing=new DailyClosingStore(db,id);
  nextDayOpening=new NextDayOpeningStore(db,id);
 }
}
