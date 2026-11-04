# Native 8 financial source-parity gates

Canonical source: supplied v3.9.359 archive, assets/main.js. This checklist describes implementation status, not an approval.

## Source-confirmed accounting paths

- `grossProfitUsdForRange`: store sales on invoice date; online receipts on collection date; repair receipts on collection date; sale and repair return impacts; expenses once.
- `profitUsdForRange`: gross profit minus external-partner share in the legacy version. New family-only accounting must not accidentally subtract a removed partner twice.
- `financeMethodVault`: cash -> drawer, ShamCash -> shamcash, bank -> bank, any other payment method -> null. `saveExpense` **still saves the expense** when vault is null, with **no cash transaction**.
- `currentSnapshot`: USD=1, SYP and TRY are currency units per USD; `usdValue` divides by the historical snapshot rate.
- Family partners use IDs `father`, `me`, `brother` and percentages 40/35/25.
- Cash-count reconciliation is excluded from profit.

## Open correctness blockers — do not mark complete

1. Operational expense schema currently requires a non-null vault and cash movement ID, and repository rejects unknown payment methods. This diverges from `saveExpense`; introduce an explicit safe migration and nullable movement before connecting the expense form.
2. Legacy financial import must derive source events from actual sales, payments, repairs and returns (including costs, taxes, discounts, dates and FX snapshots), without creating operational ledger entries.
3. Legacy partner withdrawal records must be filtered by family partner type, not merged with historical external partner payouts.
4. Operational store sales may not be represented by collection-profit events. Confirm event semantics and exclude duplicate recognition before aggregation.
5. Verify refund reversal sign conventions and historical return impacts.
6. Confirm source calendar timezone and historical payment timestamps; never silently use UTC.
7. Wire approved full-source repository into actual expenses, profits, partners and reports screens only after source reconciliation.
8. Execute Room schema migration, rollback and idempotency tests; build APK and verify on a disposable restored backup.

## Safety

`UnifiedFinancialPeriodRepository.Result.complete` defaults to false; `approvedNet()` and `approvedPartnerShares()` reject incomplete results. Do not present `partialNet` as business net profit.
