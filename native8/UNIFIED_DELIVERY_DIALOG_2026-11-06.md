# Unified delivery dialog and zero-value repairs — 2026-11-06

User verified archived delivery cash reaches drawer and accepted simplified-home styling. New request: same attractive delivery dialog for native/restored and zero/positive collection, no redirect to old detail layout.

## Changes
- Shared RepairDeliveryDialog with Tajawal, RTL, original cream/gold palette, padded rounded summary, readable amount, prominent confirmation and cancel buttons. Bounded to actual current window width (max 620dp), scrollable, capped at 85% window height and resize for keyboard.
- ReadyDeliveriesActivity opens the shared dialog directly for both sources. Details remain a separate action. MaintenanceActivity also uses it, replacing the old Compose delivery modal.
- Zero due hides money/currency/cashbox controls and shows delivery-only confirmation. Existing archived fully paid delivery continues to use its unchanged repository. Native zero-price delivery is now allowed; it saves status/date with an empty paymentLines array and no cashbox insertion.
- Native profit reader accepts validated zero-total/zero-received/empty-payment-lines evidence without creating an initial profit event. Subsequent settlement processing remains intact. Positive invoices still require receipt evidence.
- Native stale-dialog protection checks original JSON inside the transaction before any mutation. Existing status, inventory and duplicate-delivery guards remain.
- Native labour-only waiting delivery permits explicit zero price; unready repairs with parts retain readiness requirements. No changes to home cards, sale forms, historic receipt amounts, schema or restored delivery rules.

## Validation
- JVM JUnit: 119 tests passed. Added zero-value receipt/profit test and positive-invoice missing-receipt rejection; updated zero-labour eligibility test to requested behavior. Existing archived-original-source/financial tests pass.
- Kotlin Android-facing smoke compilation: passed with Android classes and stubbed Room/service/scanner boundaries. Shared dialog, ready list and native delivery repository compiled. Full Compose activity and generated Room implementation were not built by this smoke check.
- git diff --check passed. No failed final tests.
- NOT RUN: Gradle/APK, device UI/rotation, Room instrumentation or actual cash/stock mutation on device. These remain acceptance checks, not claimed successes.

## Device acceptance
1. Open a native ready zero-value repair from simplified Deliver watches: remain on ready list behind shared dialog; see no collection fields; Confirm delivery enabled.
2. Confirm: repair disappears from ready list; status delivered; cash unchanged; no new profit. Reopen/restart to check no duplicate.
3. Open positive-value native and restored repairs: same styled dialog, correct remaining amount and currency/cashbox controls. Cancel causes no write; confirm posts actual payment once.
4. Details button independently opens existing detail screen. Simplified home remains unchanged.

Original printing/template/automatic print is still the next batch; device acceptance of this dialog remains pending.

## Files
- Modified: `native8/app/src/main/java/com/alsafa/native8/maintenance/MaintenanceActivity.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDeliveryEligibility.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDeliveryRepository.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/maintenance/ReadyDeliveriesActivity.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/sales/NativeMaintenanceProfitReader.kt`
- Modified: `native8/app/src/test/java/com/alsafa/native8/sales/MaintenanceDeliveryEligibilityTest.kt`
- Modified: `native8/app/src/test/java/com/alsafa/native8/sales/NativeMaintenanceProfitReaderTest.kt`
- Created: `native8/app/src/main/java/com/alsafa/native8/maintenance/RepairDeliveryDialog.kt`
- Created: `native8/UNIFIED_DELIVERY_DIALOG_2026-11-06.md` (this report).
