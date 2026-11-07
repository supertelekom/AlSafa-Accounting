# Compact full-sales controls and keyboard visibility

Changed:
- `sales/FullSaleActivity.kt`: compact dropdowns for sale type/currency, warranty, payment currency/cashbox and change currency/cashbox. Save button uses 48dp sizing. Product-edit custom content scrolls; both edit/payment dialogs resize for IME. Search requests space for the first results and dismisses its keyboard before product editing.
- `stylekit/SafaWidgets.kt`: reusable 48dp RTL/Tajawal dropdown, no callback for initial/programmatic restoration. Input fields avoid fullscreen keyboard extraction and share focused-field reveal behavior.
- `stylekit/SafaKeyboard.kt`: cap requested context to the visible scroll viewport; reveal after IME animation; separate dialog-window resizing; dismiss keyboard on dropdown touch.
- `stylekit/SafaPageHost.kt`: use the same reveal policy instead of a competing fixed 80dp scroll target.

Change amount remains automatic using the saved exchange rate. No financial posting, schema or backup changes in this batch. No manual override added.

Validation: Kotlin compile of modified files and supporting classes passed against an Android jar with boundary stubs for catalog/Room/ML Kit. `git diff --check` passed. This is not an APK build or a tablet visual test. Financial tests were not rerun because financial files are unchanged.

Tablet checks: open currency/warranty dropdowns, choose and save values, rotate a draft, search with keyboard visible, choose a product and focus quantity/price, then add a payment with its keyboard visible. Verify focused field and first search results remain reachable, and check simplified shared text inputs for keyboard behavior.
