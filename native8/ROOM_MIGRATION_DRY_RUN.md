# Native 8 — Room 5→6 dry-run release gate

**Never run this procedure against the shop's live database.** Stop the application, preserve an untouched backup of the operational database (including `-wal` and `-shm` when present), and use a verified checkpointed copy. This document is a procedure, not a claim that tests have passed.

## 1. Prepare a consistent v5 fixture

1. On the test device, stop all database writers. Prefer `VACUUM INTO` or SQLite backup API to create a consistent snapshot; do not copy a live WAL database file by itself.
2. Preserve the original v5 snapshot read-only and calculate SHA-256 for it and the test copy. Ensure the fixture opens with `PRAGMA user_version=5`.
3. Ensure all 15 required tables exist. Older v5 databases may lack the three historical archive tables; do **not** fabricate or drop their records. Investigate and establish a non-destructive schema preparation path before proceeding.
4. Run `OperationalMigrationPreflight.capture` against the **v5 test copy**, store the returned snapshot, and abort on any missing table, integrity error, FK violation, or missing NOCASE constraint.

## 2. Migrate the disposable copy

1. Close the preflight connection. Use a fresh isolated Android test context containing only the copied `alsafa_native8_operational.db`.
2. Open `AppDatabase.get(testContext)` and execute a DAO query to force Room open/validation and `OperationalMigration5To6`; `build()` alone does not run migration.
3. If Room schema validation fails, capture the exact expected/found schema diff. Do not use `fallbackToDestructiveMigration`, alter `room_master_table`, or manually set `user_version`.
4. Close Room; open the migrated disposable copy read-only; capture `OperationalMigrationPreflight.capture` again. Run `assertPreserved(before, after)`.

## 3. Independent SQL checks

```sql
PRAGMA user_version;           -- must be 6
PRAGMA integrity_check;        -- must be ok
PRAGMA foreign_key_check;      -- must return zero rows
SELECT name, sql FROM sqlite_master WHERE name='product_variants_local';
SELECT name, sql FROM sqlite_master WHERE type='index' AND tbl_name='product_variants_local';
SELECT MAX(number) FROM sales;
SELECT MAX(invoice_number) FROM legacy_sales_archive;
SELECT sale_id, balance FROM customer_receivables ORDER BY sale_id;
SELECT parent_source,parent_id,color_name,COUNT(*) FROM product_variants_local
 GROUP BY parent_source,parent_id,color_name COLLATE NOCASE HAVING COUNT(*)>1;
```

Compare per-table row counts, each receivable amount, maximum invoice number, and sampled SHA-256 of original JSON and historical stock movements between v5 and v6. Attempt inserting a duplicate color name differing only by case **inside a rolled-back test transaction** and require a uniqueness error. Verify the next generated invoice number is `max(max(sales.number),max(legacy_sales_archive.invoice_number))+1`.

## 4. Transaction rollback and operational tests

- Post a valid sale with two lines, mixed permitted payments, and a receivable. Confirm stock movement, sale, payment, cashbox ledger and receivable rows commit together.
- Trigger a controlled failure after the first line stock movement. Verify all rows and stock totals revert.
- Collect part of a confirmed historical debt, verify the selected cashbox and exact balance delta, then test overpayment rejection.
- Test case-insensitive color uniqueness, repeated import idempotency, full and partial return, and return after prior return.
- Test sales product search beyond the first 500 imported records and ensure no duplicate barcode.
- Do not treat an absence of compile errors as evidence that accounting rules are correct.

## 5. Build and sign-off

Run from `native8/` on an Android SDK-equipped workstation:

```bash
./gradlew --no-daemon clean :app:assembleDebug :app:testDebugUnitTest
```

Capture Gradle output, Room schema validation, migration test results, and the resulting APK SHA-256. Only approve deployment after a full backup/restore rehearsal on Honor Pad X8b.

## Current blockers

- Remaining `OperationalDatabase` call sites (including restore flows) require an exhaustive repository search and migration.
- A standalone `alsafa_native8_sales.db` may contain older data; reconcile it separately before retiring it. Never silently ignore or delete it.
- Preflight currently requires all 15 tables on v5. The migration creates historical tables if absent, so preflight and migration expectations must be reconciled against actual backup schema before a release.
- The old SQLiteOpenHelper version 5 cannot safely open the upgraded Room v6 database. Any remaining production call site is a release blocker.
- Build, real v5→v6 migration and device tests are **not yet executed**.
