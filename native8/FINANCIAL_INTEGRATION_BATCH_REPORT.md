# Financial integration — current verified implementation scope

Branch: native6-development. Changes restricted to native8/.

## Implemented in this batch
- MaintenanceDao.deliveredInPeriod(from,to) provides a read-only query of repair rows with delivery dates in the selected interval.
- MaintenanceProfitAuditRepository.inspect validates period dates and returns unresolved repair IDs; it **does not recognize revenue or profit**.
- MaintenanceProfitAuditTest ensures unverified repairs cannot be published as financial profit.

## Required next steps (not implemented)
1. Inspect the original main.js repair receipt and cost event generators; map each event's stable identifier, recognition date, amount, COGS, tax and exchange snapshot.
2. Reconcile imported repair receipts with newly created operational repair collections; guard against duplicated posting.
3. Distinguish in-store invoice-date profit from online collection-date profit and verify discounts and partial payments.
4. Reconcile original return/cancellation logic with Room sale refund allocations and profit reversals.
5. Only after all streams reconcile, set financial result completeness true and wire actual report/partner/profit screens.
6. Run Android Room tests and build on a disposable database clone. No build/test execution is claimed here.

## Financial safeguards
- Imported financial records remain read-only.
- Partner withdrawals reduce dues, not net profit.
- Unknown historical FX is not guessed.
- Do not connect UI to partialNet or mark an audit complete because a repair was delivered.

## Sales channel separation batch
- Added SalesChannelProfit pure projector with explicit STORE/ONLINE invoice channel, store profit on invoice date and online profit on dated collections.
- Added SalesChannelProfitTest for dates, partial receipts, duplicate receipt IDs, and over-collection.
- **Critical integration gap:** RoomSalesEngine.recognize currently posts sale_profit_events for all payments on collection date. Do not sum those events together with SalesChannelProfit store profits or the same sale may be counted twice.
- RoomSale currently lacks a verified source-mapped channel field. Before wiring the projector to production, map the channel from the original v3.9.359 sale records and add a non-destructive Room migration for new invoices. Existing records with unknown channel must block financial approval, not default to STORE.
- Verify online discount allocation against main.js before calling these pure-model tests source parity. No actual Gradle/JUnit/Room run has occurred.

## Legacy online channel recovered from original source (2026-11-05)
- Inspected v3.9.359 assets/main.js: invoice creation stores saleType='online' or 'store' (~7933/7938); onlineSaleReceiptEvents uses (sale.saleType||'store')==='online' (~2763); original archive view shows online badge and collection button (~3429).
- Historical archive already preserves original_json, so added LegacySaleChannel parser and LegacySaleChannelAuditRepository source-scoped census without a schema change or rewriting historical invoices.
- Original main.js fallback for missing saleType is STORE. Unrecognized nonempty values fail closed.
- Existing Room sales channel column (v17) applies to NEW Room invoices, not legacy_sales_archive; do not copy legacy IDs into Room sales or repost old cash.
- Outstanding: wire source-backed channel into new invoice creation UI and receipt profit postings, reconcile imported payments and repair events, verify FX and original discount semantics; run Android/Room tests on a local build. LegacySaleChannelTest uses org.json and may require Android/Robolectric test support on local JVM.

## Native8 new invoice channel propagation batch
- SaleDraft now carries channel, default 'store'; SalesDraftSession retains selected channel and resets after successful save.
- FullSaleActivity restores original source labels 'نوع البيع *', 'بيع من المحل', 'بيع أونلاين'; QuickSaleActivity also exposes explicit choice so a shared draft cannot silently inherit a prior channel.
- RoomSalesEngine.save verifies the channel and stores it on RoomSale inside the same transaction as invoice, stock, payments and receivables.
- SaleDraftChannelTest covers default and copy-with-payment preservation.
- **Not yet complete**: RoomSalesEngine currently only writes receipt profit events for ONLINE; STORE invoice-date profit posting has not been implemented, so unified reporting remains incomplete. Existing unclassified Room invoices remain NULL; do not automatically classify without original source. No Gradle or device tests run.

## Store invoice-date profit posting batch (2026-11-05)
- Added StoreSaleProfitDao and registered in AppDatabase v18; store profit has unique sale_id primary key.
- RoomSalesEngine.save inserts store invoice profit once, in the same Room transaction as invoice, stock, cash and debt. Online still posts collection-date events only.
- UnifiedFinancialPeriodRepository now includes store profits in partialNet and refuses to read when StoreSaleProfitDao was not supplied; complete remains false and approvedNet remains blocked.
- **SAFETY BLOCK / NOT COMPLETE**: STORE returns, cancellation and unpaid corrections are currently rejected until a proper source-aligned store profit reversal and adjustment journal is implemented. This is deliberately safer than silently overstating profit. ONLINE return/cancel logic remains unchanged.
- **IMPORTANT**: legacy sale archives, repair receipt events and full source-aligned tax/discount/FX reconciliation have not yet been integrated into the unified period; existing historical sales and older Room invoices must be audited before approval. No Gradle, Room migration or device tests were run.

## Restored store invoice operations
- Paid STORE cancellation no longer has the temporary blanket block. It reuses the existing cash-refund and stock restoration transaction, and appends a store-profit compensation for remaining recognized margin. It does not create online collection-profit reversals for STORE payments.
- Unpaid STORE line corrections now append a profit delta when their original store profit matches the invoice and there are no prior adjustments. Other previously adjusted cases still require reconciliation.
- **NOT VERIFIED:** These paths have not been run under Gradle or instrumented Room. Validate mixed-currency refund allocation, partial returns, preexisting refunds, and idempotency on a disposable database.
- The full financial source union, historical repairs, legacy online receipts, original expense/partner reports, and final screen integration are still outstanding; the financial review screen remains explicitly partial.

## Visible financial traceability batch
- FinancialReviewActivity now includes per-invoice/per-payment detail of store profit, store adjustments, online receipt profit and reversals.
- Source completeness diagnostics show unverified new invoice count and explicitly identify historical sales, maintenance, expenses and partners as unapproved.
- No original historical receipts were reposted, no historical invoice was modified, and no profit completeness flag was turned on.
- Important: these are inspection controls, not final profit reports; tests and build are still pending.

## Financial audit integrity batch — November 5
- Restored positional Result compatibility for earlier financial tests and callers after storeSaleProfit was introduced.
- Excluded historical external-partner withdrawals from the family-only partner due projection; operational partner IDs are validated against father/me/brother.
- Added SalesFinancialAuditDao.nonOnlineProfitEventCount() to identify collection-profit events referencing missing, unclassified or STORE invoices.
- FinancialReviewActivity source diagnostics now shows suspect event count; UnifiedFinancialPeriodRepository refuses aggregation if any suspect receipt-profit event exists.
- This is **diagnostic and fail-closed**, not a historical repair. Existing profit event rows were not deleted or rewritten.
- Outstanding: full legacy source reconciliation, maintenance collections and costs, final report screens, migration tests, Gradle build and actual restored-device parity.

## Capital and historical invoice visibility batch
- Added LegacySaleDao.historicalInPeriod for immutable archived sales snapshots.
- Added LegacySalesProfitAudit source saleType classification with duplicate/unknown-ID reporting. It does **not** invent historical profits or mark incomplete archives as approved.
- FinancialReviewActivity now displays new STORE invoice net revenue, stored cost basis (capital of sold watches), and historical STORE/ONLINE invoice counts for the selected period. Existing new-sale receipt profit remains separately shown.
- Added LegacySalesProfitAuditTest for archived channel classification and explicit incomplete profit status.
- Remaining source-parity blockers: original historical item costs and tax, legacy online receipt dates and FX, repair collections/parts, return impacts, all-period expense reconciliation, and partner cumulative due. Do not label the combined total as final net profit before these are verified.
- No Gradle, Android instrumentation or source-JS parity execution has been performed in this batch.
