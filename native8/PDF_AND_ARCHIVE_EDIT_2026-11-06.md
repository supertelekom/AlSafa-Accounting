# PDF lifecycle and original invoice metadata editing — 2026-11-06

Scope: native8 only. Parent: 97720ac31936d7a470304914588f43ad28dc9d47.

## Changes
- Finish an active PDF page even when drawing fails; preserve the original exception rather than masking it with `Current page not finished!`. Delete incomplete output. Keep the logo bitmap alive until document close, and allow a missing logo asset without failing the invoice.
- Enable archived sale printing and the original invoice metadata editor for archived sales and maintenance invoices. Original main.js invoiceEditModal/saveInvoiceEdit (lines 3288–3302) permits invoice number, date, time, customer, phone and notes, not amounts or inventory edits.
- Check invoice number uniqueness across native sales, archived sales and maintenance. Save using Room transactions and compare-and-set original JSON to reject stale edits.
- Preserve IDs, totals, costs, payments and original metadata baseline. No stock or cash posting, schema change or destructive migration.
- Overlay edited historical invoice metadata when reading profit reports and partner statements, without rewriting immutable staging records. Online collection dates and maintenance delivery dates remain their original recognition events. Related historical return display numbers follow the edited invoice in the report projection.

## Created
- app/src/main/java/com/alsafa/native8/sales/InvoiceMetadataEdit.kt
- app/src/main/java/com/alsafa/native8/sales/InvoiceMetadataDao.kt
- app/src/main/java/com/alsafa/native8/sales/InvoiceMetadataActivity.kt
- app/src/test/java/com/alsafa/native8/sales/InvoiceMetadataEditTest.kt
- app/src/androidTest/java/com/alsafa/native8/sales/InvoiceReceiptAndroidTest.kt
- PDF_AND_ARCHIVE_EDIT_2026-11-06.md

## Modified
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/alsafa/native8/data/migration/LegacyReadRepository.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDetailPage.kt
- app/src/main/java/com/alsafa/native8/sales/AppDatabase.kt
- app/src/main/java/com/alsafa/native8/sales/FinancialReviewActivity.kt
- app/src/main/java/com/alsafa/native8/sales/InvoiceReceipt.kt
- app/src/main/java/com/alsafa/native8/sales/PartnerProfitsActivity.kt
- app/src/main/java/com/alsafa/native8/sales/SaleDetailsActivity.kt
- tools/run_financial_junit.py

## Validation
- Financial JVM suite: 122 tests passed, including metadata preservation and invalid date tests.
- SQLite: all five DAO queries validated; compare-and-set rejects stale repeat; original total preserved; cross-table duplicate number rejected.
- Kotlin compilation of affected native View screens/PDF/editor against Android API jar with boundary stubs passed. This is not a full Gradle/Room/Compose build.
- Added device PDF renderer test for a two-page receipt. Not executed here.
- Full Gradle build, Room instrumentation, actual PDF rendering, printing and tablet edit workflows still require Android environment/device validation.

## Device acceptance
1. Reopen the previously failing invoice; generate PDF, open it and print. If it fails, record the new underlying error.
2. Open an archived sale and an archived repair, edit customer/phone/notes, save, reopen and print; verify values persist.
3. Try an already-used invoice number: save must be rejected. Verify a unique number persists.
4. Change an archived store sale date on a test backup and check the report period. Totals, inventory and cash balances must remain unchanged.
