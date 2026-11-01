# Native 8 — Phase 1: original gross profit execution

Source: original v3.9.359 `assets/main.js`. Temporary fixture: 2026-10-26 formatVersion 2 JSON backup.

## Method
Executed the **unchanged** original JavaScript `grossProfitUsdForRange` and its dependencies in an isolated Node VM against the fixture. Functions used: `number`, `inRange`, `normalizedCurrency`, `rateFor`, `convertCurrencyAmount`, `usdValue`, `saleCost`, `onlineSaleReceiptEvents`, `onlineSaleReceiptCost`, `repairSelectedParts`, `repairPartsInvoiceTotal`, `repairLaborPrice`, `repairInvoiceTotal`, `repairReceiptEvents`, `repairReceiptCost`, `taxNetAmount`, `grossProfitUsdForRange`, `partnerWithdrawalUsd`.

## Results
| Period | Original-source gross profit USD |
|---|---:|
| September 2026 | 955.6144866395658 |
| October 2026 | 2342.218876548387 |

Partner withdrawals converted using each row's original rate snapshot:

| Period | father USD | me USD | brother USD |
|---|---:|---:|---:|
| September 2026 | 78.35935108662382 | 75.38347107438017 | 0 |
| October 2026 | 259.64880155944286 | 329.48481103237026 | 81.3963219310924 |

## Important limitation
The historical fixture contains two `settings.externalPartners` definitions. **Gross profit is NOT distributable family net profit.** Source: `profitUsdForRange = grossProfitUsdForRange - externalPartnerShareUsdForRange`. Do not calculate family shares or due from gross profit. Preserve historical external-partner effects without activating additional partners in Native 8. Execute the external-partner dependency graph next.

The backup is temporary and the production cutover backup will be larger. Re-run all calculations against that file; no fixed sample counts, balances, invoice numbers or IDs. Customer orders excluded from active implementation by owner instruction.

**Status:** exact source gross-profit function executed; full net-profit and partner parity pending. No production DB or user data modified.
