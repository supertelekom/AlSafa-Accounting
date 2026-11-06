# Original-rule compatibility for saved Native 8 records

User instruction: treat older/unclassified records like the original source and backup; unify their financial result rather than requiring fields introduced later.

Source reviewed: assets/main.js, grossProfitUsdForRange (6489 onward), onlineSaleReceiptEvents and saleType || 'store' branches. RoomSalesEngine saves invoice totals and line costs in its USD accounting base.

## Implemented
- Read-only fallback for invoices with null/empty channel (original default STORE) or explicit store missing a profit event. Use saved net total minus saved quantity × unit cost at invoice creation date. Already posted store events are excluded by SQL; no cash, inventory or profit rows are inserted.
- Collection profit rows belonging to effective store invoices are excluded from online totals and no longer block consolidation merely because they are store receipts. Orphan events and explicit invalid nonempty channels still surface as unresolved.
- Fallback events feed both unified period/partner calculations and chronological invoice detail rows with the same stable invoice key.
- Older maintenance records without financialSnapshotVersion use the original receipt/cost/rate reader. They no longer require paymentLines, part verification flags or a new-style ledger proof that the old format did not store. Existing Room/JSON identity/amount/date consistency checks stay. Native version-1 records retain their original checks.
- Saved cost zero remains zero. Missing actual costs, malformed totals or missing historical FX are not filled with guesses. Returned/cancelled store invoices without dated profit evidence remain unresolved rather than assigning the refund to the sale date.

## Files modified
- app/src/main/java/com/alsafa/native8/sales/NativeMaintenanceProfitReader.kt
- app/src/main/java/com/alsafa/native8/sales/NativeMaintenanceProfitRepository.kt
- app/src/main/java/com/alsafa/native8/sales/SalesChannelClassification.kt
- app/src/main/java/com/alsafa/native8/sales/SalesFinancialAuditDao.kt
- app/src/main/java/com/alsafa/native8/sales/SalesPostingDao.kt
- app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialLedgerRepository.kt
- app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialPeriodRepository.kt
- app/src/test/java/com/alsafa/native8/sales/NativeMaintenanceProfitReaderTest.kt
- app/src/test/java/com/alsafa/native8/sales/UnifiedFinancialIntegrationTest.kt
- tools/run_financial_junit.py

## Files created
- app/src/main/java/com/alsafa/native8/sales/OriginalStoreProfitCompatibility.kt
- app/src/test/java/com/alsafa/native8/sales/OriginalStoreProfitCompatibilityTest.kt
- ORIGINAL_RULE_COMPATIBILITY_2026-11-06.md

## Validation
- 87 financial JVM tests passed. Includes invoice-date unpaid store recognition, repeat-read stability, missing cost rejection, dated-return protection, and legacy maintenance with saved cost but without paymentLines/new flags.
- Actual SQL executed against SQLite fixtures: null/blank selected; explicitly online unchanged; posted store not selected twice; future records excluded; invalid/orphan references still reported.
- Additional new-ledger Kotlin type check with production DAOs/readers and temporary boundary stubs; not a Room or Android runtime test.
- git diff --check passed. Gradle/APK and tablet tests not executed.

No persistent data migration or reclassification occurred. Compatibility applies to financial reading. Actual device records were not supplied, so elimination of all 12/15/3 device warnings is not asserted. The old backup totals and existing financial parity regressions continue to pass.
