# Native 8 — Phase 1, batch 6: verified closing transaction pairs

## Source and test fixture
Original `assets/main.js` from v3.9.359; temporary historical backup `AlSafa-Backup-2026-10-26T06-21-00-520Z(1).json`. The eventual production backup will be larger. No counts are hardcoded as future expectations.

## Verified source code
`saveDailyClose` at lines 6385–6412 creates reconciliation movements from `actual-expected`, then paired drawer/external daily transfers from `transfer`, then writes `dailyClosings`. `addCashTransaction` at lines 6148–6153 stamps each cash movement with a generated ID, vault, currency, signed amount, type, date/time and rate snapshot. The closing-generated movements have **empty `referenceType` and `referenceId`**; a migration must not invent stable foreign-key references from those fields.

## Executed historical comparisons

| Check | Result |
|---|---:|
| `daily-transfer-out` in drawer | 39 |
| `daily-transfer-in` in external | 39 |
| Unmatched transfers, multiset `(date,currency,abs(amount))` | **0** |
| Duplicate transfer matching keys | **0** |
| `reconciliation` in drawer | 26 |
| `reconciliation` in shamcash | 3 |
| Reconciliation rows expected from nonzero `dailyClosings.drawerDifference` and `shamDifference` | 29 |
| Actual reconciliation rows | 29 |
| Missing or excess reconciliation rows, multiset `(date,vault,currency,signed amount)` | **0** |

The matching test uses exact decimal values parsed from JSON and does not combine different currencies. These results verify **record-level consistency of this sample**, not complete historical balance or profit correctness.

## Important limitations and implications
- Closing transfer pairs are currently identifiable by date/currency/absolute amount without collisions in this fixture, but their source records do **not** carry a stable explicit closing reference. A future larger backup may contain ambiguous same-day transfers. Preserve original row IDs and order and report ambiguity; do not force a guessed pairing.
- A naive post-close calculation includes reconciliation and transfer entries already posted during closing; do not compare that value with the stored pre-close expected amount.
- Keep `dailyClosings` and all `cashTransactions` as historical facts; importing a closing must **not** generate the same cash movements again.
- The final cutover must rerun this check on the newly supplied backup and fail safely on unmatched entries.

## Next task
Exact profit and partner withdrawal parity, followed by source-based schema design. Customer orders remain excluded from active development by explicit owner instruction.
