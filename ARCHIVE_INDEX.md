# فهرس إصدارات AlSafa Accounting

تم حفظ آخر 15 إصدارًا رقميًا من v3.9.63 إلى v3.9.77 بترتيب تصاعدي.

| الإصدار | الاسم/الغرض | الملفات | الحالة |
|---|---|---:|---|
| v3.9.63 | CASIO-ALL-REGIONS-DISCOVERY | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.64 | CASIO-REF-PRIORITY-FIX | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.65 | CASIO-UNIVERSAL-FAST-LOOKUP | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.66 | CASIO-WEB-SEARCH-DISCOVERY | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.67 | CASIO-DIRECT-OFFICIAL-RESTORE | APK + SOURCE | APK أصلي + سورس مستخرج من APK |
| v3.9.68 | MULTI-SOURCE-LOOKUP | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.69 | CASIO-3.9.62-RESTORE | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.70 | CASIO-PDF-COVERAGE | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.71 | CASIO-REF-V-FIX | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.72 | CASIO-MALAYSIA-CHINA-FALLBACK | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.73 | CASIO-FAMILY-SPECS-FALLBACK | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.74 | CASIO-LEGACY-FAMILY-OFFLINE-FALLBACK | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.75 | CASIO-REGIONAL-RESOLVER | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.76 | CASIO-MERGED-SPECS-IMAGES | APK + SOURCE | أرشيف إصدار اختباري/تطويري |
| v3.9.77 | SAFE-CLOUD-RECOVERY | APK + SOURCE | استعادة سحابية فقط — ليس للاستخدام اليومي |

## إعادة تجميع أي ملف

داخل مجلد الإصدار:

```bash
cat apk/*.part-* > AlSafaAccounting-version-SIGNED.apk
cat source/*.part-* > AlSafaAccounting-version-SOURCE.zip
```

استخدم ملف `SHA256SUMS.txt` لمقارنة البصمة بعد التجميع.

## الأمان

مفتاح التوقيع الدائم وكلمات مروره وملف signing-info.txt غير موجودة في المستودع.
