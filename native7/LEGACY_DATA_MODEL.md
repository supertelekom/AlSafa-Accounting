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
