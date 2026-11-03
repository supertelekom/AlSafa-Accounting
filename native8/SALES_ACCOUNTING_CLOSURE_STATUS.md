# Native 8 sales accounting — verification gate

This document distinguishes committed code from verified accounting closure. Do not deploy financial operations on production data until all gates pass.

## Implemented in Room
- Version 9 non-destructive migrations, payment FX snapshots, per-payment profit events, refund allocations, profit reversal events.
- Transactional new sales, collections, single-payment returns, cancellations and narrowly restricted unpaid-line corrections.
- Cancellation checks physical cash amounts per cashbox/currency against receipts, separately checks credited USD totals against invoice paid.
- Cancellation with prior refunds is blocked until allocations and profit reversal balancing are implemented.
- Single-payment returns require original cashbox/currency and saved FX, with exact cent reconciliation; other scenarios blocked.

## NOT complete
- Multi-payment partial return allocations, different-currency refund with explicit refund-time FX, cancellation after prior returns.
- Proportional profit reversals for debt-only returns, rounding residuals, and partner profit ledger.
- Consistent invoice-base-currency model beyond USD; historical invoices with missing FX.
- Fully auditable item addition/deletion and paid-invoice correction.
- UI, instrumentation tests, Room migration tests, tablet build, and financial reconciliation.

## Mandatory acceptance tests
1. 100 USD invoice: collect 25 USD + 1000 TRY + 2800 SYP; verify paid 65 USD, due 35 USD, cash movements 25 USD/1000 TRY/2800 SYP, payment profit entries dated at collection.
2. Cancel fully collected mixed-currency invoice with no returns: restore only unreturned stock; reverse exactly each original currency/cashbox; zero debt; reverse each profit once; second cancellation rejected.
3. Return part of mixed-currency invoice, refund in a different currency, then cancel: no double refund, each refund allocated to original payment and its own FX snapshot, profit reversals capped by original profit.
4. Multiple partial returns with discounts and cent rounding: sum of returned values never exceeds invoice net; no negative balances.
5. Roll back on missing FX, missing profit record, insufficient stock, failed ledger insert: no stock/cash/receivable/profit mutation.
6. Repeat after process restart and database migration from versions 7 and 8; confirm preserved old rows.
7. Fully paid invoice: no further collection permitted; zero credited amount rejected.
8. Correct unpaid line: stock delta, invoice total, receivable balance and audit trail consistent.

Status: **NOT ACCOUNTING-CLOSED / NOT DEVICE-TESTED**.
