# Period sources and dated store adjustments

## Implemented
- Sources panel now reads the same unified repository and selected dates as the period report. It shows store profit, online receipts, maintenance, dated adjustments, expenses and approved net; no unrelated whole-archive numbers or technical source-count paragraphs.
- Incomplete reports no longer print a partial negative total as a net-like result. They explain that net is unavailable and list the remaining issues.
- Financial amounts use Unicode directional isolation to keep minus signs and USD order readable in RTL.
- Compatibility invoices with returns now use saved sale_returns rows, match per-line returned quantities, and reproduce the existing discounted refund formula. Original sale profit remains at the sale date; return deltas use each saved return date.
- Saved cancellation rows (reason إلغاء الفاتورة, as written by both current cancellation paths) reverse only remaining profit at cancellation time. A cancellation following a return does not reverse the same profit twice.
- These adjustments feed both summary/partners and the unified detail ledger. No cash/stock writes or event inserts.
- A single invoice-specific compatibility error replaces the duplicated generic uncovered-sale ID message for the same invoice.

## Validation
89 JVM tests passed, including partial return date/refund allocation and cancellation after return with cutoff checks and repeat-read stability. Existing original-backup and unified partner tests passed. Additional ledger type-check uses temporary boundary stubs and production DAOs/readers; not Android/Room runtime validation. git diff --check passed.

Not run: Gradle/APK, Room instrumentation and device rendering. Existing device-only missing records cannot be reconstructed from a screenshot. Missing/mismatched dated return rows or mixed pre-existing adjustments still remain explicit issues instead of invented dates or duplicate amounts.

## Files modified
- app/src/main/java/com/alsafa/native8/sales/FinancialLedgerPresentation.kt
- app/src/main/java/com/alsafa/native8/sales/FinancialReviewActivity.kt
- app/src/main/java/com/alsafa/native8/sales/OriginalStoreProfitCompatibility.kt
- app/src/main/java/com/alsafa/native8/sales/PartnerProfitsActivity.kt
- app/src/main/java/com/alsafa/native8/sales/SalesFinancialAuditDao.kt
- app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialLedgerRepository.kt
- app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialPeriodRepository.kt
- app/src/test/java/com/alsafa/native8/sales/OriginalStoreProfitCompatibilityTest.kt
Created: PERIOD_SOURCES_RETURNS_2026-11-06.md.
