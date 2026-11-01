# Native 8 — Phase 1 batch 5: exact daily closing write order and profit source

Source: original `assets/main.js` v3.9.359. Temporary historical fixture: backup dated 2026-10-26. **Do not treat its row counts as import limits.** Customer orders are excluded from active implementation by owner request.

## Closing calculation and save order (verified directly)
1. `saveDailyClose(form)` L6385 calls `currentDeviceCanClose()`, then `cloudSyncNow({quiet:true,force:true})`, checks device again and rejects an already-closed `TODAY()`. Native 8 is offline-only; the historical cloud call is source context, **not** permission to add cloud sync.
2. For USD, SYP, TRY: `expected=drawerExpectedForDay(currency)`; `actual=max(0,input actual)`; `transfer=max(0,input transfer)`; reject `transfer>actual`.
3. Persist distinct `drawerActual=actual`, `drawerTransfer=transfer`, `drawerNext=actual-transfer`, `drawerDifference=actual-expected`.
4. If abs(difference)>0.0001, append a **drawer** `cashTransaction` with `type='reconciliation'`, signed `amount=actual-expected`.
5. If transfer>0, append two equal and opposite entries: drawer `type='daily-transfer-out'` amount `-transfer`; external vault `type='daily-transfer-in'` amount `+transfer`.
6. For Sham Cash USD and SYP only: calculate expected from `vaultBalance('shamcash',currency)`, store `shamActual` and `shamDifference`, and add signed Sham Cash `reconciliation` when abs(difference)>0.0001.
7. Create closing record with `id,date,time,drawerActual,drawerTransfer,drawerNext,drawerDifference,shamActual,shamDifference,notes,deviceId,deviceName,createdAt`; insert into `dailyClosings`.
8. `commitData({nextRoute:'vaults',...})`; on failure restore previous in-memory `data`. Native 8 must implement equivalent all-or-nothing database transactions without adding sync.

## Drawer balance definitions
- `previousDailyClosing(date)` L6312: most recent prior closing, by date then createdAt.
- `carriedDrawerBalance(currency,date)` L6313: `max(0,previous.drawerNext[currency])` or null.
- `drawerExpectedForDay(currency,date)` L6314: if no prior close, raw drawer balance; otherwise `max(0,carry + sum(drawer movements on date))`.
- `vaultBalance('drawer',currency)` L6135–6147: today carry plus today movements, clipped at zero. Do not use all-time raw sum when a carry exists.
- **Important sequencing:** closing reconciliation and transfer movements are created **before** the closing record. Recomputing `drawerExpectedForDay` later from the saved backup includes those same closing-generated movements, so a naive retrospective comparison to saved `drawerDifference` is invalid. A later report must reconstruct the pre-close snapshot (or exclude closing-generated movements) before claiming discrepancies.

## Source profit and partner rules
- `grossProfitUsdForRange` L6489–6509: store sales profit (tax-net total minus sale cost); online receipts recognized proportionally using each event's amount/total and corresponding recognized cost; repair receipts similarly; return impacts with cost recognition ratios; subtract operating expenses. Explicitly **exclude** cash-count reconciliation from profit.
- `profitUsdForRange` L6510: gross profit minus external partner share (legacy code supports external partners, but owner has removed them operationally; preserve historical evidence without inventing active partners).
- `renderPartners` L6513–6522: period share = period family profit × share percentage; period withdrawals filtered by actual dates; accumulated due = cumulative family profit × share − all-time withdrawals. Never charge withdrawals to a different month.
- FX conversion uses each row/event's `rateSnapshot`; do not sum currencies directly.

## Fixture spot checks (not a full ledger parity verdict)
- 34 closings, 722 cash transactions, 72 partner withdrawals.
- 39 `daily-transfer-out` and 39 `daily-transfer-in` entries, consistent in aggregate count with paired closing transfers.
- 29 `reconciliation` cash entries in the fixture, from both drawer and Sham Cash.
- 2026-09-22 closing: TRY actual 100, transfer 100, next 0; corresponding one outflow and one inflow exist on that date.
- 2026-09-23 closing: USD difference -29.55 and TRY difference +285; two drawer reconciliation entries exist on that date.
- The earlier retrospective mismatch tally (54 date/currency comparisons) **must not be treated as 54 errors**; it includes post-close movement effects. This batch corrects the interpretation.

## Next tests required
- Pair the 39 transfers by currency, date, amount, and stable provenance; identify ambiguity where multiple same-day transfers exist.
- Compare the 29 reconciliation rows to stored differences and their source reference metadata.
- Reconstruct pre-close balances from original chronology; then compare stored differences.
- Execute exact invoice/day/partner-profit parity tests with original JavaScript behavior before approving operational SQLite tables.

**Status:** source save-order verified, sample spot checks complete. Full financial parity and cutover import remain pending.
