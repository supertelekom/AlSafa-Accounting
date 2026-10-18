# v3.9.359 legacy JSON backup: verified collection inventory

Source: provided tablet archive `AlSafaAccounting-v3.9.359-TABLET-SUPPLIER-LEDGER-IMPORT-SOURCE.zip`, `assets/main.js`, `emptyData` and `backupPayloadText()`. This is a source-code schema inventory, not a claim that a particular exported backup contains every field.

## Envelope
`{format:"alsafa-accounting-backup",formatVersion:2,exportedAt,appVersion,deviceSettings,data}`.
The app also accepts bare `data` objects if `settings`, `sales` and `products` are present. Its exported backup filename ends in `.json`.

## Top-level collections in emptyData
`products`, `maintenanceParts`, `sales`, `repairs`, `expenses`, `payments`, `partnerWithdrawals`, `cashTransactions`, `dailyClosings`, `purchases`, `suppliers`, `returns`, `usedWatchTransactions`, `customerOrders`, `auditLog`, `discountRules`, `loyaltyLedger`, `productCostHistory`, `smartReceipts`, `productDetailOptions`, `inventoryMovements`, `inventorySessions`, `customerSessions`, `shiftHandovers`.

Non-collection fields: `version` (legacy data schema value 14), `sync`, `settings`, `rates`.

## Excluded functionality, not lost financial history
- #17 `inventorySessions`: exclude old quick barcode stocktaking UI, **but inspect inventory movements and any adjustments produced by old sessions** before dropping source records.
- #34 `customerSessions`: exclude the customer sales-session UI and do not import session workflow state. Preserve any actual sales and customer liabilities.
- #58–60: watch advisor, price advisor, modern smart center; exclude these UIs and associated shortcuts.
- `sync` and legacy device/cloud state: never imported. `deviceSettings.security` must not be blindly trusted or carried over.

## Financial correctness gates
Legacy `settings.partners` defaults in source are 40/35/25, but **do not assume these are the shop's actual ownership percentages**. Actual backup values and explicit owner approval are required. Preserve external partner funding lots and ledger history; do not calculate profit from catalog prices.

Do not equate absence of a top-level array with absence of a feature: some details may be nested in invoices, products, settings or supplier records. Before writing Native 5 transactions, analyze actual record fields, IDs, dates, currencies, amounts and links. Reconcile counts, vault balances, stock quantities, outstanding supplier/customer balances and historical period profit. No old cloud synchronization data is imported.

**Implementation status:** exact top-level names verified from packaged JS; per-record mapping and JSON parser/importer still pending. The current ZIP preflight does not parse the exported JSON.
