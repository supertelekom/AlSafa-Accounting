# Real legacy backup schema findings (private sample, 2026-10-18)

The shop owner supplied a real **3.9.353** JSON backup (formatVersion 2). Analyzed locally, without committing the original file, photos, customer details, identifiers or transaction amounts to GitHub. The reference tablet source is v3.9.359.

## Verified collection counts
| Collection | Records |
|---|---:|
| products | 552 |
| maintenanceParts | 72 |
| sales | 86 |
| repairs | 243 |
| expenses | 18 |
| payments | 28 |
| partnerWithdrawals | 49 |
| cashTransactions | 566 |
| dailyClosings | 26 |
| purchases | 6 |
| suppliers | 13 |
| **supplierLedger** | **5** |
| returns | 2 |
| auditLog | 1458 |
| discountRules | 2 |
| loyaltyLedger | 2 |
| productCostHistory | 406 |
| smartReceipts | 2 |
| productDetailOptions | 53 |
| inventoryMovements | 413 |
| inventorySessions | 0 |
| customerSessions | 0 |
| shiftHandovers | 0 |
| usedWatchTransactions | 0 |
| customerOrders | 0 |

The real file is 61,896,278 bytes, larger than the initial 30 MiB preflight cap. The cap was raised to 100 MiB for now; JSON parsing still holds the full document in memory, so memory pressure and image-heavy backups require a streaming parser before production.

## Field mapping clues (actual keys, not values)
- `products`: `id,barcode,stock,currency,salePrice,cost,externalPartnerId,externalPartnerShare,fundingLots,openingEntry`; photos are embedded as data URLs.
- `sales`: `id,invoiceNo,date,time,currency,paymentStatus,paymentMethod,items,subtotal,discount,total,paid,rateSnapshot,stockApplied,paymentCurrency,saleType,onlineStatus`.
- `repairs`: `id,invoiceNo,date,time,status,currency,price,laborPrice,partsTotal,manualCost,partsCost,usedParts,partsStockApplied`.
- `purchases`: `id,supplierId,currency,items,total,paid,paymentVault,fundingSource,fundingPartnerId,fundingTotals,fundingPaid,rateSnapshot`.
- `supplierLedger`: `id,supplierId,kind,amount,currency,date,time,vault,corrections,recoveredFromCashTransactionId`. **Do not omit this collection:** it was missing from the original generic collection list.
- `cashTransactions`: `id,vault,currency,amount,type,category,referenceType,referenceId,date,time,rateSnapshot`.
- `dailyClosings`: `id,date,drawerActual,drawerTransfer,drawerNext,drawerDifference,shamActual,shamDifference`; reconcile nested currency values.
- `inventoryMovements`: `id,itemType,itemId,before,after,change,reason,referenceType,referenceId,fundingAdjustments`.
- `payments`: `id,referenceType,referenceId,amount,currency,paymentCurrency,paymentAmount,method,rateSnapshot,paymentType,onlineCollectionDiscount`.

## Migration safety
Never replay imported `sales`, `payments`, `cashTransactions` and `inventoryMovements` as separate new business actions without identifying shared `referenceType/referenceId`; this would duplicate cash or inventory effects. The first import must be a staged historical-state migration with idempotent source IDs and a reconciliation report, **not** a normal live posting replay.

Exclude old `sync` metadata and per-record `_sync*` fields; exclude five approved UIs, but preserve historical accounting effects. This document contains no real customer or financial values. No production importer is yet implemented or validated.
