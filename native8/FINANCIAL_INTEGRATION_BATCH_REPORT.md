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

## Online capital reporting batch
- OnlineCapitalReport separates invoice-date stock cost from cost matched to collection-date profit. Neither calculation posts inventory, cash or expenses.
- SalesPostingDao.onlineInvoicesInPeriod selects verified online invoices by sale date; each invoice's lines supply cost basis with BigDecimal.
- FinancialReviewActivity shows online stock capital and online collection-matched cost as different metrics. Never add these two amounts together: they describe the same cost at different recognition stages.
- OnlineCapitalReportTest covers cost without payment, partial collection and negative-cost rejection.
- Historical invoice profit, imported receipt FX, repair collections/parts, legacy expenses and cumulative partner dues remain unverified and are NOT silently included in an approved net.
- No local Gradle/Room/Android device build was run.

## Strict historical/repair/expense/partner consolidation foundation — 2026-11-05
Files added:
- FinancialSourceReconciliation.kt: immutable dated source-keyed events, duplicate-event rejection, store/online/repair/return/expense totals.
- CumulativePartnerProfit.kt: 40/35/25 cumulative earned share minus dated withdrawals, not an operating expense.
- VerifiedFinancialConsolidation.kt: blocks publishing final net/partner statement until all six streams are source-verified and cumulative net is supplied.
- FinancialSourceReconciliationTest.kt and VerifiedFinancialConsolidationTest.kt: arithmetic and fail-closed test cases.

**Not complete / do not approve**:
- No verified historical event extractor for archived item COGS, discounts, historical FX, returns, or online payment dates has been connected. The original main.js is not present in the checked-out repository tree; source-parity against it must be established from the provided original source.
- Repair delivery/receipt/parts records have not been reconciled to source events.
- Existing UnifiedExpensePartnerReadRepository can read legacy+new expenses/withdrawals, but it has not yet been integrated into a verified all-time source statement.
- No operational ledger writes or historical corrections were performed; no Gradle/Room/device tests were executed. New unit tests are authored, not claimed passing.

## Verified event adapter batch — 2026-11-05
- Added VerifiedFinancialEventAdapters.kt for source-ID-keyed, date-specific store invoices, online receipts, repair deliveries, operating expenses and profit adjustments. Historical FX must be supplied explicitly; no current-rate fallback.
- Added VerifiedFinancialEventAdaptersTest.kt with historical TRY conversion, online receipt date, repair/expense/return net and missing-ID checks.
- Added no financial ledger writes, no stock movements and no cashbox postings.
- **Still not complete:** Source event extraction from original imported invoice JSON, archived payment history, repairs and original cost/FX remains to be implemented and verified. Final profit and partner reports are not enabled. Tests are authored but not executed.

## Historical source inventory batch
- Added LegacyPaymentEventAudit.kt: pages through original imported payment rows, retains stable import-run/ordinal keys, validates date, amount, currency and invoice reference, and reports unresolved records without inventing FX or posting cash.
- Added LegacyFinancialSourceAudit.kt: source-run counts for sales, payments, repairs, expenses and partner withdrawals, plus dated sale-payment coverage.
- Neither audit implies historical profit correctness or enables final partner distribution.
- Pending: source-specific extraction of item COGS, FX snapshots, online receipts and returns; repair delivery costs; source-JS numerical parity; JUnit/Gradle and tablet verification.

## Expense and partner operational screens + maintenance evidence (2026-11-05)
- FinancialEntryActivity created: two modes (operating expenses and partner withdrawals) with scrollable forms, four cashboxes, currency choice, and new operational history. MainActivity buttons and manifest registration added.
- Expense/withdrawal save uses OperationalExpensePartnerRepository with Room withTransaction; cashbox movement is not posted twice. Existing historical entries are untouched.
- Stable ID and createdAt retained on retry; no assumed USD=TRY/SYP exchange rate. **Still required**: use the original exchange-rate settings automatically, original expense categories, dates and legacy UI labels. This screen is provisional, not source-design parity.
- MaintenanceCostAudit created with read-only evidence checks and a unit-test fixture. It does not alter stock and does not claim a historical cost when a verified snapshot is absent.
- **Incomplete**: maintenance receipt-to-cost matching, maintenance returns and exchanges, original historical repair profit, full expense/partner archive union and final partner balances, source JS parity, Room instrumentation, Gradle build, and device QA.

## Separate financial pages and repair source coverage
- Created ExpenseActivity and PartnersActivity as separate Android Activities with separate home buttons and manifest declarations. Shared base FinancialEntryActivity retains the existing transactional expense/withdrawal writer; no extra cash postings.
- Added MaintenanceDao.allDelivered and MaintenanceFinancialCoverage to count historical vs new delivered repairs and distinguish missing original cost/receipt evidence. FinancialReviewActivity now exposes those counts under source diagnostics.
- The repair margin candidate is **not** an approved net profit: labor cost, source receipt timing, FX, refunds/exchanges and historic cost parity remain to be verified. No profit event is posted by the coverage reader.
- Partner due totals cannot be finalized until all historical sales/online/maintenance/expense events are reconciled. Do not turn on complete=true or distribute partial profits.
- New pages are operational prototypes pending original v3.9.359 UI parity. Gradle/Room tests and on-device QA have not been run.

## Expense / partner tablet QA follow-up (2026-11-05)
- User confirmed expense and family partner withdrawal postings appear once in the selected cashboxes. These were test entries.
- FinancialEntryActivity no longer exposes manual USD-per-unit FX input. It snapshots ExchangeRateSettings.rate(selectedCurrency,"USD") when Save is tapped and stores the resulting rate in the existing immutable operation record.
- After successful Room commit, a noncancelable success dialog with "موافق" is shown. Acknowledging it clears amount/notes and leaves the user on the same activity; a new ID is generated for the next entry.
- Existing recorded test entries are deliberately not rewritten. If TRY/SYP was previously entered with a manual USD-per-unit value of 1, those entries must be inspected before trusting historical USD profit calculations.
- This commit has not been Gradle-built or tested on-device. No schema changes.

## Independent expense and partner activities
- ExpenseActivity now owns its own full layout, history, automatic configured FX snapshot, and save confirmation.
- PartnersActivity now owns its own full layout, partner selection, withdrawal history, automatic configured FX snapshot, and save confirmation.
- Neither screen inherits FinancialEntryActivity. The unused shared activity is unregistered from the Android manifest; its source remains for now and must not be used by navigation.
- Both continue to call the existing transactional OperationalExpensePartnerRepository, so no database migration or duplicate cash posting was introduced.
- No Gradle build, UI instrumentation, or tablet test was executed. Original v3.9.359 UI parity and final partner profit display remain outstanding.

## Historical expenses and partner withdrawals UI integration
- Backup schema audit for 2026-10-26 v3.9.359 lists 22 expenses and 72 partnerWithdrawals, each carrying original date, currency, amount and rateSnapshot. These are source counts, not validated USD totals.
- PartnerProfitsActivity now calls UnifiedExpensePartnerReadRepository for imported and new withdrawals and shows cumulative withdrawals by current family partner ID. It does not invent cumulative profit or approve balances.
- ExpenseActivity now calls the same read-only union and displays historical/new expense counts and converted total. It does not repost old cash movements.
- Important pending verification: compare actual original partner IDs, legacy rates and source JS rules with the backup; the reader currently excludes non-family partner IDs and throws for missing non-USD snapshots.
- Build and Android tests not run. Financial partner profit is NOT closed.

## Original v3.9.359 source and backup financial extraction
- Inspected actual assets/main.js in the original source archive and 2026-10-26 backup. Backup contains 111 sales, 308 repairs, 22 expenses, 72 partner withdrawals, 36 payments, and 4 returns.
- main.js grossProfitUsdForRange uses saleCost=sum(item.cost*qty), store total minus tax, repairReceiptEvents on original repair date plus later payment dates, proportional repairReceiptCost, return adjustment and expense subtraction. Cash reconciliation is excluded from profit.
- Added LegacyHistoricalProfitProjection.kt: read-only staging extraction of historical store margin, repair receipt margin, returns, expenses and partner withdrawals with source date and FX snapshots. Added a provisional audit display in PartnerProfitsActivity.
- CRITICAL: This is an audit projection, not final source parity. Repair parts invoice totals and refunds must be compared line-by-line with source; online opening receipt reconstruction must reproduce immutable cash income rows and explicit payments; historical rate snapshots and ambiguous records must be reconciled. No source profit may be approved from partial totals.
- No ledger writes, no stock writes, no partner distributions. No Gradle build or test execution was performed.

## Historical STORE profit extraction from actual backup (2026-11-06)
- Inspected original 2026-10-26 v3.9.359 backup: 111 sale records, of which 43 explicitly store, 14 explicitly online and 54 lack saleType. The 54 untyped invoices must not be guessed.
- Historical invoice items include fundingAllocations with original lot quantity, unitCost and currency. For example a TRY invoice's item.cost can be TRY while its funding lot unitCost is USD; the extractor uses the original funding lot currency, not the display cost.
- Added LegacySalesEventExtractor.kt: only source-confirmed STORE invoices with reconciliation=confirmed, exact allocated quantities and historical FX snapshots produce read-only dated margin events.
- FinancialReviewActivity now displays candidate archived STORE margin and cost, plus unresolved count. This is NOT an approved all-source net.
- Added LegacySalesEventExtractorTest.kt; authored but not executed.
- Remaining: historical ONLINE receipts, discounts/returns source parity, 54 untyped invoices, repairs, final net and partner balances. No historical ledger writes, no stock deductions or cash postings were performed.

## Historical sales source profit reconstruction
- Inspected actual 2026-10-26 v3.9.359 backup: 111 sales (43 explicit store, 14 explicit online, 54 without saleType), 13 sale-linked payment rows. Historical empty saleType is treated as store by LegacySaleChannel, matching the documented source fallback.
- Added LegacyHistoricalSalesProfitReader: read-only invoice item cost/qty, tax, total, source FX, dated online receipts and proportional recognized cost; unresolved invoices and orphan payments are reported.
- Added LegacyHistoricalSalesProfitRepository to combine archived Room sales and original staging payment rows. Added unit tests (authored, not executed).
- FinancialReviewActivity source diagnostics shows extracted event counts and candidate store/online historical margin; candidate amounts are **not approved** and must not be combined with new profit until refunds, old collection discounts and original main.js numerical parity are verified.
- No historical stock/cash movements were posted. No Gradle build or on-device verification has been run.

## 2026-11-06 — historical sales event parity and unified read integration

Base: `bbe75a36f21977dbfd6cfa493764ba1ae39dfec4` on `native6-development`.
Only `native8/` is changed. No schema migration, cash posting, inventory posting,
archive mutation, or page layout change is part of this batch.

### Implemented
- One historical sales reader now supplies both archive diagnostics and the staged
  financial projection. Removed the duplicate store/online calculation from the projection.
- Store cost uses original invoice `items[].cost * qty`; invoice `total` already
  contains discounts. Tax is removed once, using the source net-amount rule.
- Online opening receipts use original invoice-day cash income less same-day
  explicit payments. Old-record fallback and payment-correction markers follow
  `onlineSaleReceiptEvents` exactly. Later receipts retain their original dates.
- Payment `amount` is invoice currency, not actual cash `paymentAmount`.
  Receipt FX snapshots apply to both revenue and proportionate cost; collection
  discount consumes cost without becoming revenue. Original TRY/SYP direct rates
  are respected when reconstructing opening cash receipts.
- Added original sales-return profit deltas, including recognized ratio and
  replacement cost. A cancellation represented by an original return follows the
  same rule. Deleted source invoices cannot be reconstructed without evidence.
- Stable original IDs identify events across imports. Duplicate IDs, missing cost,
  missing required historical FX, orphan records, and invalid dates are reported.
  A failed later receipt cannot leave partial events from that invoice in totals.
- Archive inspection verifies that staged originals match Room archive JSON,
  avoiding receipt joins across different restore runs. Source-run changes fail closed.
- UnifiedFinancialPeriodRepository includes eligible historical store/online/return
  amounts in its **partial** total, rejects overlap with operational sale IDs, and
  subtracts unified expenses only once. Full-net/partner approval remains blocked.
- FinancialReviewActivity now actually invokes the unified repository, retains
  detailed audit output if consolidation fails, uses Damascus period boundaries,
  fixes capital/unresolved-count bindings, and reads historical capital from the
  same events as profit. Existing controls/layout are preserved.
- Added a real org.json JVM test dependency; fixed existing nested-type references
  in LegacyFinancialRulesTest that prevented compilation.

### Executed validation
- Executed the original JavaScript functions from the supplied `assets/main.js`
  against `AlSafa-Backup-2026-10-26T06-21-00-520Z.json`.
  main.js SHA-256: `a57ba7aac9ead8138e6317eb49e84dcd312b7175cb57ca4c60cabf2fb7096126`.
- Generated a minimized financial fixture (no customer names, phones or images):
  111 sales; 114 events = 97 store + 14 online receipts + 3 sales returns.
- Kotlin matches every event key, stream, date, revenue, cost and adjustment to the
  original JavaScript oracle within 0.00000001 USD. No unresolved source records
  in this sales fixture.
- Original all-period totals: store 1914.47559583008 USD;
  online 823.8148148148148 USD; sales returns -8.75 USD.
  These are sales-only amounts, not final shop net or partner entitlements.
- **43 JUnit tests passed**, run with standalone Kotlin JVM compiler 2.2.0,
  JUnit 4.13.2 and real org.json/Room annotation jars. This is not Gradle success.
  Coverage includes original backup parity, same-day opening receipts, receipt FX,
  mixed-payment metadata, collection discounts, noncash exclusion, duplicate IDs,
  missing cost, atomic invoice quarantine and existing financial-rule regressions.
- Changed repository/projection/DAO Kotlin sources also type-checked against real
  Room annotations with a compile-only LegacyReadRepository boundary. This does
  **not** execute Android SQLite, Room generation, migrations or UI.
- `git diff --check` passed.
- `bash gradlew testDebugUnitTest --console=plain` failed **before compilation**:
  wrapper could not download Gradle 9.8.2 (`Network is unreachable`).
- Android build/APK, Room instrumentation/migrations, actual restore and Honor
  tablet checks were not executed. No successful device test is claimed.

### Files
Modified:
- app/build.gradle.kts
- app/src/main/java/com/alsafa/native8/sales/LegacyHistoricalSalesProfitReader.kt
- app/src/main/java/com/alsafa/native8/sales/LegacyHistoricalSalesProfitRepository.kt
- app/src/main/java/com/alsafa/native8/sales/LegacyHistoricalProfitProjection.kt
- app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialPeriodRepository.kt
- app/src/main/java/com/alsafa/native8/sales/SalesFinancialAuditDao.kt
- app/src/main/java/com/alsafa/native8/sales/FinancialReviewActivity.kt
- app/src/test/java/com/alsafa/native8/sales/LegacyHistoricalSalesProfitReaderTest.kt
- app/src/test/java/com/alsafa/native8/sales/LegacyFinancialRulesTest.kt
- FINANCIAL_INTEGRATION_BATCH_REPORT.md
Added:
- app/src/test/resources/historical-sales-v359.json
- tools/extract_historical_sales_fixture.cjs
- tools/run_financial_junit.py

### Remaining — do not mark the financial system complete
- Historical and new maintenance receipts/costs/returns need numerical source
  parity and integrated coverage. Existing maintenance audit classes still do not
  approve operational maintenance profit. In particular, current new-delivery JSON
  needs review of historical FX evidence and cost provenance before recognition.
- Historical expense/withdrawal duplicate-ID and original FX/partner mapping
  verification is not completed by the sales fixture.
- Final period/cumulative net, partner statements and balances are not enabled;
  PartnerProfitsActivity remains unchanged and does not publish partial balances.
- All Android/Room/device validations above remain pending.


## 2026-11-06 — maintenance parity, expenses, and cumulative partner statement

Base: `436027f9516ccca803aa391fb6ccac9f0889f1ff`. Changes are confined to
`native8/`. This section supersedes the earlier pending historical-maintenance,
expense/withdrawal audit and statement-wiring items, but not Android acceptance.

### Implemented and connected
- Historical maintenance: one reader follows the supplied main.js
  `repairReceiptEvents`, `repairReceiptCost`, `repairInvoiceTotal`, and repair-return
  branch exactly. Uses original aggregate `cost` (already includes parts/manual
  cost), so parts are not deducted twice. Opening receipt dates and later payment
  dates are preserved; exchange-difference payments are excluded from receipts
  because their impact is already in the return. Missing cost/FX/IDs are quarantined.
- Historical expenses/withdrawals: original IDs, dates/time, amounts/currency,
  rateSnapshot, categories, methods/vaults and notes remain available as evidence.
  Duplicate IDs and unknown family partner mappings cannot become zero-valued
  approved entries. Declared external partners are excluded from the current split.
- New maintenance: selection now converts part cost to invoice currency and stores
  its source currency/cost/rate. Delivery pins the aggregate parts/manual cost,
  tax and FX snapshot in existing JSON within the existing Room transaction.
  No additional stock deduction or cash posting was added.
- New maintenance reads recognize delivery on deliveredDate and validate invoice
  credit, currency, parts/labour/tax total, original cost evidence, Room-vs-JSON
  consistency, and actual posted cash totals per currency. Older delivered records
  without trustworthy part-cost/FX evidence are quarantined; they are not rewritten.
  Existing no-parts, explicit-zero-cost USD deliveries can be read without inventing FX.
- UnifiedFinancialPeriodRepository now includes historical and new maintenance,
  sales, sales/repair return effects, and expenses exactly once. Withdrawals never
  reduce operating profit. Completeness requires no unresolved sources, no ID
  overlaps, and no unclassified/missing-profit operational invoices or receipts.
- FinancialReviewActivity invokes the complete repository and reports either net
  profit or an explicitly unapproved subtotal. Removed stale diagnostics that
  claimed maintenance was disconnected. No new review page or replacement layout.
- PartnerProfitsActivity's existing button opens a period-selection dialog. It
  reads the selected period and ALL history through the selected end date within
  one Room read transaction, checking that the staging run did not change.
  Displays period profit, cumulative profit, period/cumulative withdrawals and
  remaining due for father 40%, me 35%, brother 25%. Unresolved data prevents
  publishing final entitlements. No month-only balance is presented as cumulative.
- Reused the exact Tajawal-Medium.ttf from the authorized original source archive.
  Partner page remains RTL with vertical scrolling and system-bar insets.
- No schema version change, destructive migration, historical ledger replay,
  deletion of trial invoices, current-balance rewrite, or external partner split.

### Executed numerical validation
Original source fixture (not current tablet totals):
- 111 sales / 114 sales events from the previous batch remain matched.
- 308 repairs -> 298 original receipt events plus 1 repair return, matched against
  the executed original JavaScript functions, including event IDs/dates/revenue/cost.
- All 22 expenses and 72 family withdrawals match original IDs, FX, dates and partners.
- Historical repair profit: 683.5455536388455 USD (2026-09-22 through 2026-10-25).
- Historical repair return: -1.6528925619834711 USD (2026-09-30).
- Operating expenses: 113.59970853380392 USD.
- Cumulative withdrawals: 824.2727566839094 USD.
- Combined historical net through 2026-10-26: 3297.83336318795237672 USD,
  within 0.00000001 USD of the original JavaScript financial components.

Historical-only cumulative comparison (current Native 8 entries are NOT in this fixture):
| Partner | Earned USD | Withdrawn USD | Due USD |
| --- | ---: | ---: | ---: |
| Father 40% | 1319.133345275181 | 338.008152646067 | 981.125192629114 |
| Abdul-Karim 35% | 1154.241677115783 | 404.868282106750 | 749.373395009033 |
| Yazan 25% | 824.458340796988 | 81.396321931092 | 743.062018865896 |

### Tests and limits
- Focused standalone Kotlin/JUnit financial suite: **59 tests passed** (Kotlin
  compiler 2.2.0, JUnit 4.13.2, real org.json and Room annotation jars).
  Includes original sales/maintenance/expense/withdrawal parity, full historical
  net and cumulative partner due, period-vs-cumulative separation, losses,
  tax-on-delivery, duplicated reads/IDs, missing cost/FX/receipts and external exclusion.
- An initial test compared BigDecimal scale rather than numeric value (40.00 vs
  40.0); corrected the assertion and reran successfully. No final failed JVM tests.
- Changed financial repositories/DAOs type-checked with a compile-only staging
  boundary. This is NOT Android, Room-generated DAO, or UI runtime verification.
- SQLite execution of the actual coverage SQL passed for missing store profit,
  missing online receipt profit, unclassified invoices and date cutoff.
- `git diff --check` passed.
- Gradle `testDebugUnitTest` was attempted; it failed before project compilation
  while downloading Gradle 9.8.2 (`Network is unreachable`).
- Full Gradle/APK build, Room instrumentation/migrations, actual restore, live
  cash/stock preservation and Honor Pad visual/interaction acceptance remain unexecuted.

### Remaining acceptance / unsupported workflows
- Verify the new period dialog and complete statement on the actual restored tablet.
  Current tablet Native 8 entries were not available for numerical comparison.
- Any already-delivered native repair with ambiguous part-cost currency or missing
  FX remains blocked for review. The code does not retrofit today's rates or catalog
  costs into a historical delivery. Pending old drafts with unverified parts must
  reselect the parts before delivery so costs can be pinned prospectively.
- Creating NEW maintenance return/exchange transactions is not implemented by this
  financial-integration batch; the current maintenance module has no corresponding
  persisted operational return stream. Original historical returns are covered.
  Unrepresented native refunds fail cash-evidence reconciliation rather than being
  assigned an invented profit reversal.
- Do not describe all financial/maintenance workflows as device-validated or the
  entire application as complete based on these JVM results.

### Files in this batch
- Added: `app/src/main/assets/fonts/Tajawal-Medium.ttf`
- Added: `app/src/main/java/com/alsafa/native8/sales/LegacyExpenseWithdrawalReader.kt`
- Added: `app/src/main/java/com/alsafa/native8/sales/LegacyMaintenanceProfitReader.kt`
- Added: `app/src/main/java/com/alsafa/native8/sales/NativeMaintenanceProfitReader.kt`
- Added: `app/src/main/java/com/alsafa/native8/sales/NativeMaintenanceProfitRepository.kt`
- Added: `app/src/main/java/com/alsafa/native8/sales/PartnerFinancialStatement.kt`
- Added: `app/src/test/java/com/alsafa/native8/sales/LegacyMaintenanceProfitReaderTest.kt`
- Added: `app/src/test/java/com/alsafa/native8/sales/NativeMaintenanceProfitReaderTest.kt`
- Added: `app/src/test/java/com/alsafa/native8/sales/OriginalFinancialStatementParityTest.kt`
- Added: `app/src/test/java/com/alsafa/native8/sales/PartnerFinancialStatementTest.kt`
- Added: `app/src/test/resources/historical-finance-v359.json`
- Added: `tools/extract_historical_finance_fixture.cjs`
- Modified: `app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDao.kt`
- Modified: `app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDeliveryRepository.kt`
- Modified: `app/src/main/java/com/alsafa/native8/maintenance/MaintenanceIntakeActivity.kt`
- Modified: `app/src/main/java/com/alsafa/native8/maintenance/MaintenancePartsRepository.kt`
- Modified: `app/src/main/java/com/alsafa/native8/sales/FinancialReviewActivity.kt`
- Modified: `app/src/main/java/com/alsafa/native8/sales/LegacyHistoricalProfitProjection.kt`
- Modified: `app/src/main/java/com/alsafa/native8/sales/PartnerProfitsActivity.kt`
- Modified: `app/src/main/java/com/alsafa/native8/sales/SalesFinancialAuditDao.kt`
- Modified: `app/src/main/java/com/alsafa/native8/sales/UnifiedExpensePartnerReadRepository.kt`
- Modified: `app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialPeriodRepository.kt`
- Modified: `tools/run_financial_junit.py`
- Modified: `FINANCIAL_INTEGRATION_BATCH_REPORT.md`


## Native maintenance return/exchange execution batch

Baseline verified on GitHub: `641a6fd14d032a70e3a631482b559d6fbf462c33` on
`native6-development`. All changes below are confined to `native8/`.
This section supersedes the previous batch's “NEW maintenance returns not implemented” item.
The commit containing this section records this batch; its parent is the baseline above.

### Implemented and connected
- Added the original maintenance return/exchange form to delivered **native** repairs.
  Part return or manual labour adjustment; optional explicit restock; replacement part
  or manual rework; partial collection of exchange differences; original date/time,
  cashbox and notes. Shows a confirmation with all financial results before writing.
- Return costs use the delivered part snapshot. Replacement costs/prices convert using
  the original repair exchange snapshot (including direct TRY/SYP); settlement USD
  reporting uses the settlement snapshot. Missing evidence is rejected, not repriced.
- Source-identical tax, refund, debt reduction, proportional cost recognition and
  remaining-balance formulas. Damaged parts do not restore inventory or reverse cost
  unless explicitly marked for restocking.
- Stock, append-only settlement JSON and cash ledger are saved in one Room transaction.
  The record retains stock before/after values, source IDs, cost ratios and rate snapshot.
  Identical saved request IDs return the existing result; changed stock/repair/rates
  after confirmation require a new preview. Cash IDs are deterministic and unique.
- Added subsequent collection of outstanding repair/exchange debt **in invoice currency**,
  using the original ordinary maintenance receipt-profit formula. The initial delivery
  receipt remains immutable. This is not a new mixed-currency collection screen.
- Native return and later receipt events are recognized on their own dates and feed the
  unified net profit and existing period/cumulative partner statement. Reading them
  never posts cash or deducts stock. Cash evidence is reconciled by operation ID.
- Existing maintenance detail retains its layout and shows effective total/net paid/debt
  when settlements exist. New form is RTL, uses original Tajawal, ScrollView and system/IME
  insets. It does not claim to generate a PDF: saved receipts are displayed on screen.
- No table recreation, schema version change, destructive migration, imported-record
  mutation, test-invoice deletion, or reclassification.

### Executed validation
- **69 standalone Kotlin/JUnit tests passed**, including 10 new settlement test methods.
- Fixture generator executes the actual original `repairReturnCalculation` and
  `saveRepairReturn` functions for **12 cases**, including an original backup repair.
  Checks all original saved numeric fields: damaged/restocked part, full manual return,
  cheaper/dearer replacement, partial collection, debt reduction, tax, USD/TRY/SYP,
  direct TRY/SYP conversion, and a prior return/payment sequence.
  Synthetic cases are explicitly labelled; the backup case retains its original ID
  and financial fields only. Source SHA-256 remains
  `a57ba7aac9ead8138e6317eb49e84dcd312b7175cb57ca4c60cabf2fb7096126`.
- JVM checks cover excess quantities/credit, unavailable replacement stock, duplicate
  operation ID, backdated settlements, outstanding-debt bounds, date-separated profit
  events, and repeated reads without mutation.
- New screen/repository/DAO/reader Kotlin code type-checks against Android API classes
  and Room annotations. Database/transaction boundary facades were compile-only:
  **this is not Room generation, runtime execution, or an APK build**. Only deprecated
  compatibility-inset/soft-input API warnings were produced.
- **3 SQLite checks passed** using actual migration DDL and DAO SQL: stale expected JSON
  rolls back stock; duplicate cash PK rolls back stock and repair JSON; successful
  transaction commits all three. This validates SQL constraints, not Android Room runtime.
- `git diff --check` passed. No final failed executed tests.

### Deferred / not claimed complete
- Per user request, Gradle/APK build and device work were deferred; no successful build
  is claimed. Honor Pad layout, confirmation/cancel, cash/stock persistence, Room codegen,
  migrations and restore acceptance still require Android execution.
- Added four real Room instrumentation tests for retry idempotence, rollback on cash-ID
  collision, stale-stock confirmation, and subsequent collection idempotence. They are
  **not executed here** and must not be counted among the 69 passing JVM tests.
- Current tablet records are unavailable here; full live cumulative balances cannot be
  certified from the backup fixture alone. Ambiguous old native deliveries remain
  quarantined, as documented in the previous batch.
- PDF/thermal/WhatsApp issuance of the new maintenance-return receipt is not implemented
  in this financial batch. Original historical repair records remain read-only.

### Files in this batch
- Added: `app/src/androidTest/java/com/alsafa/native8/sales/MaintenanceReturnRoomTest.kt`
- Added: `app/src/main/java/com/alsafa/native8/maintenance/MaintenanceReturnActivity.kt`
- Added: `app/src/main/java/com/alsafa/native8/maintenance/MaintenanceReturnRepository.kt`
- Added: `app/src/main/java/com/alsafa/native8/maintenance/MaintenanceReturnRules.kt`
- Added: `app/src/test/java/com/alsafa/native8/sales/MaintenanceReturnRulesTest.kt`
- Added: `app/src/test/resources/maintenance-return-v359.json`
- Added: `tools/extract_maintenance_return_fixture.cjs`
- Modified: `FINANCIAL_INTEGRATION_BATCH_REPORT.md`
- Modified: `app/src/main/AndroidManifest.xml`
- Modified: `app/src/main/java/com/alsafa/native8/maintenance/MaintenanceActivity.kt`
- Modified: `app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDao.kt`
- Modified: `app/src/main/java/com/alsafa/native8/sales/NativeMaintenanceProfitReader.kt`
- Modified: `app/src/main/java/com/alsafa/native8/sales/NativeMaintenanceProfitRepository.kt`
- Modified: `app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialPeriodRepository.kt`
- Modified: `tools/run_financial_junit.py`
