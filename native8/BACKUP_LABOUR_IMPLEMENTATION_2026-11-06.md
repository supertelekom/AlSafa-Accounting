# Native 8: labour amount, full backup/restore and reset

Base: aea610d. Only native8 changed. User confirmed latest returns/cancellation testing successful.

## Implemented
- Labour input on maintenance intake (USD) and edit (invoice currency). Nonnegative amount validation; existing notes/parts remain optional for profit. Draft totals saved to both JSON and Room price using the existing original total formula. Delivery uses the existing transaction and profit-at-delivery reader; no artificial part/cost is created.
- Main button opens a dedicated backup process. An application-wide OS file lock prevents simultaneous operational access. Main process exits during maintenance; returning starts it fresh, so stale Room/preference connections cannot write into restored files.
- Complete ZIP includes databases (operational and imported-source staging), shared preferences and internal files. Copied SQLite databases are checked and checkpointed. Selected maintenance photo URIs are embedded into copied records; source records remain unchanged. Missing/inaccessible images fail export explicitly.
- SHA-256 and length manifest, format/schema validation, bounded extraction and traversal/duplicate/extra-entry rejection. Export reopens and validates the actual saved document before displaying success.
- Restore validates the entire archive before confirmation. It replaces data roots rather than importing/replaying transactions. A pending journal and retained old roots allow startup rollback after an interrupted replacement; failed replacement attempts immediate rollback.
- Reset requires typing `تصفير`; removes databases, internal files, preferences, temporary maintenance copies and cache. External saved backups and original externally selected photos are not deleted. Persisted URI grants are released on reset.
- New screen uses RTL, Tajawal-Medium, vertical scrolling and system insets. No style-kit installed.

## Validation
- 96 JVM JUnit tests passed, including archive round trip, damaged-content rejection, path traversal rejection, interrupted replacement recovery and reset scope, plus existing financial/partner and labour-only tests.
- Backup Kotlin files compiled successfully against Android 14 API classes with a stub for the MainActivity navigation target; only deprecated-insets warnings. This is not a full APK/Gradle build.
- Added NativeDataStoreTest instrumentation case for SQLite data/settings round trip, embedded photo after source removal and isolated reset. NOT RUN: needs Android device/emulator.
- NOT RUN: full Gradle/KSP/Room build, actual document provider, process transition/locking, tablet layout, reset/restore on Honor Pad X8b. Do not call these accepted until device validation succeeds.
- Data replacement rollback is tested for process interruption. Sudden power-loss durability across filesystem renames is not verified.

## Compatibility / limits
- Complete Native 8 ZIP format v1, database schema 19; incompatible schemas rejected. Original legacy JSON remains on the original import screen.
- Archive maximum expanded size 2 GiB / 100,000 entries; external photo maximum 32 MiB. Enough free internal storage is required for validation/staging and retained old state.
- Restore overwrites all operational data; it does not merge histories. Reset is an explicit destructive operation only after user confirmation. Neither action was executed on the user's data during development.

## Modified
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/alsafa/native8/MainActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDao.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceEditActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceIntakeActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenancePartsRepository.kt
- tools/run_financial_junit.py
- READINESS_VERIFICATION_2026-11-06.md (status update)

## Created
- app/src/main/java/com/alsafa/native8/backup/NativeArchive.kt
- app/src/main/java/com/alsafa/native8/backup/DataDirectorySwap.kt
- app/src/main/java/com/alsafa/native8/backup/NativeDataStore.kt
- app/src/main/java/com/alsafa/native8/backup/NativeApplication.kt
- app/src/main/java/com/alsafa/native8/backup/NativeBackupActivity.kt
- app/src/test/java/com/alsafa/native8/sales/NativeArchiveTest.kt
- app/src/androidTest/java/com/alsafa/native8/backup/NativeDataStoreTest.kt
- BACKUP_LABOUR_IMPLEMENTATION_2026-11-06.md
