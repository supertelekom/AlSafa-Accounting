# Native 6: direct legacy tablet source comparison

Source inspected: user-supplied `AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE.zip`, `assets/main.js` (1,052,953 UTF-8 bytes). This is **v3.9.359, not v3.9.353**; do not claim byte-for-byte v3.9.353 parity. Legacy source is a packaged Android/WebView application. It is a reference only; Native 6 must remain Kotlin, SQLite and offline.

## Real legacy entry points
The old JavaScript source contains these explicit renderer functions (verified by inspection):

| Old app renderer | Native 6 coverage (code reviewed) | Gap |
|---|---|---|
| renderDashboard, renderFatherHome, renderAdvancedHome, renderModernHub | TabletDashboardActivity native home/simple/advanced | Layout, exact navigation and section parity unverified |
| renderProducts, renderStockAlerts, renderInventoryMovements | products/inventory | Old product form, cost lots, stock movements, duplicate controls not fully matched |
| renderMaintenanceParts | maintenance-parts | Parts list exists; old part workflow not fully matched |
| renderSaleForm, renderSales, renderReturns | sale/invoices | Historical sales not active; original invoice details/returns not fully mapped |
| renderRepairForm, renderRepairs, renderFatherRepair, renderFatherDeliveries | repairs | Historical repairs loaded, but old payments/invoices not linked |
| renderPurchases, renderPurchaseForm, renderSupplierProfiles, renderSmartReceiving | suppliers | Historical purchases and supplier ledger not active |
| renderExpenseForm, renderExpenses, renderDebts | partial finance sections | Expenses and debt history not active |
| renderVaults, renderDailyClose, renderWallet, renderOpeningBalances | vaults | Old closing, rollover, vault history and 3-currency continuity not restored |
| renderReports, renderPartners, renderModernReports | reports/partners | Historical profit and withdrawals not active |
| renderCustomers, renderCustomerOrders, renderWarranties, renderAlsafaCare | no verified equivalent | Customer/after-sales timeline not matched |
| renderDiscountRules, renderLoyaltyCenter | no verified equivalent | Rules and loyalty records archived only |
| renderUsedWatches, renderUsedWatchPurchase, renderUsedWatchExchange | no verified equivalent | Used-watch acquisition/exchange workflow missing |
| renderAuditLog, renderCostHistory, renderAnomalyCenter | audit / partial | Original audit and historical cost records archived |
| renderWatchAdvisor, renderVisualWatchFinder, renderWatchCompare, renderSmartPricing | no verified equivalent | Old watch advisor/search/pricing workflow not matched |
| renderCustomerDisplay, renderProductQr, renderInventoryCameraCount | partial barcode/camera | Customer QR, stock camera count and display not matched |
| renderShiftHandover, renderTodayCenter, renderCustomerSession | no verified equivalent | Shift, daily center and customer session not matched |
| renderSettings, renderPrinterSettings, renderSecurityLock | partial settings | Original configuration and printing workflow not matched |

## Verified old-app behaviors requiring fidelity
- `renderDailyClose()` reads `dailyClosings`, calculates per-method and per-currency cash (USD, SYP, TRY) and Sham balances, displays recent closing history and checks assigned closing device.
- `renderPartners()` selects a month and an explicit date range, reads `partnerWithdrawals` and calculates profit for that range.
- `renderProducts()` displays product count and offers refresh, safe import and add product actions.
- `renderRepairs()` sorts repairs by date/time, shows invoice numbers and provides invoice filters.
- `renderPurchases()` handles multi-item supplier invoices and explicitly distinguishes stock purchases from operating expenses.
- `renderDebts()` builds customer debt balances from sales and repairs.

## Implementation priority
1. Restore and reconcile original sales, repairs, purchase invoices, receipts, returns, expenses and source IDs in operational tables.
2. Reconcile mirrored cash records, vault opening balances, closing history and three currencies without double posting.
3. Activate supplier balances and partner withdrawals/profit in their actual transaction dates.
4. Recreate old UI sections using Kotlin Native widgets and old source labels/fields, not new simplified designs.
5. Validate with source-backup fixture: counts, dates, invoiceNo, stock, vault balances, receivables, payables, partner months. Build/test APK on isolated tablet.

## Current safety limitation
`LegacySnapshotImport` still archives historical finance without posting it. Imported repairs are flagged `_legacyImported` and blocked from new financial actions until reconciliation. **Do not describe this as completed backup restoration.**

## 2026-10-26 migration progress
- `LegacyFinancialLinkAudit.kt` now audits source identifiers, reference links, duplicate invoice numbers, cash entries mirrored by sales/repairs/purchases/expenses/withdrawals, and counts partner withdrawals by their original month.
- `LegacySnapshotImport.kt` retains the audit result in the restore metadata. This is a non-posting validation step.
- `MainActivity.kt` now exposes read-only historical invoice search and linked payments within the invoice section; historical supplier ledger is accessible from suppliers; cash mirror and partner-month audit can be run from staged legacy backup inspection.
- Remaining blocker: finance has not been migrated to operational ledger, and no APK build or 60 MB restore test has been run. Do not describe this work as completed restore.
