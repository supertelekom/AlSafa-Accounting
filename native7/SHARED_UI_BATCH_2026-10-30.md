# Native 7 — Shared UI source-parity batch (2026-10-30)

Source of truth: supplied tablet v3.9.359 source ZIP, `assets/style.css`; Kotlin renderer `OriginalPageStructure.kt`. Branch `native6-development`, scope `native7/` only.

## Implemented
- Commit `e989908a7b6ed49a45de8b8c7ded19cd78329631`: `.modal-head` now arranges title and close horizontally, sets the source heading to 17sp bold and adds 15dp bottom margin. `.close` gets source 32dp square, 10dp radius, `#f7eddd` background, deep gold `#7a5118`, and 19sp text. Existing click handlers remain unchanged.
- Previously saved `b6db5a35`: product metadata 14dp/8dp margins, top border, value alignment, and stock warning margin.
- Previously saved `486e75ba`: removes redundant section-title drawable allocation.
- Previous commits in `LEGACY_PARITY_PROGRESS.md` cover nested report tiles, transaction text, product headers, label gaps, and responsive report filters.

## Nine-screen impact checklist
| Section | Original screen | Common items to verify |
|---|---|---|
| Home | الرئيسية | Mode switch, home cards, headings, modal close |
| Products | المنتجات والمخزون | Product metadata, image and color dialogs |
| Sales | المبيعات والفواتير | Full/simple invoice dialogs and buttons |
| Maintenance | قسم الصيانة | Photo viewer, service intake/delivery dialogs |
| Suppliers | المشتريات والموردون | Supplier/purchase forms and modal headings |
| Expenses | المصاريف | Add-expense form, close button |
| Cashboxes | الخزائن والأرصدة | Cash movements and daily close dialogs |
| Reports | التقارير | Date filters, report tiles and dialogs |
| Settings | الإعدادات | Backup, exchange rate and partner forms |

## Unverified / not completed
- Nine-route label-by-label and control-order parity against the full original JS and structure asset.
- Modal geometry on phone/tablet/landscape; animations and transitions; image display and action wiring.
- Android Studio compilation, APK and visual device tests on Honor Pad X8b.
- The native Tajawal mandate is retained; no changes to data or accounting rules.
- These are shared-renderer code fixes, **not** nine completed and tested screens.

## Follow-up source comparison and fixes (2026-10-30)

Directly inspected the supplied v3.9.359 ZIP `assets/style.css` again. The mobile `.modal` CSS specifies `border-radius:22px 22px 0 0` and `padding:18px 15px calc(22px + env(safe-area-inset-bottom))`; tablet overrides use 20px radius. The previous Native renderer rounded **all four** mobile corners and used 18dp bottom padding. The `.detail-list` source has no outer padding.

**Saved code:**
- `c406adb68b7f8d62ce941d21ea9ee82c70d8a891`: mobile modal now rounds only the two top corners, and uses 22dp bottom padding; tablet retains all-corner 20dp.
- `44cc0b529c948ce8c5d15c486b3c8a1fe3825d11`: detail-list container uses flush vertical layout with zero outer padding.

**Still not matched:** Android safe-area bottom inset, dialog window placement/width/max height, per-row detail-list 10px padding and border, photo preview image behavior, exact animations, original source labels/order and all button actions. Nine-screen regression targets remain home, products, sales, maintenance, suppliers, expenses, cashboxes, reports and settings. No APK compilation or Honor Pad X8b test was performed. All modifications remain inside `native7/`.
