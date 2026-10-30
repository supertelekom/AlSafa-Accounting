# Native 7 — exact UI parity checklist (legacy tablet 3.9.359)

Source of truth: `assets/main.js`, `assets/style.css`, `assets/styles.css` in the original tablet 3.9.359 source archive. This is a comparison baseline, **not a claim of completion**.

## Navigation / home
- `renderDashboard`: «كيف تريد أن تبدأ؟»; «الوضع المبسط»; «الوضع الكامل»; original cards, icons, RTL order and spacing.
- Reproduce original page route behavior, not only page titles.

## Sales
- `renderSaleForm`: «فاتورة مبيعات», «قائمة الفواتير», «بيانات الفاتورة», «نوع البيع *», «بيع من المحل», «بيع أونلاين», «التاريخ *», «الوقت *»; original field sequence, required validation and actions.
- `renderSales`: «المبيعات والفواتير», «＋ فاتورة»; original invoice search/date filters, status, item details, actions, document sharing/printing.
- Compare the simplified sale screen independently; it is a different flow.

## Products
- `renderProducts`: «المنتجات والمخزون», «↻ تحديث», «⬆ استيراد آمن», «＋ منتج».
- Search placeholder: «ابحث باسم المنتج أو الباركود أو REF / Model...».
- Match product cards, variants, thumbnails, zoom, barcode, edit and stock movement dialogs.

## Repairs (verified exact original button set)
- `renderRepairForm`: «طلب صيانة»; «الطلبات»; «بيانات العميل»; «التاريخ *»; «وقت الاستلام *»; «عملة الصيانة / القبض *»; «طريقة الدفع الأولى *»; «اسم العميل *»; «رقم واتساب العميل»; «حالة الطلب»; «الماركة / نوع الساعة *»; «الموديل أو الرقم»; «العطل أو المطلوب *»; «نتيجة الفحص أو تحديث العميل»; «نسبة الضريبة %»; «المبلغ المقبوض الآن»; «عملة الصيانة والتحصيل»; «حالة الدفع»; «ملاحظات»; «إلغاء»; «حفظ طلب الصيانة».
- `renderRepairs`: «قسم الصيانة»; «＋ طلب»; original date/search filters.
- Each repair row: clickable photo thumbnail, «التفاصيل», «تعديل», «مرتجع/استبدال», «صورة الساعة» (when photo), «تحديث العطل» (when phone and not delivered), «تحديث الحالة» (when not delivered), «فاتورة PDF» and «🖨️ طباعة» (when paid), «واتساب أعمال» and «💬 شكر» (when paid and phone), «جاهزة» (when applicable), «تم التسليم» (when not delivered), «حذف».
- Original row also displays repair invoice number, customer, watch, issue, date/time, cost, settlement, status, and payment status.
- Native 7 currently exposes only a subset of these actions; **not matched**.

## Cash, expenses, settings
- `renderVaults`: «الخزائن والأرصدة», «إقفال اليوم», «＋ حركة مالية», balance cards and daily closing status.
- `renderExpenses`: «المصاريف», «＋ مصروف», original item row actions.
- `renderSettings`: «الإعدادات», «بيانات المتجر», «اسم المتجر», «عملة التطبيق (الوضع الكامل)», backup/settings actions.

## Non-negotiable visual checks
- Compare original CSS declarations for each selector: Tajawal font, RTL alignment, colors, spacing, field heights, buttons, icons, cards, modal sizing, list rows, responsive tablet breakpoints and selected/disabled/error states.
- Compare every modal, conditional button, confirmation, loading state, validation and navigation transition.
- An element counts as matched only when its **behavior and data effects** also match.
- Do not create fake buttons or placeholder screens to inflate completion.
- Run screen-by-screen screenshot comparison on Honor Pad X8b; the current audit does not establish pixel parity.

## Known gaps confirmed from source
- Native 7 repairs list lacks original details/edit/photo/status/problem/PDF/print/WhatsApp workflows.
- Native 7 restore and original restore do not have identical semantics.
- Native 7 funding and sale cost mapping has not been validated against the original dataset.
- Build, runtime and visual parity have **not** been certified.


## Persistent implementation log — 2026-10-30

**Do not repeat completed code edits without checking current HEAD.** This file is the durable handoff across conversations; read it before every new UI edit. It records code edits, not successful Android builds or pixel parity.

### Already edited in `MainActivity.kt`
- Repair list «التفاصيل»: dialog with saved fields and correct «طلب صيانة» title; line-separated fields.
- Repair «صورة الساعة»: opens stored URI in an image dialog; **one** button and **one** `photoPath` declaration per repair row (duplicate removed at commit `15af5326c1752fe0dac5fa2e5981b78c50850450`).
- Repair «تحديث العطل»: edit `form_texts[6]`, save using `RepairStore.save(..., id)`; only for undelivered requests.
- Repair «نتيجة الفحص أو تحديث العميل»: edit `form_texts[7]` independently; only for undelivered requests.
- Repair «تحديث الحالة»: selection among قيد الصيانة / بانتظار القطعة / جاهز للاستلام, saved by ID.
- Repair saving navigates to repair list after saving.
- Repair «تسليم» is deliberately blocked until payment/cashbox flow is implemented; **not parity-complete**.
- Shared form text inputs: focus border changes to champagne accent; unverified visual parity.

### Important known gaps — NOT implemented
- Original full repair edit, paid/delivery workflow, parts/costs, refund/exchange, PDF/print, WhatsApp, thank-you, deletion and status-dependent action order.
- Exact CSS-to-native mapping for every page and all tablet viewport states.
- All other modules' complete action inventories and their event/state transitions.
- Original backup restore and funding lot parity; do not use production data for tests.
- No confirmed successful Android Studio build or device screenshot comparison after these edits.

### Mandatory next workflow
1. Read this log and latest `MainActivity.kt` **before** editing.
2. Inspect the matching original legacy JS/HTML/CSS source for one complete screen, including conditional buttons and event handlers.
3. Implement all supported actions for that screen, including persistence and failure behavior, not decorative controls.
4. Check duplicate local variable names, repeated buttons, field indices and Kotlin syntax; run build when available.
5. Update this log with precise commits and remaining gaps. Never mark a screen 10/10 without screenshot and functional tests.
