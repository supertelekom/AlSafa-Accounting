# Native 8 — Synthetic SQLite dry-run, 2026-11-02

**Scope:** Executed SQLite SQL simulation only. Android OperationalMigrationPreflight.capture, OperationalMigration5To6.migrate, Room validation, and Gradle were **not executed**.

## Fixture and procedure

Created a disposable SQLite database using the 12 v5 baseline tables defined in OperationalDatabase.kt. Inserted one imported product, one color variant, one invoice numbered 401, and a receivable balance of 45. Set user_version=5. Executed the DDL statements from OperationalMigration5To6.kt within a SQLite transaction, set user_version=6, and captured pre/post counts and balances.

## Observed results

| Check | v5 | v6 |
| --- | --- | --- |
| Tables | 12 | 15 |
| Imported products | 1 | 1 |
| Color variants | 1 | 1 |
| Sales invoices | 1 | 1 |
| Receivables | 1 | 1 |
| Receivable balance | 45 | 45 |
| Max invoice number | 401 | 401 |
| Foreign key violations | 0 | 0 |
| integrity_check | ok | ok |

All 12 original table counts matched. Three new archive tables were empty. Case-insensitive duplicate Black/black for the same product was rejected. Expected next invoice number is 402.

## Limits and release gate

- This is **not** a real v5 backup or Android Room migration test. It does not establish Room schema compatibility.
- No Android SDK or Gradle was available; no APK or instrumentation tests ran.
- Validate the actual OperationalMigrationPreflight and Room migration on an isolated, consistent copy of a real v5 database, then compare original JSON, stock movements, financial records, balances, indices, and foreign keys.
- Do not deploy over shop data until those tests and rollback/restore rehearsals pass.
