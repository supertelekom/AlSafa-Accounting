# Archived undelivered maintenance editing

- Direct detail navigation shows a themed loading surface instead of briefly rendering the old list.
- Existing draft editor is now available for undelivered imported repairs. Supports labour, descriptions and parts selection; delivered/archive-delivery records remain protected.
- The reference backup's nine undelivered repairs all have partsStockApplied=false and received=0. Existing stock-applied parts remain protected from draft replacement, rather than replaying stock. Imported invoice currency stays fixed to preserve historical payment/FX interpretation.
- Save is one Room transaction with expected-JSON protection. Preserve a nativeDraftOriginal snapshot for imported edits. Existing part unit costs are retained; new parts capture cost conversion. Recompute invoice total and combined parts/manual cost; no stock/cash posting on draft save. Existing archived delivery path applies unapplied parts once on delivery.
- Added Room instrumentation regression for archive draft quantities, retained historical unit cost despite a different current catalogue cost, unchanged stock and receipts. Requires Android; not run here.

Modified: MaintenanceActivity.kt, MaintenanceDetailPage.kt, MaintenanceEditActivity.kt, MaintenanceDao.kt, MaintenancePartsRepository.kt.
Created: app/src/androidTest/java/com/alsafa/native8/sales/ArchivedRepairEditRoomTest.kt and this report.
Validation: actual editor/parts repository Kotlin compilation against Android API jar with boundary stubs passed; full Gradle/Compose/Room/device tests not run. SQL optimistic-lock checks performed separately. Tablet validation remains required.
