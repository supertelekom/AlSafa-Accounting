# Native 7 — Integrated UI parity batch (2026-10-30)

## Original sources reviewed
Tablet v3.9.359 archive: `assets/style.css`, `assets/styles.css`, and original route names from `assets/main.js`. Native UI uses the mandated Tajawal font. Do not replace it with CSS Tahoma fallback.

## Implemented and committed
- `b6db5a358ea4f0d4dd0db21cb5ee70a091d0058b`: product metadata outer margins, top divider, RTL value alignment, stock warning spacing.
- `486e75ba83c6f2071fb709cb2dc031ab0b922d5a`: remove duplicate bottom-divider Drawable in shared section-title renderer.
- `73b517ad1dba71038069a50e1ab22f5773e80100`: recursively style nested h3/link-button descendants of section titles; apply original form-card tablet padding based on source class rather than only structural kind.

## Nine-route audit and test matrix
| Route | Source page | Shared component to test | Pending |
|---|---|---|---|
| renderDashboard | الرئيسية | section-title, cards | simple/full mode actions, motion |
| renderProducts | المنتجات والمخزون | product-info, stock-warning, headings | pictures, color variants, barcodes |
| renderSales | المبيعات والفواتير | section-title, forms | full/simple invoice, payments, returns |
| renderRepairs | قسم الصيانة | headings, form-card | photos, status, modal interactions |
| renderPurchases | المشتريات والموردون | headings, form-card | statements, purchases and debts |
| renderExpenses | المصاريف | headings, form-card | expense input/actions |
| renderVaults | الخزائن والأرصدة | headings, form-card | movements and closing |
| renderReports | التقارير | headings, report filter/tile | date filtering, values |
| renderSettings | الإعدادات | headings, form-card | backups, settings modals |

## Verification status
GitHub commits verified. Kotlin compilation: **not tested**. APK: **not built**. Honor Pad X8b: **not tested**. All nine screen visual parity, label/order fidelity, modal geometry and animation fidelity: **not complete**. No database/accounting rule changes; `native6/` untouched.

## Next integrated pass
Compare source JSON nodes and route screenshots against original markup for all nine screens, fix responsive grid and modal geometry, validate every shared class selector and preserve legacy action names. Compile and run screenshot regression on tablet before declaring parity.
