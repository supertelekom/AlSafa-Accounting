# Native 5 approved feature scope — AlSafa tablet v3.9.359

Approved by store owner on 2026-10-18. The provided reference archive is `AlSafaAccounting-v3.9.359-TABLET-SUPPLIER-LEDGER-IMPORT-SOURCE.zip`. It contains packaged Android assets (`assets/main.js`, `assets/index.html`, `assets/legacy-migration.html`, DEX and resources), not a full original Android Gradle project. Treat it as a behavior/data-format reference; do not copy the old synchronization code.

## Explicit exclusions (original numbered feature inventory)
- **17** — quick barcode inventory counting screen. Preserve barcode scanning/search for products, checkout, and repair parts.
- **34** — customer sales session and bundled selection workflow. Preserve customer records, invoices, reservations and debts.
- **58** — watch selection advisor by budget/specifications.
- **59** — price advisor.
- **60** — modern AlSafa center and smart tools.

All other features in the previously presented 66-item numbered inventory are IN SCOPE, subject to accounting correctness and safe redesign. No unspecified features are excluded.

## Required preservation
- Two modes: simplified and advanced; fast/full sales, invoices, receipts and returns; mixed currencies with explicit rates; online dispatch and collection; PDF/printing/WhatsApp.
- Product catalog, variants, images, REF, stock, barcode search, labels, duplicates, reorder alerts, stock valuation and cost history.
- Repair tickets, repair parts and inventory, statuses, delivery, warranty, customer balances, bookings and message templates.
- Suppliers, purchase invoices, supplier ledgers, debts, settlements and partner-funded purchases.
- Drawer USD/TRY/SYP; Sham Cash USD/SYP; external cashbox/bank; transfers, owner withdrawals, opening balances, expenses, closing, carryover, variances and immutable reports.
- Family and external partner capital, shares and reports; store configuration, PIN, shift handover, audit/activity history and backup/restore.
- Import of the ACTUAL last production backup into Native 5, with read-only inspection, staging, full per-currency reconciliation, explicit approval and rollback strategy.

## New sync, not old sync
Tablet SQLite is authoritative. Every committed change (including nonfinancial edits) must atomically create a durable outbox event. Background upload to Firestore is one-way, retryable and idempotent. No cloud overwrite of local ledger and no reuse of legacy sync metadata.

## Migration and delivery gates
1. Extract reference behavior and backup/export format from v3.9.359 packaged assets.
2. Create a source-field-to-Native5 schema mapping and acceptance tests for each retained feature.
3. Implement accounting-safe functionality, with local transaction + outbox, then tablet UI.
4. Dry-run a real backup import, compare inventory, receivables, payables, vaults by currency and historical realized profit; reject any unexplained difference.
5. Validate tablet builds, offline scenarios, process restart and cloud retries before use in the store.

This scope approval does not mean the features or importer are already implemented or tested.
