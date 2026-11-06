# Pending repairs and post-collection receipts

- Simplified home adds a compact pending-repairs tile next to ready deliveries (four columns on tablets; two on narrower layouts).
- Reuses ReadyDeliveriesActivity in pending mode: old/new repairs in repair or awaiting a part, invoice number/status/customer, direct details and status editing. No historical financial JSON is loaded for this list. Existing onResume refresh removes newly-ready records from pending and shows them in deliveries.
- Successful maintenance delivery, quick repair delivered immediately, and fast sale completion open the final receipt only after the repository save succeeds. An uncollected quick repair does not trigger automatic receipt sharing.
- If a customer phone exists, launch WhatsApp Business with the PDF, thank-you text and recipient hint. Final sending still requires WhatsApp user confirmation; Android sharing does not provide silent sending. No send is attempted without a phone. One automatic launch per receipt activity instance/restored state; manual retry remains available.
- Financial writes, invoice numbering and inventory logic are unchanged. A delivery without outstanding payment can still produce its receipt after successful delivery.

Modified: MainActivity.kt, stylekit/SimplifiedHomePage.kt, maintenance/MaintenanceDao.kt, maintenance/ReadyDeliveriesActivity.kt, maintenance/RepairDeliveryDialog.kt, maintenance/QuickRepairActivity.kt, sales/FastSaleActivity.kt, sales/SalePrintActivity.kt.
Created: this report.

Validation: limited Kotlin compilation against Android API jar and dependency stubs; SQL pending/ready classification checks. Full Gradle/Room/Compose build and tablet/WhatsApp interaction not run. Bluetooth work remains deferred.
