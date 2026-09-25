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

تمت إضافة تحقق صارم لـ `SALE`: عنصر واحد على الأقل وحد أقصى 100، productId إلزامي وغير مكرر، كمية صحيحة موجبة، سعر غير سالب، العملات USD/SYP/TRY، طرق الدفع CASH/SHAM_CASH/LATER، وتطابق total مع مجموع العناصر.

داخل Firestore Transaction واحدة: فحص receipt لمنع التكرار، منع استبدال saleId، فحص المنتجات والمخزون، إنشاء البيع، إنقاص المخزون، inventoryMovements، cashMovements للدفع الفوري، auditLog، commandReceipt، changeLog وزيادة Revision. أي فشل يلغي Transaction كاملة.

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

## 2026-09-25 — نتيجة بوابة التكامل

تم تشغيل GitHub Actions على commit `6aaf3de3` ونجح workflow `Verify Native 4 command server` بالكامل.

### Unit tests

- 20 ناجح.
- 0 فشل.
- اختبار integration داخل التشغيل العادي كان Skip متعمداً لأنه يحتاج Emulator.

### Firestore Emulator integration tests

نجحت 5/5 حالات فعلية، صفر فشل:
1. SALE ناجح + إعادة نفس operationId: المخزون ينخفض مرة واحدة فقط، وتوجد فاتورة واحدة وحركة مخزون واحدة وحركة صندوق واحدة وaudit وreceipt وchangeLog واحد.
2. نقص المخزون: rollback كامل ولا تبقى أي كتابة جانبية.
3. منتج مفقود: rollback كامل.
4. دفع LATER: البيع وخصم المخزون ينجحان بدون cashMovement.
5. عمليتا بيع متزامنتان على آخر قطعة: تنجح واحدة فقط، الثانية تفشل، والمخزون النهائي صفر بدون بيع مزدوج.

كما نجح workflow بناء APK التجريبي على نفس commit.

### ملاحظات CI غير مانعة

ظهر تحذير بأن بعض أدوات GitHub Actions تنتقل من Node 20، وتحذيرات npm dependencies متوسطة. هذه لم تفشل الاختبارات ولا تغيّر نتيجة بوابة منطق البيع، لكنها يجب أن تعالج لاحقاً كصيانة dependencies/CI منفصلة حتى لا نخلطها مع منطق المزامنة.

## البوابة التالية — Android Outbox التجريبي

بعد نجاح Transaction وIdempotency في Emulator أصبح مسموحاً بدء ربط Android Outbox، لكن فقط مع test-store والـendpoint التجريبي. المطلوب قبل أي بيانات حقيقية:
1. حفظ command محلياً قبل محاولة الإرسال.
2. operationId ثابت لا يتغير عند Retry أو إعادة تشغيل التطبيق.
3. حالات محلية واضحة: PENDING / SENDING / CONFIRMED / FAILED_RETRYABLE / FAILED_PERMANENT.
4. عدم حذف command المؤكد مباشرة؛ الاحتفاظ بإيصال/مرجع للمراجعة.
5. Retry بعد انقطاع الشبكة وإعادة تشغيل التطبيق دون إنشاء SALE جديد.
6. اختبار جهازين على test-store قبل أي تفعيل إنتاجي.

## قاعدة العمل

لا يتم توصيل هذه الطبقة ببيانات الصفا الحقيقية قبل اجتياز اختبارات Outbox والانقطاع والاستئناف من جهازين. كل مرحلة لاحقة يجب إضافتها إلى هذا السجل مع commits المرتبطة بها.
