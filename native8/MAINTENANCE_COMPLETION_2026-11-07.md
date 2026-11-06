# Maintenance completion batch — 2026-11-07

Scope: Native 8 maintenance only. Home was accepted before this batch.

## Implemented
- Native Safa/Tajawal register with search, status filters and 50-row paging. List projection excludes original_json and photos; detail loads one repair.
- One displayed readiness state, جاهزة للتسليم. Existing جاهز للاستلام and بانتظار التسليم are accepted without rewriting historical records. Zero labour and linked parts do not independently block a ready repair. Delivery financial/stock validations remain in the existing repositories.
- Status saves use the displayed record as an optimistic concurrency precondition; delivered records remain protected.
- Register, parts inventory, intake, labour/parts editor and settlement form use the shared native styling. Details refresh after edits without a Compose/old-style intermediary.
- Edit screen can remove individual selected parts before saving. Existing draft accounting remains transactional.
- Additional existing photo arrays can be browsed from details.
- Quick repair button management: add/edit/delete, label/icon/order, fixed TRY price or manual amount, stock part link/quantity. The built-in manual-problem button remains available. Local settings overlay legacy settings and are included by the existing full native backup shared_prefs capture.
- Intake snapshots form values on the main thread before database work.

## Validation
- 131 JVM financial/rule tests passed, including four new quick-button tests and updated ready-with-parts eligibility coverage.
- Kotlin compiler passed for actual changed maintenance activities/repositories and connected UI, against Android API jar and boundary stubs for unrelated AndroidX/database infrastructure. This is NOT Gradle/KSP/Room-generated code or an APK build.
- Actual register SQL executed in SQLite fixture: 105 rows, 50-row non-overlapping pages, readiness aliases, invoice search, seven-column metadata projection passed.
- git diff --check passed.

## Tablet acceptance
1. Full mode → maintenance: search old/new invoices, filter each state, open detail, return and load more.
2. Intake or quick repair → leave watch: edit labour, add/remove parts, save; mark ready; confirm it moves from pending to deliveries.
3. Ready repair with zero labour and a part: collect correct total; also deliver an entirely free repair. Verify drawer, stock and profit date, and that repeated delivery cannot post twice.
4. Maintenance → manage quick repair buttons: create fixed linked-part button, use it, confirm quantity/cost; edit its order/name; create manual button; delete a custom button. Reopen the app and verify persistence. Native backup/restore should retain the configuration.
5. Open old/new photos; verify invoice PDF/WhatsApp and settlement form navigation.

## Explicit remaining boundaries
- Bluetooth printer pairing and physical printing remain the separately deferred printer task. No claim of hardware validation.
- WhatsApp Business opens a prepared share; final send depends on WhatsApp/user confirmation.
- Archived delivered repairs keep their existing read-only/return restrictions; this batch does not invent historical refund or stock postings.
- Device acceptance is required before calling maintenance operationally closed.

## Files
New: MaintenanceFormStyle.kt, QuickRepairButtonRules.kt, QuickRepairButtonsActivity.kt, QuickRepairButtonRulesTest.kt, this document.
Modified: AndroidManifest.xml, MaintenanceActivity.kt, MaintenanceDao.kt, MaintenanceDeliveryEligibility.kt, MaintenanceDetailPage.kt, MaintenanceEditActivity.kt, MaintenanceIntakeActivity.kt, MaintenancePartsActivity.kt, MaintenanceReturnActivity.kt, QuickRepairRepository.kt, MaintenanceDeliveryEligibilityTest.kt, tools/run_financial_junit.py.
