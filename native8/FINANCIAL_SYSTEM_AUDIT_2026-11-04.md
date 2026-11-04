# Native 8 — Cashboxes / expenses / partners / reports audit (2026-11-04)

Authority: v3.9.359 source, not native4/native6/native7. Scope: native8 only.

## Inspected
- UnifiedCashboxBalance: read-only legacy projection plus operational Room ledger, excluding overlapping historical IDs. Closing snapshot is an anchor, not a second receipt.
- UnifiedCashboxOperations: Room transaction for two-leg transfers; record entry; insufficient-funds guard on verified historical balance.
- CashboxMovementsActivity: combined historical and operational list, currency filtering, transfer UI.
- MaintenanceDeliveryRepository: maintenance collection and stock deduction in one transaction, operational cash movement.
- ExchangeRateSettings: direct user-configured currency rates, not a replacement for historical FX snapshots.
- PHASE1_CLOSING_RECONCILIATION.md: 34 historical closings, 722 historical cash rows, 72 partner withdrawals in the previously examined fixture; historical differences must not be overwritten.

## Implemented in this batch
1. Canonical alias for ChamCash (previous maintenance writes used 'ChamCash', historical balance calculator omitted that alias). Existing rows remain unchanged and now resolve to the same balance.
2. Maintenance delivery writes canonical cashbox IDs for all future collections.
3. Transfers and manual ledger entries validate vault/currency combinations, including USD/SYP only for ShamCash as documented in the legacy-source analysis.
4. Maintenance collection rejects unsupported ShamCash TRY instead of writing an invisible/unsupported currency balance.

## Important invariants / remaining work
- Do not post imported cash movements again; historical movements are read-only.
- Do not sum currencies without their original FX rate snapshots.
- Do not treat partner withdrawals, transfers, or cash-count reconciliation as operating expenses.
- Do not automatically rewrite prior daily closing snapshots based on current reconstructed history.
- Verify closing save-path and posting order before implementing new operational daily closings.
- Audit all cash writers (sales, maintenance, purchases, expenses, partner withdrawals) for canonical vault IDs and reference-level idempotency.
- Audit all payment and expense posting to use transactionally consistent ledger writes and source-defined rate snapshots.
- Audit partner shares 40/35/25 and withdrawal dates against restored history; audit report date filters and recognized profit.
- Run Gradle compile, migration tests, and tablet verification. **No build or APK test has been run in this batch.**

## QA matrix to run on cloned DB, never live data
- Existing historical 'ChamCash' operational row included once in shamcash USD balance.
- New maintenance receipt to ChamCash USD counted once; TRY receipt rejected.
- Transfer 30 TRY drawer -> external: two ledger rows with same reference; total TRY unchanged.
- Expense 20 TRY from drawer reduces cash and recognized profit once.
- Partner withdrawal reduces cash and partner due but not recognized profit.
- Close with 10 TRY drawer carryover: next day begins with 10 TRY; prior-day income not reposted.
- Verify date-scoped profit from prior online collections and maintenance delivery.
