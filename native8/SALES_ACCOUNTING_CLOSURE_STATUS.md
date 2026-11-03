# Native 8 sales accounting — verification gate

This document distinguishes committed code from verified accounting closure. Do not deploy financial operations on production data until all gates pass.

## Implemented in Room
- Version 9 non-destructive migrations, payment FX snapshots, per-payment profit events, refund allocations, profit reversal events.
- Transactional new sales, collections, single-payment returns, cancellations and narrowly restricted unpaid-line corrections.
- Cancellation checks physical cash amounts per cashbox/currency against receipts, separately checks credited USD totals against invoice paid.
- Cancellation with prior refunds now attempts verified per-payment remaining refunds and profit reversal balancing within Room transactions; requires execution tests before acceptance.
- Returns now allocate across saved payment credits, record refund-time FX per allocation, and reverse attributed profits within the Room transaction; any unresolvable cent round-trip fails the transaction.

## Latest safety additions
- Added `RefundAllocationMath` for deterministic per-payment allocation, bounding each payment by its unreversed credited base; it is **not yet wired into the posting workflow**.
- FX round-trip guard requires exact cent equivalence and refuses silent tolerance; explicit rounding adjustment postings are still needed.
- The return engine now rejects refunds exceeding verified credited receipts minus prior refund allocations.

## NOT complete
- Multi-payment refund and cancellation paths are implemented in code but **not yet verified** by Android Room integration tests; refund-time rate comes from locally editable settings, not a live market feed.
- Rounding residual journal entries, profit reconciliation for debt-only returns, partner profit ledger, and historical FX compatibility remain open.
- Consistent invoice-base-currency model beyond USD; historical invoices with missing FX.
- Fully auditable item addition/deletion and paid-invoice correction.
- UI integration for refund-rate confirmation, instrumentation tests, Room migration tests, tablet build, and financial reconciliation.

## Mandatory acceptance tests
1. 100 USD invoice: collect 25 USD + 1000 TRY + 2800 SYP; verify paid 65 USD, due 35 USD, cash movements 25 USD/1000 TRY/2800 SYP, payment profit entries dated at collection.
2. Cancel fully collected mixed-currency invoice with no returns: restore only unreturned stock; reverse exactly each original currency/cashbox; zero debt; reverse each profit once; second cancellation rejected.
3. Return part of mixed-currency invoice, refund in a different currency, then cancel: no double refund, each refund allocated to original payment and its own FX snapshot, profit reversals capped by original profit.
4. Multiple partial returns with discounts and cent rounding: sum of returned values never exceeds invoice net; no negative balances.
5. Roll back on missing FX, missing profit record, insufficient stock, failed ledger insert: no stock/cash/receivable/profit mutation.
6. Repeat after process restart and database migration from versions 7 and 8; confirm preserved old rows.
7. Fully paid invoice: no further collection permitted; zero credited amount rejected.
8. Correct unpaid line: stock delta, invoice total, receivable balance and audit trail consistent.

Status: **IMPLEMENTED PATHS ARE UNVERIFIED / NOT ACCOUNTING-CLOSED / NOT DEVICE-TESTED**. Pure JUnit allocation tests were added but not executed here. Room transaction rollback and migration require real test runs. Do not mark this section as ready for production.
