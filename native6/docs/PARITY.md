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
