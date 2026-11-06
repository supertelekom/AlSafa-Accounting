# Full-mode sales UI batch

Scope: native8 only. Date/time expansion postponed. Existing Room posting rules and database schema are unchanged.

## Changed files
- FullSaleActivity.kt: native full invoice cart, search/barcode, edit/remove lines, explicit USD discount, multiple currency/cashbox receipts, credit sale, state restoration and idempotent save request. Resume the older shared draft entry point without dropping its lines.
- SalesArchiveActivity.kt: native searchable archive, existing date filters, old/new details and returns-history entry point.
- SaleDetailsActivity.kt: native details, PDF/print/share, outstanding collection, customer correction, existing unpaid-line correction, per-line return and cancellation confirmation. Historical customer metadata editing retained.
- SalePaymentsActivity.kt: native collection page, classify invoices by Room records rather than ID prefix; historical collection keeps its original currency and repository checks.
- SaleReturnsActivity.kt: native quantity/reason/refund selectors, preload the sole original payment currency/cashbox, prevent duplicate submissions, call the existing return transaction.
- SalesPostingActions.kt: database-record classification for historical/native collections.

All six files are under app/src/main/java/com/alsafa/native8/sales/.

## Validation
- Financial JVM regression suite: 131 tests passed. These test existing rules, not device UI interaction.
- Kotlin compile of affected screens and supporting classes against Android jar and boundary stubs: passed. This is not a Gradle, Room KSP, Compose integration or APK build.
- Full Android Studio build and device workflow checks remain required.

## Remaining scope — do not claim complete parity
- Full invoice accounting/display base remains USD; exact original foreign-currency line representation is not added in this batch.
- Overpayment/change posting is not implemented. Saving overpayment is blocked with an explicit message; no invented cash movements.
- Historical financial item editing/returns and an atomic exchange workflow are not implemented. Existing historical metadata editing and confirmed collections remain available.
- Legacy loyalty/automatic discount rules, manual products and all original full-sale form options still need source parity review.
- Bluetooth and automatic WhatsApp sending are outside this batch.

## Device acceptance
1. Create multi-item invoice; edit quantity/price, delete one item, save once.
2. Try cash, credit with customer, and two receipts in different currencies/cashboxes; verify ledger and balance.
3. Open old/new archive entries, edit customer, collect a remaining balance, open PDF.
4. Return part of a native invoice, then cancel the remainder; verify stock/refunds are not repeated.
5. Reopen/rotate a draft and verify lines/receipts are retained.
