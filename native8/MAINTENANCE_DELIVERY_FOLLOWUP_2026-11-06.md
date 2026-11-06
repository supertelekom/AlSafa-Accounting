# Maintenance device follow-up

- Enable diagnosis/work/notes/status updates for restored, undelivered repairs as well as native repairs. Optimistic original-JSON match plus undelivered status prevents overwriting a concurrent delivery. Financial fields and stock remain unchanged.
- Ready-deliveries query already matches original renderFatherDeliveries across all sources. Reference backup has 2 ready, 1 awaiting delivery and 6 in repair (299 delivered). Added invoice number/status and access to maintenance records for readiness updates. Absence of specific restored rows on the user's device is not reproduced; requires their invoice numbers if it persists.
- Explicit zero part cost is valid without conversion evidence because zero is currency-independent; stamp the snapshot in the copied delivery JSON. Missing cost and nonzero unverified cost remain rejected. No invented cost or stock replay.
- Format the suggested receipt input (100 instead of 100.000...) using InvoiceDisplay.
- Add a clearly labelled invoice/PDF/print action directly on delivery cards and rename the detail action consistently. Existing SalePrintActivity preview remains unchanged.
- Replace the maintenance missing-phone Toast with a Tajawal-styled dialog and explanatory text.

Modified: MaintenanceActivity.kt, MaintenanceDao.kt, MaintenanceDetailPage.kt, MaintenanceMessaging.kt, ReadyDeliveriesActivity.kt, RepairDeliveryDialog.kt, NativeMaintenanceProfitReader.kt, NativeMaintenanceProfitReaderTest.kt.
Created: this report.

Validation: 126 financial JVM tests passed after correcting a test JSON accessor (initial run: 1 test failure); limited Android API Kotlin compilation with stubs passed; SQLite compare-and-set check passed. Full Gradle/Compose/Room instrumentation and tablet execution not performed. Device acceptance still needed for invoice 432 (actual raw data unavailable), restored readiness/delivery, PDF preview, and the missing-phone dialog.
