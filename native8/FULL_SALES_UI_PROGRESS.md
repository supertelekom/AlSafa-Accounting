# Full-mode sales progress

## Current correction batch
- Normalize supported currency codes with trim/Locale.ROOT uppercase. A legacy `usd` product no longer fails the exchange-rate precondition; unsupported currencies have an Arabic message.
- Arrange invoice data in a card with paired type/currency, date/time, and customer/phone controls. Keep source sections for inventory, invoice items, payment and notes.
- Add sale currency display with a saved rate-to-USD snapshot. The operational accounting base remains USD. Full-form unit prices and manual discount are rounded to USD cents; exact arbitrary foreign-currency precision is not promised.
- Date/time use calendar and clock pickers; selected date is saved as invoice date and store-profit date. Actual cash receipt/change events retain actual posting time.
- Warranty choices match source: none, 3, 6, 12 (default), 24 months. Notes and warranty are persisted and shown in details/PDF. No warranty is invented for older rows.
- Only manual discount is included, per user request. New/old-client automatic discounts and loyalty rules are not pending requirements.
- Overpayment now exposes change currency and cashbox, previews the converted cash amount and records both cash sides atomically with the invoice.

## Change accounting
Example: invoice 95 USD, tender 100 USD in drawer, return 250 TRY from external at 50 TRY/USD.
- Invoice receipt: 95 USD, eligible for later return/cancellation.
- Linked cash exchange `sale-change:<invoice id>`: +5 USD drawer, -250 TRY external.
- Combined actual balance effect: +100 USD drawer, -250 TRY external.
- Original tender, returned cash, cashbox and rate snapshot are retained in invoice metadata and printed.
- Later cancellation refunds the remaining invoice receipt, without returning the already-issued change again. Existing return/cancellation validation is retained.
- Unrepresentable currency rounding is rejected explicitly rather than silently losing value.

## Storage and backup
Room 19→20 adds only `sales.metadata_json TEXT NOT NULL DEFAULT '{}'`. No rows are deleted or financially reposted. Register the explicit migration. Full backup export uses schema 20; restore accepts 19 and 20, allowing Room to upgrade previous backups on open.

## Files
New:
- sales/SaleCurrency.kt
- sales/SaleChangePlan.kt
- sales/OperationalMigration19To20.kt
- test sales/SaleChangePlanTest.kt
Modified under app/src/main/java/com/alsafa/native8/:
- sales/ExchangeRateSettings.kt
- sales/FullSaleActivity.kt
- sales/RoomSalesEngine.kt
- sales/SalesEntities.kt
- sales/SalesRoomEntities.kt
- sales/AppDatabase.kt
- sales/SaleDetailsActivity.kt
- sales/InvoiceReceiptRepository.kt
- backup/NativeArchive.kt
- backup/NativeDataStore.kt
Also updated NativeArchiveTest.kt and tools/run_financial_junit.py.

## Validation
- 141 JVM tests passed, including lowercase currency, mixed tender, alternate change currency/cashbox, zero/partial/exact payments, rounding rejection and previous-schema archive acceptance.
- Kotlin compile of actual affected UI and RoomSalesEngine/DAOs succeeded against Android jar and boundary stubs. No Room KSP/APK/device claim.
- SQLite execution of migration SQL preserved a sample previous row and verified metadata default plus integrity_check. This is not Android Room schema validation.
- git diff --check passed.

## Device checks
1. Retry the `usd` product that failed; add another product and edit/delete an item.
2. Set a date/time, warranty, notes and TRY sale currency; save and verify details/PDF persist them.
3. Invoice 95 USD, receive 100 USD, choose external/TRY for change: expect 250 TRY at 50, +100 USD drawer and -250 TRY external. Check invoice net paid is 95 USD.
4. Return part, then cancel the rest; ensure the already-given change is not refunded twice.
5. Restore a previously-tested schema-19 backup and verify the update retains data.

## Remaining parity work
Manual non-stock items, explicit atomic exchange, and historical financial item editing/returns remain outside this batch. Bluetooth/automatic messaging are not completed here. These changes do not constitute full visual or functional parity of every legacy sale option. Simplified screen layout is unchanged.
