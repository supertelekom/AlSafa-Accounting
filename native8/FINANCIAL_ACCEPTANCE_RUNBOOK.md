# Native 8 — Financial audit execution checklist

Branch: native6-development. Scope: native8 only.

## Current implementation

- RoomFinancialIntegrityAudit: orphan checks for new sales child tables, FK checks, and cross-archive numbering collisions.
- FinancialIntegrityAcceptance: read-only checks for historical archive line orphans, duplicate legacy numbers, duplicate item ordinals, source-import count mismatches, and safe next invoice number calculation.
- LegacyArchiveAudit: read-only staged source-to-Room comparison for invoice identity, date, customer, phone, total, and original line JSON.

## Execution (isolated data copy only)

1. Back up the tablet application data and copy the SQLite database with a consistent WAL snapshot. Never copy a live main .db alone while WAL contains uncheckpointed transactions.
2. Stage the original legacy backup into an isolated test database using the same importer.
3. Call FinancialIntegrityAcceptance.inspect(testDb.openHelper.readableDatabase) and record every Result field. Do not use a success label based on a screenshot or count alone.
4. Call LegacyArchiveAudit(staging, testDb).inspect(runId), compare all findings and separately review the two flagged historical invoices.
5. Import the same backup a second time into the isolated copy. Compare all affected table counts and row IDs before/after, including stock movements, payments, cashbox movements and receivables.
6. Create test sales in the isolated copy to confirm next number = max(historical, new)+1 and uniqueness after repeated imports. Check the cross-table collision audit again.
7. Only after zero orphans and verified financial reconciliation, design an explicit Room version 7 migration for foreign keys. No destructive fallback and no retroactive edits to original transactions.

## Unresolved findings

- No device database or staged backup was available for execution in this change.
- Historical paid/due reconciliation must account for staged payments, currency, refunds and cancellations before a 100% financial match can be claimed.
- Child tables currently lack enforced Room foreign keys; the read-only checks detect but do not repair this.
- The acceptance code has not been compiled or run in Android instrumentation in this session.

**Status: automated audit code extended; runtime verification pending.**
