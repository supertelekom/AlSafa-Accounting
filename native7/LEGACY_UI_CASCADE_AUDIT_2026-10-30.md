# Native 7 — nine-screen source cascade review

**Scope:** all nine legacy render entrypoints, shared CSS components, tablet-specific action visibility. **Branch:** `native6-development`; **directory:** `native7/` only.

## Completed this batch

1. Inspected the supplied tablet v3.9.359 source ZIP (`assets/style.css`) and checked repeated CSS selectors. Source `.list-row` is a 13px × 14px flex row with 11px gap; `.status` uses 9px pills and distinct done/debt palettes. Tablet CSS has later `.row-actions` overrides, so the initial 5px-gap rule is not always the effective rule.
2. Adjusted Native 7 shared tablet row actions to show fewer controls per row (three at 720–1199dp, five at >=1200dp), with **no horizontal scrolling**, per the user's explicit request. This is a conservative layout choice, not a pixel-perfect implementation of browser flex-wrap. Commit `a690435d7d57a0add9d14e98a1af1227a54dd279`.
3. Added `native7/tools/audit_legacy_ui.py`, a read-only standard-library script that accepts the original source ZIP and inventories all nine legacy `render*` functions, the CSS cascade of 27 shared selectors in both `assets/style.css` and `assets/styles.css`, and the stylesheet load order. Optional `--output` writes a JSON report. Commit `93c9c98784fb19eaceca251c852a8dbe556ce822`. This avoids repeatedly auditing only the first CSS declaration.

## Screens to compare (source entrypoints)

| Screen | Original renderer | Key shared elements to validate |
|---|---|---|
| الرئيسية | `renderDashboard` | simple-action, metric-card, list-row |
| المنتجات | `renderProducts` | product-card, photo-picker, row-actions |
| المبيعات | `renderSales` | transaction, detail-list, status, tiny-button |
| الصيانة | `renderRepairs` | row-actions, status, photo-picker, modal |
| الموردون | `renderPurchases` | list-row, detail-list, form-actions |
| المصروفات | `renderExpenses` | form-card, list-row, tiny-button |
| الصناديق | `renderVaults` | transaction, form-actions, detail-list |
| التقارير | `renderReports` | report-filter, report-grid, report-tile |
| الإعدادات | `renderSettings` | form-grid, modal, form-actions |

The table is a **test matrix**, not a claim that every class is present in each screen.

## How to reproduce

```bash
python native7/tools/audit_legacy_ui.py AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE.zip --output native7/legacy-ui-audit.json
```

## Unfinished / untested

- Complete source JSON node-by-node comparison of **labels, element ordering, actions, dialogs, and animations** for each route.
- Confirm all shared style changes apply to the correct native view kinds, including `status` and nested `row-actions`.
- Validate long Arabic labels fit without truncation and without sideways scrolling on the Honor Pad X8b in portrait/landscape.
- Confirm Tajawal font asset loads, and visually compare original screenshots with Native 7.
- Verify photo capture/gallery, image previews/zoom, button handlers, backup restore, and accounting functions.
- **No Android Studio build, APK install, or tablet test was run in this batch.** The audit script was committed but has not been executed from GitHub in the user environment.

No changes to Native 6, database rules, or user data.
