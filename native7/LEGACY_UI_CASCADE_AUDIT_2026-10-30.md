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

## Follow-up: shared search and photo controls

The original tablet `assets/style.css` was inspected directly for these rules:
- `.search-box`: horizontal flex, 9px gap, 10px vertical / 12px horizontal padding, 2px sand border, 15px radius, white background, 12px bottom margin; nested span 22px and input 46px min-height / 15px text / transparent background.
- `.simple-recent`: 14px vertical margins, paper fill, 1px line border, 17px radius and clipped content.
- `.product-photo-button, .repair-photo-button`: 38×38px, no padding, 1px sand border, 11px radius, sand fill.
- `.simple-action`: 104px source height, but **Native 7 intentionally uses 52dp** as explicitly requested by the user. Preserve that override.

**Implemented commits:**
- `138d9d705287543fd03bffae0219612e5a404aa1`: shared search-box container and input appearance; recent list card geometry.
- `52dca62ab780c60cb571de839c2668477e1da141`: product/repair photo action geometry.

**Review impact across nine routes:** dashboard recent records; product search/photo actions; sales search; maintenance photo actions and search; supplier search; expense search; cashbox movement search; report filters/search; settings search if present. These are cross-route *targets*, not verified occurrences.

**Pending verification:** Search must actually filter as text is typed; styling does not implement search logic. Photo buttons are still not proof of camera/gallery/zoom functionality. Android compilation, APK, device screenshots and all nine route visual parity remain untested. No modifications to accounting rules or `native6`.

## Follow-up: RTL wrapping geometry fix (203b7eae)

- Inspected the current shared Native7 `SourceFlow` implementation rather than repeating the legacy CSS extraction.
- Found a concrete cross-screen overflow defect: the wrap condition compared `used + childWidth` against available width but did not include the gap before the next item. A row could therefore exceed its measured width even when every button individually fit.
- Corrected wrap measurement to include the inter-item gap, and positioned each RTL child using the same measured offset. This is a shared component change affecting any page using `SourceFlow`, not a change to labels, original routes, or accounting rules.
- Code commit: `203b7eaeb736334ea4e2f53fae66622f394fd231`.
- Remaining verification: Android Studio compilation, actual tablet portrait/landscape, long Arabic action labels, and nine-screen screenshot comparisons. This change does not establish that all screens match the old UI or that search/photo actions work.

## Follow-up: narrow report filter layout (3d81c3ea)

- Reviewed `OriginalPageStructure.reportFilter` and confirmed the existing 380dp compact threshold left 381–599dp widths displaying two flexible date fields plus a button in one row, potentially squeezing Arabic date controls.
- Updated the compact threshold to widths below 600dp. The action is placed beneath the two date fields at narrow widths; larger tablet layouts preserve the original single-row arrangement.
- Code commit: `3d81c3eab66e491b56fe4f9c27e1191a264975c3`.
- Verification still required: screenshot comparisons of report filters at 380/420/600dp and on Honor Pad X8b, Android Studio build, all nine screens, and functional searches/photos. No accounting logic or native6 files changed.

## Direct source comparison corrections (2026-10-30)

Compared the actual supplied v3.9.359 `assets/style.css` against Native7 `OriginalPageStructure.kt` rather than inferring UI values from previous notes.

- **54145456**: restored the exact source `@media (max-width: 380px)` report-filter breakpoint. Reverses the unsupported 600dp threshold introduced in 3d81c3ea. Source base rule is `grid-template-columns: 1fr 1fr auto`; narrow rule is `1fr 1fr` with button spanning both columns.
- **109416ad**: restored the missing 1px `--line` (#eadfce) border around the paper-colored report-filter panel, retaining the source 15px radius and 13px padding.
- **203b7eae** (earlier): shared RTL wrapping fix for action rows includes inter-item gaps in overflow calculations.

### Nine-screen status
The original renderer inventory remains `renderDashboard`, `renderProducts`, `renderSales`, `renderRepairs`, `renderPurchases`, `renderExpenses`, `renderVaults`, `renderReports`, `renderSettings`. Shared styling changes can affect multiple routes but are **not** evidence of complete element-by-element comparison. In this source pass, report-filter differences were directly verified; the other eight route comparisons and modal/animation parity are still open.

### Tests pending
Android Studio compile and device tests, screenshots on Honor Pad X8b, source-derived node-by-node labels/order/actions comparison, search behavior, gallery/camera/zoom, RTL text truncation, modal lifecycle and nine-route regression. No Native6, financial-rule or user-data changes.

## Cross-screen source field parity — d72748c7

Compared legacy tablet `assets/style.css` rules at `input, select, textarea` (42px minimum height, 9x10 padding, `#fffcf7` fill, `1px solid var(--line)`, 10px radius) and `input:focus, select:focus, textarea:focus` (gold border) against `OriginalPageStructure.kt`.

- Corrected shared `EditText` background to include the missing 1dp `--line` border, preserving the source fill, radius, padding and existing Tajawal/RTL behavior. Added focus listener to switch to the original gold border when focused. Native Android does not yet reproduce the CSS 3px focus shadow.
- Corrected shared `Spinner` background to include the missing 1dp `--line` border, preserving its existing dimensions and source-derived options.
- These shared controls can appear on dashboard, products, sales, repairs, purchases, expenses, vaults, reports and settings, but route-specific presence and complete element-order parity are not yet certified.
- Code commit: `d72748c7b9f3f54dfa9a2a9f841d0aba5e171290`.
- Device tests pending: focus state and keyboard, dropdown readability, long Arabic labels, screen captures on Honor Pad X8b, Android Studio build, camera/gallery/zoom and real-time search.
- No native6, database, financial or user-data changes.

## Source-derived responsive photo picker — 227c1a79 (2026-10-31)

Directly inspected the supplied tablet v3.9.359 `assets/style.css` photo-picker rules. Original `.photo-picker` uses `grid-template-columns: 116px minmax(0,1fr); gap:12px`; `@media(max-width:520px)` switches it to one column and `.photo-preview` to full width/170px height. Native7 previously always used a horizontal `photoRow` and minimum 116x84 preview.

- Updated `OriginalPageStructure.kt` shared `photoRow` to vertical at <=520dp, with 12dp inter-item spacing and full-width children, retaining 116dp/remaining-width horizontal layout above that threshold.
- Updated shared preview to minimum 170dp high at <=520dp and 116x84dp minimum on wider screens.
- Code commit `227c1a79990956a361425e2dc403e9692fee36fd`.
- These are source-verified shared layout corrections, **not** completed nine-route element-by-element parity. Still requires route-specific label/order/handler/modal/animation comparison, Android Studio build, device screenshot tests, and gallery/camera/zoom tests.
- No Native6, data, or financial-rule changes.

## Source CSS shared action layout pass — 2026-10-31

Direct comparisons against supplied v3.9.359 `assets/style.css` and Native7 `OriginalPageStructure.kt`:

- `.photo-actions {display:grid;gap:8px}`: shared native vertical photo action column had generic 2dp child margins. Corrected to 8dp between actions, with no extra bottom margins. Code commit `b4b134fe5b208e9fff0f6aa4f69d232ea74ab11a`.
- `.form-actions {display:flex;gap:9px;margin-top:18px}`: native `actions` used equal-width `SourceGrid`, which changed button sizing. Corrected to RTL `SourceFlow` with source 9dp gap and 18dp top spacing; it wraps only when needed. Code commit `4dab354d4a7e1a793fc22f0007373f2e67f1b2a7`.

Both are source-verified **shared-component** corrections; they do not demonstrate that every button, text, modal, action, and animation in all nine routes has been compared. Pending: route-by-route node inventories and visual screenshot comparisons, build/device verification, camera/gallery, search and action tests. Native6 and accounting remain unchanged.

## CSS cascade correction — 2026-10-31

The preceding note about `.form-actions` was incomplete: inspection of the actual original `assets/style.css` found both `.form-actions { display:flex; gap:9px; margin-top:18px }` **and** `.form-actions > * { flex:1 }`. The earlier SourceFlow change (`4dab354d`) wrongly used content-width buttons. Code commit `ebcac005753aee0e24eb85ba5c08a8aaa336155e` restores equal flex-weight button widths in a native RTL horizontal LinearLayout, 9dp gaps and 18dp top spacing.

**Important limitation:** source stylesheet has later, overlapping `.form-grid` media rules and `body:not(.father-mode)` overrides. Therefore a single generic grid threshold cannot be certified as matching all modes without a route/mode-aware cascade analysis. Do not mark all nine screens as matched based on shared CSS fixes. The full route-by-route DOM/element/interaction comparison and screenshot/build tests remain pending.

## CSS cascade audit: form grids — 2026-10-31

Read actual supplied legacy tablet `assets/style.css` and compared every `.form-grid` declaration in source order against `OriginalPageStructure.kt` grid column selection:

| Source width/orientation | Effective source columns | Native7 after fix |
|---|---:|---:|
| <=380px | 1 | 1 |
| 381–719px | 2 | 2 |
| 720–1199px | 2 (later override) | 2 |
| >=1200px, landscape | 3 (later override) | 3 |
| >=1200px, portrait | 4 (earlier >=1080 rule remains) | 4 |

Native7 had incorrectly used two columns at >=1200px portrait; corrected in code commit `c798c16730030bdb8153671df9cd81ba46030327`. This is a **shared form-grid rule**; source also contains `body:not(.father-mode) .form-grid` with auto-fit, which can alter effective columns by container width and must be checked with route/mode-specific screenshots. The table alone does not certify exact nine-screen parity. Android Studio build, tablet screenshots, all nine route DOM comparisons, dialogs, interactions and animations remain unverified. No Native6 or accounting changes.

## Legacy CSS spacing comparison — 2026-10-31

Inspected the actual supplied tablet `assets/style.css` and compared shared classes against `OriginalPageStructure.childParams`:

- `.search-box` has `margin-bottom:12px`; the generic native column had only a 2dp child margin. Fixed source-class bottom margin to 12dp.
- `.simple-actions` has `margin:10px 0 18px`; native generic margin was 2dp and had no source-specific top margin. Fixed top to 10dp and bottom to 18dp.
- Code commit `696aeb3efabb043b9ef5797bd6fa5018c905bcdf`.

These two shared CSS corrections are source-verified, but they do not establish element-by-element equivalence of all nine screens. Source route DOM, dialogs, button handlers, conditional visibility, animations, screenshot parity, Android Studio compilation and Honor Pad X8b tests remain outstanding. No native6 or accounting changes.

## Source cascade: small delete action buttons — 2026-10-31

Compared legacy `assets/style.css` `.tiny-button` and `.tiny-button.delete` against Native7 shared `button(n)` renderer. Found a real Kotlin override-order bug: the delete-specific pink/red drawable was immediately overwritten by the generic sand drawable, so destructive action buttons lost their visual distinction. Corrected to a single drawable selected by `delete` class, using source background `#fff2ef`, border `#f1c9c1`, and danger text `#b8584b`; standard tiny actions retain `#faf1e3` and sand border. Commit `812453acafd56c4b7243395ce11e436fb7a8be45`.

Scope: shared visual component, not proof of full nine-screen element parity. Still need route-by-route comparison of dynamic markup, labels, actions, modals, animations, and actual device screenshots/compilation.

## Shared button classes across nine screens — 2026-10-31

Compared source `assets/style.css` lines 152–175 with Native7 `OriginalPageStructure.kt` `button(n)`. Source `.primary-button`, `.secondary-button`, `.danger-button` all have min-height 44px, horizontal padding 15px and 12px radius; source primary uses #b38332 → #8b601f gradient and white text, secondary #f7eddd with #dfc9a4 border and #7a5118 text, danger #fff0ed with #f2c7c0 border and #b8584b text. Native7 formerly chose the primary style solely from JSON `primary` and otherwise applied secondary style, ignoring the original `primary-button`/`danger-button` classes and using 12dp horizontal padding. Commit `e8fbcd5f4dea976db1d6b093c7bb43c201af790e` applies all three source classes to shared buttons, preserving explicit primary flag and existing tiny/close overrides.

This correction applies wherever these classes appear among the nine routes. **Still unverified**: exact text/ordering/field parity for each route, original modal behavior and animation, Android Studio compilation, and tablet screenshot comparison. No changes to Native6 or accounting.

## Form action margin double-count — 2026-10-31

Checked legacy `.form-actions { display:flex; gap:9px; margin-top:18px }` against Native7's two layers: `childParams()` already gives `.form-actions` 18dp **top margin**, while `build(kind="actions")` added another 18dp **top padding**. When both were present, this made 36dp instead of the original 18dp. Commit `64a525e97b29fa516872e5e0c425d88e6e5bf24e` makes the internal top padding conditional: zero for a `.form-actions` node, preserving 18dp only for generic actions without that CSS class. The 9dp horizontal gap and equal-width flex weights are unchanged.

Verification still pending: inspect all nine rendered route structures (including helper-generated DOM), Arabic strings and ordering, modals, transitions, click handlers, build and tablet screenshots. This fix alone does not establish complete parity.

## Modal padding source comparison — 2026-10-31

Legacy `assets/style.css` `.modal` sets `padding:18px 15px calc(22px + env(safe-area-inset-bottom))` and tablet `@media(min-width:720px)` changes modal width, max-height, and border radius only. Native7 `OriginalPageStructure.kt` had changed tablet side padding to 22dp and bottom to 18dp, shifting all modal content. Commit `de798ab4072c179c2a35b6517d37dbb1f3e8a30e` restores 15dp side, 18dp top, 22dp bottom padding for both widths. **Caveat:** native safe-area inset handling and modal host placement are not verified; no Android build/device comparison performed.

**Nine-route coverage:** this is a shared modal geometry fix affecting any route that renders `.modal`, not evidence that all nine routes' dynamic dialogs, labels, actions, transitions, and data have been reproduced.

## Final-cascade record action strip — 2026-10-31

Compared legacy `assets/style.css` `.row-actions` selectors across cascade. Earlier declaration uses `gap:5px; margin-top:7px`, but later rules override with `margin-top:12px`, RTL, `flex-wrap:nowrap`, `gap:6px`, `overflow-x:auto`, hidden scrollbar and padding `3px 1px 5px`. Native7 instead split buttons into groups of three or five per row on tablets, changing layout and visual order. Commits `47a7c3b01799e6b7cff1d1bc0b9bfa486a9d5e4e` and `f7f0f2477d9aa83cee04d18b2046331744ac25a2` replace wrapping with a horizontal RTL scroll strip and correct the top margin to 12dp. The strip preserves original button order and 6dp gap. This affects any of the nine screens that render `.row-actions`; exact screenshot parity and Android compilation still require verification. This is an intentional reversal of the earlier tablet-specific wrapping behavior to honor the user's renewed explicit request for strict source parity.

## Correction: tablet action-strip media query — 2026-10-31

Re-inspected **all** `.row-actions` declarations in the original `assets/style.css`, not only the narrow-screen repair selector. The default selector at line ~200 is `display:flex; gap:5px; margin-top:7px`. The later `[data-invoice-row="repair"] .row-actions` selector at ~1372 adds `flex-wrap:nowrap; gap:6px; overflow-x:auto` for repair rows. Crucially, the `@media(min-width:720px)` block at ~1427 explicitly overrides **all** `.row-actions` to `flex-wrap:wrap`, and repair rows to `flex-wrap:wrap; overflow-x:visible`; the landscape block repeats the repair override. The earlier audit assertion that nowrap+12px was the universal final cascade was **incorrect**: `12px` belongs to `.qr-product-card .row-actions`, not the generic selector. Commit `79a0835c070dc715e8daf44f8b33d5f387110cee` corrects tablet rows to an RTL `SourceFlow` with 5dp gaps and restores default 7dp margin. The narrow-screen fallback still scrolls non-repair rows too, so phone-specific parity is pending; this commit prioritizes the Honor Pad tablet. Treat the earlier 2026-10-31 'Final-cascade record action strip' paragraph as superseded by this correction.
