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
- Firestore Emulator integration ناجح، بما فيه lost-response retry.
- إعادة operationId نفسها لا تخصم المخزون مرتين.
- نقص المخزون والمنتج المفقود يؤديان إلى rollback كامل.
- LATER لا ينشئ cashMovement.
- بيعان متزامنان على آخر قطعة: ينجح واحد فقط.

## 2026-09-25 — Android Outbox المحلي

تم تنفيذ بوابة Outbox المحلية قبل تشغيل الشبكة:

1. SQLite schema 7 مع source_device_id وconfirmed_revision.
2. البيع المحلي وSALE command في SQLite Transaction نفسها.
3. operationId UUID ينشأ مرة واحدة ويبقى ثابتاً في retries.
4. الحالات: PENDING, SENDING, FAILED_RETRYABLE, FAILED_PERMANENT, CONFIRMED.
5. SENDING المتروك بعد قتل التطبيق يستعاد FAILED_RETRYABLE.
6. CONFIRMED يبقى محفوظاً مع revision للمراجعة.
7. الواجهة تعرض queue state وoperationId.

## 2026-09-25 — Sender وCallable gate

- أضيف OutboxSender؛ لا يعيد بناء SALE ولا operationId.
- أضيفت JVM policy tests لمتاجر الاختبار والrevision والهوية المحفوظة.
- أضيف اختبار Firestore Emulator لحالة: الخادم commit ثم يضيع الرد ثم يعيد العميل نفس operationId؛ النتيجة duplicate=true ونفس revision ولا يتكرر أي side effect.
- أضيف FirebaseCallableTransport باستخدام Firebase Auth + Functions في europe-west1، مع رفض storeId غير تجريبي.
- أضيفت INTERNET permission مع usesCleartextTraffic=false.
- لم يتم ربط Sender تلقائياً في MainActivity بعد، ولم يتم تفعيل Firebase production config.

### نتائج CI المؤكدة

- commit `648c992d`: JVM safety tests + APK build + artifact upload = success.
- commit `153c0cf`: Verify Native 4 command server = success؛ JVM safety tests = success؛ APK build = success؛ artifact upload = success.
- بهذا تم اجتياز compile/build gate لطبقة Callable مع صلاحية الإنترنت دون تشغيل إرسال حي.

## البوابة التالية — Firebase test configuration + live test-store round trip

لا يبدأ الإرسال الحي إلا بعد إعداد Firebase تجريبي/معزول مناسب لحزمة `com.alsafa.native4.test` ومستخدم Auth تجريبي. الاختبار المطلوب: SALE محلي -> Outbox -> authenticated Callable -> test-store -> revision -> CONFIRMED. بعدها يختبر timeout/restart على Android نفسه، ثم فقط يبدأ changeLog/checkpoint للاستقبال بين جهازين.

## قاعدة العمل

لا يتم توصيل هذه الطبقة ببيانات الصفا الحقيقية قبل اجتياز اختبارات Outbox والانقطاع والاستئناف من جهازين. لا استبدال snapshots كاملة، ولا حذف تلقائي لبيانات محلية سليمة بسبب نسخة بعيدة أصغر. كل مرحلة لاحقة تضاف إلى هذا السجل مع commits والاختبارات المرتبطة بها.
