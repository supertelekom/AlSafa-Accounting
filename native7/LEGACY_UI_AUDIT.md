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

Not implemented: exact visual equivalence, original logo and Tajawal asset bundling, settings-driven repair configuration, full original forms and archive, barcode camera, validated currency conversion, persistence, data migration, printing, build/device tests.

**Never activate preview submission controls as real accounting operations.** No new database until source UI behavior and structure have been verified.

## Sale line verification — 2026-10-28

Checked exact `saleLine(item,index)` implementation from the original `assets/main.js`:

- Name required; model optional; quantity required integer with minimum 1; price required numeric minimum 0 with 0.01 step.
- Original line total is quantity × price, and may show a product-stock badge only when the line refers to a real inventory product.
- Original cost is hidden in the form; it is not a manually typed sales-line field.
- Native 7 now provides interactive manual-line add/remove and live exact-decimal multiplication. It does **not** yet apply original currency formatting or the source's full invoice totals and discounts.

Original `renderSaleForm()` label order confirmed: نوع البيع, التاريخ, الوقت, عملة البيع / القبض, طريقة الدفع, اسم العميل, رقم واتساب العميل, مدة الضمان, بحث سريع عن المنتج, حالة الدفع, قاعدة الخصم, خصم الفاتورة اليدوي, نقاط الولاء المستخدمة, نسبة الضريبة %, نوع القبض, المبلغ المقبوض الآن, المبالغ المقبوضة فعلياً, USD, TRY, SYP, ملاحظات.

**Do not add the unused external partners to Native 7.** User explicitly confirmed they have no operations in the legacy backup. This does not remove the three actual shop partners.
