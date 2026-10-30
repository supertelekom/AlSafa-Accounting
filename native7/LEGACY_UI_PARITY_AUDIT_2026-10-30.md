# Native 7 — legacy UI parity audit (2026-10-30)

## Scope and immutable rules
- Reference: supplied AlSafaAccounting v3.9.359 tablet source archive (assets/main.js, assets/style.css, assets/styles.css, assets/index.html).
- Target: native7 only on native6-development. Do not edit native6.
- No invented labels, routes, buttons, accounting rules or visual states.
- Match the actual old app before declaring any page complete. Android Studio build and Honor Pad X8b visual tests remain necessary.

## Verified source observations
- Legacy CSS root: cream #f8f3eb; paper #fffdf9; gold #a97929; ink #3e3429; muted #8a7b6c; line #eadfce; danger #b8584b; success #5a8a67.
- Legacy CSS body font-family: Tahoma, Arial, sans-serif; archive also contains assets/Tajawal-Medium.ttf. Inspect actual per-element font overrides before changing all typography.
- Native7 MainActivity currently uses matching ink/gold/pearl/paper constants and loads Tajawal-Medium.ttf; common TextView builder centers text and adds 9dp horizontal/8dp vertical padding. Verify each screen against legacy instead of applying one alignment globally.
- Native7 has navigation history, repair draft snapshot/restore, photo capture/gallery and ML Kit barcode scan paths. Presence of code is not proof of working behavior.

## Cross-screen verification matrix (NOT YET SIGNED OFF)
| Area | Compare from old source | Target validation |
|---|---|---|
| Home and simplified mode | Exact button labels/order, sizing, spacing, icons, navigation | Tablet screenshots and back navigation |
| Products | Cards, create/edit, variants, camera/gallery, barcode, zoom, dialogs | Form spacing, persistence, duplicate handling |
| Sales | Full/simplified invoices, discounts, payments, cancel/return, PDF/share | No unintended UI or financial behavior changes |
| Maintenance | Intake, status, delivery, quick repairs, photo preview | Draft survival and image zoom |
| Suppliers | Supplier lists, invoice fields, payment dialogs | Correct original labels and layout |
| Expenses | Form and list layout, currencies, date filters | Cross-screen consistency |
| Cashboxes | Channels, balances, transfers, closing dialogs | Original labels and state transitions |
| Reports | Date selectors, partner report, filters | Period isolation and readable alignment |
| Settings | All original options, confirmations, backup/restore | No fabricated menu entries |

## Shared component regression checks
1. RTL alignment, text baseline and Tajawal/Tahoma source distinction.
2. Button dimensions, icon+label centering, card radius/border/padding.
3. Dialog titles, actions, keyboard and dismissal behavior.
4. Navigation and back stack across all nine areas.
5. Search filtering as typed and date ranges where present in legacy.
6. Image permissions, gallery/camera, ML Kit barcode behavior.
7. No changes to native6; no UI-only code represented as operational accounting.

## Completion status
- Completed: source archive identification; initial CSS token extraction; initial inspection of Native7 MainActivity; cross-screen audit and regression plan recorded.
- Not completed: exhaustive element-by-element comparison, implementation of all UI differences, Android Studio build, device screenshot comparison, functional regression tests.
- This document is a checkpoint, not a claim of visual parity.
