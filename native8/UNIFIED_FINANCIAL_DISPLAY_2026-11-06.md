# Unified financial presentation — 2026-11-06

Supersedes the separate backup-month UI introduced by 9fc4778. One date range now drives one combined report and partner statement.

## Changes
- Archive event keys resolve to original invoice number, customer and item/work descriptions, including online payment and return references. Keys remain internal and are not printed as invoice details.
- A read-only ledger joins archived and native sales, collections, maintenance, returns, operating expenses and partner withdrawals, then sorts across all origins by recognition/operation date. Duplicate event keys are rejected and sale/repair ID overlaps are blocked rather than silently double-counted.
- Review totals and partner totals now use the same unified repository. Store/online/maintenance totals combine origins. Expenses deducted once; withdrawals remain outside operating profit.
- Removed the backup-only partner button and its separate calculation entry point. Expense/withdrawal histories no longer label each record old/new.
- Native repairs whose opening and delivery dates are after the cutoff do not block an earlier period. Existing invalid or ambiguous records within scope remain unresolved; no cost, receipt or classification is invented.
- No accounting records, stock, cash, schema or migrations changed. Style-kit not installed.

## Modified files
- app/src/main/java/com/alsafa/native8/sales/FinancialReviewActivity.kt
- app/src/main/java/com/alsafa/native8/sales/PartnerProfitsActivity.kt
- app/src/main/java/com/alsafa/native8/sales/ExpenseActivity.kt
- app/src/main/java/com/alsafa/native8/sales/PartnersActivity.kt
- app/src/main/java/com/alsafa/native8/sales/LegacyFinancialHistoryDisplay.kt
- app/src/main/java/com/alsafa/native8/sales/NativeMaintenanceProfitRepository.kt
- app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialPeriodRepository.kt
- app/src/test/java/com/alsafa/native8/sales/UnifiedFinancialIntegrationTest.kt
- tools/run_financial_junit.py

## Created files
- app/src/main/java/com/alsafa/native8/sales/FinancialLedgerPresentation.kt
- app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialLedgerRepository.kt
- app/src/test/java/com/alsafa/native8/sales/FinancialLedgerPresentationTest.kt
- UNIFIED_FINANCIAL_DISPLAY_2026-11-06.md

## Verification
- Financial JVM suite: 84 tests passed, including all original fixture events resolving to invoice descriptions, chronology across origins, duplicate presentation rejection, unified old/new totals and cumulative partner calculations, and future invalid native repairs not blocking earlier months.
- New ledger repository also receives a Kotlin type check with actual production DAOs/readers and temporary database/source boundary stubs. This is not Room or Android runtime verification.
- git diff --check passed.
- Gradle/APK, Android UI compilation and Room/device instrumentation: not run. Tablet display requires updating and checking real-window rendering.

## Remaining
Device-native records still lacking classification/receipt/cost evidence are not silently accepted. These may still prevent approval for periods containing them; the change unifies the sources and display, not their missing evidence. Source-inspection diagnostics can still distinguish origins internally. The everyday financial report and partner statement do not offer separate old/new accounts.
