# Backup-only monthly partner statements

Scope: native8 only. User requested September/October results from the restored backup without blocking them on newer trial transactions.

## Implemented
- Added `historicalStatement` to the existing unified financial repository. It reads only the historical projection for period and cumulative totals and uses the existing partner calculation (40/35/25 and cumulative withdrawals).
- Added a month selector in the existing PartnerProfitsActivity, populated from restored record dates. It explicitly labels results as backup-only. Existing combined old/new report remains available.
- Native trial invoices, receipts and repairs do not enter this backup-only calculation and therefore cannot block it. No native records are deleted, reclassified, or marked verified. No cash/inventory writes or migration.
- Existing original-source readers remain in use; no hard-coded monthly amounts or invented costs/rates.

## Actual backup verification
File: AlSafa-Backup-2026-10-26T06-21-00-520Z.json; exportedAt 2026-10-26T06:21:00.191Z.
Executed production readers against the full JSON:

| USD | September | October, available records up to backup |
|---|---:|---:|
| Store profit | 807.11 | 1107.37 |
| Online profit | 0.00 | 823.81 |
| Maintenance profit | 163.88 | 519.67 |
| Returns/adjustments | -10.40 | 0.00 |
| Operating expenses | 4.96 | 108.64 |
| Net profit | 955.61 | 2342.22 |

Values rounded only for display; net computed before rounding. Cumulative net displayed: 3297.83 USD. Projection unresolved lists empty for both months. October is not a claim of complete data through October 31.

## Validation and remaining
- 79 financial JVM tests passed; includes regression proving incomplete native trial records do not block backup monthly statements and still block combined approval.
- Full original JSON read executed, including sales, receipts, returns, repairs, expenses and withdrawals.
- git diff --check passed.
- Android Gradle/APK build, Room instrumentation and tablet interaction not executed. Tablet needs update to see the added selector.
- Combined old/new account still has device-specific unresolved native records; this change does not pretend to repair those records.

Modified files:
- app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialPeriodRepository.kt
- app/src/main/java/com/alsafa/native8/sales/PartnerProfitsActivity.kt
- app/src/test/java/com/alsafa/native8/sales/UnifiedFinancialIntegrationTest.kt
Created: BACKUP_MONTHLY_PROFITS_2026-11-06.md.
