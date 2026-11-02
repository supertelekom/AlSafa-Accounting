# Native 8 financial integrity audit — 2026-11-02

Scope: native8/ on native6-development. No UI or production data changes.

## Implemented

`RoomFinancialIntegrityAudit.inspect(db)` performs **read-only** SQL checks for:
- Orphaned sale_lines, sale_payments, sale_returns, cashbox_movements and customer_receivables.
- Returns referring to a nonexistent line or a line belonging to a different sale.
- Duplicate new invoice numbers and collisions between new and historical invoice numbers.
- SQLite PRAGMA foreign_key_check violations.

The existing `LegacyArchiveAudit.inspect(runId)` compares staged source invoice IDs, dates, customer, phone, currency, total, original JSON and item snapshots against Room. It checks imported product counts and checks that historical paid is not less than raw paid. It does **not** yet prove full reconciliation of payments and due against payment history.

## Code findings

- `sales.number` is uniquely indexed, but the same number may appear in `legacy_sales_archive.invoice_number`; the two-table uniqueness rule is not enforced by a shared database constraint.
- `RoomSalesEngine.save` calculates `max(maxNewNumber,maxLegacyInvoiceNumber)+1` inside `AppDatabase.withTransaction`. This should be tested on an isolated database, including concurrent saves and imports.
- The sales child entities currently lack explicit Room foreign keys to `sales` and `sale_lines`. The audit detects existing orphans; it does not delete them.
- Adding Room foreign keys requires an explicit versioned migration rebuilding affected tables and preserving every existing row. Do not alter version 6 in place or apply destructive migrations.
- The historical importer records reconciled paid as max(raw paid, staged payment sum). This must be compared against source payment semantics and currency handling before claiming 100% financial parity.

## Not yet executed

The audit has **not been run** against the user's tablet Room database or an isolated Android test database. Therefore no zero-orphan result, 100% invoice match, or successful numbering isolation test can be claimed.

## Acceptance gate

1. Run the read-only audit against an exported copy of the device database, including WAL consistency.
2. Run `LegacyArchiveAudit` on the staged original source and capture per-invoice discrepancies, including the two review records.
3. Reconcile payments by sale ID, currency and source transaction semantics; validate due and any refunds/cancellations.
4. Run isolated migration and numbering tests; then introduce validated FK constraints in a new explicit migration if appropriate.
5. Approve only when discrepancies are resolved without rewriting historical transactions.

Status: **CODE AUDIT IMPLEMENTED; FINANCIAL CERTIFICATION PENDING REAL-DATA EXECUTION.**
