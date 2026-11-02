# Native 8 — Static audit of retired SQLite connections (2026-11-02)

Scope: Kotlin files in `native8/app/src/main/java`, branch `native6-development`. No Gradle or Android SDK build was executed.

## Findings

- Removed eight retired source files: `OperationalDatabase.kt`, `SalesDatabase.kt`, `OperationalProductRepository.kt`, `ProductEditRepository.kt`, `ProductImportPromoter.kt`, `ProductSnapshotAudit.kt`, `ProductStockRepository.kt`, and `ProductVariantRepository.kt`.
- Searched the remaining Kotlin source files for these eight class identifiers after deletion: no executable call sites were found. References in comments are historical descriptions.
- The remaining `SQLiteOpenHelper` implementation is `LegacyStagingDatabase`, which opens the separate `alsafa_native8_staging.db` fixture/import database. It must not be confused with the operational source of truth.
- `AppDatabase` remains the owner of `alsafa_native8_operational.db` and its Room DAOs.

## Critical data-preservation warning

Removing `SalesDatabase.kt` does **not** merge any existing `alsafa_native8_sales.db` files. If test devices or earlier builds contain invoices in that separate database, they must be detected, audited, backed up, and imported transactionally before deployment. **Do not delete that on-device database.**

The original v5 helper source is still available in Git history for migration verification. The v5-to-v6 preflight and migration must be exercised against a real disposable database copy. Deleting the old helper class does not establish Room schema compatibility.

## Release blockers

1. Compile Kotlin and Room with Android Gradle Plugin; no build has been executed.
2. Test actual v5-to-v6 Room schema validation, including NOCASE unique index and legacy table constraints.
3. Reconcile historical standalone sales DB, if present.
4. Verify backup restore, sales, mixed payments, stock and receivables, returns, and rollback on device.
5. Audit non-Kotlin resources and scripts before claiming a repository-wide zero-reference state.
