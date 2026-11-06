# Full home completion — home only

Scope: MainActivity, FullHomePage, home-only read projections/tests. No changes to other section screens, database schema, receipt/stock writers or accounting repositories.

## Implemented
- Compact 90dp-minimum action/admin cards; 5/4/2 responsive columns. Tajawal/RTL and shared Safa palette.
- Legacy homeCurrencyAmountsHtml semantics: prior closing drawer carryover; today's positive sales receipts; today's positive repair receipts; USD/SYP/TRY remain separate. Historical/native IDs are deduplicated, and a native prior closing takes precedence over an older legacy closing. No derived profit displayed as receipt totals.
- Home amount visibility persisted, initialized from legacy hideHomeAmounts when available.
- Low stock compares current product/part stock with its minStock (default zero per original). Product patches and stock movements are included. One source JSON record at a time; no retained images. Full results shown in batches of 30.
- Configurable home shortcuts persisted locally; supported legacy shortcut route settings are mapped on first use. Only implemented destinations are offered.
- Today opens an in-home summary with work-in-progress, ready deliveries, stock alerts and closing links.
- Home search combines sales, repairs and the existing cancellable product index; 250ms input delay and cancellation prevent stale results. Matching sale/repair rows open individual records; products open the existing products section.
- Debts list includes current sales receivables and repair balances using existing return/payment calculations; zero/settled rows excluded. Existing detail screens perform collections.
- Recent records and all existing administration navigation retained.

## Validation
Three JVM tests cover receipt separation by currency/type, duplicate IDs, and exclusion of transfers/expenses/negative receipts. Actual home/activity Kotlin compilation with Android API jar and boundary stubs; destination/route inspection and whitespace checks. Full Gradle/Room/APK and tablet visual/runtime testing unavailable in this environment.

## Device checks
Verify compact cards; compare the three monetary blocks with previous drawer closing and today's actual receipts; toggle visibility; configure shortcuts and reopen; check low-stock products and parts; search customer/phone/product; open a debt invoice; return to home and simplified mode. No need to repeat posting/reset tests for this read-only home change.
