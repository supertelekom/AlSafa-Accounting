# AlSafa Accounting

نسخة محفوظة كاملة من تطبيق محاسبة **الصفا للساعات**.

## النسخة الحالية

- الإصدار: `v3.9.77-SAFE-CLOUD-RECOVERY`
- النوع: نسخة استعادة سحابية آمنة.
- مخصصة لاستعادة البيانات من Firebase مع إيقاف الرفع لحماية البيانات من الكتابة الفارغة.
- ليست النسخة التشغيلية اليومية بعد اكتمال الاستعادة.

## الملفات الكاملة

بسبب قيود قناة الرفع، حُفظ كل ملف في 12 جزءًا داخل:

- `releases/v3.9.77/source-parts/`
- `releases/v3.9.77/apk-parts/`

### إعادة تجميع السورس على Android/Termux أو Linux/macOS

```bash
cat releases/v3.9.77/source-parts/*.part-* > AlSafaAccounting-v3.9.77-SAFE-CLOUD-RECOVERY-SOURCE.zip
```

### إعادة تجميع APK

```bash
cat releases/v3.9.77/apk-parts/*.part-* > AlSafaAccounting-v3.9.77-SAFE-CLOUD-RECOVERY-SIGNED.apk
```

بعد التجميع شغّل:

```bash
sha256sum -c releases/v3.9.77/SHA256SUMS.txt
```

## محتوى السورس

الحزمة المصدرية هي مشروع APK مفكوك وقابل لإعادة البناء، وليست مشروع Android Studio تقليديًا بـ Gradle. تتضمن واجهة التطبيق، منطق المحاسبة، موارد Android، الطبقة الأصلية، ملفات ML Kit، الخطوط، ومكتبات PDF وQR والباركود.

## الأمان

لا يحتوي المستودع على مفتاح توقيع AlSafa الدائم أو كلمات مروره أو ملف `signing-info.txt`. تبقى هذه الملفات محفوظة بشكل منفصل وآمن.
