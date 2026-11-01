# Native 8 — Phase 1 source contract and cutover gates

## Scope and authority
- Canonical source: `AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE.zip`, especially `assets/main.js`.
- Historical test fixture: `AlSafa-Backup-2026-10-26T06-21-00-520Z.json` (format `alsafa-accounting-backup`, `formatVersion=2`).
- **The fixture is temporary.** The eventual production backup will contain more records. Never hardcode fixture counts, last invoice numbers, dates, balances or IDs into the importer.
- All features, field names, accounting behaviors, UI labels and business workflows must be traced to the original source. Do not invent substitute rules.
- Only change `native8/`; do not modify `native6/` or `native7/`.
- Kotlin/SQLite, tablet-first, offline-only. No cloud synchronization or WebView.

## Observed legacy collection inventory
`products`, `maintenanceParts`, `sales`, `repairs`, `expenses`, `payments`, `partnerWithdrawals`, `cashTransactions`, `dailyClosings`, `purchases`, `suppliers`, `supplierLedger`, `returns`, `usedWatchTransactions`, `customerOrders`, `auditLog`, `discountRules`, `loyaltyLedger`, `productCostHistory`, `smartReceipts`, `productDetailOptions`, `inventoryMovements`, `inventorySessions`, `customerSessions`, `shiftHandovers`. Other top-level fields include `version`, `sync`, `settings`, `rates`. Preserve unknown fields losslessly.

## Legacy backup format
The original `backupPayloadText` exports JSON with `format`, `formatVersion`, `exportedAt`, `appVersion`, `deviceSettings`, and `data`. `normalizeBackupFile` accepts the wrapper and legacy bare-data forms. The legacy `nonDestructiveRestoreMerge` is a device merge algorithm, **not** an appropriate replay strategy for new accounting ledgers.

## Known links from the historical fixture
- `purchases.supplierId -> suppliers.id`: 8 links, none missing.
- `supplierLedger.supplierId -> suppliers.id`: 2 links, none missing.
- `sales.items[].productId -> products.id`: 126 links, none missing.
- `repairs.usedParts[].partId -> maintenanceParts.id`: 190 links, none missing.
- `payments.referenceId -> sales/repairs.id`: 36 links, none missing.
- `returns.referenceId -> sales/repairs.id`: 4 links, none missing.
- `inventoryMovements.itemId -> products/maintenanceParts.id`: 513 links, **5 unresolved** in the current product snapshot. Retain their original history; do not fabricate replacement products or silently drop movements.

## Source accounting paths requiring parity
- `saleSubtotalValue`, `saleTotal`, `saleCost`, `repairInvoiceTotal`.
- `paidFor`, `refundedFor`, `effectiveTotalFor`, `netPaidFor`, `balanceFor`, `onlineCollectionDiscountFor`.
- `convertCurrencyAmount`, `onlineSaleReceiptEvents`, `onlineSaleReceiptCost`.
- `rawVaultBalance`, `vaultBalance`, `addCashTransaction`, `recordIncomingPayment`.
- `saveSale`, `saveFatherSale`, `savePayment`, `saveRepair`, `saveRepairDelivery`, `savePurchase`, `savePurchasePayment`, `saveSupplierOpeningDebt`, `saveSupplierDirectPayment`, `saveExpense`, `saveCashMovement`, `saveSaleReturn`, `saveRepairReturn`, `commitData`.
- Preserve original transaction `date`; never substitute `createdAt` for reporting dates.

## Import and cutover acceptance criteria
1. Preflight every incoming backup: envelope/version, required collections, JSON integrity, full file SHA-256, original export date, all actual section counts and unknown fields.
2. Store immutable original payload/record identity and a complete import manifest before any operational posting.
3. Import must be atomic and idempotent; rollback on validation failure. Never silently merge production backups or overwrite the current database.
4. Never replay sales, payments, cash transactions and inventory movements as independent new actions. Reconcile shared original references and their historical effects first.
5. Validate all references, original IDs, nested rows, currencies, FX snapshots, supplier debt, inventory funding lots, closing carryovers and partner periods. Report historical missing references rather than deleting records.
6. Compare invoice-by-invoice and day-by-day results against original JavaScript calculations. Do not enable financial writes until tests pass.
7. At cutover, inspect a **fresh larger production backup**, re-run all checks on its own contents, preview changes, back up existing Native 8 data, and activate only after reconciliation.
8. Do not transfer old security credentials or cloud-sync state into Native 8 authorization.

## Current state
This document is a source-derived engineering contract, **not** a claim that the new SQLite operational schema, final reconciliation, or Android build is complete. The next step is to finish constructor/write-site enumeration and resolve historical reference semantics, then design the typed schema.
