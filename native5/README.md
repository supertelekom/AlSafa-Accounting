# AlSafa Native 5 — clean foundation

This is a **new, isolated implementation**. The production AlSafa app and Native 4 are not modified by Native 5 development.

## Non-negotiable invariants
1. The tablet is the accounting authority; every committed operation is durable in its local SQLite database.
2. Financial postings, inventory movements, and an immutable outbox event commit in **one SQLite transaction**.
3. The cloud is an upload/backup destination; it must never silently replace tablet accounting records.
4. Retry uses stable event IDs and idempotent cloud writes. A failed upload never reverses or repeats a local financial posting.
5. Amounts are integer minor units with an explicit currency (USD, TRY, SYP). Currency conversion is an explicit recorded transaction, never an implicit sum.
6. Corrections use reversal/adjustment entries, not destructive edits of posted financial history.
7. Local operation never depends on network or Firebase authentication.

## Background upload requirement
After every successful tablet posting, call `TabletSyncScheduler.schedule(context, storeId)`; also schedule at app startup once a store ID and Firebase access have been configured. WorkManager persists work when the user opens WhatsApp or the app process exits, waits for network, and retries with backoff. Android does not guarantee immediate execution. The scheduler is implemented but is NOT yet connected to a configured store identity or every posting workflow; do not claim end-to-end background sync is operational.

## Delivery stages
- Stage 1: pure-Java accounting rules and unit tests (this branch).
- Stage 2: Android SQLite schema, atomic posting and durable outbox.
- Stage 3: Firebase upload-only transport, offline retries, idempotency tests.
- Stage 4: sales, purchases, repairs, inventory, cash, daily close, profit reports.
- Stage 5: tablet-only UI parity against production v3.9.353, migration dry runs, verified tablet APK.

**Status:** foundation only; not an installable or production-ready app.
