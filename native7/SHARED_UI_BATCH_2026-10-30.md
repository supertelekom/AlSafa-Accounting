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

## Source ZIP review — photo picker and notes (2026-10-30)

Verified directly in tablet v3.9.359 `assets/style.css`:
- `.note-box`: margin-top 12px, padding 10px, radius 10px, background `#faf4e9`, muted text 11px, line-height 1.6.
- `.photo-picker`: 116px fixed preview + flexible controls, 12px gap, centered vertically.
- `.photo-preview`: 116×84px, 12px radius, dashed sand border, `#fffaf2` background, muted 10px text.

**Commits:** `5a015e2f2bca4957170589f0a36a0cb8633ff85f` styles note boxes as both text and container nodes and applies the source top margin. `6b9642193da9b0ecbca5e1442601753c937e8cf9` implements horizontal photo-picker layout and dashed photo-preview text surface.

**Nine-section review targets:** Home (shared notes), Products (photo-picker and product pictures), Sales (invoice details), Maintenance (service photos), Suppliers (supplier forms), Expenses (form notes), Cashboxes (movement details), Reports (report notes), Settings (backup/settings notes). Verify actual occurrence of these CSS classes per source JSON; no claim every route uses them.

**Important limitations:** The `picture` node in the Native renderer still uses a text placeholder rather than loading a real photo; photo-picker layout does not implement image capture/gallery or zoom. Source CSS may override these rules in later stylesheets; screenshot comparison is required. Build, Kotlin compilation and Honor Pad X8b testing were not performed. No change to `native6` or accounting data.

## Integrated responsive/forms/details batch — 2026-10-30

Compared against original tablet v3.9.359 `assets/style.css`:
- `.photo-picker` defaults to 116px preview + flexible second column, 12px gap. **At viewport <=520px** source overrides to one column and 170px high preview. This responsive override was missing from Native 7.
- `.form-actions` is horizontal flex, 9px gap, 18px top margin; all direct children grow equally.
- `.detail-list li` uses a horizontal row with space-between, 8px gap, 10px vertical padding, 12px font, bottom border except final row; descendant span muted.

**Changes committed:**
1. `21201cc3b76c9b927fe54c63fa5bb233c54255d7`: photo picker responsive layout at <=520dp; equal-width form actions and 18dp top margin.
2. `d9b00c21b6719427395bfecabf265c1144f762f5`: detail-list horizontal rows with 10dp vertical padding, 8dp gap, 12sp text, muted span, and divider except last row.

**Shared cross-route review targets:** Dashboard (form actions), Products (photo picker/details), Sales (invoice detail rows), Repairs (service photos and detail rows), Purchases (supplier detail lists), Expenses (form actions), Vaults (movement detail lists), Reports (detail rows), Settings (form actions). Exact source node occurrence per route must be checked before calling any screen complete.

**Not tested:** Android Studio Kotlin compilation, image loading, camera/gallery, photo zoom, action wiring, source JSON ordering, tablet portrait/landscape and mobile screenshot comparisons. No accounting rules modified. Only `native7` touched.
