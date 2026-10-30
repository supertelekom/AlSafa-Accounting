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
