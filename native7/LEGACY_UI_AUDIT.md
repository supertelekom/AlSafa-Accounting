# Legacy UI audit — source v3.9.359

Source of truth: `assets/main.js` in `AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE.zip`. These are verified original functions, not proposed labels.

## Simplified mode

- `renderFatherHome()`: `father-head`, `homeCurrencyAmountsHtml()`, four `father-main-card` actions, route switch to advanced mode.
- `renderFatherSale()`: form `father-sale-form`. Product search by name/REF/number/barcode, barcode entry, camera scan, lookup/add, selected product, `fatherCurrencyChoices('payCurrency','USD')`, `fatherPaymentChoices()`, submit.
- `renderFatherRepair()`: form `father-repair-form`. `quickRepairButtons()` is **settings-driven**, not merely the eight initial `QUICK_REPAIR_SERVICES` constants. Button can reference maintenance part, priceMode manual, stock count. Manual amount + required work description. Delivery radio and conditional customer fields. Currency defaults TRY.
- `renderFatherDeliveries()`: derives entries from repair records with statuses `بانتظار التسليم`, `جاهز للاستلام`, or including `انتظار التسليم`. Each entry must be a real repair record; no invented empty status.
- `renderShiftHandover()`: handover names, currency amounts, pending work groups and notes.

## Shared controls

- `fatherCurrencyChoices(name, selected)`: required positive numeric final amount, dropdown TRY/USD/SYP, original note about changing price after conversion, converted-value indicator. Currency conversion must use actual exchange rates; **never infer a rate**.
- `fatherPaymentChoices()`: two radio options: نقدي and شام كاش. Not a generic dropdown.
- `updateFatherCurrencyAmount()`: converts only with valid rates; on missing rate displays `سعر الصرف غير متوفر — حدّث الأسعار` and clears amount.

## Advanced mode

- `renderAdvancedHome()`: daily totals, low-stock summary, shortcuts, seven main actions, recent activity.
- Advanced sale is `sale-form`, **not** `father-sale`. Separate implementation needed.
- Management pages must use actual routes, fields and detail flows from the original before declaring them implemented.

## Current Native 7 status

Implemented as UI previews: mode chooser, simplified/advanced home, navigation, currency summary placeholders, quick sale, quick repair, handover, delivery placeholder, advanced sale skeleton, management entry grid.

Not implemented: exact visual equivalence, original logo, settings-driven repair configuration, full original forms and archive, barcode camera, validated currency conversion, persistence, data migration, printing, build/device tests.

**Never activate preview submission controls as real accounting operations.** No new database until source UI behavior and structure have been verified.

## Sale line verification — 2026-10-28

Checked exact `saleLine(item,index)` implementation from the original `assets/main.js`:

- Name required; model optional; quantity required integer with minimum 1; price required numeric minimum 0 with 0.01 step.
- Original line total is quantity × price, and may show a product-stock badge only when the line refers to a real inventory product.
- Original cost is hidden in the form; it is not a manually typed sales-line field.
- Native 7 now provides interactive manual-line add/remove and live exact-decimal multiplication. It does **not** yet apply original currency formatting or the source's full invoice totals and discounts.

Original `renderSaleForm()` label order confirmed: نوع البيع, التاريخ, الوقت, عملة البيع / القبض, طريقة الدفع, اسم العميل, رقم واتساب العميل, مدة الضمان, بحث سريع عن المنتج, حالة الدفع, قاعدة الخصم, خصم الفاتورة اليدوي, نقاط الولاء المستخدمة, نسبة الضريبة %, نوع القبض, المبلغ المقبوض الآن, المبالغ المقبوضة فعلياً, USD, TRY, SYP, ملاحظات.

**Do not add the unused external partners to Native 7.** User explicitly confirmed they have no operations in the legacy backup. This does not remove the three actual shop partners.



## Product form source transfer — 2026-10-29

Reference inspected: `AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE(6).zip`, SHA-256 `771c0e4f8c43ece9dc5a5872c99b5d5a6bc7302c1987d5b89cf265cf27d39852`.
Read `assets/main.js`, `assets/style.css`, and the additional `assets/styles.css` loaded by `index.html`.

- Rebuilt the new-product preview from `productForm()`: removed duplicate couple, description, specifications and funding blocks. External-partner fields remain excluded per the user's instruction above.
- Transferred `watchCustomerDetailsFields()` in its original 14-field sequence, and the exact `WATCH_DETAIL_DEFAULTS` option sequences. Warranty initially selects the original `0` / `حسب الفاتورة` option.
- Brand choices follow `WATCH_BRAND_DEFAULTS`; Casio-family controls appear when Casio is selected. No product lookup or brand persistence is implemented.
- Transferred `colorVariantsEditorHtml()` / `colorVariantRowHtml()` labels and interactive draft add/remove, including the two independent photo slots. These rows are previews and do not write stock.
- Transferred the exact advisor age/use/style checkbox choices and order. Product description, specifications and notes are multiline controls.
- Photo previews follow the legacy 116×84 layout beside camera/gallery/clear actions. Product photo actions, barcode, lookup and save are still inactive preview operations.
- Product action order: `إلغاء`, `⚡ حفظ + إضافة التالي`, `حفظ المنتج`.
- Bundled the original `assets/Tajawal-Medium.ttf` byte-for-byte (SHA-256 `9f18a40474af149e9896ae4655149b69af124b28b8ebe3e90e4aa19e92257e85`); use it in text, fields, dropdowns, checkboxes and navigation.
- Fixed an existing malformed duplicated tail after `repairFormPreview()`, and attached its existing action row once. The previous branch source failed Kotlin syntax parsing at that tail.

Validation: all three Kotlin source files pass tree-sitter Kotlin syntax parsing; compared product detail label order, unique sections, external-partner exclusion and font bytes with the reference. This is **not** Kotlin type checking, an Android build, or device visual verification. Android SDK/Gradle are unavailable in this environment. Product layout remains two-column; exact landscape modal/grid behavior and draft retention across recreation still need work. No Actions run requested; Native 6 untouched.
