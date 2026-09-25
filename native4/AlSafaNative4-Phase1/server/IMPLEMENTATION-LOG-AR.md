# سجل تنفيذ AlSafa Native 4 — طبقة الأوامر

هذا الملف سجل دائم للمراجعة. لا يعتمد المشروع على المحادثة وحدها لمعرفة ما تم تنفيذه.

## حدود الأمان الحالية

- الفرع: `native4-sync-core`.
- المسار: `native4/AlSafaNative4-Phase1`.
- جميع معالجات الكتابة الحالية محصورة بمتاجر اختبار اسمها يبدأ بـ `test-` أو ينتهي بـ `-test`.
- لا يوجد تفعيل لكتابة بيانات متجر الصفا الحقيقية.
- وصول Android المباشر إلى `native4Stores` ممنوع بقواعد Firestore الحالية؛ الكتابة مصممة للمرور عبر Command Endpoint.

## SALE server safety gate

Command envelope موحد مع operationId UUID وstoreId وsourceDeviceId وkind وrecordId وpayload. يوجد commandReceipts لمنع التنفيذ مرتين وRevision متزايد وchangeLog. SALE ينفذ داخل Firestore Transaction واحدة: فحص receipt، منع استبدال saleId، فحص المنتج والمخزون، إنشاء البيع، إنقاص المخزون، inventoryMovements، cashMovements للدفع الفوري، auditLog، receipt، changeLog وRevision. أي فشل يلغي Transaction كاملة.

### نتائج الخادم المؤكدة

- Unit tests: 20/20 ناجح، صفر فشل.
- Firestore Emulator integration: 5/5 ناجح، صفر فشل.
- إعادة operationId نفسها لا تخصم المخزون مرتين.
- نقص المخزون والمنتج المفقود يؤديان إلى rollback كامل.
- LATER لا ينشئ cashMovement.
- بيعان متزامنان على آخر قطعة: ينجح واحد فقط.

## Commits الأساسية

- `3bfbc823` — validate atomic SALE command payload
- `62b9f7cd` — add test-only SALE document handler
- `d49bce2f` — process SALE atomically in test command endpoint
- `11d2fd56` — test SALE command validation and safety rules
- `57d7315f` — cover SALE handler and live-store boundary
- `efd91a86` — add permanent Arabic implementation review log
- `938267fb` — add isolated command processor for integration testing
- `4052f764` — run SALE integration suite against Firestore emulator in CI
- `6aaf3de3` — configure isolated Firestore emulator for integration CI

## 2026-09-25 — Android Outbox المحلي

تم تنفيذ بوابة Outbox المحلية قبل تشغيل الشبكة:

1. ترقية SQLite إلى schema version 7 وإضافة `source_device_id` و`confirmed_revision` إلى outbox.
2. عملية البيع المحلية وكتابة أمر SALE إلى outbox أصبحتا داخل SQLite Transaction نفسها. إذا فشل حفظ command فلا تعتمد الفاتورة ولا خصم المخزون.
3. `operationId` UUID ينشأ مرة واحدة ويخزن دائماً مع payload وrecordId وsourceDeviceId؛ إعادة المحاولة لا تنشئ operationId جديداً.
4. الحالات المدعومة: `PENDING`, `SENDING`, `FAILED_RETRYABLE`, `FAILED_PERMANENT`, `CONFIRMED`.
5. عند فتح التطبيق، أي سجل بقي `SENDING` بسبب إغلاق/قتل التطبيق يتحول إلى `FAILED_RETRYABLE` ليعاد بنفس operationId.
6. CONFIRMED لا يحذف من outbox؛ يحتفظ بـ confirmed revision للمراجعة.
7. واجهة الاختبار تعرض عدد العمليات المنتظرة والحالة وعدد المحاولات وoperationId.
8. لم يتم تشغيل أي اتصال شبكة أو Firebase من Android في هذه المرحلة.

### Commits Android Outbox

- `27093f8e` — make SALE outbox durable and retry-safe
- `d9e93602` — allow exact persisted command reconstruction
- `16e3e24f` — recover interrupted outbox and expose queue state
- `ec972b90` — show durable outbox status in test UI

### تحقق البناء

GitHub Actions run `36175937164` على commit `ec972b90` اكتمل بنجاح. Job `debug-apk` نجح، بما فيه Build separate Native 4 test package ورفع artifact. هذا يثبت أن تغييرات schema/Java/UI الحالية تُبنى معاً بنجاح.

## البوابة التالية — إرسال Outbox إلى endpoint التجريبي

يسمح الآن بالبدء في Sender، لكن بالشروط التالية فقط:

1. `storeId` ثابت وصريح من نوع test-store؛ ممنوع production store.
2. Sender يقرأ command المخزن كما هو؛ ممنوع إعادة إنشاء SALE أو operationId أثناء الإرسال.
3. قبل المحاولة: PENDING/FAILED_RETRYABLE -> SENDING.
4. نجاح endpoint: SENDING -> CONFIRMED مع revision.
5. خطأ شبكة/timeout/5xx: -> FAILED_RETRYABLE دون تغيير operationId.
6. أخطاء validation الدائمة: -> FAILED_PERMANENT مع إبقاء السجل للمراجعة.
7. إذا نجح الخادم ثم انقطع الرد، إعادة نفس command آمنة بسبب commandReceipts/idempotency.
8. لا يتم تفعيل استقبال تغييرات جهاز آخر قبل نجاح اختبارات Sender والانقطاع وإعادة التشغيل.

## قاعدة العمل

لا يتم توصيل هذه الطبقة ببيانات الصفا الحقيقية قبل اجتياز اختبارات Outbox والانقطاع والاستئناف من جهازين. لا استبدال snapshots كاملة، ولا حذف تلقائي لبيانات محلية سليمة بسبب نسخة بعيدة أصغر. كل مرحلة لاحقة تضاف إلى هذا السجل مع commits والاختبارات المرتبطة بها.
