# Native 7 — Nine-screen original-source audit

Date: 2026-10-30. Source: attached `AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE.zip`, `assets/main.js` and `assets/style.css`. Target: `native7` on `native6-development`. This document lists original-source actions, **not** claims that Native 7 already implements them.

## Original routes and source actions

| Route | Original page | Selected actions found in source |
|---|---|---|
| `renderDashboard` | الرئيسية | `choose-sale-search-product`; simplified/full mode controls need separate trace |
| `renderProducts` | المنتجات والمخزون | `refresh-products`, `safe-products-import`, `new-product`, `view-product-photo`, `product-label`, `edit-product`, `adjust-product-stock`, `view-product-barcode-photo`, `delete-product` |
| `renderSales` | المبيعات والفواتير | `view-sale`, `edit-invoice`, `sale-return`, `correct-sale-payment`, `repair-sale-footprint`, `repair-mixed-sale-cash`, `pdf-sale`, `print-sale`, `collect-online-sale`, `wa-sale`, `thanks-sale`, `delete-sale` |
| `renderRepairs` | قسم الصيانة | `view-repair-photo`, `view-repair`, `edit-invoice`, `repair-return`, `repair-problem-update`, `repair-status-update`, `pdf-repair`, `print-repair`, `wa-repair`, `thanks-repair`, `repair-ready`, `repair-delivered`, `delete-repair` |
| `renderPurchases` | المشتريات والموردون | `new-supplier`, `supplier-statement`, `supplier-opening-debt`, `supplier-direct-payment`, `edit-supplier`, `delete-supplier`, `pay-purchase`, `purchase-sync-missing`, `cancel-purchase` |
| `renderExpenses` | المصاريف | `delete-expense`, `new-payment` |
| `renderVaults` | الخزائن والأرصدة | `open-cash-movement`, `close-modal` |
| `renderReports` | التقارير | report filters and date/month controls; no `data-action` captured in initial 10KB scan |
| `renderSettings` | الإعدادات | `quick-repair-button-new`, `quick-repair-button-edit`, `quick-repair-button-delete`, `message-templates-reset`, `backup-auto-now`, `backup-auto-latest`, `backup-auto-previous`, `backup-export-file`, `backup-import-file`, `backup-copy`, `backup-show`, `clear-data` |

Action extraction used the first 10,000 source characters starting at each `render*` function. This is an **initial inventory**, not an exhaustive action list. Some functions delegate to other renderers or attach events separately. No new UI labels were invented.

## Original common CSS

- Palette: paper `#fffdf9`, gold `#a97929`, deep gold `#7a5118`, ink `#3e3429`, muted `#8a7b6c`, border `#eadfce`.
- `.section-title`: horizontal, 14px/15px padding, bottom border, h3 15px.
- `.modal-head`: horizontal title and close, 15px bottom margin, h3 17px.
- `.close`: 32px square, radius 10px, background `#f7eddd`, text 19px.
- `.modal`: on tablet centered with radius 20px, base max width 660px, max height viewport minus 50px; on narrow screens bottom sheet with top corners 22px. Later media rules increase tablet max width to 820px and wide landscape to 900px.
- `.report-filter`: two flexible fields plus auto action, gap 8px, 13px padding, radius 15px.
- `.form-grid`: 2 columns, gap 11px; responsive overrides apply.
- `.product-info`: 1fr/auto columns, gap 7px/12px, margin 14px top and 8px bottom, 11px top padding, 1px top border.

Native 7 uses **Tajawal** as required by the project, even if the original web stylesheet has fallback fonts.

## Changes actually pushed in this batch

- `407fb5c454d39840dde4b6ae11a824c97abf8b83`: apply source-colored `.modal` surface, 20dp tablet/22dp mobile corner radius, responsive horizontal and vertical padding, clipping. Modal placement, width and max height are **host-owned and not yet implemented by this change**.
- `40472a3fc3643e0039985701cc3a614df06df084`: remove unused orientation variable.
- Earlier `e989908a` adds `.modal-head` and `.close` styling; `b6db5a35` fixes product-info outer spacing; `486e75ba` removes duplicate section-title drawables.

## Mandatory next validation

1. Inspect each complete original render function and all delegated dialogs, then compare JSON node order and exact labels against Native 7, without renaming.
2. Confirm source `.modal` classes actually occur on renderer nodes; ensure the Activity modal host owns tablet centering, width and max height.
3. Verify click handlers and real data persistence for every source action; appearance alone is insufficient.
4. Compile in Android Studio, exercise all nine routes and screenshot compare on Honor Pad X8b, including portrait/landscape.
5. Validate no changes to Native 6 or accounting records. No APK build or device test performed in this batch.

**Status:** source inventory and shared modal surface change saved; nine-screen full parity **not complete**.
