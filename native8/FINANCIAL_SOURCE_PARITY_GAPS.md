# Native 8 — financial parity gate (legacy v3.9.359)

Source of truth: user-provided archive, `assets/main.js`. This document is an implementation blocker list, not a claim of completion.

## Verified source functions
- `reportProfitBreakdown(from,to)` near line 6428: store profit by sale date; online recognized profit by receipt event date; repair receipts net of allocated cost; returns recognized with reference-type-specific formulas; expenses at their recorded dates; reconciliation displayed separately and excluded from net profit.
- `grossProfitUsdForRange(from,to)` near line 6489 and `profitUsdForRange` near 6510: authoritative partner profit calculation. External partner deduction in legacy is historical; Native8's approved family split is 40/35/25 with external partners removed.
- `renderPartners()` near line 6513: distinguish selected-period share, selected-period withdrawals, cumulative due.
- `saveExpense(form)` near line 8203: validate amount and selected vault balance; record category, notes, currency, amount, payment method, vault, historical rate snapshot, date/time; post a negative vault movement; roll back in-memory change on persistence failure.
- `renderReports()` near line 6450: selected date interval, gross invoice totals, recognized profit, online pending profit, repair receipts, returns, expenses, withdrawals, reconciliation shown separately.

## Important mismatch with newly introduced calculation helper
`LegacyFinancialRules.kt` is pure and **not connected** to Room or UI. Its `Receipt` assumes preallocated cost and therefore cannot by itself reproduce `onlineSaleReceiptCost(event)` or `repairReceiptCost(event)`. Its `Adjustment` requires a caller to compute the source-specific refund formula; no caller currently establishes that. It cannot be used as the authoritative reporting ledger until these mappings are implemented and checked.

## Required implementation before financial signoff
1. Read both staged legacy data and Room operational events exactly once, with source IDs and deduplication rules.
2. Implement transaction-safe expense persistence, vault balance checks and historic FX snapshots.
3. Implement transaction-safe partner withdrawals, dated cash movements, and cancellation compensation.
4. Wire source-equivalent profit breakdown and period filters to the actual reports and partner screens.
5. Compare per-event and per-period totals against the same legacy backup executed through the original JS functions; include store, online partial payments, repairs, returns, mixed currency, expenses, partner withdrawals, daily close carryover.
6. Run `testDebugUnitTest`, `assembleDebug`, and on-device restore/regression checks. Never declare completion from APK build alone.

**Status:** NOT IMPLEMENTED / NOT VERIFIED for the end-to-end integration. Do not rely on displayed partner profit as certified.
