# Native 5 — cancellation implementation report (2026-10-20)

Branch: `native5-clean-foundation`. This report describes source changes, not production deployment.

## Implemented together
- SQLite schema v8 additive upgrade: `online_dispatch_links` records the original dispatch event for each newly dispatched online invoice; `online_cancellations` records immutable, one-per-invoice cancellation details. Both tables have immutable update/delete guards.
- Online dispatch now writes its exact event linkage within the existing transaction containing the invoice, stock removal, journal and outbox.
- `UncollectedOnlineCancellationService` supports a strictly limited **uncollected** online invoice with a **pending review** and explicit physical return confirmation. It requires a linked original dispatch, zero collected/released cost, no collection history, the original two balanced transit/inventory journal lines, and nonempty negative dispatch stock movements.
- In one SQLite transaction it posts opposite inventory/transit journal lines, restores the original product quantities, records the immutable cancellation and adds a sync outbox operation. Duplicate cancellation is blocked by a unique invoice constraint. A failed write rolls back the entire transaction.
- Online receipt posting rejects cancelled invoices.
- Android instrumentation tests added for the uncollected reversal, collected-invoice rejection, and the exact returned stock in the outbox JSON.

## Follow-up implementation in the same development batch\n- Cancellation outbox payload now carries a structured JSON array of product-level returned quantities, instead of only cost and invoice IDs.\n- Added `TabletOnlineCancellationGateway`, which schedules WorkManager upload only after the local transaction succeeds; upload remains durable if scheduling is temporarily unavailable.\n- Extended the cancellation instrumentation test to parse and validate the stock movement payload.\n\n## Deliberate restrictions and remaining release blockers
1. **Do not enable a production cancellation button yet.** A `TabletOnlineCancellationGateway` now schedules upload after successful commit; **the service is not wired into the tablet UI**. The Firebase receiver/phone is not yet implemented.
2. Firebase receiver/phone application of `ONLINE_DISPATCH_CANCELLATION` is not implemented or validated. The cancellation outbox payload now includes exact per-product return movements (`inventoryMovements`: productId, quantityDelta). **However, the receiver still has no verified idempotent apply path**, and dispatch ordering/authorization on the phone must be tested before production.
3. Paid or partially collected invoices need a separate refund/credit-note workflow: refund liability, actual cash disbursement and physical goods return must be distinct audited events. This stock-only service refuses them.
4. Legacy invoices without `online_dispatch_links` are intentionally blocked, not guessed or silently backfilled.
5. A pending review still blocks new collections; rejection resumes collection. Approval workflow and decision/authorization policy remain incomplete.
6. Android instrumentation tests have been committed but **not executed**; Firebase sync and actual Android builds have **not been verified**. GitHub Actions minutes were previously unavailable.
7. Before any real rollout: run migrations on a disposable copy of a current backup, instrumentation tests, two-device offline/online sync tests, inventory and journal reconciliation, and verify zero writes to live data until explicit approval.

No live shop data or Firebase records were modified by these source commits.
