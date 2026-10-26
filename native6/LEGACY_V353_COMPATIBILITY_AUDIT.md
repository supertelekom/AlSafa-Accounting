# Native 6 — Legacy v3.9.353 compatibility gate (2026-10-26)

## Evidence inspected
- Source data: `AlSafa-Back.json`, format `alsafa-accounting-backup`, formatVersion `2`, appVersion `3.9.353`, exported 2026-10-18.
- Native implementation: `LegacySnapshotImport.kt`, `LegacyRestoreReceipt.kt`, `Backup.kt`, `Store.kt`, `StoreDb.kt`, `MainActivity.kt`, `TabletDashboardActivity.kt` on `native6-development`.
- **The actual old v3.9.353 source ZIP has not been obtained for this audit. UI parity is therefore UNVERIFIED.** Never infer screen-by-screen parity from a JSON backup.

## Collection-to-operational mapping (counts from supplied backup)
| Legacy collection | Count | Current Native 6 behavior | Gap to full compatibility |
|---|---:|---|---|
| products | 553 | Imported as watch inventory and metadata | Validate every field, color variant, images, prices and cost lots against old UI |
| maintenanceParts | 72 | Imported as part inventory | Validate historical part use and valuation |
| repairs | 245 | Imported into repairs table with old JSON details | Old invoices/receipts not linked to operational repair invoices |
| suppliers | 13 | Imported into suppliers table; raw records retained | Reconcile opening debts and supplier transactions |
| sales | 93 | Immutable archive only | Operational invoice lines, invoice numbers, paid/unpaid, returns, COGS |
| purchases | 6 | Immutable archive only | Supplier payable, funding and stock movement reconstruction |
| payments | 28 | Immutable archive only | Payment reference and cash receipt reconciliation |
| supplierLedger | 5 | Immutable archive only | Supplier debt and historical payments |
| cashTransactions | 578 | Immutable archive only | Cash balances by vault, currency, actual date; avoid double counting |
| dailyClosings | 27 | Immutable archive only | Daily rollover and difference reconciliation |
| partnerWithdrawals | 49 | Immutable archive only | Partner/day/month mapping; preserve 40/35/25 percent historical rules |
| returns | 2 | Immutable archive only | Refund/debt/stock corrections tied to source invoice |
| expenses | 18 | Immutable archive only | Expense accounts and cash impact |
| auditLog | 1469 | Immutable archive only | Operational audit history |
| inventoryMovements | 424 | Immutable archive only | Historic movement timeline; no double stock deduction |
| productCostHistory | 407 | Immutable archive only | Cost lots and valuation reconciliation |
| discountRules | 2 | Immutable archive only | Rules/settings |
| loyaltyLedger | 2 | Immutable archive only | Customer loyalty ledger |
| smartReceipts | 2 | Immutable archive only | Receipt and source document linkage |
| productDetailOptions | 53 | Immutable archive only | Product form choices |
| Other arrays/configuration | Variable | Preserved as immutable raw archive | Explicit mapping and verification needed |

## Hard accounting invariants before financial activation
1. Source IDs, dates, invoiceNo and links remain stable. Preserve original raw records.
2. Never book both a cashTransaction and its corresponding sale/payment/purchase as two cash movements.
3. Reconstruct per-currency vault balances (USD, TRY, SYP) with recorded exchanges, transfers, actual dates and closings.
4. Never treat `paid` as an extra receipt if a referenced payment/cash transaction already posted it.
5. Reconstruct historical returns before calculating receivables, revenue and inventory.
6. Supplier debt = opening balance + purchases on credit - actual supplier payments (deduplicate mirrored cash entries).
7. Partner withdrawals stay on actual transaction date/month. Monthly profit reports contain exactly one month and exclude old balances from that month's profit.
8. Reconcile counts, per-currency totals, invoice numbers and stock quantities with source before enabling operational balances.
9. Import into a new, empty test DB; do not modify old application or production backup.

## Immediate safeguards implemented
- Imported repairs receive `_legacyImported=true` in JSON details.
- Delivering such repairs as new invoices is blocked until historical invoice/payment reconciliation is implemented. This avoids duplicating revenue.
- **This is a safety gate, not completion of migration.** Current `LegacyRestoreReceipt` intentionally requires `financialPostingStatus=NOT_POSTED`.

## Next work required
1. Obtain exact v3.9.353 source ZIP and produce old-vs-native screen/function inventory.
2. Implement deterministic legacy invoice/receipt/cash/returns reconciliation with fixture-based assertions.
3. Activate historical records only after independently reconciled cash and payable opening balances; no blind replay.
4. Build APK and run complete 60 MB backup restore in an isolated tablet test environment.

## Source-backup read-only integrity audit
- JSON parse: PASS (Python standard library, no data mutation).
- Exact size: 61,954,988 bytes.
- SHA-256: `8b9ea135719f3e20186bd2274b3487c1bd91a51413efacc76fadbd96cb63ae88`.
- Required collections present: products, maintenanceParts, repairs, suppliers, sales, purchases, payments, cashTransactions, dailyClosings, partnerWithdrawals, returns.
- Missing/blank IDs in those 11 collections: **0**.
- Duplicate IDs *within each of those collections*: **0**.
- These are structural checks, **not** evidence that financial relationships, invoice numbering, payment totals, or screen parity are correct.
- No Android build, instrumentation run or actual SQLite restore was performed in this audit.

## 2026-10-26: Actual backup reference reconciliation
- Read-only local Python verification of the original AlSafa Backup v2 JSON: **1,689 records** across 13 core collections checked for source ID duplication and financial references.
- **0 duplicate IDs and 0 broken references** in those collections.
- Payment references: **28 linked**. Return references: **2 linked**.
- Cash references: **415 linked to source records**, **56 workflow references** (currency exchanges, opening balances and payment corrections) requiring dedicated semantic matching.
- These results are **structural only**, not financial reconciliation. No balance activation, APK build or actual Android restore is proven by these tests.
- Reproducible independent verifier added: `native6/tools/verify_legacy_v2.py`.
