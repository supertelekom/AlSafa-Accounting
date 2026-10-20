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

## Tablet review UI integration (subsequent batch)
- Added explicit confirmation before saving a review request; a required reason and the exact invoice lookup are validated by the service.
- Added a separate confirmed review rejection action that uses the immutable decision service. No invoice deletion, refund or stock restoration occurs from these UI controls.
- Prevented new review requests for invoices already marked cancelled.
- UI explicitly warns that review requests and decisions are **local to this device**; no cross-device replication for these records has been built.
- **Release blocker:** do not use this review UI for multi-device store operation until review request/decision operations are synced and authorized consistently on all devices. This is especially important because a review blocks collections only on the device holding that review.
- The UI cancellation posting action remains deliberately disabled until a verified idempotent phone/cloud replay and actual instrumentation tests exist.
- Android build, migrations, Firebase replay and instrumented tests have not been run in this session. These are source changes only; no APK or release certification is claimed.

## Further accounting integrity hardening
- Cancellation now verifies that the linked original accounting event has kind `ONLINE_DISPATCH`, not merely matching journal amounts and inventory movements. A non-dispatch event cannot be reversed through this path.
- Kept database uniqueness enforcement for duplicate cancellation to preserve the existing instrumentation test's expected SQLite constraint exception.
- Review-request UI integration is source-only. No evidence of a successful Android build or instrumentation execution has been obtained.
- **Critical remaining work:** no production-safe cloud-to-phone cancellation replay or review-decision replication, no collected-invoice refund workflow, and no verified end-to-end multi-device tests. Consequently this is **not a completed release batch**, and enabling production cancellation would be unsafe.

## Integrity checks added in latest batch
- Android instrumentation test now forces a stock quantity overflow while reversing a physically returned uncollected invoice. It asserts rollback of the cancellation event, journal, audit record and outbox; no partial reversal is allowed.
- New read-only `OnlineCancellationAudit.summary(invoiceId)` exposes cancellation event ID, local outbox state and counts of journal and inventory movement lines for diagnostic use. **Counts are not proof of accounting correctness** and must not be used as a release gate without deeper reconciliation.
- All changes remain on the development branch. Instrumentation tests and APK builds remain unexecuted/unverified.

## Review synchronization implementation batch — source only
- Schema v9 `review_sync_outbox` is append-only for request and decision payloads (only queue delivery status is mutable).
- Review request service now inserts its versioned JSON upload operation **in the same SQLite transaction** as the immutable review request.
- Review decision service likewise inserts its upload operation atomically with the immutable reject/approve decision.
- `ReviewSyncPayload` encodes structured versioned JSON for both review operations.
- `ReviewSyncUploader` uses the existing immutable Firestore operation writer; retries can re-send identical IDs without overwriting conflicting cloud records.
- `TabletSyncWorker` uploads review operations as well as accounting outbox entries, with bounded batches and retry while either queue is pending.
- **Not yet complete:** review operations are upload-only; the phone has no verified read/apply pipeline. UI does not directly schedule the worker because it lacks a verified store session ID; pending operations await normal worker scheduling. Cloud authorization and remote application are not validated.
- **Deferred per user request:** build, migration/instrumentation and two-device tests will be run at the final testing stage. These changes are not yet approved for production.

## Consolidated cancellation completion pass
- Added a distinct tablet **approve for posting** control with explicit confirmation; approval never posts a reversal or refund.
- Updated uncollected cancellation regression fixtures to record approval before attempting a reversal, consistent with the service's authorization gate.
- Corrected the legacy migration test's expected database schema from v7 to v9 and added a check that the review sync queue exists after migration. The tests remain deferred and have not been executed.
- Cloud IDs for review operations are prefixed `review-` to avoid accidental collision with accounting operation IDs in the same Firestore collection.
- Tablet invoice preview now shows an immutable review decision and existing posted cancellation audit record, when present.
- The implementation still lacks a verified phone/cloud replay and paid-invoice credit-note/refund posting, so **the complete cancellation feature is not yet finished**. No production activation is justified.

## Paid-invoice schema groundwork — not activated
- Native5 SQLite schema advanced to v10 with `online_credit_notes` (customer refund liability) and `online_refunds` (separate actual refund event). Both preserve immutable audit identity; the credit-note row only permits controlled changes to the cumulative refunded amount.
- **Important:** no credit-note posting service, refund posting service, stock return reconciliation, UI activation or cloud-to-phone replay has been successfully committed in this batch. Schema alone must never be represented as a working cancellation or refund feature.
- Android migrations and tests are deferred as requested. This branch is not production-ready.
