# Native 7 — Cross-route list and action component batch

Date: 2026-10-30. Branch: `native6-development`. Only `native7/` was edited.

## Source checked
Tablet v3.9.359 ZIP, `assets/style.css`. Source CSS:
- `.list-row`: horizontal flex, align center, 11px gap, padding 13px 14px, bottom border `#f2ebdf`, except final child.
- `.list-icon`: 38×38px, radius 11px, background `#f8eddb`.
- `.list-main`: flexible; nested `b` 12px, `small` muted 10px with 4px top gap; overflow ellipsis.
- `.list-side`: intrinsic width; nested `b` 12px and `small` muted 10px.
- `.row-actions`: horizontal, gap 5px, margin-top 7px.
- `.tiny-button`: min-height 25px, padding 3px 7px, sand background `#faf1e3`, 7px radius, deep-gold 10px text.
- Prior batch: `.photo-picker` <=520px vertical, `.form-actions` equal width, `.detail-list` rows.

## Implemented in code
- `9cb367f7804f2b0a0790f903fbb9a10e4f5af7eb`: list-row horizontal layout and separators; list icon, action row and tiny button; initial nested typography.
- `f537a7d175d5f38bb42155f920438e57045ee104`: avoid reading the outer view before initialization.
- `054ec90aa58fe90c129da2443b4ac3e250e9c421`: move nested list typography into the ViewGroup path.
- `781a4858b01029f8c5d69efd0de50f3748a04fc4`: remove generic bottom margins between list rows.

## Nine-screen regression matrix
| Screen | Inspect after shared changes |
|---|---|
| الرئيسية | dashboard recent-activity rows and quick action buttons |
| المنتجات والمخزون | product records, row actions, barcode buttons |
| المبيعات والفواتير | sale list rows, payment details, action buttons |
| قسم الصيانة | service rows, photo actions and status controls |
| المشتريات والموردون | supplier/purchase lists and payment actions |
| المصاريف | expense records and delete actions |
| الخزائن والأرصدة | movement rows and closing actions |
| التقارير | report list rows and filters |
| الإعدادات | configuration list rows and small buttons |

This matrix describes **what to test**, not confirmed usage of every class on every route.

## Not complete / requires validation
- Last-row divider removal currently uses a `last` metadata flag; source CSS uses `:last-child`. Must validate or replace with a reliable sibling check.
- `.row-actions` 7px top margin not yet implemented in this batch.
- Full label/order parity and all original CSS overrides have not been checked for each route.
- Kotlin compilation, Android Studio APK build, and Honor Pad X8b screenshot tests were **not performed**.
- Photos are still placeholders in the source-structure renderer; operational buttons, persistence, camera, gallery and zoom require separate verification.
- No accounting rules or Native 6 files were changed.

**Status:** shared source-based list rendering updated and committed; nine-screen complete parity is **not** established.
