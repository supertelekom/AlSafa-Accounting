# Financial review and partner diagnostics — 2026-11-06

Baseline checked against origin/native6-development: 420c12f00a7b4c8debe7340cc5ea4a4574427503.

## Findings
- The screenshot review range is November 1–6. October archive profits correctly fall outside it; source inspection uses the full archive and therefore shows different totals.
- Detail rendering queried only operational sale events, expenses and withdrawals. Historical events were not listed even when their dates were selected.
- Partner reporting threw immediately on any non-online profit event anywhere in the database. This prevented showing verified historical sources and specific unresolved invoice references.
- Source inspection printed unrounded BigDecimal strings and the review TextViews did not consistently force RTL or Tajawal.

## Changes
- Historical sale/collection/return details now read the validated archive reader and filter by recognition date. Archived expense/withdrawal evidence is shown too.
- Added an all-records date shortcut, explicit selected-period label and explicit whole-archive source-inspection scope.
- Summary counts now use the same projected recognition-date events as historical profit totals, rather than a separate invoice-created-date count. Labels explicitly say events.
- Presentation rounds money to two decimals only; calculations retain full precision. RTL alignment, line wrapping, spacing, Tajawal and partner names applied.
- Suspect profit events become invoice-specific unresolved items up to the period end. Verified sources remain readable, but complete=false and partner approval still fails. No event deletion, reclassification, write or ledger replay occurs.

## Files modified
- app/src/main/java/com/alsafa/native8/sales/FinancialReviewActivity.kt
- app/src/main/java/com/alsafa/native8/sales/PartnerProfitsActivity.kt
- app/src/main/java/com/alsafa/native8/sales/SalesFinancialAuditDao.kt
- app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialPeriodRepository.kt
- app/src/main/java/com/alsafa/native8/sales/LegacyHistoricalProfitProjection.kt
- app/src/test/java/com/alsafa/native8/sales/UnifiedFinancialIntegrationTest.kt

Created: this report.

## Validation
- Financial JVM suite: **78 tests passed**, using tools/run_financial_junit.py with Kotlin 2.2.0, Room annotations, JSON, coroutines and JUnit. These are production-reader/repository tests with in-memory DAO doubles, not Android Room instrumentation.
- New regression: suspect receipts retain historical figures but prevent approved partner balances; receipts after the requested end do not block earlier periods.
- New regression: October historical detail events reconcile to period totals and are absent from November.
- Actual DAO SQL executed in SQLite memory database: verified online excluded; store/null-channel/orphan references retained; duplicate references collapsed; end boundary exclusive; no writes.
- git diff --check: passed.
- Gradle/APK, Room instrumentation and tablet layout tests: **not run**. No claim of Android build or visual acceptance.

## Remaining
The screenshots report 12 unclassified native invoices, 15 non-online receipt events and 3 unresolved native repairs. Their device records are not available in these screenshots or the October source backup. This patch intentionally does not guess their classification or cost. It exposes references and keeps final partner balances blocked until evidence is resolved. It does not declare the financial system complete.

No style-kit files were installed; no schema migration, cash, inventory or historical accounting data was changed. Only native8 files changed.
