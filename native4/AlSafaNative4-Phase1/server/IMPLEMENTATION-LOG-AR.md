# سجل تنفيذ AlSafa Native 4 — طبقة الأوامر

هذا الملف سجل دائم للمراجعة. لا يعتمد المشروع على المحادثة وحدها لمعرفة ما تم تنفيذه.

## حدود الأمان الحالية

- الفرع: `native4-sync-core`.
- المسار: `native4/AlSafaNative4-Phase1`.
- جميع معالجات الكتابة الحالية محصورة بمتاجر اختبار اسمها يبدأ بـ `test-` أو ينتهي بـ `-test`.
- لا يوجد تفعيل لكتابة بيانات متجر الصفا الحقيقية.
- وصول Android المباشر إلى `native4Stores` ممنوع بقواعد Firestore الحالية؛ الكتابة مصممة للمرور عبر Command Endpoint.

## ما كان موجوداً قبل تفعيل SALE

- Command envelope موحد مع `operationId` UUID و`storeId` و`sourceDeviceId` و`kind` و`recordId` و`payload`.
- `commandReceipts/{operationId}` لمنع تنفيذ الأمر نفسه مرتين.
- Revision متزايد و`changeLog`.
- `PRODUCT_CREATE` داخل Transaction مع منع استبدال منتج موجود.

## 2026-09-25 — تفعيل SALE التجريبي

### تغييرات البروتوكول

تمت إضافة تحقق صارم لـ `SALE`: عنصر واحد على الأقل وحد أقصى 100، productId إلزامي وغير مكرر، كمية صحيحة موجبة، سعر غير سالب، العملات USD/SYP/TRY، طرق الدفع CASH/SHAM_CASH/LATER، وتطابق total مع مجموع العناصر.

### تنفيذ البيع على الخادم

داخل Firestore Transaction واحدة: فحص receipt لمنع التكرار، منع استبدال saleId، فحص المنتجات والمخزون، إنشاء البيع، إنقاص المخزون، inventoryMovements، cashMovements للدفع الفوري، auditLog، commandReceipt، changeLog وزيادة Revision. أي فشل يلغي Transaction كاملة.

### الاختبارات

تم تشغيل GitHub Actions بعد هذه التغييرات ونجحت 20/20 اختبارات بدون فشل. كما نجح Build للـ APK التجريبي.

## Commits لهذه المرحلة

- `3bfbc823` — validate atomic SALE command payload
- `62b9f7cd` — add test-only SALE document handler
- `d49bce2f` — process SALE atomically in test command endpoint
- `11d2fd56` — test SALE command validation and safety rules
- `57d7315f` — cover SALE handler and live-store boundary
- `efd91a86` — add permanent Arabic implementation review log
- `938267fb` — add isolated command processor for integration testing

## 2026-09-25 — بدء اختبار التكامل

تم إنشاء `command-processor.js` لفصل منطق Transaction عن Firebase Callable. الهدف هو أن يختبر Firestore Emulator نفس منطق البيع الفعلي مباشرة، وليس نسخة مبسطة منه. ما زال الملف محصوراً بحدود test-store ولم يتم ربطه ببيانات الإنتاج.

الاختبارات المطلوبة لهذه المرحلة قبل Android Outbox:
1. SALE ناجح يخفض المخزون مرة واحدة ويكتب البيع والحركات وreceipt وrevision.
2. إعادة نفس operationId ترجع duplicate ولا تخفض المخزون مرة ثانية ولا تنشئ حركة صندوق ثانية.
3. مخزون غير كافٍ: لا تُحفظ فاتورة ولا حركة صندوق ولا حركة مخزون ولا receipt ولا revision جديد.
4. منتج مفقود: rollback كامل.
5. دفع LATER: البيع والمخزون يسجلان بدون حركة صندوق.
6. عمليتان متزامنتان على آخر قطعة: واحدة فقط يمكن أن تنجح.

## الخطوة التالية

إضافة Firestore Emulator إلى CI وكتابة integration test للحالات الست السابقة، ثم تشغيلها في GitHub Actions. بعد نجاحها فقط يتم ربط Android Outbox بالـ endpoint التجريبي.

## قاعدة العمل

لا يتم توصيل هذه الطبقة ببيانات الصفا الحقيقية قبل اجتياز اختبارات التكرار والانقطاع والمخزون من جهازين. كل مرحلة لاحقة يجب إضافتها إلى هذا السجل مع commits المرتبطة بها.
