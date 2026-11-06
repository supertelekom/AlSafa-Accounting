# Simplified mode: fast sale and ready deliveries — 2026-11-06

Reference: original v3.9.359 assets/main.js renderFatherSale/saveFatherSale and renderFatherDeliveries; original style.css. Changes confined to native8.

## Implemented
- Two square actions (fast sale, existing quick repair), with centered square deliveries below; Tajawal, RTL, scrolling and system insets through existing stylekit.
- Original fast sale form sequence: search/barcode, selected watch/base price, final price/currency, cash or ChamCash, completion. One watch per sale; uses existing Room transaction for stock, cash and profit. TRY prohibited for ChamCash. Payment preserves selected currency; invoice accounting uses existing USD schema.
- Stable request ID prevents duplicate invoice save on retry. Shared numbering includes maintenance maximum.
- Ready delivery list reads all ready/waiting records, native and restored, with no 250-record cap. Historical amount due includes source-specific payments and returns. Unreadable amounts explicitly request detail review.
- Native delivery opens existing collection dialog; list refreshes on return.

## Validation
- Financial JVM JUnit suite: 110 tests passed, including two new fast-sale tests (250 TRY at 50/USD, currency-preserving receipt, one item, cost conversion, prohibited TRY ChamCash).
- Initial JVM compilation failed for a missing harness dependency (SalesChannelClassification); dependency added and suite passed.
- Android-facing Kotlin compilation passed against Android classes with boundary stubs for Room/services/scanner; this is a syntax/type smoke check, NOT a full Android build or Room integration test. Existing deprecated Android API warnings remain.
- git diff --check passed.
- Gradle, APK installation, Room/KSP generation, real camera scanning, physical printing and tablet layout NOT run here.

## Remaining / honest limits
- Restored ready repairs are visible and open their details, but legacy archive write protection remains: delivery/collection of those records needs a separate transactional implementation. Only native records currently expose the delivery action.
- Completion opens the existing print screen; original automatic thermal printing/template is not ported in this batch.
- Barcode camera uses external photo capture/image recognition, not an embedded live scanner.
- Device acceptance: square layout/RTL, fast sale cash and ChamCash, conversion, stock/cash/profit once, ready native delivery/removal after completion, restored ready visibility.

## Files
- Modified: `native8/app/src/main/AndroidManifest.xml`
- Modified: `native8/app/src/main/java/com/alsafa/native8/MainActivity.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/maintenance/MaintenanceActivity.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDao.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/sales/RoomSalesEngine.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/sales/SalesProductCatalog.kt`
- Modified: `native8/tools/run_financial_junit.py`
- Created: `native8/app/src/main/java/com/alsafa/native8/maintenance/ReadyDeliveriesActivity.kt`
- Created: `native8/app/src/main/java/com/alsafa/native8/sales/FastSaleActivity.kt`
- Created: `native8/app/src/main/java/com/alsafa/native8/sales/FastSaleDraft.kt`
- Created: `native8/app/src/main/java/com/alsafa/native8/stylekit/QuickSalePage.kt`
- Created: `native8/app/src/main/java/com/alsafa/native8/stylekit/SimplifiedHomePage.kt`
- Created: `native8/app/src/test/java/com/alsafa/native8/sales/FastSaleDraftTest.kt`
- Created: `native8/SIMPLIFIED_SALE_DELIVERIES_2026-11-06.md` (this report).
