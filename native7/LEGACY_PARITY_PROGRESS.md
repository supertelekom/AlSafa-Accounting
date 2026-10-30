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

## Compact quick-service source override and verification — 2026-10-30

Verified directly in `assets/style.css` that the last original quick-service card rules are:
- Baseline father-mode quick service: min-height 78px, padding 6px 5px, radius 14px.
- `@media(max-width:420px)`: min-height 74px, padding 5px 4px.
- `@media(min-width:900px) and (orientation:landscape)`: min-height 72px, padding 5px 4px, four-column grid and 7px gap.

Commit `8c3bfde874e9eba0a8cf34849af3f61fa37f3884` fills the missing <=420dp case in the shared native renderer. This is responsive styling only; it does not certify the quick-service operations.

**Acceptance checklist, nine sections:** home navigation/header; products fields/photos/variants; sales full/simple/returns; maintenance photos and quick actions; suppliers purchases/debts; expenses movements; cashboxes currencies/closing; reports date ranges/partner pages; settings backups/printing. Each still needs full label/order/modal/source parity verification and tablet screenshots. Tajawal asset loading is already in the shared renderer, but device font rendering has not been compared. APK compilation and on-device runtime tests were not performed.

## Structural shared-class bug and report filters — 2026-10-30

Inspected original tablet source ZIP `assets/style.css` and Native 7 shared `OriginalPageStructure.kt`.

**Fixed:** source-class styling had been incorrectly placed inside the `else` of `when(kind)`. Consequently, nodes of kinds `section`, `form`, `stat`, `field`, `help` and `sectionHeading` bypassed the CSS-class styling, even if the original node declared classes such as `section-card`, `form-card`, `metric-card`, or `section-title`. Commit `24b9c9246a8ff0e3f74d3ea2d217e4292266c11f` moved class styling after the structural-kind switch so it applies consistently across all routes. This changes shared rendering behavior and therefore needs a full regression test.

**Added:** `.report-filter` source CSS panel background `#fffdf9`, border `#eadfce`, 15px radius and 13px padding in commit `8ba677be5393c57c43372952a7bb1d3e8df9ac54`. Source report filter grid column sizing and nested label overrides still need verification.

**Sections affected:** home, products, sales, maintenance, suppliers, expenses, cashboxes, reports and settings, to the extent they use shared source-class nodes. This was a structural code audit, **not** a full label/order/animation comparison of every route.

**Still unverified:** Android Studio compilation; Honor Pad X8b screenshots; screen-by-screen element ordering and names; all dialogs, keyboard/focus, images, animations, accessibility, functional save/restore and print operations. No APK was built or device test run.

## Shared view tree integrity and navigation race review — 2026-10-30

Reviewed `OriginalPageStructure.kt` alongside the original tablet v3.9.359 source stylesheet and the native app entry-point's shared `OriginalPageStructure` usage.

1. **Source-node-aware accent typography:** `0e87e130f88f68297c347366c2f5252d17a6a155` replaces positional pairing of source children with native child views when applying accent card colors. Native wrappers and optional/hidden nodes can invalidate positional pairing; the renderer now follows registered source metadata on each built child. This is a consistency fix for any route rendering accent metrics.
2. **Navigation race guard:** `5687e7bcac0d3d9e0cfdff8ecc1227df04c9d352` ensures the deferred `conditions()` pass runs only when the original view and route are still current. This prevents a previously posted UI update from touching controls of a subsequently navigated route.

### Nine-section audit coverage and remaining tests
- Home: shared metric typography and navigation callbacks inspected; shell, logo and route layout unverified.
- Products: shared condition lifecycle inspected; product photos, forms, color variants and source ordering unverified.
- Sales: shared condition lifecycle inspected; full/simple invoices, dialogs and totals unverified.
- Maintenance: shared condition lifecycle inspected; intake, photos and delivery unverified.
- Suppliers: shared condition lifecycle inspected; purchase lines, debts and dialogs unverified.
- Expenses: shared condition lifecycle inspected; forms and movement lists unverified.
- Cashboxes: shared condition lifecycle inspected; balance cards and closing unverified.
- Reports: shared accent card rendering inspected; tables, filters and partners unverified.
- Settings: shared navigation inspected; backup, printer and nested settings unverified.

The source CSS, source JSON, Kotlin route mapping, modal behaviors and every button's operational behavior have **not** been exhaustively compared in this batch. The code is committed but **no APK build, compilation or Honor Pad X8b test was performed**. Do not interpret these commits as nine-section pixel parity or functional certification.

## Report-filter structural parity audit — 2026-10-30

Inspected original tablet v3.9.359 archive `assets/style.css` for the actual `.report-filter` CSS:
- display:grid; grid-template-columns:`1fr 1fr auto`; gap:8px; align-items:end; padding:13px; paper background; line border; radius15px.
- button min-height42px and horizontal padding12px.
- at <=380px: two columns, with button spanning both columns.

Native commits:
- `34b906a7858ff1e0eb06c5425adaca89575bdb0d`: source-style `reportFilter` structural kind.
- `67e3a74e37f7f75d19b9dbd9cae62de543cba956`: apply the grid when the source node is a generic container with `report-filter` class, avoiding the previous vertical-stack fallback.
- `1227600a0026e8fca103514bfe66169d54d8f03a`: report filter action min-height42dp in both renderer paths.

**Remaining difference:** `SourceGrid` currently divides the available width into equal cells, whereas the original filter uses two flexible columns plus an **auto-width** action. This is an approximation, not pixel parity. The source's nested `.report-filter .field label` 10px and button horizontal padding12px still require source-node-aware verification.

**Nine-section status:** this batch specifically changes shared report-filter presentation and may affect reports and other routes using the same class. Home, products, sales, maintenance, suppliers, expenses, cashboxes, reports and settings still require complete original-label/order comparisons, modal and animation review, tablet screenshots, compilation and runtime verification. No APK built or device tested.

## Consolidated shared-renderer batch — 2026-10-30

**Authoritative source inspected:** original tablet v3.9.359 ZIP `assets/style.css` (86,920 bytes); `assets/index.html` is a small entry point, not the complete screen markup. This batch does **not** claim a complete route-by-route extraction from the original JS or structure JSON.

### Changes actually committed
- `a235f646da4896b73ce1822a4fcd5018f5c0c1d9`: remove duplicated equal-width report-filter layout paths and implement a shared `reportFilter` renderer. On normal/tablet widths, fields use equal *flexible* weights and the action uses intrinsic width, matching original CSS `1fr 1fr auto`; on <=380dp the button moves below the fields and spans the available width. Preserve source child order.
- `98f57fc93665a79a3c807a346f25853ee2bb042e`: apply source `.report-filter .field label` 10px typography and action 12px horizontal padding; action min-height42dp.
- Existing related commits: `24b9c924` shared structural-kind/class styling; `0e87e130` accent metric source metadata; `5687e7bc` stale navigation callback guard.

### Cross-section regression checklist (not completed)
| Route | Shared area to test | Remaining source comparison |
|---|---|---|
| Home | metric cards, navigation | hero, all dashboard tiles and exact text/order |
| Products | form fields and cards | list/edit/variants/images/barcode, original labels |
| Sales | form grids and actions | full/simple invoices, payments, discount, returns, print dialogs |
| Maintenance | quick service cards, grids | intake, ready, delivered, photos and original modals |
| Suppliers | forms and action groups | supplier invoices, payments and debt displays |
| Expenses | shared fields and action styles | date filters, expense records and movement details |
| Cashboxes | cards and shared controls | currency balances, transfers, closing and dialogs |
| Reports | report filters and accent metrics | filter dates, partner breakdowns, all report grids |
| Settings | shared form/card styling | nested pages, backup/restore and printer controls |

**Important limitation:** No full nine-route visual or behavior audit, no modal/animation parity certification, no Android Studio build, no Honor Pad X8b device test. The filter renderer assumes the original source has two fields followed by its action, which must be verified against actual source-derived nodes. The source layout has been matched structurally, but Android view measurement and RTL placement need a screenshot comparison. Do not claim 100% parity.

## Nine-route original JavaScript inventory — 2026-10-30

Unlike prior CSS-only passes, directly inspected the original v3.9.359 tablet archive `assets/main.js` (1,052,953 bytes). The following source function headings and control captions were extracted from the original `render...` functions. **This is an inventory for parity verification, not a claim the Kotlin implementation already matches.**

| Section | Original source function | Original heading | Representative source control labels (in extracted order) |
|---|---|---|---|
| Home | `renderDashboard` | كيف تريد أن تبدأ؟ | Dashboard branches to additional `renderFatherHome` and `renderAdvancedHome`; inspect these separately |
| Products | `renderProducts` | المنتجات والمخزون | ↻ تحديث؛ ⬆ استيراد آمن؛ ＋ منتج؛ 🏷 لصاقة المنتج؛ تعديل؛ ⚖ تسوية المخزون؛ صورة الباركود؛ حذف |
| Sales | `renderSales` | المبيعات والفواتير | ＋ فاتورة؛ التفاصيل؛ تعديل؛ مرتجع/استبدال؛ تصحيح عملة القبض؛ 🧰 إصلاح آثار الفاتورة؛ 💵 استكمال قبض مختلط؛ PDF؛ 🖨️ طباعة؛ تحصيل الأونلاين؛ واتساب أعمال؛ 💬 شكر؛ حذف |
| Maintenance | `renderRepairs` | قسم الصيانة | ＋ طلب؛ التفاصيل؛ تعديل؛ مرتجع/استبدال؛ صورة الساعة؛ تحديث العطل؛ تحديث الحالة؛ فاتورة PDF؛ 🖨️ طباعة؛ واتساب أعمال؛ 💬 شكر؛ جاهزة؛ تم التسليم؛ حذف |
| Suppliers | `renderPurchases` | المشتريات والموردون | ＋ مورد؛ ＋ فاتورة شراء؛ الموردون؛ كشف الحساب؛ رصيد سابق؛ دفع؛ تعديل؛ حذف؛ فواتير الشراء؛ تسديد؛ إلغاء الفاتورة |
| Expenses | `renderExpenses` | المصاريف | ＋ مصروف؛ حذف |
| Cashboxes | `renderVaults` | الخزائن والأرصدة | إقفال اليوم؛ ＋ حركة مالية؛ سجل الحركات |
| Reports | `renderReports` | التقارير | اختيار شهر؛ من تاريخ؛ إلى تاريخ؛ تحديث؛ حركة الفترة |
| Settings | `renderSettings` | الإعدادات | اسم المتجر؛ عملة التطبيق (الوضع الكامل)؛ رقم واتساب المتجر (اختياري)؛ حفظ الإعدادات؛ نسب الشركاء؛ الإنترنت وأسعار العملات؛ فحص الاتصال |

**Important intentional exception:** The old `renderSettings` contains a `الشريكان الخارجيان` section, but Native 7 must **not** restore those removed partners; preserve the user-approved active father 40%, Abdul-Karim 35%, Yazan 25% arrangement. The original suppliers view also contains a cloud/phone synchronization control; Native 7 is intentionally offline-only, so do not recreate online sync.

**Additional implementation:** `0165d02fa3ac3b04a7b994ce460565a3ad8e9bde` adjusts empty-state vertical padding from 22dp to source 27dp and adds transaction-title/subtitle class styling. Confirm these classes exist in the source-derived JSON; source's original transaction-info children are `strong` and `span` tags.

**Next required verification:** extract complete nested markup/conditions from all nine functions, compare against original-structure JSON and native route mapping, check dialogs, action bindings, images and actual device screenshot parity. No Android Studio compile or Honor Pad X8b device test was performed. This inventory alone is not a complete audit.

## Shared nine-route renderer regression batch — 2026-10-30

**Source cross-check:** Original tablet v3.9.359 `assets/style.css` was opened directly from the archived source. Confirmed `.transaction-info strong` 12px bold, one-line ellipsis; `.transaction-info span` 10px muted with 3px top gap; `.form-section` 20px top and 14px bottom spacing; `.report-filter` two flexible fields plus intrinsic-width action, 8px gap, compact button full width. These selectors are contextual: styles apply based on the **parent** source class, not on invented classes on child tags.

**Code commit `321e6cdea4ecaef649280b03346ea84cfd2d1ab0`:**
1. Applies original strong/span typography to direct children of source-class `transaction-info` containers, based on registered source tag metadata. This fixes the prior ineffective reliance on hypothetical `transaction-title` and `transaction-subtitle` classes.
2. Applies `form-section` 20dp top and 14dp bottom margins through the shared child layout helper. Check first-child override: original CSS has `.form-section:first-child { margin-top:0 }`, not yet represented.
3. Removes early return from compact `reportFilter` rendering, so nodes after its action are no longer silently dropped. Keeps all fields/actions in their source order within their respective rows. Compact filter still needs screenshot comparison and verification of source node sequences.

### Route impact and acceptance status
| Route | Shared renderer changes potentially relevant | Required verification |
|---|---|---|
| Home | transaction text, section spacing | dashboard branches, actual layout and navigation |
| Products | form-section spacing | list, edit, images, colors, barcode |
| Sales | transaction text, form sections | invoice full/simple, returns, collection, modal actions |
| Maintenance | transaction text, form sections | photo preview, ready/delivered, quick repairs |
| Suppliers | transaction text, form sections | purchase lines, supplier payments, debts |
| Expenses | transaction text, form sections | expense forms and transaction lists |
| Cashboxes | transaction text, form sections | movements, currency totals, closing |
| Reports | report-filter, transaction text | report dates, partners, filter layout |
| Settings | form sections | settings fields, restore, printer dialogs |

**Not completed:** full screen-by-screen source JS/JSON label/order audit; all modal and animation parity; complete UI/functional regression; Android Studio compilation; Honor Pad X8b screenshot comparison. No APK built. This is a cross-route shared-renderer fix, not certification of nine complete pages.

## Source JavaScript controls audit — 2026-10-30

**Method:** Opened the actual archived `assets/main.js` (approximately 1.05 MB) and extracted original markup `id`, `data-action` and literal heading/label text from the opening 9,000 characters of each of nine `render...` functions. This is a **partial lexical inventory**, not a full function/conditional execution or screen parity test.

| Original function | Verified original action IDs / controls | Implementation test target |
|---|---|---|
| `renderDashboard` | `الوضع المبسط`, `الوضع الكامل`, `مبيع`, `صيانة سريعة`, `استلام أو تسليم مباشر` | Mode selection and quick-entry navigation |
| `renderProducts` | `refresh-products`, `safe-products-import`, `new-product`, `view-product-photo`, `product-label`, `edit-product`, `adjust-product-stock`, `view-product-barcode-photo`, `delete-product` | Search, product images, variants, stock adjustment and original button ordering |
| `renderSales` | `view-sale`, `edit-invoice`, `sale-return`, `correct-sale-payment`, `repair-sale-footprint`, `repair-mixed-sale-cash`, `pdf-sale`, `print-sale`, `collect-online-sale`, `wa-sale`, `thanks-sale`, `delete-sale` | Full/simple invoice, mixed collection, returns, printing and WhatsApp |
| `renderRepairs` | `view-repair-photo`, `view-repair`, `edit-invoice`, `repair-return`, `repair-problem-update`, `repair-status-update`, `pdf-repair`, `print-repair`, `wa-repair`, `thanks-repair`, `repair-ready`, `repair-delivered`, `delete-repair` | Photos, status updates, delivery and print/share |
| `renderPurchases` | `new-supplier`, `supplier-statement`, `supplier-opening-debt`, `supplier-direct-payment`, `edit-supplier`, `delete-supplier`, `pay-purchase`, `cancel-purchase` | Supplier balances, payments, purchases and cancellation |
| `renderExpenses` | `delete-expense`; source heading `المصاريف`, button `＋ مصروف` | Expense form, delete and ledger |
| `renderVaults` | `open-cash-movement`, `cash-movement-form`; source `إقفال اليوم`, `＋ حركة مالية`, `سجل الحركات` | Cash movement modal, currency selectors, daily closing |
| `renderReports` | `report-form`, `report-month`; `اختيار شهر`, `من تاريخ`, `إلى تاريخ`, `تحديث` | Monthly/custom period filters, metric cards and partner reports |
| `renderSettings` | `settings-form`, `partners-settings-form`, `rates-form`, `message-templates-form`, `backup-export-file`, `backup-import-file`, `quick-repair-button-new`, `quick-repair-button-edit`, `backup-auto-now` | Offline backup, partners, quick buttons, printer and settings |

**Source compatibility constraints:** The old JavaScript also contains `external-partners-settings-form`, `test-connection`, `refresh-rates`, `purchase-sync-missing`. Do **not** blindly port these: the two external partners were removed by user decision and the target is offline-only. Preserve the approved three-partner split and existing local backup policy. Keep original display labels for retained functionality.

**Shared renderer commit:** `73c2027b157bf83a6b3863571704a62d3f659bb4` fixes `.form-section:first-child` top margin so the original CSS's zero-margin exception is respected.

**Outstanding:** Complete extraction of every nested conditional and dialog in each function, compare the resulting source structure with `original-structure.json`, inspect the Native Kotlin screen mapping, verify Tajawal and colors/measurements in screenshots, and test every action. No APK build or Honor Pad X8b test performed.

## Cross-page section heading parity and source modal audit — 2026-10-30

**Code commit `4b0a159b2e9e89fff00a79209a994fb126c08eae`:** Native `OriginalPageStructure.kt` now applies original CSS descendant rules to registered child nodes of `.section-title`: `h3` is 15sp bold with zero padding and `.link-button` is 12sp gold-deep `#7a5118` with zero padding. It also sets `.report-filter button` vertical padding to zero, consistent with source `padding:0 12px`. These changes apply to all nine routes wherever those shared source classes occur.

**Original modal CSS reviewed directly from `assets/style.css`:**
- Mobile `.modal-backdrop`: fixed, dim `rgba(62,52,41,.36)`, bottom-aligned; `.modal` bottom-sheet rounded upper corners 22px, width min(100%,680px), max-height90vh, padding18px 15px plus safe area.
- Tablet breakpoint >=720px: backdrop centered with padding25px; modal width min(820px, viewport minus48px).
- Landscape >=1200px: modal width min(900px, viewport minus64px).
- A separate `.modal` rule also specifies width min(660px,100%), max-height viewport minus50px, radius20px; cascade and breakpoint ordering must be checked when implementing. `.modal-head` flex, justify-between, margin-bottom15px; heading `h3` 17px.

**Audit matrix:** Home (dashboard/metric/section title), Products (form/section title), Sales (section titles and modal invoices), Maintenance (quick repairs and modals), Suppliers (purchase forms and dialogs), Expenses (forms and modal), Cashboxes (movement dialogs), Reports (filter and section titles), Settings (forms and modals). The shared section-title changes are implemented; **none of these nine complete screens is yet certified visually**.

**Still outstanding:** Implement/verify actual native modal geometry against original CSS and Android window insets; compare complete original JS conditional markup to native route nodes; compare exact text/order, pictures, all actions and animations; compile APK and test on Honor Pad X8b. No APK build or device test performed in this batch.

## Shared card spacing and typography cross-route patch — 2026-10-30

**Original source independently checked:** `assets/style.css` from tablet v3.9.359 ZIP. Confirmed `.section-card {margin:14px 0; background:var(--paper); border:1px solid var(--line); border-radius:17px}` and `.form-section {margin:20px 0 14px; color:var(--gold-deep); font-size:13px; font-weight:800}`, plus `.form-section:first-child {margin-top:0}`. `--gold-deep` resolves to `#7a5118`.

**Implementation commit `3826d990dd64afa0420dd362b71a3263cb736468`:** shared child layout params now preserve top and bottom 14dp margins for source-class `section-card`, and source-class `form-section` text now uses the original deep gold rather than the lighter accent. Existing first-child top-margin override is retained.

**Affected shared UI:** Cards/headings may be present in home, products, sales, maintenance, suppliers, expenses, cashboxes, reports and settings. This patch is shared, not nine independently verified page implementations.

**Still requires validation:** exact route-by-route labels/order and complete nested JS/JSON markup; section-card clipping and shadow; dialog geometry and transitions; fonts and images in all screens; navigation and every button action; APK compilation and tablet screenshots. No build/device test was executed. 

## Shared transaction row / empty-state source parity — 2026-10-30

Read the original tablet v3.9.359 `assets/style.css` directly from the provided ZIP. Source declarations:
- `.transaction`: flex horizontal, align-items center, gap10px, padding12px 15px, bottom border1px `#f4ede3`; `:last-child` has no border.
- `.transaction-info`: flex1/min-width0, with `strong` and `span` descendant styles.
- `.transaction-icon`: 34x34, radius11, paper-gold background.
- `.transaction-amount`: RTL, 12px, bold.
- `.empty`: padding27px 18px, muted, center, 13px, line-height1.7.

### Implemented and committed
- `485858400dde06685e5d9c305a220973f0b98d71`: Shared generic renderer now switches containers carrying `transaction` source class to horizontal layout with flexible `transaction-info` and intrinsic-width siblings, 10dp gap, 12x15dp padding, original 1dp divider.
- `9becd6551b1b02841f13094da350b42061594d13`: Shared text renderer now applies `empty` source-class muted center alignment, 13sp typography, 1.7 line-height multiplier, 27x18dp padding.

### Remaining source fidelity and validation
The original `.transaction:last-child` divider exception is **not yet implemented**. Original `.transaction` must be checked against actual source-derived JSON node structure to ensure source classes are on the intended container, not just descendants. No screenshots or compilation have been run.

| Route | Cross-page audit checkpoint |
|---|---|
| Home | recent transaction list and empty dashboard cards |
| Products | stock movement rows and no-results state |
| Sales | sales/payment movement rows and empty invoice list |
| Maintenance | maintenance activity rows and no-results state |
| Suppliers | supplier ledger and no-results state |
| Expenses | expense transaction list and empty state |
| Cashboxes | cash movements and empty state |
| Reports | report transaction rows and empty filtered period |
| Settings | empty lists and shared layout consistency |

The table is a regression **test plan**, not evidence that all nine routes have been individually visually verified. Full markup, dialogs, animations, images, button wiring, original labels/order, font asset checks, and Android Studio/Honor Pad X8b validation remain pending.

## Original nine-render-function locations and shared card/row fix — 2026-10-30

**Actual source archive check:** `assets/main.js` in tablet v3.9.359 ZIP contains all nine entry functions. Character offsets in UTF-8-decoded JavaScript (not line numbers): `renderDashboard` 210402; `renderProducts` 327860; `renderSales` 296807; `renderRepairs` 535888; `renderPurchases` 580671; `renderExpenses` 612885; `renderVaults` 626566; `renderReports` 656757; `renderSettings` 688251. These are reference locations for a deeper nested-markup audit, **not** evidence that all their contents were ported.

**CSS cross-check:** Original `.section-card` includes `overflow:hidden`, which the native shared renderer had not applied; `.transaction:last-child` suppresses the 1px bottom rule.

**Code commit `132cebf94b04d320a1d696109541a9eca244363b`:**
- Shared `section-card` now clips children to the rounded outline, matching original overflow behavior.
- Shared vertical container suppresses the trailing transaction row's divider when the final visible child is a `transaction`. This is a structural approximation; dynamic visibility and nested wrappers still require device tests.

**All nine sections:** Home, products, sales, maintenance, suppliers, expenses, cashboxes, reports and settings should be regression-tested for shared card clipping and any transaction list dividers. The above changes do not certify full nine-screen parity.

**Remaining before acceptance:** extract complete source markup/labels/order from the nine JavaScript functions; verify original-structure JSON mapping; compare font asset and full responsive CSS; implement and inspect modals, animations, images, controls; compile APK in Android Studio and compare screenshots on Honor Pad X8b. No APK/device test was performed.

## Integrated shared card layout audit — 2026-10-30

**Original source inspected:** `assets/style.css` from the tablet v3.9.359 ZIP; source declarations `.section-card`, `.section-title`, `.form-card`, `.form-section`, `.transaction` and their descendants. The Android renderer previously treated every `section` as a padded card even when the source class was `section-card`, and injected generic 2dp child margins into header/transaction sequences. These were cross-route layout differences.

### Implemented
- **`027aa0cb3abb07d24c78ff078e81c9ef7751b5b4`**: `section-card` has zero internal padding so the original section title/body determine their own spacing; source `form-card` still retains its 15dp/22dp padding. Removes generic 2dp bottom gap inside `section-title` flex children and normalizes `form-section` to 13sp bold deep gold.
- **`617cbf295693f56c12f146951fe79df1095c9e98`**: shared `childParams` stops adding synthetic bottom margins to `section-title` and `transaction` elements; the original CSS owns their dividers and spacing.

### Nine-section regression matrix

| Section | Shared parity checks after these commits | Not yet verified |
|---|---|---|
| الرئيسية | dashboard cards, section headers, transaction rows | mode switches, all card sizes and animations |
| المنتجات | product form headings, stock movements | variants, image thumbnails, barcode controls, complete field order |
| المبيعات | invoice card sections, collection rows | full/simple invoice dialogs, discount rules, print/WhatsApp |
| الصيانة | repair sections, history rows | photo viewer, quick buttons, delivery dialogs |
| الموردون | supplier card headers, debt and payment rows | source purchase forms and supplier statements |
| المصروفات | expense card and transaction list spacing | all inputs and expense actions |
| الصناديق | cash movement rows and sections | currency balances, daily closing UI |
| التقارير | report sections and transaction list | date filters, partner report layout |
| الإعدادات | form section headings | backup dialogs, all settings and controls |

### Status and constraints
The two commits were written to GitHub branch `native6-development` under `native7` only. This is an **integrated shared-layout patch** with a nine-route regression checklist, **not a completed screen-by-screen comparison**. The original source remains authoritative for retained UI; intentionally excluded external partners and cloud-only features must not be reintroduced. No Android Studio compile, APK build, or Honor Pad X8b screenshot comparison was run. Remaining tasks: full JavaScript/JSON node-by-node audit, exact names/order, modal sizing and animations, image controls, event wiring, and device verification.

## CSS cascade verification and report-grid margins — 2026-10-30

**Read directly from old source archive:** `assets/style.css` (86,920 bytes) and inspected repeated CSS declarations, including later tablet overrides. The final v3.9.23 Honor Pad X8b rules override earlier form-grid breakpoints: `@media (min-width:720px) and (max-width:1199px)` uses two columns and `@media (min-width:1200px) and (orientation:landscape)` uses three columns. This agrees with current native `formGrid` counts. `.report-grid` uses two columns and 9px gap with 13px vertical margins, expanding to three columns at tablet widths. The native grid already matches column count and gap, but the shared vertical layout did not explicitly preserve the 13px outer margins.

**Implemented commits:** `d32964db7049e1e58de1171587b86558a98651b3` added 13dp top and bottom margins to report grids; `7152c12c3ba5458f1ae611c298fb49af46647c86` corrected detection to recognize both the actual `reportGrid` node kind and source CSS `report-grid` class. Both commits are within `native7` on `native6-development`.

**Nine-section test matrix:** Dashboard: summary and reports entry; Products: form grids; Sales: full/simple forms; Repairs: repair form grid; Purchases: supplier/purchase forms; Expenses: expense forms; Cashboxes: movement form; Reports: report-grid tiles and filter; Settings: settings forms. These are affected component types or test targets, **not evidence of verified screen parity**.

**Not completed:** Full nine-screen markup and exact labels/order comparison, responsive modal and transition implementation, image/photo viewers, every button action, font rendering comparison, APK compilation and Honor Pad X8b device test. The report-grid patch is a limited source-based improvement; further shared component work is required.

## Nine-section shared report-tile typography correction — 2026-10-30

Read the tablet v3.9.359 archive `assets/style.css` directly and checked the complete three-rule `.report-tile` block: card 12px padding, paper background, 1px line border, 13px radius; descendant `small` is muted 10px; descendant `b` is bold 14px, 7px top margin and `--gold-deep` (`#7a5118`). The Kotlin renderer previously recognized hypothetical `report-tile-label` and `report-tile-value` classes on text nodes, which cannot substitute for the original descendant selectors when source JSON retains HTML tags.

**Code commits:** `048e3ab148438d3f46b07da7ac6769b0034591fb` now applies typography to source-tagged `small` and `b` children within a `report-tile` container; `e3a4065be71f3a98a1e36a5b19811f6ebbbcfe57` sets `b` to original deep gold.

**Nine-route scope of inspection:** Home (summary metrics), Products (inventory summaries), Sales (invoice summaries), Maintenance (repair summaries), Suppliers (purchase balances), Expenses (expense summaries), Cashboxes (cash balances), Reports (report tiles), Settings (summary cards). These are potential shared renderer usages, not confirmation each page contains a report tile.

**Remaining tests:** Inspect source JSON to verify actual `small`/`b` tag preservation and report-tile child nesting, screenshot typography on the tablet, and run regression for the nine routes. No APK build, Kotlin compile, or device test was performed. Full labels, field order, dialogs, animations and action parity are still pending.

## Section-title border and padding parity — 2026-10-30

**Old source:** Tablet v3.9.359 `assets/style.css` has `.section-title {display:flex;justify-content:space-between;align-items:center;padding:14px 15px;border-bottom:1px solid var(--line)}`; `h3` is 15px bold; `.link-button` is 12px gold-deep. Previous Native 7 fixes removed outer `section-card` padding but did not apply this source header padding/divider, leaving card headers visibly compressed.

**Implemented:** Commit `a01f8a1c3d876f9ff720afc26a2828d9c82ce6ab` applies 15dp horizontal and 14dp vertical padding and draws a 1dp bottom rule using the existing source `line` color on shared `section-title` containers. The existing descendant typography, horizontal layout, and zero outer card padding are preserved.

**Nine-route regression:** Dashboard, products, sales, maintenance, suppliers, expenses, cashboxes, reports, and settings must be inspected wherever source-class `section-title` is used. Check text wrapping, button hit targets, RTL alignment, divider continuity, and clipping against rounded `section-card` edges.

**Pending:** Full per-route original JS markup and names/order audit, modal dimensions/transitions, source animations, photo rendering, action parity, Android Studio compilation and Honor Pad X8b screenshots. This shared fix is not a complete nine-screen implementation; no build/device test was run.

## Cross-route source extraction + shared field typography batch — 2026-10-30

**Direct source verification:** Examined the actual supplied tablet v3.9.359 ZIP `assets/index.html`, `assets/style.css`, `assets/styles.css`, and `assets/main.js`. The index loads **both** CSS files in order: `style.css` then `styles.css?build=3.9.42`. The main stylesheet defines `.field label` as muted 11px with a 6px bottom margin; `.report-filter .field label` overrides it to 10px. The CSS body declares `font-family:Tahoma,Arial,sans-serif`, while the archive also contains `assets/Tajawal-Medium.ttf`. Native 7 intentionally retains the user's explicit **Tajawal** requirement; it is not correct to assert that the old stylesheet itself globally specified Tajawal.

**Code commits:**
- `a01f8a1c3d876f9ff720afc26a2828d9c82ce6ab`: Fixes source `field label` text padding and the shared field child margin: exactly 6dp after a label and no synthetic 6dp after input/select nodes. This prevents double vertical spacing in forms across routes.
- `1f6d83f4a055ace6a48712139d82ac6f61e91579`: Report-filter label descendants use 10sp, muted text and zero padding while retaining the field's original label gap.
- Previous source-derived `a01f8a1c3d876f9ff720afc26a2828d9c82ce6ab` (earlier commit, same prefix but different full hash) restored section-title 14x15 padding and its bottom divider. **Use full hashes when comparing similarly prefixed commits.**

### Nine original render functions inspected
Balanced-brace extraction of each `function renderX()` in `assets/main.js`; the numbers below count **literal** `data-action` attributes in those function bodies, not controls produced by nested helpers or dynamically generated markup.

| Original function | Body characters | Unique literal data-action values |
|---|---:|---:|
| `renderDashboard` | 1,278 | 0 |
| `renderProducts` | 2,969 | 9 |
| `renderSales` | 3,143 | 12 |
| `renderRepairs` | 3,515 | 13 |
| `renderPurchases` | 3,074 | 9 |
| `renderExpenses` | 884 | 1 |
| `renderVaults` | 1,683 | 1 |
| `renderReports` | 4,743 | 0 |
| `renderSettings` | 9,250 | 14 |

**Source controls by route:** Products: `refresh-products`, `safe-products-import`, `new-product`, `view-product-photo`, `product-label`, `edit-product`, `adjust-product-stock`, `view-product-barcode-photo`, `delete-product`. Sales: `view-sale`, `edit-invoice`, `sale-return`, `correct-sale-payment`, `repair-sale-footprint`, `repair-mixed-sale-cash`, `pdf-sale`, `print-sale`, `collect-online-sale`, `wa-sale`, `thanks-sale`, `delete-sale`. Maintenance: `view-repair-photo`, `view-repair`, `edit-invoice`, `repair-return`, `repair-problem-update`, `repair-status-update`, `pdf-repair`, `print-repair`, `wa-repair`, `thanks-repair`, `repair-ready`, `repair-delivered`, `delete-repair`. Suppliers: `new-supplier`, `supplier-statement`, `supplier-opening-debt`, `supplier-direct-payment`, `edit-supplier`, `delete-supplier`, `pay-purchase`, `purchase-sync-missing`, `cancel-purchase`. Expenses: `delete-expense`. Cashboxes: `open-cash-movement`. Settings: `test-connection`, `refresh-rates`, `quick-repair-button-new`, `quick-repair-button-edit`, `quick-repair-button-delete`, `message-templates-reset`, `backup-auto-now`, `backup-auto-latest`, `backup-auto-previous`, `backup-export-file`, `backup-import-file`, `backup-copy`, `backup-show`, `clear-data`. Dashboard and Reports contain no literal `data-action` attributes in their main functions; their controls may be rendered via forms/helpers.

**Deliberate native-only exclusions:** Source `purchase-sync-missing`, `test-connection`, `refresh-rates` are tied to legacy cloud/network functionality and must not be reinstated solely to match old markup. External partners must remain removed.

**Unverified:** Full generated markup including helper functions, exact route labels and order, every dialog and transition, source-vs-native image treatment, device font rendering, action functionality, Android compilation, tablet screenshots. A CSS and static JavaScript audit is not a functional parity test.

## Source-node variant consistency patch — 2026-10-30

**Context:** Native 7's shared renderer supports both source-derived tag metadata (`small`, `b`) and class-labelled text nodes (`report-tile-label`, `report-tile-value`, `form-section`). Previous changes fixed container descendant styling, but class-labelled direct text still used the lighter `gold` variable. This created different colors depending on which original structure representation a route used.

**Code changes:** `555db13ced34daa6c23af6a61f2f1e4d33dc1bc2` unifies direct `form-section` and `report-tile-value` to `#7a5118`, matching the source CSS deep-gold color. `2bb8ac53a4c747527b69e0166947cf0de78add50` removes generic text padding from `small.report-tile-label` nodes to avoid adding space that the original source does not specify. Earlier container-level `report-tile small/b` handling remains intact.

**Cross-route audit checklist:** Main/home metrics, product summaries, sales summaries, repair summaries, supplier balances, expense totals, cashbox totals, report tiles and settings summary text. Check that direct class and tag-descendant representations render identically. This is a shared-code consistency fix, not evidence every route has been visually tested.

**Pending:** Complete exact source field/control order, modal geometry and animation, photo rendering, responsive RTL alignment, click action parity, build verification and Honor Pad X8b screenshots. No build or device tests were run.
