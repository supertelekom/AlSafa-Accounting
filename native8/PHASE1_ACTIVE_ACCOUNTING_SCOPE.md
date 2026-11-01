# Native 8 — active-scope accounting audit, batch 3

## Owner-approved scope
Customer orders are **excluded from Native 8 operational development**. Do not build customer-order screens, save paths, or related business features. Preserve any historical `customerOrders` data on import/backup round-trip without activating the workflow. The v3.9.359 source remains the sole authority for all other features; never invent substitutes.

## Historical sample (temporary, not a production cutover)
`AlSafa-Backup-2026-10-26T06-21-00-520Z.json` is a test fixture only. The final backup will be larger. No importer limits, last IDs, invoice counters, or opening balances may be inferred from this sample.

## Verified original accounting formulas
- `rawVaultBalance` (assets/main.js 6132–6134): signed sum of `cashTransactions.amount` filtered by exact `vault` and `currency`.
- `vaultBalance` (6135–6147): for `drawer`, where `carriedDrawerBalance(currency,TODAY())` exists, result is `max(0, carry + sum(today drawer movements in currency))`. Otherwise use `rawVaultBalance`. Therefore **do not interpret raw all-time drawer sums as current drawer balances**.
- `addCashTransaction` (6148): signed row includes original `vault`, `currency`, `amount`, `type`, `category`, `referenceType`, `referenceId`, `date`, `time`, `rateSnapshot`.
- `grossProfitUsdForRange` and `profitUsdForRange` (near 6480–6510): store, online receipt and repair profits, return adjustments, operating expenses, external partner share. A cash-count reconciliation changes the physical vault balance **but is not profit or operating expense**.
- `renderPartners` (near 6514): period family profit share = `familyProfitUsdForRange(from,to) * partner.share/100`; period withdrawals use actual withdrawal dates; accumulated due uses cumulative profit minus all withdrawals.

## Historical sample shape
- `cashTransactions`: 722 rows; actual vault identifiers: `bank`, `drawer`, `external`, `shamcash`; currencies observed: `USD`, `TRY`, `SYP`.
- `dailyClosings`: 34 rows, with `drawerActual`, `drawerTransfer`, `drawerNext`, `drawerDifference`, `shamActual`, `shamDifference`.
- `partnerWithdrawals`: 72 rows; original `partnerId`, `partnerName`, `amount`, `currency`, `vault`, `date`, `time`, `rateSnapshot`.
- `inventoryMovements`: 513 rows, 5 historical opening-stock movements without a corresponding current product. Retain without fabricating products.

## Next acceptance gates
1. Audit `carriedDrawerBalance` and all 34 historical closing records against source semantics.
2. Reconcile cash references to sales, repairs, purchases, payments, supplier settlements, returns and withdrawals without double posting.
3. Execute invoice/day/partner profit parity tests using exact source formulas.
4. On final migration, re-run on the new larger backup and require validation before activation.

**Status:** Source-based findings and explicit scope decision documented. Not yet a completed reconciliation or production database.
