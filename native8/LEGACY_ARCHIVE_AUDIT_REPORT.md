# Native 8 — Room and historical archive audit

Date: 2026-11-02. Branch: native6-development. Scope: native8 only.

## Evidence and status

- Tablet restore screen reported 558 products in Room and 111 historical invoices in the archive, with 2 flagged for review.
- User confirmed unfiltered invoice listing, name/date search and opening an old invoice work.
- **Not yet independently reconciled**: the tablet's operational SQLite rows have not been exported or read in this audit. A count on the restore screen is not a field-by-field reconciliation.
- New read-only implementation: `LegacyArchiveAudit.inspect(runId)`. It compares the staged source and Room rows without writing to either database. It is not wired to any UI and has not been executed on the tablet.

## Schema and data-flow inspection

- `AppDatabase`: 15 registered Room entities, version 6; explicit 4→5 and 5→6 migrations, no destructive fallback.
- Product imports: composite product primary key `(source_sha256, source_ordinal)`; source SHA is the `source_imports` primary key. `RoomProductImportPromoter.promote` checks existing source and inserts source/products atomically.
- Legacy sales: `legacy_sales_archive.legacy_id` primary key; `legacy_sales_imports.source_sha256` primary key. `LegacySalesArchiveImporter.importRun` checks the source, inserts invoice and line snapshots in a Room transaction, rejects repeated legacy IDs. No stock deduction or payment/cashbox posting in this importer.
- Historical receivables are not reposted to the live `customer_receivables` table by the archive importer.
- New sales: `sales.number` has a unique index; `RoomSalesEngine` selects max(new number, historical number)+1. This is a sequencing rule, not a database-enforced cross-table unique constraint; concurrent numbering and legacy/new number collisions require explicit tests.
- Legacy `invoice_number` is indexed but **not unique**. This is intentional pending source-data reconciliation; do not add uniqueness without checking historical duplicates.
- Several sales child entities have no declared Room foreign keys to their parent sales rows. Orphan detection and parent integrity tests remain necessary.
- Archive search currently limits results to 100 rows; the presence of 111 historical invoices does not mean all 111 display at once.

## Reconciliation contract

Call `LegacyArchiveAudit(staging, operational).inspect(runId)` on a staged backup and the same device's Room database. The report checks:
- Source hash, invoice count, source invoice ID uniqueness and presence in Room.
- Invoice number, date+time as epoch milliseconds, customer, phone, currency, total, paid floor, nonnegative due, and original invoice JSON.
- Number of item rows, item ordinal and original JSON for each line (including name/quantity/price where present in source JSON).
- Source and Room product counts for the selected source.

The auditor is read-only. Its `matches` property only certifies the checks implemented; it is **not** a financial ledger certification. In particular, final paid and due require independent reconciliation against legacy payment records, cancellations, returns, and the original accounting rules. The two review invoices require individual examination.

## Required acceptance tests before declaring full parity

1. Run audit on the tablet's existing staging run and capture all findings; expect 111 invoice matches and 558 product matches for the known backup.
2. Re-run the same import and verify source and row counts, invoice IDs, stock movements, payments, cashbox movements, and receivables do not change.
3. Compare historical invoice numbers with new invoice numbers and test creation after import; preserve existing new invoices and avoid renumbering old records.
4. Test invalid/duplicate source IDs in an isolated test database; ensure atomic rollback.
5. Verify migrations on a **copy** of a real v4/v5 database, including indexes and foreign-key integrity.
6. Resolve the two review invoices using the original source and accounting records, without automatically modifying historical financial entries.

**Status: PARTIAL / NOT APPROVED FOR FINANCIAL PRODUCTION.** No APK build or instrumented database tests were executed in this change.
