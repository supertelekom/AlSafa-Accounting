# الصفا Native 7

المرحلة الحالية: **الهيكلة الخارجية فقط**. صفحات Android Native تقابل صفحات التطبيق القديم v3.9.359، بنفس العناوين والمسميات وترتيب الحقول والأزرار. لا نربط العمليات المحاسبية أثناء مراجعة الهيكل.

- `OriginalRoutes.kt`: مقابلة مسارات القديم بمسارات الصفحات الأصلية.
- `OriginalPageStructure.kt`: يبني الواجهات بعناصر Android الفعلية: TextView وEditText وSpinner وCheckBox وRadioButton وViewGroup.
- `assets/original-structure.json.gz`: وصف للهيكل مستخرج من المصدر، وليس محرك WebView أو HTML أو JavaScript.
- الخط وملف الشعار من أصول التطبيق القديم، دون إعادة رسم الشعار.
- الحفظ والقبض والاستعادة والطباعة والكاميرا والمسح في هذه الواجهات غير مفعلة. تغيير خيارات العرض والتنقل وإظهار الحقول لا ينفذ عمليات محاسبية.
- Native 6 لم يتغير. لا Firebase ولا صلاحية إنترنت ولا مزامنة.

## إعادة استخراج الهيكل للمطور

يتطلب Node وPython 3 فقط. مرر مجلد `assets` من السورس المرجعي، وليس بيانات متجر أو نسخة تشغيلية:

```bash
node tools/render_source_structure.cjs /path/to/legacy/assets /tmp/alsafa-rendered
python3 tools/derive_structure.py /tmp/alsafa-rendered app/src/main/assets/original-structure.json.gz
```

يعمل الاستخراج على بيانات افتراضية فارغة داخل بيئة تطوير معزولة، مع تعطيل الشبكة والمؤقتات. لا تُشحن ملفات JavaScript أو HTML أو CSS في التطبيق الجديد.

## التحقق

تم التحقق من ترتيب مسميات الأزرار ومساراتها مقابل المصدر وفحص صياغة Kotlin. لم يُجر بناء APK أو اختبار على التابلت في هذه البيئة؛ المقارنة البصرية الفعلية على Honor Pad X8b ما زالت مطلوبة قبل إعلان التطابق النهائي. البناء محلياً عبر Android Studio، دون GitHub Actions.

## ملفات البناء المحدثة — 29 أكتوبر 2026

| المكوّن | الإصدار المثبّت |
| --- | --- |
| Android Gradle Plugin | 9.4.1 المستقر |
| Gradle وWrapper | 9.8.2 |
| Kotlin compiler/plugin runtime | 2.4.21 عبر Kotlin المدمج في AGP |
| compileSdk / targetSdk | 37 / 37 |
| SDK Build Tools | يختار AGP نسخته الافتراضية المتوافقة (36.0.0 في AGP 9.4) |
| Java bytecode | 17؛ استخدم Gradle JDK 17 أو أحدث، مثل JDK المدمج في Android Studio |
| أقل Android للتشغيل | API 26 كما كان |

افتح **مجلد `native7` نفسه** في إصدار Android Studio مستقر يدعم AGP 9.4، مثل Rabbit 1 (2026.2.1) أو أحدث. من SDK Manager ثبّت Android SDK Platform 37 وBuild Tools 36.0.0، واضبط Gradle JDK على Embedded JDK (17 أو أحدث). ينشئ Android Studio ملف `local.properties` الخاص بمسار SDK على جهازك؛ لا تنسخ مسار SDK من جهاز آخر.

تتوفر ملفات `gradlew` و`gradlew.bat` و`gradle/wrapper/gradle-wrapper.jar` وملف خصائص Wrapper. نُقلت ملفات Wrapper من مستودع Gradle الرسمي، وتطابق بصمة JAR البصمة المنشورة رسمياً. تنزيل التوزيعة محمي أيضاً ببصمة SHA-256.

للبناء محلياً من طرفية Windows داخل `native7`:

```bat
gradlew.bat :app:assembleDebug
```

المخرج المتوقع **بعد نجاح البناء**: `app/build/outputs/apk/debug/app-debug.apk`.

أول Sync يحتاج اتصالاً لتنزيل Gradle وAGP وKotlin وحزم SDK. يمكن تنزيل المتطلبات باستخدام اتصال الكمبيوتر/VPN المعتاد؛ لا يحتاج التطبيق نفسه إلى الإنترنت.

### حدود التحقق الحالي

فُحصت الملفات وWrapper وبصمته وXML وصياغة Kotlin. بدأ Wrapper التنفيذ، ثم فشل تنزيل Gradle بسبب `Network is unreachable` في بيئة العمل. لم يصل الفحص إلى حل الاعتماديات أو تجميع Android، ولذلك لا توجد نتيجة بناء ناجحة أو APK مختبر حتى الآن.

هذه أحدث الإصدارات المستقرة التي تحققنا من نشرها، وليست إصدارات Alpha/Beta/RC. توضح JetBrains أن نطاق الاختبار الكامل لـKotlin 2.4.21 يصل إلى Gradle 9.7.0 وAGP 9.3.1، وأن استخدام الأحدث ممكن مع احتمال تحذيرات التوافق؛ الإصداران الأحدث المثبتان هنا يحتاجان تحقق Sync/Build فعلياً، ولا نُخفي تحذيراتهما.

مراجع الإصدارات:

- https://developer.android.com/reference/tools/gradle-api
- https://developer.android.com/build/releases/agp-9-4-0-release-notes
- https://services.gradle.org/versions/current
- https://kotlinlang.org/docs/releases.html
- https://kotlinlang.org/docs/gradle-configure-project.html
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/about/versions/17/setup-sdk
