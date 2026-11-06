# Native 8 readiness verification — 2026-11-06

Reviewed branch head c494da9. This is verification, not a release approval.

## Labour-only maintenance
- Original historical fixture has 98 `صيانة مشكلة` repairs with zero cost and no parts. Existing integration test verifies full revenue as profit and that notes do not change it.
- NativeMaintenanceProfitReader.snapshot supports an empty usedParts array, defaults absent manualCost to zero and saves zero total cost. Profit is recognized on deliveredDate.
- Added two JVM regression cases for native labour-only delivery: USD and TRY with saved exchange rate; USD also verifies date and stable repeated reads.
- However, the operational UI is incomplete: MaintenanceIntakeActivity initializes price=0 without a labour amount input. MaintenanceEditActivity edits descriptive fields/parts only. MaintenanceActivity displays laborPrice but its status update does not accept/save it. Delivery requires a positive invoice total. Consequently, a new labour-only repair cannot be completed through the current UI despite reader support. A saved historical repair is not evidence that new intake works.
- Required remaining implementation: original labour amount input and persisted invoice totals, wired to existing delivery without creating parts or cost. No production UI changes made in this verification batch.

## Backup and restore
- MainActivity and RestoreActivity expose old JSON import. LegacyBackupStager stages source data in a transaction and detects repeated source hashes.
- RestoreActivity promotes products, archived sales, suppliers and maintenance via separate import steps; import is not one all-or-nothing transaction across all datasets.
- No Native 8 operational backup exporter or corresponding round-trip restore was found in main sources/manifest. Legacy JSON import does not export new Room operations, settings or referenced photos.
- A complete native backup/restore implementation and isolated round-trip test remain release blockers; this is missing functionality, not merely an unperformed device test.

## Validation and scope
- Result: 91 JUnit tests passed (exit 0); git diff --check passed.
- Run: tools/run_financial_junit.py with local Kotlin/JUnit dependencies. Includes original-source financial parity, partner balances, unified historical/native reports, return/cancellation rules and native maintenance reader tests.
- No Gradle/APK build, Android UI test, Room instrumentation or device backup/restore performed. JVM tests do not prove Activity or SQLite execution.
- User-confirmed store/online sale, parts maintenance and daily closing/reopen tests remain recorded as successful for their tested cases.
- No production data, balances, stock or existing screens changed by this verification.

## Files
- Modified: app/src/test/java/com/alsafa/native8/sales/NativeMaintenanceProfitReaderTest.kt
- Created: READINESS_VERIFICATION_2026-11-06.md
