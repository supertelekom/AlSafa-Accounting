# نشر خادم اختبار Native 4

هذا المسار خاص حصراً بمشروع Firebase التجريبي المستقل `alsafa-native`، وقد تم تثبيت هذا المشروع في `.firebaserc` لتقليل احتمال النشر إلى مشروع آخر بالخطأ.

الدالة المنشورة هي `submitCommand` في `europe-west1`. الخادم يتطلب Firebase Authentication ويقبل فقط storeId تجريبياً يبدأ بـ `test-` أو ينتهي بـ `-test`. المسار الحالي يدعم TEST وPRODUCT_CREATE وSALE؛ SALE ينفذ ذرياً مع receipt/revision/changeLog وحركات المخزون والنقد والتدقيق.

## حدود الأمان

- لا تستخدم مشروع Firebase الإنتاجي للصفا في هذه المرحلة.
- تطبيق Android التجريبي يستخدم الحزمة `com.alsafa.native4.test` والمشروع `alsafa-native`.
- المصادقة الحالية للاختبار Email/Password، وليست Anonymous Auth.
- لا تُحفظ كلمة مرور مستخدم الاختبار أو service-account JSON أو Firebase CLI token في GitHub.
- `firebase.json` ينشر codebase واحداً فقط مصدره `server`.
- قواعد Firestore ليست ضمن أمر نشر Functions أدناه، لذلك لا يتم استبدال القواعد بهذا الأمر.

## المتطلبات في مشروع alsafa-native

1. Firebase Authentication مع Email/Password مفعّل ومستخدم اختبار موجود.
2. Cloud Firestore مفعّل.
3. Cloud Functions/Blaze requirements مكتملة حسب Firebase.
4. الطرف الذي ينفذ النشر مصادق على Firebase CLI ولديه صلاحية نشر Functions للمشروع `alsafa-native`.

## فحص قبل النشر

من مجلد `native4/AlSafaNative4-Phase1`:

```bash
firebase use
```

يجب أن يعرض `alsafa-native`. إذا ظهر أي project id آخر، توقف ولا تنشر.

## أمر النشر المقيد

```bash
firebase deploy --only functions:native4-command-server --project alsafa-native
```

بعد نجاح النشر يجب التأكد من ظهور `submitCommand` في `europe-west1` داخل مشروع `alsafa-native` فقط. بعدها يجرى أول اختبار حي من APK 4.0.10 باستخدام `test-native4`: تسجيل الدخول -> إنشاء PRODUCT_CREATE تجريبي إذا لزم -> SALE محلي -> Outbox -> إرسال يدوي -> revision -> CONFIRMED. لا يتم ربط أي متجر إنتاجي في هذه المرحلة.
