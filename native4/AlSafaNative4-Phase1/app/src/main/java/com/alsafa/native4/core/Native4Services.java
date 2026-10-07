package com.alsafa.native4.core;

/** Single composition root for migrated business modules; every writer shares one CoreDb and device identity. */
public final class Native4Services {
 public final CoreDb db;
 public final ProductStore products;
 public final ProductVariantStore productVariants;
 public final CustomerStore customers;
 public final CashTransferStore cash;
 public final RepairPartStore repairParts;public final QuickRepairButtonStore quickRepairButtons;
 public final RepairPartReservationStore repairPartReservations;
 public final RepairMediaStore repairMedia;
 public final DirectRepairPartSaleStore directPartSales;
 public final RepairWorkflowStore repairs;public final RepairUpdateStore repairUpdates;
 public final ReservationStore reservations;
 public final PartnerStore partners;
 public final AlsafaCareStore care;
 public final ProductMediaStore productMedia;
 public final AdvancedSaleStore sales;
 public final SaleInvoiceSnapshotStore saleInvoices;
 public final OnlineSaleSettlementStore onlineSales;
 public final SupplierStore suppliers;
 public final PurchaseStore purchases;
 public final ReportStore reports;
 public final DailyClosingStore closing;
 public final NextDayOpeningStore nextDayOpening;
 public final DeveloperTodaySummaryStore developerToday;
 public final MixedCurrencyChangeStore mixedCurrencyChange;
 public final DailyFxStore dailyFx;
 public Native4Services(CoreDb db,String deviceId){
  if(db==null)throw new IllegalArgumentException("CoreDb مطلوب");String id=deviceId==null?"":deviceId.trim();if(id.isEmpty())throw new IllegalArgumentException("Device ID مطلوب");this.db=db;
  products=new ProductStore(db,id);productVariants=new ProductVariantStore(db,id);customers=new CustomerStore(db,id);cash=new CashTransferStore(db,id);repairParts=new RepairPartStore(db,id);quickRepairButtons=new QuickRepairButtonStore(db);repairPartReservations=new RepairPartReservationStore(db);repairMedia=new RepairMediaStore(db);directPartSales=new DirectRepairPartSaleStore(db,id);repairs=new RepairWorkflowStore(db,id);repairUpdates=new RepairUpdateStore(db);reservations=new ReservationStore(db,id);partners=new PartnerStore(db,id);care=new AlsafaCareStore(db,id);productMedia=new ProductMediaStore(db,id);sales=new AdvancedSaleStore(db,id);saleInvoices=new SaleInvoiceSnapshotStore(db);onlineSales=new OnlineSaleSettlementStore(db,id);suppliers=new SupplierStore(db,id);purchases=new PurchaseStore(db,id);reports=new ReportStore(db);closing=new DailyClosingStore(db,id);nextDayOpening=new NextDayOpeningStore(db,id);developerToday=new DeveloperTodaySummaryStore(db);mixedCurrencyChange=new MixedCurrencyChangeStore(db,id);dailyFx=new DailyFxStore(db);
 }
}
