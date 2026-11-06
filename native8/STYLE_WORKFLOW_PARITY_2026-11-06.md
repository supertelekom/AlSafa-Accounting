# مطابقة التصميم وطرق العمل — الدفعة الأولى

المرجع: assets/main.js من v3.9.359 والنسخة AlSafa-Backup-2026-10-26T06-21-00-520Z.json. البداية من commit 73f38af. التعديل داخل native8 فقط.

## ما رُبط فعلياً
- أزرار «صيانة سريعة» من settings.quickRepairButtons في آخر نسخة مستعادة: 60 زر، 58 مرتبطاً بقطع، 6 بأسعار يدوية. قراءة ديناميكية، لا تثبيت أسماء/أسعار النسخة داخل الإنتاج. في غياب الإعدادات تستخدم الدالة أزرار المصدر الافتراضية الثمانية و«صيانة مشكلة» فقط.
- الأسماء والمعرفات والأيقونات والترتيب والأسعار والكميات وروابط القطع الأصلية. لا يعاد استيراد أو خصم مخزون عند القراءة.
- تصميم الصيانة المبسطة من حزمة التصميم المستخرجة: Tajawal، RTL، شبكة مرنة، تمرير عمودي، مراعاة أشرطة النظام ولوحة المفاتيح، اختيار الخدمة، المبلغ اليدوي والعمل المنجز، التسليم أو ترك الساعة، العميل والهاتف، العملة، نقدي/شام كاش.
- السعر الأساسي TRY كما renderFatherRepair؛ تحويل مقترح قابل للتعديل والمبلغ النهائي هو المحفوظ. دعم الأرقام العربية.
- سعر الزر المرتبط بقطعة هو إجمالي الخدمة، دون إضافة أجرة خفية ثانية. التكلفة من القطعة الحالية في Room؛ كلفة الخدمات القديمة غير المرتبطة تقرأ من quickRepairCosts حسب المصدر.
- الحفظ الفوري: إنشاء الفاتورة وترقيمها ثم التسليم والمخزون والقبض ضمن نفس معاملة Room. معرف طلب ثابت يمنع إعادة الحفظ إذا أعيدت نفس المحاولة.
- ترك الساعة: يحفظ بانتظار التسليم مع received=0، دون خصم قطعة أو قبض. التسليم لاحقاً يمر عبر نفس مستودع التسليم الموجود. مصدر الربح يقرأ نفس سجلات الصيانة الجديدة دون محرك مالي موازٍ.
- مدخل «صيانة سريعة» في الرئيسية وفي قسم الصيانة. لا حذف أو تغيير للمسار الكامل الموجود.

## الاختبارات
- 102 حالة JUnit ناجحة. تشمل مقارنة 60 زر × 3 عملات = 180 حالة ناتجة عن تشغيل saveFatherRepair الأصلية في Node؛ ليست 180 اختبار JUnit منفصلاً.
- أول تشغيل كشف قراءة stock الرقمية باستخدام getString؛ صُحح ليدعم تمثيل JSON الرقمي والنصي، ثم نجحت المجموعة.
- ملف المرجع يرفع مخزون القطع اصطناعياً داخل الاختبار فقط للسماح بمقارنة جميع الأزرار؛ لا يمثل مخزون العمل ولا يُستورد إليه. quickRepairCosts في النسخة المرجعية فارغ.
- فحص تجميع Kotlin لواجهة الصيانة السريعة ومستودعها ومكونات التصميم مقابل Android، مع بدائل مؤقتة لحدود AppDatabase/staging/navigation/Room transaction. ليس بناء APK أو تحقق Room/KSP كاملاً.
- لم تُشغّل Gradle أو اختبارات Android/Room أو المقارنة البصرية على Honor Pad X8b. تظل مطابقة المقاسات ونقرة الحفظ والقبض والمخزون الفعلي بحاجة لاختبار الجهاز.

## ما لم ينقل بعد — لا يعتبر مكتملاً
| القسم | الوضع |
|---|---|
| صيانة مبسطة | العرض والحفظ مربوطان؛ إدارة/تعديل الأزرار والطباعة التلقائية بعد الحفظ لم تنقل بعد |
| المبيع السريع الأصلي | renderFatherSale/saveFatherSale: صنف واحد، بحث/باركود، سعر نهائي وعملة وطريقة دفع وطباعة؛ QuickSaleActivity الحالي ما زال مسودة أصناف ثم صفحة قبض، ويتطلب نقل التدفق |
| المبيع الكامل | يلزم مطابقة الحقول والخصومات والآجل والنوافذ والطباعة دون تبديل RoomSalesEngine |
| الصيانة الكاملة | يلزم مطابقة قوائم الانتظار والتفاصيل والتحرير وحالات التسليم والإيصال |
| الرئيسية والوضع المبسط | نقل التنقل وبطاقات الأرصدة والاختصارات المحفوظة؛ غير منفذ في هذه الدفعة |
| باقي الأقسام | المنتجات والموردون والخزائن والمصاريف والشركاء والتقارير والإعدادات: الواجهات والسلوك المتبقي للمطابقة |

هذه دفعة تنفيذ أولى وليست إعلان اكتمال الستايل أو جميع طرق العمل.

## الملفات المعدلة
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/alsafa/native8/MainActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/MaintenanceDeliveryEligibility.kt
- tools/run_financial_junit.py

## الملفات المنشأة
- app/src/main/java/com/alsafa/native8/maintenance/QuickRepairActivity.kt
- app/src/main/java/com/alsafa/native8/maintenance/QuickRepairRepository.kt
- app/src/main/java/com/alsafa/native8/maintenance/QuickRepairRules.kt
- app/src/main/java/com/alsafa/native8/stylekit/SafaStyle.kt
- app/src/main/java/com/alsafa/native8/stylekit/SafaWidgets.kt
- app/src/main/java/com/alsafa/native8/stylekit/SafaGrid.kt
- app/src/main/java/com/alsafa/native8/stylekit/SafaPageHost.kt
- app/src/main/java/com/alsafa/native8/stylekit/QuickFormFields.kt
- app/src/main/java/com/alsafa/native8/stylekit/QuickRepairPage.kt
- app/src/test/java/com/alsafa/native8/sales/QuickRepairRulesTest.kt
- app/src/test/resources/quick-repair-v359.json
- tools/quick_repair_original_oracle.js
- STYLE_WORKFLOW_PARITY_2026-11-06.md
