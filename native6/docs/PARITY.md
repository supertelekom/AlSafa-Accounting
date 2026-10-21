# Legacy parity register

Source: v3.9.353; 944 declared functions, 44 static navigation routes, 48 forms, 165 named fields. Static inventory covers the full file. Detailed semantic review has focused on sale, return, purchase, vault, backup and homepage paths; full business-rule audit is NOT finished.

| Legacy route | Native 6 status |
|---|---|
| advanced-home | Native navigation to implemented operations |
| audit-log | Pending migration; no dummy screen added |
| customer-orders | Pending migration; no dummy screen added |
| customers | Pending migration; no dummy screen added |
| daily-close | Count snapshot and difference; carry/reconciliation workflow pending |
| dashboard | Pending migration; no dummy screen added |
| debts | Pending migration; no dummy screen added |
| expense-form | Pending migration; no dummy screen added |
| expenses | Pending migration; no dummy screen added |
| father-deliveries | Pending migration; no dummy screen added |
| father-home | Native quick menu; handover pending |
| father-repair | Pending migration; no dummy screen added |
| father-sale | Pending migration; no dummy screen added |
| inventory-analytics | Pending migration; no dummy screen added |
| inventory-movements | Pending migration; no dummy screen added |
| maintenance-parts | Basic catalog only |
| modern-hub | Pending migration; no dummy screen added |
| more | Pending migration; no dummy screen added |
| opening-balances | Pending migration; no dummy screen added |
| partners | Native tablet partner screen, dated share registry, journal-linked drawings, selected-month and older-balance lines; mid-month schedule splits and final audit pending |
| printer | Pending migration; no dummy screen added |
| products | Create/edit/search; images and variants pending |
| purchase-form | Pending migration; no dummy screen added |
| purchases | Pending migration; no dummy screen added |
| repair-form | Receive without media |
| repairs | Receive/ready/labor invoice; parts and full state machine pending |
| reports | One-month accrual report; preliminary partner allocation API with dated drawings; full accounting audit and UI pending |
| returns | Partial/full sale returns and separate refund; exchanges pending |
| sale-form | Same-currency store sale, discount, debt and cash; advanced options pending |
| sales | List, collection, returns, cancellation, native PDF |
| search | Pending migration; no dummy screen added |
| security | Pending migration; no dummy screen added |
| settings | Backup section only |
| shift-handover | Pending migration; no dummy screen added |
| shortcut-settings | Pending migration; no dummy screen added |
| stock-alerts | Pending migration; no dummy screen added |
| today-center | Pending migration; no dummy screen added |
| used-watch-exchange | Pending migration; no dummy screen added |
| used-watch-purchase | Pending migration; no dummy screen added |
| used-watches | Pending migration; no dummy screen added |
| vaults | Balances, transfer, expense, withdrawal and FX |
| wallet | Pending migration; no dummy screen added |
| warranties | Pending migration; no dummy screen added |
| watch-advisor | Pending migration; no dummy screen added |

## Release blockers

- Full legacy data import: only lossless isolated staging exists; no active financial migration.
- Legacy design parity: original palette and typography reused; full layout/behavior parity remains.
- Offline equivalents for former online features (brand lookup, website, WhatsApp handoff); no network permission will be added.
- Camera, photo gallery, barcode/QR printing, per-color product data and funding lots.
- Online collection recognition, taxes, mixed-currency receipt/change and cross-currency stock valuation.
- External partners, dated share schedules, loyalty, reservations, used-watch exchanges, warranties and inventory sessions.
- Repair parts, repair credit notes, purchase cancellation and exchanges.
- Full restoration/recovery workflow into an occupied database; currently rejected without changing data.
- Real tablet UI/end-to-end acceptance and release signing.

No real shop backup has been opened or changed in this work.
## Next implementation batch

Priority: complete partner share schedules, month-isolated allocations and withdrawals by actual transaction date; supplier opening debt and dated settlements; then multi-currency receipts and repair parts. Every change must preserve append-only per-currency balanced journals and atomic SQLite transactions. Do not migrate live data until explicit approval. Tests are the final verification stage, not a substitute for feature completion.

## Partner integration caveats

- Partner membership and effective shares are not pre-seeded. Configure them explicitly before requesting partner month reports.
- The partner month report currently uses posted revenue and expense accounts as its preliminary distributable-profit basis; partner-specific profit policy, taxes and adjustments require further reconciliation.
- If share schedules change inside a month, the reader rejects that month rather than silently applying the wrong percentage. Effective-date splitting remains pending.
- Existing historical withdrawal journal entries are not backfilled into partner_drawings automatically. A verified one-time migration is required before historical reports are complete.
- Native 6 SQLite schema version 2 upgrades version 1 in place; it does not touch the legacy application database.

## October 20 partner UI batch

- Native screen supports partner creation, effective-dated share schedules, a confirmation-gated historical withdrawal register backfill, and one selected-month report with older balances shown separately.
- Older balances are calculated only from the explicitly chosen first month; this is not an assertion of complete lifetime partner debt.
- Existing journal withdrawals whose destination does not match a registered partner ID are deliberately not guessed or backfilled.
- No claim of a passing Android build or device validation for this batch. Do not deploy to the shop yet.

## SQLite v3 partner integrity batch

- Added immutable UPDATE/DELETE triggers for partner drawings, share periods, and share lines, plus a unique effective-date index.
- Existing Native 6 databases at versions 1 and 2 upgrade to version 3 in place. Existing records are retained; migration will reject contradictory duplicate effective dates rather than silently discard data.
- Historical corrections require explicit reversing/adjustment records in a future workflow, not editing posted history.
- Build and migration test execution remain outstanding; do not deploy to live shop data.

## Urgent stabilization and migration gate

- A GitHub Actions workflow now compiles the debug APK and runs JVM unit tests on pushes to the Native 6 development branch. A passing build is required before any tablet trial.
- Startup now falls back to Android's system font when the bundled Tajawal asset is unavailable, and a failed page render displays a recovery screen with a backup shortcut instead of terminating the activity.
- **Critical blocker:** importing the v3.9.353 shop backup into the live Native 6 ledger is NOT implemented. Legacy JSON can only be staged in isolation. Do not enter real trading on Native 6 yet: products, debts, receipts, repair history and partner capital must be reconciled and migrated first.
- Keep the current app and an external copy of its backup untouched; test only on a separate tablet/install with disposable data until migration validation passes.

## Legacy readiness audit (development)

- Staged old-format JSON backups can now be inspected from the native archive screen, without writing anything into sales, journal, inventory or supplier balances.
- The inspector reports counts for products, sales, repairs and suppliers, samples field names and flags duplicate/missing recognized identifiers. Missing identifiers are warnings, not proof of corrupt records: the legacy format may use other keys.
- Added unit tests for duplicate detection and missing sections. CI is configured, but no passing Android build or test run has been independently verified.
- **This is not migration**. Mapping old identifiers, invoices, debt settlements, stock valuation, partner shares and all currencies into balanced Native 6 journal entries is still blocked pending source-schema reconciliation and a real backup sample.

## Backup regression coverage — October 2026

- Integrity checks verify that each stock movement references a valid accounting event and product, in addition to checking aggregate quantity and valuation.
- Added round-trip regression cases for stocked opening inventory with a later nonfinancial product edit and for a zero-stock opening event without journal entries.
- Tests have been committed but are **not reported as passing** until an Android/Gradle runner executes them.

## Per-currency inventory reconciliation

- Backup integrity now compares the product valuation totals with the immutable inventory asset journal for every currency, in both directions (including a journal currency with no products).
- Regression tests added for separate USD/TRY opening stock and an unpaid sale fully returned to inventory.
- This checks Native 6 internal consistency only; it does not establish parity with legacy v3.9.353 stock, supplier balances or profits.
- Android CI/test results remain unverified.

## Additional accounting regression cases

- Added purchase → weighted-average inventory → sale test, asserting the remaining units and minor-unit inventory valuation.
- Added a tampered-backup test where the attacker recomputes the JSON SHA-256 after altering product valuation. Restoration must still reject it using ledger/stock reconciliation and leave the destination database empty.
- Tests are committed, not yet independently executed; do not claim a successful build from these additions.

## Actual legacy v3.9.353 backup mapping — 2026-10-20

Inspected the user's original **AlSafa Backup v2**, exported 2026-10-18 (approximately 61.9 MB), without uploading the private backup to GitHub. Verified actual legacy sections: 552 products, 72 maintenance parts, 13 suppliers, 243 repairs, 86 sales, 6 purchases, 5 supplier-ledger entries, 28 payments, 49 partner withdrawals, 566 cash transactions and 26 daily closings.

- Implemented an **explicit, atomic, empty-database-only partial restore** from a staged v2 backup: watches, maintenance parts, suppliers, and repair history. Original product JSON (including embedded photos) and repair JSON remain in the details field; source archive remains untouched.
- Legacy prices/costs can have more than two decimals. Current Native 6 minor-unit storage rounds half-up to two decimals; original precision remains in details. This is not a historical profit reconciliation.
- **Not yet imported:** legacy sales/invoice numbering, purchases, historical supplier debt and repayments, cash/closing balances, partner shares/withdrawals, and other historical ledgers. They must not be fabricated from opening inventory.
- This partial restore must not be described as a completed migration or production-ready accounting app.
- Added Robolectric tests for the partial importer; build/test execution remains unverified.

## 2026-10-20 legacy restore implementation continuation

- Ran an independent **read-only preflight on the actual 2026-10-18 backup** for all 552 products, 72 maintenance parts, 13 suppliers and 243 repairs: 880 unique identifiers, no duplicate nonblank barcodes, no negative stock, and supported currencies for inventory. This is a structural preflight, not an Android runtime test.
- Added transaction rollback regression for a duplicate barcode, and Native 6 export/restore regression after snapshot import.
- Added read-only historical financial audit from the staged original: sales, purchases, supplier ledger, payments, partner withdrawals, cash transactions and closings. Currency totals are not converted or presented as balances; duplicate accounting representations must be reconciled before any financial migration.
- **Remaining:** verified historical journal import, legacy invoice numbering, supplier balance reconciliation, cash opening balances, partner share/withdrawal reconstruction, Android CI build and on-device acceptance tests.

## Historical records archive (database schema 4; native backup schema 3)

- Added `legacy_records(category,legacy_id,payload)` to preserve original historical sales, purchases, supplier ledger, payments, partner withdrawals, cash transactions, daily closings, expenses, returns, inventory movements and loyalty entries as **unposted, separate SQLite records** during explicit import.
- The archive participates in Native 6 backup export and restore, and the archive screen displays per-category counts. Previous Native backup schema 1 and 2 can still be restored with an empty archive.
- The 2026-10-18 source snapshot has 86 sales, 6 purchases, 5 supplier ledger entries, 28 payments, 49 partner withdrawals, 566 cash transactions, 26 closings, 18 expenses, 2 returns, 413 inventory movements and 2 loyalty entries.
- These entries are historical **evidence**, not reconstructed financial journal postings. Profit, supplier debt, cash and partner balances remain unverified until transaction mapping and reconciliation are implemented.
- Large embedded product photos and preservation of the full original backup may make the Native backup exceed its 128 MiB safety limit. This must be addressed before production migration.

## Native backup v4 compression

- Native backup exports now use `schema=4`, `encoding=gzip-base64`, SHA-256 of the **uncompressed** JSON data, and a compressed data payload. The external backup JSON limit remains 128 MiB, while decompression is capped at 256 MiB to avoid unbounded expansion.
- Imports retain backward compatibility with native schemas 1, 2 and 3; older schemas have missing archive tables initialized empty.
- Historical `legacy_records` are now protected against SQL UPDATE and DELETE by triggers, not just a UI convention.
- Added compression round-trip, tampering and existing financial-integrity regression adaptations. CI/Android execution remains unverified.

## 2026-10-20 real-backup historical data preflight

- Independently inspected the 61,896,278-byte original backup in a private working environment (the backup itself was **not** committed or uploaded to GitHub).
- 4,002 array records total, of which 880 are products, maintenance parts, suppliers and repairs; 3,122 other historical records are now retained in the archive by automatic section discovery, including unknown future array categories. No duplicate record IDs were found inside any archived category.
- The source includes 86 sales, 243 repairs, 6 purchases, 5 supplier ledger entries, 28 payments, 49 partner withdrawals and 566 cash transactions.
- **Verified source invoice collisions:** sale invoice number 9 occurs twice; repair invoice number 221 occurs twice. Original record IDs remain distinct; never key migration by invoice number alone.
- Preserve legacy settings and exchange rates as non-active configuration evidence. In particular, historical partner percentages and invoice sequence high-water must not be inferred from current defaults.
- The financial ledger migration remains **not done**; raw cash transactions overlap sales/repairs/payments and must not be double-booked. Do not present raw financial totals as balances.

## Legacy import safeguards — continued implementation

- Historical sections are discovered dynamically rather than using a fixed allowlist, so unrecognized array sections are retained in `legacy_records` instead of being discarded. Non-object array values are wrapped in `_legacyValue` for lossless representation.
- Original settings, exchange rates and device metadata are archived as read-only evidence; they do **not** activate Firebase or overwrite the offline Native 6 configuration.
- Confirmed from the original 2026-10-18 source settings: partner shares father 40%, me 35%, brother 25%, and invoice sequence high-water 335. These figures are **source metadata**; effective dates and historical account balances still require reconciliation.
- Financial audit now surfaces partner shares and invoice sequence and continues to distinguish historical withdrawals by transaction month.
- New regression covers an unknown future section and settings retention. Android build and test execution still pending; source changes must not be interpreted as a verified APK.

## Referential reconciliation of original 2026-10-18 backup

- Added `LegacyReconciliation` with a read-only screen to classify cash transactions and customer payments by source `referenceType/referenceId`, detect missing known invoice references, and distinguish unsupported types from truly missing references.
- **Measured against the original source:** all 28 payment references resolve; among 566 cash transactions, 398 resolve to known source record types, 103 have no reference ID, and 65 use reference types requiring additional reconciliation. There were **zero missing IDs** for supported reference types.
- The 566 cash transactions include 236 repair incomes, 84 sale incomes, 49 partner withdrawals and 18 expenses; these are often representations of underlying business records and must not be posted again as separate profit.
- Supplier opening-debt ledger consists of 3 opening entries and 2 payments: 520 USD paid in full, 1500 USD with 500 USD paid (1000 USD remainder), and 770 USD with no ledger payment. These are opening-ledger differences **only**, not total supplier liabilities; purchase invoices and payments must be reconciled separately.
- Added tests for cash-reference classification and supplier opening-debt reconciliation. Execution on Android/CI is still unverified.

- The legacy import now checks actual stored product, repair and historical archive record counts against the source JSON before its enclosing transaction commits; a mismatch rejects and rolls back the import.

## Additional financial reconciliation safeguards

- Read-only financial audit now detects invoice number collisions **across** sales, purchases and repairs, not just duplicates within a single section. Original transaction IDs remain authoritative.
- Cash movement audit groups raw amounts by the **actual recorded calendar month and original currency** (USD, TRY, SYP). These are descriptive totals, not cash balances, profit, or journal postings.
- Added regression tests for cross-category invoice collisions and month/currency segregation.
- **Remaining blockers:** source schema field mapping for all purchase liabilities and partner profit events, journal posting reconciliation, CI test execution and a verified installable APK. The current application is not production-ready.

## October 20: receivables and migration hardening

- The read-only audit reports unpaid sales invoice portions separately by original currency, **before** allocating later payment records; amounts are provisional, not customer account balances.
- Source payment reference types are counted so subsequent matching can be reviewed explicitly.
- Partial legacy restore now asserts that no native invoices or cash receipts were created during import; supplier counts are reconciled before transaction commit.
- SQLite upgrades from schema 1/2/3 to schema 4 now install the same immutable accounting triggers as new installations.
- Added unit regression for separating provisional USD and TRY unpaid sales. No APK build or test execution has been confirmed.

## Subsequent payment matching

- Added read-only aggregation of historical payment entries by original reference type, reference ID and **actual payment currency**. Duplicate payment IDs are flagged, and ambiguous amounts are excluded.
- When `paymentCurrency` is present, the original `paymentAmount` is required; an amount denominated in invoice currency must not be substituted or silently converted.
- The report explicitly requires a verified historical exchange rate before cross-currency payments can be allocated against invoice receivables.
- The partial legacy import must create **no historical invoices or receipts**. Opening inventory is intentionally posted to inventory/equity at the current snapshot date; that legitimate journal must not be rejected.
- A unit regression covers TRY receipt against USD sale. Android test execution and APK build are not yet confirmed.

## Additional accounting integrity work

- The legacy snapshot importer now checks the supplier count, the absence of native invoices and receipts, and ensures only `OPENING_STOCK` events were created by partial import. Inventory opening entries are deliberately posted to inventory/equity and are **not** sales history.
- The financial preflight distinguishes later sale payments in the same invoice currency, different currency, missing invoice, or missing payment currency. Since the source invoice `paid` may already reflect later payments, it never deducts those payments a second time.
- Partner withdrawals are grouped by their actual recorded month, partner ID, and currency. Historical unpaid profit from other months is not merged into the selected month.
- Added regression tests for duplicate receipt protection and withdrawal month isolation. Tests are committed but not yet executed in Android CI.

## Read-only legacy history browser

- The Android Native UI now exposes a `legacy-history` page from advanced mode, with archive categories for old sales, repairs, purchases, payments, cash movements, supplier ledgers, partner withdrawals, expenses, and returns.
- Search uses SQLite parameter binding; records are fetched in pages of 30 and inspected in a read-only dialog. Archive payloads remain immutable and are not reposted to accounting.
- Historical invoice numbers are displayed but are not used as unique keys: original archive row identity remains distinct even for repeated invoice numbers.
- Verified against the private October 18 source backup structure: 86 sales, 6 purchases, 243 repairs, 28 payments, 566 cash movements, and 49 partner withdrawals. The source backup was **not** committed to GitHub.
- APK build and UI tests remain unverified; this screen is an implementation, not proof of release readiness.

## Archive usability and CI build reliability

- Added Arabic archive labels, accurate page counts and bounded pagination. Read-only browsing now includes daily closings, inventory movements, product cost history, audit logs, loyalty and discount data in addition to invoices and payments.
- Archive search uses literal SQLite `instr` matching, avoiding unintended `%`/`_` wildcard behavior.
- CI now installs Gradle 8.9 with `gradle/actions/setup-gradle`, runs unit tests and debug APK assembly, and uploads test reports even on failed builds. APK success is **not** yet confirmed.
- Added a regression test asserting that archived financial records cannot be modified or deleted directly in SQLite.

## Import rollback and repair state integrity

- Added Robolectric regression tests for atomic rollback of the full legacy import when duplicate barcodes or unsupported currency values are encountered. Tests assert zero residual products, events and journal entries.
- Legacy repair state mapping now preserves known Arabic and English states, and marks unrecognized nonempty states as `legacy_unknown` for manual review rather than silently converting them to `received`.
- Historical repair archive list now shows the original repair state.
- These tests have been committed, but their execution and Android APK build have not been independently verified.

## Archive interaction fixes

- Switching archive categories refreshes immediately and resets pagination; changing the search term also resets to the first page.
- Empty archive results are explicit rather than displaying a blank screen.
- Archive rows show available customer names, dates, totals and currencies from several legacy field variants, without calculating or altering financial balances.
- See `docs/RELEASE_CHECKLIST.md` for the release acceptance gate; build and device testing are not yet confirmed.

## Invoice numbering continuity

- New native sale, purchase and repair invoices use the larger of the existing native invoice number and the archived legacy `settings.invoiceSequenceHighWater`, then increment it. The original October backup has high-water 335, so the first native invoice should be 336, avoiding number reuse.
- Added tests for the first two sales after importing legacy settings. A corrupted archived numbering JSON now blocks new invoice issuance instead of silently restarting the sequence.
- This is not a substitute for verifying invoice-number duplicates already present in historical records, and does not post legacy invoices into native accounting.

## Inventory validation hardening

- Product creation and editing compare trimmed barcode values before enforcing uniqueness, and limit barcode input to 128 characters.
- Product edits reject missing IDs rather than emitting a misleading audit event.
- Product search trims leading and trailing query whitespace.
- Added regression tests for whitespace-equivalent duplicate barcodes and nonexistent-product edit rollback. Build/test results are not yet verified.

## Repair workflow hardening

- Native repair orders now support `received -> in_progress -> ready -> delivered` with explicit tablet work-start and ready actions. Direct `received -> ready` remains allowed for simple jobs.
- Work-start and readiness transitions append audit events. Repeated start is rejected; legacy unknown statuses are not offered unsupported transitions.
- Regression tests cover the receive/start/ready timeline and duplicate start. Repair parts consumption, returns, costs and migration of historical repair invoices remain incomplete. Android CI results have not been verified.
