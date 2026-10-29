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
