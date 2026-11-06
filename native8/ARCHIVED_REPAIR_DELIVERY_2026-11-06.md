# Restored repair delivery — 2026-11-06

Scope: native8 only. Source: v3.9.359 assets/main.js balanceFor, applyRepairPartsStock, appendRepairDeliveryPayment, saveRepairDelivery (around 5645 and 8252–8279). Reference backup dated 2026-10-26. Existing native delivery/fast repair styling retained.

## Implemented
- Ready-deliveries cards now offer delivery/collection for restored repairs, plus a separate detail button.
- Scrollable confirmation asks actual received amount, USD/TRY/SYP and drawer/external/ChamCash/bank; TRY ChamCash is rejected. Actual currency receipt is separate from full remaining invoice credit, as in source. Already-paid repairs deliver with zero new cash/profit.
- Remaining invoice balance includes original received, source-matched payments, return credits and refunds. Current balance and original JSON are rechecked before commit.
- One Room transaction changes status/date, optional stock and new cash movement. Original source ID, invoice number and historical receipt remain unchanged. The immutable original JSON and new payment evidence are retained under nativeArchiveDelivery in the existing JSON column; no schema migration.
- Parts already marked applied are skipped. Unapplied parts are deducted now, on actual delivery, following original rule. Insufficient stock aborts the transaction.
- Stable cash reference and status/JSON conditional update protect retries. No old receipt or inventory movement is replayed.
- Existing unified financial repository and partner reports receive only the new delivery receipt profit dated today (Asia/Damascus), using original invoice cost/tax and the current receipt exchange snapshot. Historical profit remains in its existing source. No false historical/native ID overlap.
- Financial details resolve the original invoice/customer; maintenance details additionally show new delivery receipt/date/settled balance. Detail-page style remains deferred per user instruction.

## Verification
- 117 JVM JUnit tests passed. Seven new tests cover old receipts/returns in balance; delivery-only date/profit; paid invoice with no new event; mixed currency; ready-status guard; repeated financial reads, cutoff and cash-evidence validation; the three actual ready repairs in the existing original backup fixture.
- At a test snapshot 50 TRY/USD, the three original repairs produce new receipt profits 2.7018 USD (150 TRY less original cost 14.91 TRY), 4 USD (200 TRY labour), and 40 USD (labour). These are test rates, not imposed settings.
- Earlier runs failed on numeric JSON values read as strings in two locations; changed to numeric-compatible strict reads and reran successfully.
- Actual finalizeArchivedDelivery SQL executed in in-memory SQLite: rejects stale JSON, updates once, rejects repeat, preserves original received/source.
- Kotlin Android-facing smoke compilation passed against Android classes with service/Room/scanner boundary stubs; existing deprecation warnings. This excludes full Compose activity, generated Room implementation and Android packaging.
- git diff --check passed.
- NOT run: full Gradle/KSP build, Room instrumentation/transaction rollback on device, tablet UI, hardware printing. No production database or backup was mutated by these tests.

## Device acceptance
1. Simplified mode > Deliver watches > restored ready repair > Delivery/collection.
2. Check remaining invoice value; select amount/currency/cashbox > Confirm delivery.
3. Confirm removal from ready list, delivered status, correct single cash increase, and new receipt profit on delivery date. Previous month profit must remain unchanged.
4. Reopen/restart: no second cash/profit. Check a paid repair has no new cash. Applied parts must not deduct again; previously unapplied parts deduct once.
5. Full Native 8 backup/restore should preserve the delivery marker and receipt because both are in existing backed-up databases; device verification pending.

## Remaining
Original receipt template/automatic printing remains the next batch. Original automatic WhatsApp send is not added here. Historical return/exchange editing and maintenance detail redesign are not expanded by this delivery change.

## Files
- Modified: `native8/app/src/main/java/com/alsafa/native8/maintenance/MaintenanceActivity.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDao.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/maintenance/ReadyDeliveriesActivity.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/sales/NativeMaintenanceProfitRepository.kt`
- Modified: `native8/app/src/main/java/com/alsafa/native8/sales/UnifiedFinancialLedgerRepository.kt`
- Modified: `native8/app/src/test/java/com/alsafa/native8/sales/UnifiedFinancialIntegrationTest.kt`
- Modified: `native8/tools/run_financial_junit.py`
- Created: `native8/app/src/main/java/com/alsafa/native8/maintenance/ArchivedRepairDeliveryRepository.kt`
- Created: `native8/app/src/main/java/com/alsafa/native8/sales/ArchivedRepairDeliveryRules.kt`
- Created: `native8/app/src/test/java/com/alsafa/native8/sales/ArchivedRepairDeliveryRulesTest.kt`
- Created: `native8/ARCHIVED_REPAIR_DELIVERY_2026-11-06.md` (this report).
