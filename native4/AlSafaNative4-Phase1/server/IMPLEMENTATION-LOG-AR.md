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

تمت إضافة تحقق صارم لـ `SALE`:
- عنصر واحد على الأقل وحد أقصى 100 عنصر.
- `productId` إلزامي وغير مكرر داخل الفاتورة.
- الكمية عدد صحيح أكبر من صفر.
- السعر رقم غير سالب.
- العملات الحالية: `USD`, `SYP`, `TRY`.
- طرق الدفع الحالية: `CASH`, `SHAM_CASH`, `LATER`.
- يجب أن يساوي `total` مجموع `quantity * unitPrice`.

### تنفيذ البيع على الخادم

داخل Firestore Transaction واحدة:
1. فحص `commandReceipts` أولاً؛ إذا كان `operationId` منفذاً سابقاً يرجع CONFIRMED بدون إعادة البيع.
2. منع إنشاء فاتورة فوق `saleId` موجود.
3. قراءة كل منتج والتحقق من وجوده وكفاية المخزون.
4. إنشاء سجل البيع.
5. إنقاص كمية كل منتج.
6. إنشاء `inventoryMovements` سالبة مرتبطة بنفس العملية.
7. عند الدفع الفوري (`CASH` أو `SHAM_CASH`) إنشاء `cashMovements`; البيع `LATER` لا يضيف حركة صندوق.
8. إنشاء `auditLog`.
9. إنشاء `commandReceipt` و`changeLog` وزيادة Revision.

أي فشل داخل هذه الخطوات يلغي Transaction كاملة، فلا يجب أن توجد فاتورة بلا خصم مخزون أو خصم مخزون بلا فاتورة.

### أخطاء محمية

- `PRODUCT_NOT_FOUND`
- `INSUFFICIENT_STOCK`
- `SALE_EXISTS`
- `INVALID_SALE_PAYLOAD`
- `TEST_STORE_REQUIRED`

### الاختبارات المضافة

- قبول SALE صحيح.
- رفض إجمالي لا يطابق العناصر.
- رفض كمية صفر.
- رفض عملة غير مدعومة.
- رفض تكرار المنتج داخل نفس الفاتورة.
- اختبار بناء sale document وتطبيع المعرفات.
- اختبار رفض SALE خارج test-store boundary.

## Commits لهذه المرحلة

- `3bfbc823` — validate atomic SALE command payload
- `62b9f7cd` — add test-only SALE document handler
- `d49bce2f` — process SALE atomically in test command endpoint
- `11d2fd56` — test SALE command validation and safety rules
- `57d7315f` — cover SALE handler and live-store boundary

## الخطوة التالية

1. تشغيل/تأكيد مجموعة اختبارات الخادم في بيئة Node 20.
2. إضافة اختبار تكامل للـ Command Endpoint مع Firestore Emulator للتحقق فعلياً من atomicity وidempotency ونقص المخزون.
3. بعد نجاحه فقط: ربط Outbox في Android بالـ endpoint التجريبي.
4. اختبار إعادة إرسال نفس `operationId` وانقطاع الاتصال والاستكمال دون تكرار البيع.

## قاعدة العمل

لا يتم توصيل هذه الطبقة ببيانات الصفا الحقيقية قبل اجتياز اختبارات التكرار والانقطاع والمخزون من جهازين. كل مرحلة لاحقة يجب إضافتها إلى هذا السجل مع commits المرتبطة بها.
