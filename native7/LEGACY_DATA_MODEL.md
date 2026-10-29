# Native 7 — Legacy data model contract (phase 1)

Source of truth: `AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE`, `assets/main.js`, `const emptyData`, `normalizeBackupFile`, `backupPayloadText`.

## Observed root fields (not invented)

- Scalar/object roots: `version`, `sync`, `settings`, `rates`.
- Record arrays: `products`, `maintenanceParts`, `sales`, `repairs`, `expenses`, `payments`, `partnerWithdrawals`, `cashTransactions`, `dailyClosings`, `purchases`, `suppliers`, `returns`, `usedWatchTransactions`, `customerOrders`, `auditLog`, `discountRules`, `loyaltyLedger`, `productCostHistory`, `smartReceipts`, `productDetailOptions`, `inventoryMovements`, `inventorySessions`, `customerSessions`, `shiftHandovers`.
- Additional backup keys are **not** discarded. They remain in `legacy_sections`.

## Required migration layers

1. **Lossless archive**: `legacy_metadata.original_backup` and `legacy_sections` preserve the original JSON.
2. **Source-derived record staging**: `legacy_records` stores each array item, section name, original ordinal, original ID if supplied, and its complete JSON. This is a staging representation, **not yet the accounting domain model**.
3. **Typed operational tables**: must be designed after inspecting real row shapes, IDs, references, variants, currency/payment rules, and actual backup data. No assumptions about financial columns or foreign keys.
4. **Transactional import**: archive, stage, validate counts, and commit together. On any error rollback. Never merge silently.
5. **Operational migration**: implement per-domain adapters and verify balances against old app before enabling mutations.

## Acceptance gates

- No record, root key, or nested field lost.
- Every array item stored exactly once in staging; missing IDs must not be fabricated as original IDs.
- Import rejects malformed data and never overwrites existing imports.
- Record counts per section match backup; raw original backup can be read back.
- Products/variants, invoices/lines, services, suppliers/debts, stock movements, currencies, payments, cashboxes, partners, and daily closings must be verified before calling the migration complete.
- No Firebase, network synchronization, WebView, GitHub Actions, or modifications outside `native7`.

## Status

The archive and staging layers do not make sales, inventory, or accounting operational. Typed schema and domain logic remain to be implemented and tested.

## Actual backup inspection — 2026-10-26

The 65,903,245-byte original JSON backup was inspected. Observed section counts:
products 558; maintenanceParts 74; sales 111; repairs 308; expenses 22;
payments 36; partnerWithdrawals 72; cashTransactions 722; dailyClosings 34;
purchases 8; suppliers 13; returns 4; auditLog 1732; discountRules 2;
loyaltyLedger 2; productCostHistory 411; productDetailOptions 53;
inventoryMovements 513; supplierLedger 2. Other declared sections have zero records.

Important correction: **supplierLedger exists in the actual backup** although omitted
from the initial emptyData declaration. Its records have supplierId, kind,
amount, currency, date, time, notes, rateSnapshot, and optional corrections/vault.

Verified nested record groups:
- products.fundingLots: purchaseId, partnerId, qtyOriginal, qtyRemaining, unitCost, currency.
- sales.items: productId, qty, price, cost, fundingAllocations; sales.paymentBreakdown.
- repairs.usedParts: partId, qty, price, cost.
- purchases.items: itemKind, itemId, qty, unitCost, lineTotal; purchases.payments.
- suppliers.openingDebts and suppliers.directPayments.
- returns.returnFundingAllocations.
- inventoryMovements.fundingAdjustments.
- supplierLedger.corrections.

Observed relationships are **logical references**, not yet enforced SQLite foreign
keys: sale items -> products, repair parts -> maintenanceParts, purchase items
-> products or maintenanceParts according to itemKind, purchase -> supplier,
payments/returns/cashTransactions -> polymorphic referenceType/referenceId,
inventory movements -> itemType/itemId, supplier ledger -> supplierId.

SQLite v3 now creates section-specific `op_*` tables plus `op_children`.
These are **lossless operational staging tables with indexed original identifiers,
dates, invoice numbers and references**, not yet normalized financial posting
tables. No balances are recomputed and no accounting writes are enabled.
Enforcing foreign keys or generating accounting balances before checking real
reference coverage would be premature. Preserve raw JSON for every row.

## Reference verification against the actual backup (2026-10-26)

Checked original IDs (not inferred names) in the supplied 65.9 MB JSON:
- Purchases -> suppliers: 8 references, 0 unresolved.
- Supplier ledger -> suppliers: 2 references, 0 unresolved.
- Sale line productId -> products: 126 references, 0 unresolved.
- Repair usedParts.partId -> maintenanceParts: 190 references, 0 unresolved.

The typed `domain_*` projection tables and `domain_children` were introduced
in SQLite schema v4. Import occurs in the existing archive transaction.
`legacy_reference_audit` currently checks purchase/supplier-ledger supplier IDs;
nested line-level verification above was performed against the actual backup,
but is not yet enforced at runtime. Polymorphic inventory movements require
`itemType` discrimination and must not be assumed to target products only.

The read-only `LegacyReceivables` Kotlin module ports `paidFor`,
`refundedFor`, `effectiveTotalFor`, `netPaidFor`, `balanceFor`,
`paymentStatusFor` from `assets/main.js`. Its caller must provide the
source-derived sale/repair total; **do not substitute record.total** without
checking `saleTotal` and `repairInvoiceTotal`.

### Outstanding release blockers

- Validate Kotlin/Gradle compilation and import of a full 65.9 MB backup
  without blocking the Android main thread or exceeding memory.
- Complete typed field and child-table mappings; current `domain_children`
  retains raw child JSON and selected fields, not all child attributes.
- Enforce/test polymorphic references, duplicates, and missing IDs without
  destroying historical data.
- Port totals, cost/funding allocation, purchase atomic stock posting,
  returns, currency conversion, daily closings and partner accounting rules.
- Compare all financial outputs with the original application before enabling
  live writes.
