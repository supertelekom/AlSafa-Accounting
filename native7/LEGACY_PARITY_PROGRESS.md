# Native 7 — سجل مطابقة التطبيق القديم

آخر مراجعة: 2026-10-30

## مصادر الحقيقة
- التطبيق القديم: AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE.zip (ملف مرفق بالمحادثة؛ assets/main.js و assets/app.js و assets/styles.css و assets/style.css و assets/index.html).
- المشروع: فرع native6-development، مجلد native7 فقط. ممنوع تعديل native6.
- نسخة البيانات: نسخة الصفا القديمة formatVersion 2، تُستعاد إلى بيانات تشغيلية لا أرشيفية. يلزم التحقق من الملف الفعلي قبل أي ترحيل.

## ما تحقق منه في هذه الجلسة
- أمكن فتح MainActivity.kt على GitHub؛ يحتوي واجهة Android أصلية، OriginalPageStructure، LegacyBackupStore، RepairStore، ومسار اختيار الصور.
- ملف المصدر القديم المرفق يحتوي assets/main.js و assets/app.js و CSS والخط Tajawal-Medium.ttf.
- **لم تثبت بعد مطابقة بصرية كاملة أو مطابقة حركات جميع الشاشات.** لا تُعتبر أي شاشة مكتملة دون مقارنة فعلية.

## قواعد العمل
1. انقل نصوص الأزرار والحقول والترتيب والقياسات والحركات من المصدر حرفياً؛ لا تُنشئ تسميات أو وظائف افتراضية.
2. قارن شاشة بشاشة: الرئيسية، الوضع المبسط، المنتجات، فواتير المبيع الكاملة والمبسطة، الصيانة، الموردون، المصروفات، الصناديق، التقارير، الإدارة.
3. لكل شاشة سجّل: مرجع الدالة/النمط القديم، ملف Kotlin المقابل، عناصر مطابقة، عناصر ناقصة، اختبار الجهاز، commit.
4. لا تعدّل بيانات الاستعادة أو الحسابات لتقليد الواجهة؛ تحقق من القواعد الأصلية أولاً.
5. عند كل دفعة تحديث، أضف هنا ما نُفذ فعلاً وSHA؛ لا تكرر العناصر المكتملة بعد اختبارها.

## قائمة التحقق الحالية
| القسم | مطابقة الحقول والأزرار | مطابقة الشكل والحركات | اختبار على Honor Pad X8b |
|---|---|---|---|
| الرئيسية والوضع المبسط | غير متحقق | غير متحقق | غير متحقق |
| المنتجات والألوان والصور | غير متحقق | غير متحقق | غير متحقق |
| فواتير المبيع | غير متحقق | غير متحقق | غير متحقق |
| الصيانة | غير متحقق | غير متحقق | غير متحقق |
| الموردون | غير متحقق | غير متحقق | غير متحقق |
| المصروفات والصناديق | غير متحقق | غير متحقق | غير متحقق |
| التقارير والإدارة | غير متحقق | غير متحقق | غير متحقق |

## الخطوة التالية
استخراج خريطة صفحات المصدر القديم ومقارنتها مع OriginalPageStructure و MainActivity؛ تنفيذ الفروقات على دفعات قابلة للاختبار، ثم تحديث هذا السجل بعد كل commit.

## مراجعة إضافية 2026-10-30 — فحص المصدر الفعلي
- فُتح ZIP القديم المرفق مباشرةً: يحتوي assets/index.html وassets/main.js وassets/app.js وassets/style.css وassets/styles.css وخط Tajawal-Medium.ttf.
- تمت قراءة OriginalPageStructure.kt من الفرع الحالي. الواجهة تعتمد على original-structure.json وتُنشئ Android Views أصلية، وتحتوي توصيفاً صريحاً بأنها **معاينة تخطيطية** وأفعال الحفظ غير مفعلة. لذلك لا يجوز اعتبار الأزرار أو الحركات مكتملة.
- مرجع CSS مؤكد: assets/style.css السطر 433: .simple-actions شبكة بعمودين وفجوة 11px؛ السطر 434: .simple-action بحد أدنى للارتفاع 104px وحشوة 16px 14px ونصف قطر 18px. يلزم مقارنة هذا بالمقاسات النهائية التي طلبها المستخدم للوضع المبسط، لا تطبيقه عشوائياً.
- مرجع Android الحالي: OriginalPageStructure.kt يبني SourceGrid وفق عرض الجهاز؛ يحتاج قياساً مرئياً قبل إعلان التطابق.
- **حالة التنفيذ لهذه المراجعة:** تدقيق وتوثيق للمصدر فقط؛ لم تُعدّل شاشات التطبيق ولم يجر اختبار APK. لا تعِد تنفيذ التدقيق أعلاه دون سبب.
- الخطوة التنفيذية التالية: جرد source-derived JSON وواجهات Android بالكامل، استخراج فروقات الحقول والتفاعلات، ثم إصلاح كل فرق في ملفات Kotlin مع اختبارات وبناء فعلي.

## إصلاح تنفيذي — 2026-10-30
- Commit `897cbdabdcb246cde92a25796f045e5963921baa`: تعديل `OriginalPageStructure.kt` لتنظيف metadata وcontrols وradioNames عند حذف الصفوف الديناميكية ثم إعادة تطبيق conditions. يمنع تأثير الحقول المحذوفة في الشروط اللاحقة.
- تم التحقق من CSS الأصلي: `.simple-actions` عمودان gap 11px؛ `.simple-action` min-height 104px، gap 12px، padding 16px 14px، radius 18px. **لم تُغيّر هذه المقاسات** لأن المستخدم طلب سابقاً تصغير أزرار الوضع المبسط، ويلزم حسم المقاس النهائي بالمقارنة على الجهاز.
- لم يُبن APK ولم يجر اختبار على الجهاز؛ هذا إصلاح جزئي وليس إكمالاً للمطابقة.

## دفعة تنفيذ إضافية — 2026-10-30
- Commit `455116df07ec48ea5c929f5879d6198dcbd13ed2`: استدعاء `conditions()` فور إضافة سطر بيع ديناميكي أو صف لون جديد، وإعطاء نسخة صف اللون مفتاحاً فريداً `variant-copy-N`.
- المصدر الأصلي ZIP v3.9.359 متاح محلياً وفُحصت محتوياته، ويضم `assets/main.js` و`assets/style.css` و`assets/styles.css` و`assets/index.html`.
- الحالة: إصلاحات تفاعلية جزئية؛ لا دليل بعد على اكتمال جميع الواجهات أو مطابقتها بصرياً، ولم يتم بناء APK أو اختباره.

## إصلاح تنفيذي إضافي — 2026-10-30
- Commit `a56a4b58481ac6164b62b620036badc434015f7a`: عند حذف سطر مشتريات من `OriginalPageStructure.kt` يجري الآن تنظيف `metadata` و`controls` و`radioNames` لكل أبنائه وإعادة `conditions()`. استُخرجت دالة `unregisterDetached` المشتركة لحذف أسطر الفواتير والألوان أيضاً.
- هذا إصلاح لسلوك الواجهة وليس إثباتاً لمطابقة جميع الواجهات أو لاكتمال حفظ العمليات؛ يلزم بناء واختبار Android APK ومقارنة مرئية.

## مقارنة CSS أصلية وتنفيذ — 2026-10-30
- فُحص الملف المرفق `assets/style.css` مباشرة: قاعدة `@media (min-width:720px)` تضبط `.simple-actions` إلى **3 أعمدة**؛ دونها **عمودان**.
- Commit `0e8ee547e54cc75bcb410e72f98edf4b520444eb`: تصحيح `simpleGrid` في `OriginalPageStructure.kt` إلى 3 أعمدة للأجهزة اللوحية بعرض >=720dp، وعمودين للأصغر، بدلاً من عمودين دائماً.
- تفاصيل CSS التي لا تزال تحتاج تدقيقاً بصرياً: `.simple-action:active {transform:scale(.985)}`، رمز 46px، وتفاصيل اختلاف المقاسات بناءً على طلب المستخدم السابق بتصغير أزرار الوضع المبسط.
- هذا تعديل حقيقي واحد موثق؛ لا توجد شهادة بناء APK أو مطابقة شاملة حتى الآن.

## نقل حركة أصلية — 2026-10-30
- المصدر المؤكد من ZIP القديم `assets/style.css`: `.simple-action:active { transform:scale(.985); }`.
- Commit `9d10b7baad01401c74693258d3d670969ff59e39`: تطبيق تصغير مؤقت `scaleX/scaleY=0.985f` لأزرار `simple-action` أثناء `ACTION_DOWN` وإعادتها عند `ACTION_UP/CANCEL` مع إبقاء `OnClickListener` يعمل.
- لا توجد حتى الآن شهادة اختبار حركة الضغط على جهاز Honor Pad X8b؛ لا يُعتبر النقل الشامل مكتملاً.

## دفعة نماذج ديناميكية — 2026-10-30
- Commit `a4c0225bf929ec150aec0da146f5200cbaf9c1df`: إضافة `uniqueDynamicIds` لمنع تكرار `sourceId` و`key` في الصفوف المنسوخة، وتطبيقها على أسطر المشتريات وأسطر البيع اليدوية وألوان المنتجات.
- Commit `1457f34b9453efd7bd4138c0b2786fa0e5f1e38b`: عند إضافة سطر مشتريات أو لون جديد داخل `SourceGrid` يستخدم `addCell` لضمان دخوله في حساب أعمدة التخطيط، مع إعادة تطبيق شروط ظهور الحقول.
- التعديلات لا تغير أسماء الحقول أو بيانات المستخدم أو قواعد المحاسبة. لا تزال بحاجة إلى بناء Android واختبار فعلي قبل اعتبارها مطابقة.

## إصلاح ترتيب الشبكات والتدفق — 2026-10-30
- Commit `e131e2c747deb9a35bef11e38e4e1be41aa4d70a`: `SourceGrid` يزيل بيانات `full` و`frames` عند حذف عنصر ويستخدم ترتيب الأبناء الحالي في `onLayout` بدلاً من ترتيب `IdentityHashMap` غير المضمون.
- Commit `1765ee316d87916d902c54a98d65bd5f5d99b668`: `SourceFlow` ينظف `frames` عند حذف العناصر ويطبق مواضع RTL بترتيب الأبناء الحالي.
- نطاق هذه الدفعة إصلاحات التخطيط فقط؛ لا إثبات بعد لمطابقة بصرية شاملة أو اكتمال وظائف الأزرار أو اختبار APK.

## دفعة إصلاح عناصر النماذج — 2026-10-30
- Commit `ca174a0a1bd2592a138afc866ab87ac1339f1ee8`: إعطاء مجموعات radio في الصفوف الديناميكية المنسوخة نطاق اسم فريداً؛ إضافة تحديث شروط العرض عند تغيير checkbox؛ تصحيح قياس ارتفاع SourceGrid عندما لا يبقى أي عنصر مرئي.
- مرجع المقارنة في ZIP القديم: `assets/style.css` يحوي شبكة `.form-grid` بعمودين افتراضياً، 3 أعمدة في مقاسات معينة و4 عند >=1080px، و`.simple-actions` عمودين افتراضياً و3 عند >=720px. تتداخل قواعد CSS لاحقة ويجب مراعاة ترتيبها قبل تعديل المقاسات مرة أخرى.
- هذه إصلاحات برمجية محددة؛ لم يثبت اكتمال نقل جميع الشاشات أو الحركات، ولم يتم اختبار APK على الجهاز.

## دفعة مطابقة أيقونات الوضع المبسط — 2026-10-30
- المصدر: `assets/style.css` من ZIP الإصدار اللوحي v3.9.359. `.simple-icon` الأصلية 46×46px وخط 25px، وعند عرض صغير 40×40px وخط 22px، وزاوية 14px. `.simple-action` لها فجوة 12px، وفي وضع الشاشة الصغيرة 9px.
- Commit `48c33d68f5c2420affcf640e2cc2170a1bff47c1`: تصحيح مقاس الرمز وحجمه وخلفيته والفجوة في `OriginalPageStructure.kt` مع إبقاء ارتفاع أزرار الوضع المبسط المصغّر بطلب المستخدم.
- Commit `1038f08439dd9cfc1e15f1be26b89c59c5bc556f`: نقل ألوان الأيقونات الأصلية لـ `deliver` (#eaf4ec) و`debt` (#fff5df) و`expense` (#fff0ed)، وحد زر `sale` (#dac398).
- لا يوجد بناء APK أو اختبار بصري يؤكد التطابق الكامل؛ هذه مطابقة موضعية مثبتة من CSS.

## تصحيح إضافة ألوان المنتج — 2026-10-30
- Commit `3b26d746275d6257357fb114456a257e8b7b9adc`: إزالة الاعتماد الخاطئ على `purchaseRow` في تحديد حاوية ألوان المنتجات.
- Commit `78eb7c315353585fd5ad1ebcde00e55645d009b3`: اختيار حاوية `data-color-variants` الأقرب لزر إضافة اللون وفق القرابة في شجرة الواجهة، ثم استخدام `SourceGrid.addCell` عند الحاجة.
- هذه إصلاحات بنيوية وليست إثباتاً لتطابق التصميم بصرياً؛ لم يجر اختبار APK بعد.

## سلامة مراجع الحقول بعد الحذف — 2026-10-30
- Commit `095800338a6afab14a039821f8ba2e76d0d1e128`: عند إزالة عنصر من شجرة الواجهة، تنظف `unregisterDetached` مراجع العنصر وتعيد ربط `sourceId` بعنصر باقٍ يحمل المعرّف نفسه إن وجد، لمنع فقدان مرجع التحكم في النماذج الديناميكية.
- لا يغيّر هذا أسماء الحقول أو تصميمها. لم يتم اختبار بناء APK أو تأكيد تطابق جميع الواجهات.

## إصلاحات الفاتورة والصيانة السريعة — 2026-10-30
- Commit `19f0a53de68513330c89ca943b67e301b8016ba5`: اختيار قالب سطر المشتريات من أبناء حاوية `purchase-lines` نفسها بدلاً من أول قالب في خريطة العناصر.
- Commit `72e1fead05da36254ec56a27d55e2f33fd6c1fc0`: جعل تمييز اختيار الصيانة السريعة حصرياً؛ إعادة بقية الأزرار إلى الخلفية الاعتيادية عند اختيار زر آخر.
- التعديلات لا تثبت التطابق البصري الشامل أو تشغيل حفظ الفواتير، وتحتاج إلى بناء وتجربة Android.

## مطابقة سلوك الخيارات المعطلة — 2026-10-30
- Commit `c3e8f227d9919a33aed06c310dc2f4afe362dab9`: تعديل `Spinner` في `OriginalPageStructure.kt` ليُعلن بدقة عن وجود خيارات معطّلة عبر `areAllItemsEnabled` ويختار أول خيار متاح عندما تكون القيمة الافتراضية معطّلة؛ يحافظ على ترتيب ومسميات الخيارات من المصدر.
- لم يجرِ اختبار بناء Android أو مقارنة بصرية شاملة. لم تكتمل مطابقة كل الصفحات أو وظائفها.

## دفعة عناصر النماذج والطي — 2026-10-30
- Commit `bdff7633257c9f693f5d8613b0377b3ed0c9b6e4` في `OriginalPageStructure.kt`: احترام الحالة الأولية `open` للأقسام القابلة للطي، جعل رأس القسم قابلاً للتركيز، وإضافة انتقال ظهور 150ms يحتاج مقارنة مرئية مع الأصل.
- دعم لوحة مفاتيح البريد الإلكتروني `email` والهاتف `tel` والإدخال العشري/الرقمي؛ تطبيق `readonly` على المدخلات؛ احترام `disabled` على checkbox/radio.
- تحقق مرجعي: `assets/style.css` يحتوي `.simple-action` بمقاسات 104px و18px radius و12px gap وscale(.985) عند الضغط؛ يظل ارتفاع 52dp المصغّر استثناءً بناءً على طلب المستخدم السابق.
- لا يوجد اختبار APK أو ضمان مطابقة جميع الشاشات والحركات.

## نقل مباشر من دوال main.js القديمة — 2026-10-30
المصدر المعاين فعلياً: `assets/main.js` داخل `AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE.zip`.
- `purchase-quick-add` في المصدر: إنشاء سطر `purchaseLine({itemKind:'product',itemId:'__new__',isNew:true})` ثم `wireInteractiveElements` و`updatePurchaseTotals` وتركيز `data-purchase-new-name` والتمرير السلس. Commit `a62a3027fee96b8d40dd2533acdfb21dbfabf498` نقل التركيز والتمرير؛ **حساب الإجماليات لم يُنقل في هذه الدفعة**.
- `select-quick-repair` في المصدر: يختار الخدمة، يعبيء `father-repair-service` و`father-repair-quick-id` والسعر والحقول اليدوية ويستدعي `updateFatherCurrencyAmount`. Commit `824f47789206fc9a4f4862668eb1d4f8a5b1649f` نقل تعبئة الحقول والتحكم اليدوي؛ **تحويل العملة لم يُنقل**.
- `remove-color-variant` في المصدر: إذا كان اللون محفوظاً يتحقق من السجل و`variantRole` و`productCanBeFullyEdited` ثم يمر عبر `deleteRecord` المؤكد. Commit `b8c21015f65400d6ca36bd77bbaf99b0a8cf4427` يمنع حذف اللون المحفوظ من واجهة المعاينة دون مسار الحذف التشغيلي، ولا يدّعي تنفيذ الحذف المحاسبي.
- المقارنة النهائية تتطلب تتبع دوال الحفظ والإجماليات والاستعادة والتصوير والبحث من `main.js`، ثم بناء APK واختبار فعلي.

## نقل حسابات فاتورة المشتريات من main.js — 2026-10-30
- راجعت مباشرة سورس v3.9.359 في `assets/main.js`: الدوال `updatePurchaseTotals` و`readColorVariantRows` و`purchaseLine`.
- Commit `fcd5b2102f2c66be2dbd9747b64321f2848041c1`: إضافة `updatePurchaseTotals()` في Kotlin لحساب `qty * cost` وتكاليف الألوان `variantQty * (variantCost ?: mainCost)` وإجمالي الفاتورة ورصيدها بعد المدفوع، وربط تغييرات المدخلات بإعادة الحساب.
- فروق بحاجة إلى التحقق قبل الاعتماد: تصفية صفوف الألوان غير المدخلة، تنسيق `money` الخاص بالعملة، ربط جميع معرّفات حقول JSON الفعلية، وصحة التحديثات في الجهاز. لا توجد كتابة SQLite أو حركات مخزون في هذا التنفيذ.
- **لم يُبنَ APK ولم يثبت التطابق الحسابي أو المرئي بعد**.

## مطابقة مقاسات البطاقات الداخلية والخارجية — 2026-10-30
المصدر المقارن مباشرة: `assets/style.css` من سورس التابلت v3.9.359.
- `.father-main-card`: في CSS عمودي `flex-direction:column`، ارتفاع أدنى `185px` (و`155px` في المقاس الضيق)، أيقونة `51px` (و`42px` ضيق)، عنوان `24px` (و`20px` ضيق)، هوامش عمودية `13px` و`8px`، حشوة `18px` (و`13px 8px` ضيق). طُبقت تعديلات على Kotlin في commits `15cdf41e` و`d4ead28f`.
- `.manage-card`: ارتفاع أدنى أصلي `115px` بدلاً من `84dp` السابق؛ عُدّل.
- `.simple-action`: استثناء المستخدم السابق بتصغير ارتفاع الأزرار إلى النصف ما زال محفوظاً؛ لا يُعد مطابقاً لقيمة CSS الافتراضية `104px` إلا إذا وافق المستخدم على العودة للمقاس القديم.
- هذه مطابقة نصية لجزء من مقاسات CSS، **وليست شهادة تطابق شامل**: يلزم مراجعة الشاشات والوسائط المتجاوبة، ومقارنة screenshots على Honor Pad X8b وبناء APK. لم يُختبر البناء بعد.

## Tablet UI parity batch — 2026-10-30
- Source inspected: legacy tablet v3.9.359 `assets/style.css` from source ZIP.
- `manage-card`: original icon 25px, title 14px, description 10px, icon margin 8px, description margin 5px. Kotlin card typography and spacing adjusted in commit `843b4eaa2b37e61dfefa8c8593f4c9dc4e6432c3`.
- `simple-action`: original icon 25px (40/46px container), title 17px, description 11px, horizontal padding 14px, gap 12px. Corrected text sizing and horizontal padding; existing user-requested half-height remains an intentional exception to legacy CSS.
- Previous `SourceGrid` row-stretch correction: commit `2ccb93b766916c181f618f16bcb941c69e3f5d22`.
- NOT verified: full screen pixel parity, all media queries, actual APK build and tablet screenshots. UI preview actions are not a substitute for operational logic.

## Tablet interface layout corrections — 2026-10-30
- `90f9303c81d6a44de081ca0a7aa85539cd7caf88`: SourceGrid now measures children to the final tallest-row height, not just stretching their layout rectangles. Affects tablet grids across sections.
- `8587c9c96d14f80192c1e825240b32af35635cc6`: horizontal quick-action labels use remaining weighted width to avoid text overflowing beside their fixed-size icons.
- Visual parity still unverified on Honor Pad X8b. No APK build or screenshot comparison was performed. These are layout consistency fixes, not evidence of complete pixel parity.

## Tablet-wide interface audit — 2026-10-30

Reference: original tablet v3.9.359 source archive, `assets/style.css`; Android renderer: `OriginalPageStructure.kt`. Target: Honor Pad X8b. This batch inspected the shared layout engine used across home, products, sales, maintenance, suppliers, expenses, vaults, reports and settings. It does NOT establish individual pixel-perfect verification for every route.

### Confirmed source CSS / native changes
- **Form grid:** original baseline 2 columns, 720px+ 3 columns, 1080px+ 4 columns, but the *later Honor Pad override* sets **2 columns at 720–1199px** and **3 columns at 1200px+ landscape**. The later rule wins; native implementation previously ignored it and displayed 3 or 4 columns. Corrected in `de2749d4bcde5c60eb88f29ea5d72fce7e93fb6a`.
- **Full-width fields:** original `.field.full {grid-column:1/-1}`. Native grids previously checked only `full` JSON boolean; now also recognize source class `full` and inline full-span declaration. Same commit.
- **Row sizing:** `SourceGrid` measures children at final tallest row height (`90f9303c81d6a44de081ca0a7aa85539cd7caf88`), reducing inconsistent neighboring card heights.
- **Quick action text:** weighted remaining horizontal width (`8587c9c96d14f80192c1e825240b32af35635cc6`); previously fixed text width could overflow.
- **Shared separators/search row:** 1dp separator and 42dp minimum search row (`98c040c6ebcc4eb03fcec333f16241331d8dc2cd`).
- **Existing source typography and card changes:** `843b4eaa2b37e61dfefa8c8593f4c9dc4e6432c3`, `15cdf41e`, `d4ead28f`. Simple action reduced height is an explicit user-requested exception, not literal original CSS.

### Per-section coverage and open verification
| Section | Shared native structure reviewed | Still needs direct original-screen comparison |
| --- | --- | --- |
| Home | Cards, RTL, quick-action grid | Header/sidebar, responsive shell, logo and exact margins |
| Products | Form grids, fields, buttons | Product list/cards, photos, color variants, image zoom |
| Sales | Form grids, buttons, dynamic rows | Full/simple invoice, discounts, dialogs, printable invoice |
| Maintenance | Search rows, forms, quick actions | Intake/delivery dialogs, photos, repair status cards |
| Suppliers | Purchase grid, dynamic lines, inputs | Supplier forms, debt history, selection dialogs |
| Expenses | Shared forms, input sizing | Expense list, currency/payment controls |
| Vaults | Summary grids and fields | Cash movement, currency panels, daily closing |
| Reports | Report/summary grids | Tables, period filters, monthly and partner report screens |
| Settings | Shared cards and forms | All nested dialogs, backup/restore and print settings |

### Important limitations
- No Android Studio build, instrumentation test or screenshot overlay was performed in this batch.
- Shared renderer contains preview-only buttons; working actions and backend behavior are not proven by visual changes.
- Original CSS modal/backdrop, app shell/sidebar and several route-specific layouts still require direct parity work.
- The preview renderer includes unverified purchase total calculations; do not use them as accounting validation.
- Next acceptance gate: build APK locally, capture corresponding old/new screens on Honor Pad X8b, verify text, font, colors, dimensions, alignment, scrolling, dialog open/close and navigation, then correct discrepancies.

## Source archive cross-check and shared UI changes — 2026-10-30

Directly inspected `assets/style.css` in the attached original v3.9.359 tablet source ZIP. The same source's `assets/Tajawal-Medium.ttf` exists. Changes target the common Native 7 renderer and therefore affect the nine principal sections where corresponding node kinds/classes are used.

| Original CSS selector | Source value | Native change |
|---|---|---|
| `.field label` | 11px, bottom margin 6px | Text labels now have 6dp bottom padding |
| `textarea` | min-height 78px | Multiline EditText now min-height 78dp |
| `.metric-card` | min-height 101px, padding 14px, radius 16px | Common metric/stat cards updated |
| `.report-tile` | min-height 78px, padding 12px, radius 13px | Source-class report tile renderer updated |
| `.section-card` | radius 17px | Source-class section card background updated |
| `.form-section` | margin 20px 0 14px | Section heading padding adjusted |
| `.form-grid` | Honor Pad 720–1199px override 2 columns, 1200px+ landscape 3 columns | Previously corrected in `de2749d4` |

Commits: `480a5a91b6fc9b7fb029ce20a4f046ebdfde8c8e`, `86e8f67f4102bb78fa527f0870d57a0540f1c195`.

Scope inspected: shared UI renderer for home, products, sales, maintenance, suppliers, expenses, cashboxes, reports and settings. The source structure is used for route-specific content; this batch did **not** individually certify every label, screen, modal or motion. The CSS also has general responsive `auto-fit` rules; some pages may still differ from source when their usable content width changes. **No APK build or on-device screenshot comparison performed.** Dialogs, pictures, dynamic interactions, app shell and route-specific card styles require further work. All preview-only actions remain non-operational.

## Cross-section UI audit and source-grounded fixes — 2026-10-30 (batch)

Original archive actually opened and inspected: `AlSafaAccounting-v3.9.359-TABLET-PARTNER-DATE-REPORT-FIX-SOURCE.zip` / `assets/style.css`. This is a shared-component audit covering the home, products, sales, maintenance, suppliers, expenses, cashboxes, reports and settings route renderer, **not** a pixel-by-pixel certification of all nine sections.

| Source rule or original behavior | Change | Commit |
|---|---|---|
| Native `details/summary` should not acquire an invented 150ms fade | Removed unverified animation and kept immediate fold visibility changes | `d78ca95a76a301b7e3d9bde9a9aa5dd1179511bf` |
| `.metric-card.accent`: gradient #b28232 → #8b601f, foreground #fff9ec | Restored accent gradient and readable text for stat/metric-card source classes | `2ecd3795ff237e162ea730e30a0f05179ef3a282` |
| `.section-title`: horizontal heading/actions, padding 14px 15px | Added RTL horizontal layout and weighted title with wrap-width actions; prevent overflow | `7d965770a36d540daafe41df5ee0f628167717e9`, `376320f65db7ee0f6ba335f5ff33405a110d879c` |
| `.form-card`: paper background, radius 17px, tablet padding 22px | Source-class form-card padding and background | `7d965770a36d540daafe41df5ee0f628167717e9` |

Original root CSS palette verified: paper #fffdf9; ink #3e3429; muted #8a7b6c; line #eadfce; gold #a97929; gold-deep #7a5118. Native uses Tajawal per the project's explicit typography requirement; original root CSS itself lists Tahoma/Arial, so claiming the original CSS requires Tajawal would be inaccurate.

### Coverage / remaining acceptance tests
- **Home:** shell/topbar/logo, main card dimensions and mode chooser screenshot comparison pending.
- **Products:** list cards, image preview and color variant forms pending.
- **Sales:** full/simple invoices, manual items, receipt controls, discount dialogs and invoice print view pending.
- **Maintenance:** intake, status cards, photo preview and quick-repair screens pending.
- **Suppliers:** purchase line controls, supplier dialogs and debt pages pending.
- **Expenses:** form field and payment method layout pending.
- **Cashboxes:** currency panels, movement dialogs and daily close layout pending.
- **Reports:** report tiles, tables, filters and partner reports pending.
- **Settings:** nested pages, restore/backup and printer dialogs pending.

**Not tested:** Android Studio compilation, actual Honor Pad X8b screenshot comparison, all click interactions, keyboard behavior, long text overflow, modal sizing and animations. The changes affect layout rendering only; no accounting logic is certified. Follow-up must build locally and compare screenshots against the old tablet app.

## Shared renderer conflict review — 2026-10-30

Reviewed the shared Native 7 view tree and grid measurement used by the nine primary sections (home, products, sales, maintenance, suppliers, expenses, cashboxes, reports, settings). This is a code-level conflict review, not a full screenshot audit.

- `f228af8abcdb3b785e8fd71304cfdfe9cf701208`: removed a newly introduced `section-title` border which was not established by the original CSS. Preserved existing child bottom margins when converting title children to a horizontal RTL row. Extracted shared child margin selection into `childParams` so field spacing and section spacing use a consistent rule.
- `8cfd33e9f33bc3c4e5cb0484db73e4b798bea381`: `SourceGrid` now respects the parent-provided width measurement constraint when reporting its measured width.

### Remaining verification by section
Home: header, main cards, navigation; Products: list, add/edit, image and variants; Sales: full/simple invoice and dialogs; Maintenance: intake/delivery and photos; Suppliers: purchases and balances; Expenses: forms and filters; Cashboxes: currency/movements/closing; Reports: tables and periods; Settings: nested settings and backup/printing. These all still require actual old-vs-new screenshots and interactive tablet tests.

No APK compilation or Honor Pad X8b on-device test was run in this batch. The visual renderer still includes preview-only controls and does not establish full feature parity.

## Original source verification and compile-safety audit — 2026-10-30

Inspected the original v3.9.359 ZIP's `assets/style.css` directly. This audit targets shared renderer components used across all nine principal sections, rather than asserting completion of every screen.

| Original source selector | Confirmed CSS | Applied code |
| --- | --- | --- |
| `.section-title` | flex, space-between, padding 14px 15px, **border-bottom** 1px `var(--line)` (no full border) | `93828dd44304d3d7fdf6694241324e804179dd21`: bottom divider drawable |
| Kotlin renderer `when(kind)` | `else` must be the final branch | `147e6ab03e6e19474e6ddfa7071a78ad1075507a`: moved `help` before `else`; addresses a compile-blocking code issue |
| `.metric-label` | 11px muted | `08fd4de9704178b16738cc367e5c2e27f5e52405`: label typography |
| `.metric-value` | 18px bold, top margin 8px | same commit: value typography |
| `.metric-note` | 10px muted, top margin 5px | same commit: note typography |
| `.form-section` | 13px bold, gold-deep | same commit: heading typography |

The source CSS also specifies `.section-title h3` at 15px; actual per-route markup mapping still needs checking. Source HTML order/labels, external shell, nested dialogs, photographs and animations have **not** been certified against all original screens.

### Status by section
- Home: shared cards and metric styles improved; header and logo comparison pending.
- Products: shared form typography improved; cards, variants and image interaction pending.
- Sales: shared form typography improved; full/simple invoice screens and dialogs pending.
- Maintenance: shared fields and card layout improved; photos and state transitions pending.
- Suppliers: shared fields and grid layout improved; purchase controls and dialogs pending.
- Expenses: shared forms improved; transaction list and filters pending.
- Cashboxes: metric styles improved; balances, currency controls and closing pending.
- Reports: metric/report tiles improved; tables, period filters and partner reports pending.
- Settings: shared cards improved; settings screens and backup/print dialogs pending.

**Build and device testing still pending.** No Android Studio build, runtime verification, screenshot diff or full button/navigation audit has been performed here. Changes are committed but not yet certified as pixel-identical or functionally complete.

## Source CSS accent and report text corrections — 2026-10-30

Inspected original tablet v3.9.359 `assets/style.css` directly and corrected the following shared typography:
- `.metric-card.accent .metric-label` and `.metric-card.accent .metric-note` require **#fcebd0**, whereas ordinary accent content uses **#fff9ec**. Previously the renderer tinted all text uniformly. `4b66b96fe0f0db698ecbd14c1ee5e6d75158ab06` applies the source distinction recursively by source class.
- `.report-tile small` uses muted 10px and `.report-tile b` uses gold-deep 14px with 7px top spacing. `a2addd80f9387dcad475e6362fb33d4025cd48d1` handles source-class `report-tile-label` and `report-tile-value` nodes. Note that unclassified child nodes may still differ.

**Nine-section review status:** common metrics and report tiles impact the home, products, sales, maintenance, suppliers, expenses, cashboxes, reports and settings where source-class nodes are present. Their individual screen labels, ordering, modal dimensions, icons, photo handling, complete navigation and animations have **not** been fully inspected or proven equivalent. Need screenshots of all original and native routes on Honor Pad X8b, compile and runtime tests, accessibility/RTL tests, and button-by-button verification. No APK was built in this batch.

## Source precedence: quick repair grid — 2026-10-30

Direct inspection of original tablet v3.9.359 archive `assets/style.css` found later rules labelled v3.9.90 and v3.9.91 that override earlier generic quick-service layouts:
- `body.father-mode .quick-service-grid` has **7px** gap.
- Landscape tablets from **900px** width have **4 columns**, even below the generic 1024px breakpoint.
- Individual quick-service cards are **72px minimum height** in this landscape mode, with **5px 4px** padding; outside that rule they are 78px minimum height with 6px 5px padding.

Native 7 commit `e2c7641387e2b32847a8b422367e57e5e5224dc5` applies the grid gap and column breakpoint. **The individual quick-service card dimensions remain to be checked against the current renderer and original source**; do not claim complete parity.

All nine principal sections remain subject to route-specific verification (labels/order/controls/images/modal behavior). No APK build or device screenshots in this batch.

## Nine-section shared UI batch — original tablet CSS verification, 2026-10-30

Directly inspected `assets/style.css` inside the original v3.9.359 tablet source archive. Changes apply to shared UI nodes used by the home, products, sales, maintenance, suppliers, expenses, cashboxes, reports and settings routes where these source classes occur.

| Area | Source-grounded details | Commit |
|---|---|---|
| Quick repair grid | 7px gap; landscape 900px+ uses 4 columns | `e2c7641387e2b32847a8b422367e57e5e5224dc5` |
| Quick repair cards | min-height 78px, 6px/5px padding, 14px radius; landscape 900px+ min-height 72px and 5px/4px padding; 23px icon, 16px title, 15px price, 3px vertical spacing | `6b374d7ea6d47e76a6e759d0fbd45bec76f4c7a6`, `ed1f7f8c58a3e33a4031f7ff75dc84a52dccd1b3` |
| Transaction icon | 34px square, 11px radius, #f7eddd background | `b397a553fdd58f6d39aca758d9423e374dcb8058` |
| Transaction amount | 12px bold and single line, negative red | `b397a553fdd58f6d39aca758d9423e374dcb8058` |

### Coverage by section
- Home: shared cards and transaction display; header/logo/layout still unverified.
- Products: shared forms; product photos, variants and edit dialogs still unverified.
- Sales: shared form/transaction presentation; complete invoice and payment dialogs still unverified.
- Maintenance: quick-repair card and grid source override implemented; photos, intake and delivery still unverified.
- Suppliers: shared form and transaction presentation; purchase invoice controls still unverified.
- Expenses: shared transaction styles; filters and forms still unverified.
- Cashboxes: shared transaction styles; movement and closing screens still unverified.
- Reports: shared report and transaction styles; tables, periods and partners still unverified.
- Settings: shared card/form styles; nested settings, restore and printer dialogs still unverified.

These changes were committed to the GitHub development branch. **No Android Studio compilation, Honor Pad X8b device test, screenshot comparison, full source-label/order audit or modal animation audit occurred in this batch.** Native preview-only actions remain preview-only.
