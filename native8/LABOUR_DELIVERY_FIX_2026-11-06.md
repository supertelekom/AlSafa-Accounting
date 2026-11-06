# Labour-only invoice delivery correction

User device report: labour invoice saved, delivery button absent, currency/cashbox choices unreachable.

## Cause and changes
- Intake stored USD unconditionally. Added USD/TRY/SYP invoice currency selection and saves labour exactly in that unit. Part prices/costs selected in their source currency are converted to the invoice unit when saving intake.
- Edit supports explicitly selecting a currency for labour-only records with no extra cost. Existing delivered records remain protected; no historical record is automatically changed.
- Delivery previously required ready-for-pickup even for a labour-only invoice in the initial awaiting-delivery status. Shared eligibility now allows explicit delivery of a positive labour-only invoice in awaiting-delivery state. In-progress, waiting-for-part and delivered states do not gain this shortcut.
- Delivery dialog now starts with the saved invoice amount. It already contains cashbox and receipt currency selectors; these become reachable via the delivery button. Displayed total uses the same original total formula including tax.
- No automatic collection on intake and no new inventory deduction for no-part invoices.

## Validation
- 98 JUnit tests passed (exit 0); git diff --check passed.
- Financial JVM runner includes two new eligibility tests, alongside existing labour-only USD/TRY and financial regression tests.
- Full Android build/device checks are not run in this batch. The reported device amount cannot be diagnosed as a specific saved value without its record; hardcoded USD was confirmed in source.
- Device acceptance: create 250 TRY labour-only invoice with no parts, open delivery, choose drawer and TRY, confirm 250 TRY receipt. With saved USD/TRY=50 and no other costs/tax, profit should be 5 USD at delivery. Changing receipt currency shows its converted suggestion.

## Files modified
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDeliveryRepository.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceIntakeActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceEditActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDao.kt
- tools/run_financial_junit.py

## Files created
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDeliveryEligibility.kt
- app/src/test/java/com/alsafa/native8/sales/MaintenanceDeliveryEligibilityTest.kt
- LABOUR_DELIVERY_FIX_2026-11-06.md
