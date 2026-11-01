# Native 8 — Phase 1: closing and partner source reconciliation

**Authority:** original v3.9.359 `assets/main.js`. **Temporary fixture:** 2026-10-26 formatVersion 2 backup. The eventual production backup will be larger; no fixed counts, IDs, balances, or invoice numbers.

## Verified source paths
- `rawVaultBalance` (L6132–6134): sum signed cash transactions by vault and currency.
- `vaultBalance` (L6135–6147): drawer balance is `max(0, carriedDrawerBalance(currency,TODAY()) + today's drawer movements)` if previous closing exists; otherwise raw historical balance.
- `previousDailyClosing` (L6312): latest closing before requested date.
- `carriedDrawerBalance` (L6313): previous `drawerNext[currency]` clipped at zero, or null.
- `drawerExpectedForDay` (L6314): prior carry plus that date's movements, clipped at zero.
- `dailyCloseSuggestedTransfer` (L6315): `max(0, actual - min(actual,target))`.
- `grossProfitUsdForRange` (L6489–6509): store-sale profit, online collection profit, repair collection profit, returns, operating expenses. Cash-count reconciliation **does not** affect profit.
- `profitUsdForRange` (L6510): gross profit minus external partner share.
- `renderPartners` (L6513–6522): partner period share from period profit and share percentage, actual-dated withdrawals, cumulative due from all-time share minus withdrawals.

## Actual fixture observations
- 34 daily closings between 2026-09-22 and 2026-10-25; no duplicate dates.
- 722 cash transactions across `bank`, `drawer`, `external`, `shamcash`.
- 72 partner withdrawals. Shares in actual settings: father 40%, Abdul-Karim 35%, Yazan 25%.
- 33 days have a previous closing. In 23 of those days, one or more currencies show a nonzero difference between stored `drawerActual` and a **retrospectively computed** expected drawer balance.
- Across 99 date/currency comparisons, **54** retrospectively computed differences also do not match stored `drawerDifference` to 0.02 precision. **This is not evidence of 54 corrupted closings**: a backup snapshot can contain later-entered or corrected cash rows, while historical closings retain the values calculated at their original save times. Do not overwrite historical closings with today's recomputation. Source write-path and audit chronology must be checked before attributing cause.

## Migration invariants
1. Keep counted `drawerActual`, stored `drawerDifference`, `drawerDifferenceReason`, `drawerNext`, `drawerTransfer`, `shamActual`, `shamDifference` as distinct historical facts.
2. Never post a second cash entry when importing a payment, sale, expense, transfer, or partner withdrawal whose historical cash transaction already exists.
3. Do not add different currencies without applying the source rate snapshot.
4. Keep all original dates, IDs and audit metadata. Never auto-correct historical records based on retrospective arithmetic alone.
5. Customer orders excluded from operational development by owner instruction; preserve any historical payload losslessly.

## Pending
Inspect exact closing save path, reconciliation posting order, historical correction chronology and partner-profit parity before approving the operational SQLite schema. This is an audit finding, not a claim that reconciliation is complete.
