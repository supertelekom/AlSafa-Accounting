package com.alsafa.native6

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.text.InputType
import java.util.concurrent.Executors
import java.time.YearMonth

/** Android Views only. Palette, terminology and mode chooser taken from v3.9.353. */
open class MainActivity:Activity() {
    companion object {
        const val EXTRA_PAGE="com.alsafa.native6.PAGE"
        const val EXTRA_PRODUCT_ID="com.alsafa.native6.PRODUCT_ID"
    }
    private lateinit var store:Store
    private lateinit var body:LinearLayout
    private lateinit var font:Typeface
    private val io=Executors.newSingleThreadExecutor()
    private val imageIo=Executors.newFixedThreadPool(2)
    private var page="home"
    @Volatile private var busy=false
    private var pdfInvoice:String?=null
    private val basket=mutableListOf<SaleItem>()
    private var basketCurrency=Currency.USD
    private var lastScrollY=0
    private var contentScroll:ScrollView?=null
    private val visibleInputs=mutableListOf<EditText>()
    private var savedInputs:ArrayList<String>?=null
    private var barcodeTarget:EditText?=null
    private var chooserBarcodeField:EditText?=null
    private var chooserRefresh:(()->Unit)?=null
    private var photoProductId:String?=null
    private var photoRepairId:String?=null
    private var intakePhotoPath:String?=null
    private var intakePhotoStatus:TextView?=null
    private var intakePhotoPreview:ImageView?=null
    private val ink=Color.rgb(62,52,41)
    private val gold=Color.rgb(169,121,41)
    private val vaultNames=listOf("درج المحل","الصندوق الخارجي","شام كاش","البنك")
    override fun onCreate(state:Bundle?) {super.onCreate(state);store=Store(StoreDb(this));font=try { Typeface.createFromAsset(assets,"Tajawal-Medium.ttf") } catch (_:Exception) { Typeface.DEFAULT };page=state?.getString("page")?:intent.getStringExtra(EXTRA_PAGE)?:"home";if(page=="stock-history")intent.getStringExtra(EXTRA_PRODUCT_ID)?.let { productId -> selectedProduct=try { store.product(productId) } catch (_:Exception) { null } };pdfInvoice=state?.getString("pdfInvoice");photoProductId=state?.getString("photoProductId");photoRepairId=state?.getString("photoRepairId");intakePhotoPath=state?.getString("intakePhotoPath");state?.let { saved ->
        basketCurrency=try { Currency.valueOf(saved.getString("basketCurrency")?:"USD") } catch (_:Exception) { Currency.USD }
        val ids=saved.getStringArrayList("basketProductIds")
        val quantities=saved.getLongArray("basketQuantities")
        val prices=saved.getLongArray("basketUnitPrices")
        if(ids!=null && quantities!=null && prices!=null && ids.size==quantities.size && ids.size==prices.size) {
            ids.indices.forEach { i -> basket.add(SaleItem(ids[i],quantities[i],prices[i])) }
        }
        lastScrollY=saved.getInt("contentScrollY",0)
        savedInputs=saved.getStringArrayList("visibleInputs")
    };safeRender();contentScroll?.post { contentScroll?.scrollTo(0,lastScrollY) };if(this !is SectionActivity && store.scalar("SELECT count(*) FROM events")>0L)io.execute{Store.vaults.forEach{vault->try{store.autoCloseMissingDays(vault,31)}catch(e:Exception){runOnUiThread{if(!isFinishing&&!isDestroyed)Toast.makeText(this,"تعذر الإقفال التلقائي للصندوق $vault: ${e.message}",Toast.LENGTH_LONG).show()}}};runOnUiThread{if(!isFinishing&&!isDestroyed && page=="vaults")safeRender()}}}
    override fun onNewIntent(newIntent:Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        if(this !is SectionActivity) {
            page="home"
            safeRender()
        }
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    // Native interpretation of the v3.9.359 CSS tokens (no WebView).
    private val paper get()=Color.rgb(255,253,249)
    private val cream get()=Color.rgb(248,243,235)
    private val sand get()=Color.rgb(239,228,208)
    private val muted get()=Color.rgb(138,123,108)
    private val lineColor get()=Color.rgb(234,223,206)
    private fun style(v:TextView){
        v.typeface=font
        v.setTextColor(ink)
        v.textSize=14f
        v.setPadding(dp(12),dp(9),dp(12),dp(9))
        if(v is EditText){
            v.textSize=15f
            v.setSingleLine(true)
            v.background=background(paper)
            v.setPadding(dp(14),dp(12),dp(14),dp(12))
        }
    }
    private fun background(color:Int=paper,radius:Int=14)=GradientDrawable().apply{
        setColor(color);cornerRadius=dp(radius).toFloat();setStroke(dp(1),lineColor)
    }
    private fun goldButtonBackground()=GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(179,131,50),Color.rgb(139,96,31))
    ).apply { cornerRadius=dp(12).toFloat() }
    private fun column()=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        layoutDirection=View.LAYOUT_DIRECTION_RTL
        setPadding(dp(15),dp(10),dp(15),dp(10))
    }
    private fun text(value:String,parent:LinearLayout=body,size:Float=18f)=TextView(this).also{
        style(it);it.textSize=size;it.text=value
        if(size<=15f)it.setTextColor(muted)
        parent.addView(it)
    }
    private fun button(label:String,parent:LinearLayout=body,action:()->Unit)=Button(this).also{
        style(it)
        it.text=label
        it.textSize=13f
        it.isAllCaps=false
        val primary=label.startsWith("＋") || label.startsWith("⚡") || label.startsWith("◆") ||
            label.startsWith("استلام صيانة") || label.startsWith("حفظ")
        it.background=if(primary)goldButtonBackground() else background(paper,14)
        it.setTextColor(if(primary)Color.WHITE else ink)
        it.gravity=Gravity.CENTER_VERTICAL or Gravity.RIGHT
        it.setPadding(dp(13),0,dp(13),0)
        it.minHeight=dp(44)
        it.minimumHeight=dp(44)
        // One consistent, touch-accessible button height on every redraw.
        // Avoid the previous 52dp height plus 10dp of outer margins.
        parent.addView(it,LinearLayout.LayoutParams(-1,dp(46)).apply{setMargins(0,dp(3),0,dp(3))})
        it.setOnClickListener{if(!busy)action()}
    }
    private fun field(label:String,parent:LinearLayout,value:String="",numeric:Boolean=false):EditText {text(label,parent,15f);return EditText(this).also{style(it);it.setSingleLine();if(numeric)it.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;val isScreen=::body.isInitialized && parent===body;val restored=if(isScreen)savedInputs?.getOrNull(visibleInputs.size) else null;it.setText(restored?:value);if(isScreen)visibleInputs.add(it);parent.addView(it)}}
    private fun select(label:String,items:List<String>,parent:LinearLayout):Spinner {text(label,parent,15f);return Spinner(this).also{it.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items);parent.addView(it)}}
    private fun form(title:String,submit:String="حفظ",build:(LinearLayout)->(()->Unit)) {
        val layout=column().apply { setPadding(dp(20),dp(14),dp(20),dp(18));setBackgroundColor(paper) };val action=build(layout);val scroll=ScrollView(this);scroll.addView(layout)
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("رجوع",null).setPositiveButton(submit,null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(busy)return@setOnClickListener
                try {
                    action()
                    dialog.dismiss()
                } catch(e:Exception) { error(e) }
            }
        }
        dialog.show()
        // setOnShowListener is invoked by show(); keep validation errors on screen.
    }
    private fun error(e:Exception){AlertDialog.Builder(this).setTitle("لم تُحفظ العملية").setMessage(e.message?:"حدث خطأ").setPositiveButton("حسناً",null).show()}
    private fun work(task:()->Unit){if(busy){android.util.Log.w("AlSafaRestore","Background action ignored because another action is still busy");Toast.makeText(this,"العملية السابقة لم تنتهِ بعد، انتظر قليلاً",Toast.LENGTH_LONG).show();return};busy=true;try{io.execute{try{task();runOnUiThread{busy=false;if(!isFinishing&&!isDestroyed){Toast.makeText(this,"اكتملت العملية",Toast.LENGTH_SHORT).show();safeRender()}}}catch(e:Exception){runOnUiThread{busy=false;if(!isFinishing&&!isDestroyed)error(e)}}}}catch(e:java.util.concurrent.RejectedExecutionException){busy=false;if(!isFinishing&&!isDestroyed)error(e)}}
    /** Every destination is a separate native Activity instance and owns its content. */
    private fun go(value:String){
        if(value==page)return
        val next=Intent(this,SectionActivity::class.java).putExtra(EXTRA_PAGE,value)
        if(value=="stock-history")selectedProduct?.let { next.putExtra(EXTRA_PRODUCT_ID,it.id) }
        startActivity(next)
    }
    private fun backPage(){ finish() }
    private fun homePage(){
        if(this is SectionActivity) {
            startActivity(Intent(this,TabletDashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        } else {
            page="home"
            safeRender()
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==71){
            val path=data?.getStringExtra(MediaCaptureActivity.EXTRA_RESULT_PATH)
            if(resultCode==RESULT_OK && !path.isNullOrBlank()){
                try { localPhoto(path) } catch(e:Exception) { error(e);return }
                val previewBitmap=runCatching {
                    val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeFile(path,options)
                    require(options.outWidth>0 && options.outHeight>0) { "الصورة غير قابلة للقراءة" }
                    var sample=1
                    while(maxOf(options.outWidth,options.outHeight)/sample>800) sample*=2
                    android.graphics.BitmapFactory.decodeFile(path,
                        android.graphics.BitmapFactory.Options().apply { inSampleSize=sample })
                        ?: error("تعذر معاينة الصورة")
                }.getOrElse { error(it);return }
                intakePhotoPath=path
                intakePhotoStatus?.text="تم اختيار صورة الساعة"
                intakePhotoPreview?.setImageBitmap(previewBitmap)
            }
            return
        }
        if(requestCode==70){
            val path=data?.getStringExtra(MediaCaptureActivity.EXTRA_RESULT_PATH)
            val id=photoRepairId
            photoRepairId=null
            if(resultCode==RESULT_OK && !path.isNullOrBlank() && id!=null) {
                try { localPhoto(path);work{store.setRepairImage(id,path)} }
                catch(e:Exception){error(e)}
            }
            return
        }
        if(requestCode==69){
            val path=data?.getStringExtra(MediaCaptureActivity.EXTRA_RESULT_PATH)
            val productId=photoProductId
            photoProductId=null
            if(resultCode==RESULT_OK && !path.isNullOrBlank() && productId!=null){
                try { localPhoto(path);work{store.appendProductImage(productId,path)} }
                catch(e:Exception){error(e)}
            }
            return
        }
        if(requestCode in 101..105){handleDocumentResult(requestCode,resultCode,data);return}
        if(resultCode!=RESULT_OK || requestCode !in listOf(65,66,67,68))return
        val code=data?.getStringExtra(BarcodeScanActivity.EXTRA_BARCODE)?.trim().orEmpty()
        if(code.isEmpty())return
        if(requestCode==67){
            barcodeTarget?.setText(code)
            barcodeTarget?.setSelection(barcodeTarget?.text?.length ?: 0)
            barcodeTarget=null
            return
        }
        if(requestCode==68){
            chooserBarcodeField?.setText(code)
            chooserBarcodeField?.setSelection(chooserBarcodeField?.text?.length ?: 0)
            chooserRefresh?.invoke()
            return
        }
        val matches=store.products(code).filter { it.barcode==code }
        when {
            matches.size>1 -> AlertDialog.Builder(this).setTitle("باركود مكرر")
                .setMessage("يوجد أكثر من صنف بنفس الباركود: $code. يرجى تصحيح البيانات.")
                .setPositiveButton("حسنًا",null).show()
            matches.isEmpty() -> AlertDialog.Builder(this).setTitle("لم يُعثر على الصنف")
                .setMessage("الباركود: $code")
                .setPositiveButton("حسنًا",null).show()
            requestCode==65 -> productForm(matches.first())
            matches.first().stock<=0 -> AlertDialog.Builder(this).setTitle("نفاد المخزون")
                .setMessage("الصنف ${matches.first().name} غير متوفر في المخزون.")
                .setPositiveButton("حسنًا",null).show()
            else -> addSaleProduct(matches.first())
        }
    }
    private fun restoreWithProgress(payload:String) {
        if(busy) {
            Toast.makeText(this,"انتظر اكتمال العملية السابقة",Toast.LENGTH_LONG).show()
            return
        }
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(22),dp(16),dp(22),dp(16))
        }
        val status=TextView(this).apply {
            style(this)
            text="التحضير للاستعادة..."
            textDirection=View.TEXT_DIRECTION_RTL
        }
        val bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=100
            progress=0
            isIndeterminate=false
        }
        val percent=TextView(this).apply {
            style(this)
            text="0%"
        }
        layout.addView(status)
        layout.addView(bar,LinearLayout.LayoutParams(-1,dp(14)))
        layout.addView(percent)
        val dialog=AlertDialog.Builder(this)
            .setTitle("استعادة بيانات الصفا")
            .setView(layout)
            .setCancelable(false)
            .create()
        dialog.show()
        work {
            var lastPercent=-1
            var lastDescription=""
            var lastUpdateAt=0L
            try {
                LegacySnapshotImport.import(store,payload,this@MainActivity) { value,description ->
                    val current=value.coerceIn(0,99)
                    val now=android.os.SystemClock.uptimeMillis()
                    if((current!=lastPercent || description!=lastDescription) && now-lastUpdateAt>=120L) {
                        lastPercent=current
                        lastDescription=description
                        lastUpdateAt=now
                        runOnUiThread {
                            if(!isFinishing&&!isDestroyed && dialog.isShowing) {
                                bar.progress=current
                                percent.text=current.toString()+"%"
                                status.text=description
                            }
                        }
                    }
                }
                runOnUiThread {
                    if(!isFinishing&&!isDestroyed && dialog.isShowing) {
                        bar.progress=100
                        percent.text="100%"
                        status.text="اكتملت الاستعادة والتحقق"
                        dialog.dismiss()
                    }
                }
            } catch(e:Throwable) {
                runOnUiThread { if(dialog.isShowing) dialog.dismiss() }
                if(e is Exception) throw e
                throw IllegalStateException("فشل الاستعادة بسبب خطأ حرج: ${e.message}",e)
            }
        }
    }
    private fun label(value:String)=mapOf("sale" to "مبيع", "purchase" to "شراء", "repair" to "صيانة", "posted" to "مسجلة", "cancelled" to "ملغاة", "received" to "مستلمة", "in_progress" to "قيد الصيانة", "legacy_unknown" to "حالة قديمة تحتاج مراجعة", "ready" to "جاهزة", "delivered" to "تم التسليم")[value]?:value
    private fun money(n:Long,c:Currency)=Money(n,c).display()
    private fun amount(e:EditText,c:Currency)=Money.parse(e.text.toString(),c).minor
    override fun onDestroy() {
        io.shutdown()
        imageIo.shutdownNow()
        super.onDestroy()
    }
    override fun onSaveInstanceState(out:Bundle){
        out.putString("page",page)
        out.putString("pdfInvoice",pdfInvoice)
        out.putString("photoProductId",photoProductId)
        out.putString("photoRepairId",photoRepairId)
        out.putString("intakePhotoPath",intakePhotoPath)
        out.putString("basketCurrency",basketCurrency.name)
        out.putStringArrayList("basketProductIds",ArrayList(basket.map { it.productId }))
        out.putLongArray("basketQuantities",basket.map { it.qty }.toLongArray())
        out.putLongArray("basketUnitPrices",basket.map { it.unitPrice }.toLongArray())
        out.putInt("contentScrollY",contentScroll?.scrollY?:0)
        out.putStringArrayList("visibleInputs",ArrayList(visibleInputs.map { it.text.toString() }))
        super.onSaveInstanceState(out)
    }
    override fun onBackPressed(){if(this is SectionActivity)finish() else super.onBackPressed()}
    private fun safeRender() {
        try { render() } catch (e: Exception) {
            page="home"
            val root=column()
            setContentView(root)
            text("تعذر فتح الشاشة المطلوبة",root,22f)
            text(e.message ?: "حدث خطأ أثناء قراءة البيانات المحلية",root,15f)
            button("العودة إلى الرئيسية",root) { homePage() }
            button("تصدير نسخة احتياطية",root) {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type="application/json"
                    putExtra(Intent.EXTRA_TITLE,"AlSafa-Native6-recovery-${Store.today()}.json")
                },101)
            }
        } finally {
            savedInputs=null
        }
    }
    private fun render(){
        visibleInputs.clear()
        val root=column();root.setBackgroundColor(Color.rgb(248,243,235));setContentView(root)
        if(page=="home") {
            val header=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                layoutDirection=View.LAYOUT_DIRECTION_RTL
                gravity=Gravity.CENTER_VERTICAL
                setPadding(dp(18),dp(16),dp(18),dp(16))
                background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(249,243,232),Color.rgb(234,217,185)))
            }
            val mark=TextView(this).apply {
                text="◷"
                textSize=25f
                gravity=Gravity.CENTER
                setTextColor(Color.rgb(122,81,24))
                background=GradientDrawable().apply {
                    setColor(Color.rgb(249,243,232))
                    cornerRadius=dp(24).toFloat()
                    setStroke(dp(2),gold)
                }
            }
            header.addView(mark,LinearLayout.LayoutParams(dp(44),dp(44)).apply{marginEnd=dp(11)})
            val brand=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            text("الصفا للساعات",brand,19f).apply {
                setTextColor(Color.rgb(122,81,24));setPadding(0,0,0,0)
            }
            text("ساعات • صيانة • محاسبة محلية",brand,11f).apply {
                setPadding(0,dp(3),0,0)
            }
            header.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
            root.addView(header,LinearLayout.LayoutParams(-1,-2))
        } else {
            val titles=mapOf(
                "simple" to "الوضع المبسط","advanced" to "الوضع الكامل",
                "products" to "المنتجات وقطع الصيانة","inventory" to "المخزون",
                "stock-history" to "حركات المخزون","stocktake-history" to "سجل الجرد",
                "sale" to "المبيع","invoices" to "الفواتير",
                "vaults" to "الخزنات والإقفال","suppliers" to "الموردون والمشتريات",
                "repairs" to "الصيانة","reports" to "التقارير الشهرية",
                "period-reports" to "تقارير الفترة","trial-balance" to "ميزان المراجعة",
                "account-ledger" to "دفتر الحساب","journal-integrity" to "تدقيق القيود",
                "structural-audit" to "التدقيق المحاسبي","partners" to "الشركاء",
                "backup" to "النسخ الاحتياطي","legacy-audit" to "تدقيق الأرشيف",
                "legacy-history" to "الأرشيف القديم","audit" to "سجل العمليات"
            )
            val header=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                layoutDirection=View.LAYOUT_DIRECTION_RTL
                gravity=Gravity.CENTER_VERTICAL
            }
            header.setPadding(dp(10),dp(4),dp(10),dp(4))
            header.background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(250,245,236),Color.rgb(240,226,200)))
            root.addView(header,LinearLayout.LayoutParams(-1,dp(66)))
            val back=Button(this).also {
                style(it);it.text="رجوع";it.isAllCaps=false;it.background=background()
                header.addView(it,LinearLayout.LayoutParams(dp(100),dp(56)))
                it.setOnClickListener { if(!busy)backPage() }
            }
            text(titles[page] ?: "الصفا للساعات",header,22f).apply {
                setTextColor(gold)
                gravity=Gravity.CENTER_VERTICAL
                layoutParams=LinearLayout.LayoutParams(0,dp(60),1f)
            }
            Button(this).also {
                style(it);it.text="الرئيسية";it.isAllCaps=false;it.background=background()
                header.addView(it,LinearLayout.LayoutParams(dp(116),dp(56)))
                it.setOnClickListener { if(!busy)homePage() }
            }
        }
        val scroll=ScrollView(this).apply {
            isFillViewport=true
            clipToPadding=false
            setBackgroundColor(cream)
        }
        contentScroll=scroll
        body=column().apply {
            val tablet=resources.configuration.smallestScreenWidthDp>=600
            val horizontal=if(tablet)dp(28) else dp(12)
            setPadding(horizontal,dp(15),horizontal,dp(24))
        }
        scroll.addView(body)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        when(page){"home"->home();"simple"->simple();"advanced"->advanced();"products"->products();"maintenance-parts"->products("part");"inventory"->inventoryDashboard();"stock-history"->if(selectedProduct!=null)stockHistory(selectedProduct!!) else {text("لم يتم اختيار منتج لعرض حركاته.");button("العودة إلى المنتجات"){go("products")}};"stocktake-history"->stocktakeHistoryScreen();"sale"->sale();"invoices"->invoices();"vaults"->vaults();"suppliers"->suppliers();"repairs"->repairs();"reports"->reports();"period-reports"->periodReports();"trial-balance"->trialBalanceScreen();"account-ledger"->accountLedgerScreen();"journal-integrity"->journalIntegrityScreen();"structural-audit"->structuralAuditScreen();"partners"->partners();"backup"->backup();"legacy-audit"->legacyAudit();"legacy-history"->legacyHistory();"audit"->audit();else->{text("هذه الصفحة غير متاحة في النسخة الحالية.");button("العودة إلى الرئيسية"){homePage()}}}
    }
    private fun legacyHistory() {
        text("أرشيف العمليات القديمة — قراءة فقط",size=25f)
        text("هذه السجلات لم تُرحّل كفواتير جديدة، ولا تدخل في رصيد الصندوق أو الأرباح.",size=15f)
        val categories=listOf("sales","repairs","purchases","payments","cashTransactions",
            "supplierLedger","partnerWithdrawals","expenses","returns","dailyClosings",
            "inventoryMovements","productCostHistory","auditLog","loyaltyLedger",
            "smartReceipts","discountRules","productDetailOptions")
        val categoryLabels=listOf("المبيعات","الصيانة","المشتريات","الدفعات","حركات الصناديق",
            "حساب الموردين","سحوبات الشركاء","المصاريف","المرتجعات","الإقفالات اليومية",
            "حركات المخزون","تاريخ تكلفة المنتجات","سجل التدقيق","سجل الولاء",
            "الإيصالات الذكية","قواعد الخصومات","خيارات تفاصيل المنتجات")
        button("ديون العملاء والموردين القديمة حسب الاسم",body) {
            work {
                val message=LegacyCounterpartyDebt.summary(store)
                runOnUiThread {
                    if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                        .setTitle("ديون تاريخية حسب العميل والمورد — قراءة فقط")
                        .setMessage(message)
                        .setPositiveButton("إغلاق",null).show()
                }
            }
        }
        button("تقرير الوضع المالي للنسخة المستوردة",body) {
            work {
                val message=LegacyArchivedFinancialPosition.summary(store)
                runOnUiThread {
                    if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                        .setTitle("مطابقة مالية تاريخية — قراءة فقط")
                        .setMessage(message).setPositiveButton("إغلاق",null).show()
                }
            }
        }
        button("مطابقة أرصدة الفواتير القديمة",body) {
            work {
                val sales=LegacyInvoiceSnapshot.page(store,"sales",limit=1)
                val purchases=LegacyInvoiceSnapshot.page(store,"purchases",limit=1)
                val integrity=LegacyArchiveIntegrity.inspect(store)
                val report="فواتير المبيع: ${sales.getInt("total")}\n"+
                    "ديون العملاء بحسب عملة الفاتورة: ${sales.getJSONObject("outstandingByCurrency")}\n"+
                    "فواتير الشراء: ${purchases.getInt("total")}\n"+
                    "ديون فواتير الموردين: ${purchases.getJSONObject("outstandingByCurrency")}\n"+
                    "سلامة عدد السجلات المستوردة: ${if(integrity.optBoolean("complete")) "مطابقة" else "تحتاج مراجعة"}\n"+
                    "تنبيه: أرصدة دفتر الموردين التاريخية منفصلة، ولم تُضف هنا لتجنب التكرار. جميع القيم للقراءة فقط."
                runOnUiThread {
                    if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                        .setTitle("تقرير مطابقة النسخة القديمة")
                        .setMessage(report).setPositiveButton("إغلاق",null).show()
                }
            }
        }
        val choice=select("نوع السجل",categoryLabels,body)
        val search=field("بحث في بيانات السجل القديم",body)
        val unpaidFrom=field("من تاريخ (YYYY-MM-DD)",body)
        val unpaidTo=field("إلى تاريخ (YYYY-MM-DD)",body)
        val unpaidCurrency=select("عملة الفواتير غير المسددة",listOf("جميع العملات","USD","TRY","SYP"),body)
        val unpaidPage=field("رقم الصفحة (1، 2، 3...)",body)
        val supplierPage=field("صفحة الموردين القديمة (1، 2، 3...)",body)
        val ledgerSupplier=field("معرف المورد لتصفية دفتره القديم (اختياري)",body)
        val ledgerPage=field("صفحة دفتر الموردين القديم",body)
        button("دفتر الموردين القديم (قراءة فقط)",body) {
            val supplierId=ledgerSupplier.text.toString().trim()
            val number=ledgerPage.text.toString().trim().ifEmpty { "1" }.toIntOrNull()
            if(number==null || number !in 1..100000) {
                AlertDialog.Builder(this).setMessage("رقم صفحة غير صالح")
                    .setPositiveButton("إغلاق",null).show()
                return@button
            }
            work {
                val report=LegacySupplierLedgerView.page(
                    store,supplierId=supplierId,offset=(number-1)*30)
                val items=report.getJSONArray("records")
                val lines=(0 until items.length()).joinToString("\\n") { index ->
                    val entry=items.getJSONObject(index)
                    "${entry.optString("date")} | ${entry.optString("supplierId")} | ${entry.optString("kind")} | ${entry.optString("amount")} ${entry.optString("currency")}"
                }
                val message="القيود: ${report.getInt("totalMatching")}\\n"+
                    "الإجماليات حسب النوع والعملة: ${report.getJSONObject("totalsByKindAndCurrency")}\\n"+
                    lines+"\\nلا يوجد ترحيل مالي."
                runOnUiThread {
                    if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                        .setTitle("دفتر الموردين التاريخي")
                        .setMessage(message).setPositiveButton("إغلاق",null).show()
                }
            }
        }
        button("الموردون من النسخة القديمة (قراءة فقط)",body) {
            val searchText=search.text.toString().trim()
            val pageNumber=supplierPage.text.toString().trim().ifEmpty { "1" }.toIntOrNull()
            if(pageNumber==null || pageNumber !in 1..100000) {
                AlertDialog.Builder(this).setMessage("رقم صفحة الموردين غير صالح").setPositiveButton("إغلاق",null).show()
                return@button
            }
            work {
                val report=LegacySupplierArchive.page(store,search=searchText,offset=(pageNumber-1)*30)
                val records=report.getJSONArray("records")
                val lines=(0 until records.length()).joinToString("\\n") { index ->
                    val item=records.getJSONObject(index)
                    "${item.optString("name")} | ${item.optString("phone")} | ${item.optString("id")}"
                }
                val message="الموردون المطابقون: ${report.getInt("totalMatching")}\\n"+
                    lines+"\\n\\nبيانات للقراءة فقط؛ لا يوجد ترحيل مالي."
                runOnUiThread {
                    if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                        .setTitle("الموردون المستوردون")
                        .setMessage(message).setPositiveButton("إغلاق",null).show()
                }
            }
        }
        button("تدقيق أرصدة الخزنات القديمة",body) {
            work {
                val report=LegacyCashActivationPlan.inspect(store)
                val message=LegacyCashReportFormatter.format(report)
                runOnUiThread {
                    if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                        .setTitle("أرصدة الخزنات - دون ترحيل")
                        .setMessage(message)
                        .setPositiveButton("إغلاق",null).show()
                }
            }
        }
        button("الفواتير القديمة غير المسددة (قراءة فقط)",body) {
            val queryText=search.text.toString().trim()
            val dateFrom=unpaidFrom.text.toString().trim()
            val dateTo=unpaidTo.text.toString().trim()
            val unit=listOf("","USD","TRY","SYP")[unpaidCurrency.selectedItemPosition.coerceIn(0,3)]
            val pageNumber=unpaidPage.text.toString().trim().ifEmpty { "1" }.toIntOrNull()
            if(pageNumber==null || pageNumber !in 1..100000) {
                AlertDialog.Builder(this).setMessage("أدخل رقم صفحة صحيحاً من 1 إلى 100000").setPositiveButton("إغلاق",null).show()
                return@button
            }
            val invoiceOffset=(pageNumber-1)*20
            if(dateFrom.isNotEmpty() || dateTo.isNotEmpty()) {
                val valid=runCatching {
                    if(dateFrom.isNotEmpty()) java.time.LocalDate.parse(dateFrom)
                    if(dateTo.isNotEmpty()) java.time.LocalDate.parse(dateTo)
                    require(dateFrom.isEmpty() || dateTo.isEmpty() || dateFrom<=dateTo)
                }.isSuccess
                if(!valid) {
                    AlertDialog.Builder(this).setMessage("تأكد من صحة تاريخ البداية والنهاية وترتيبهما بصيغة YYYY-MM-DD").setPositiveButton("إغلاق",null).show()
                    return@button
                }
            }
            work {
                val sales=LegacyOutstandingInvoices.page(store,"sales",offset=invoiceOffset,limit=20,query=queryText,from=dateFrom,to=dateTo,currency=unit)
                val purchases=LegacyOutstandingInvoices.page(store,"purchases",offset=invoiceOffset,limit=20,query=queryText,from=dateFrom,to=dateTo,currency=unit)
                fun describe(name:String, data:org.json.JSONObject):String {
                    val records=data.getJSONArray("records")
                    val lines=(0 until records.length()).joinToString("\\n") { index ->
                        val item=records.getJSONObject(index)
                        "${item.optString("invoiceId")} | ${item.optString("date")} | ${item.optString("customer").ifBlank { item.optString("supplierName") }} | الإجمالي ${item.optString("total")} | المدفوع ${item.optString("paidSnapshot")} | المتبقي ${item.optString("outstandingSnapshot")} ${item.optString("currency")}"
                    }
                    return "$name: ${data.getInt("totalOutstandingInvoices")} فاتورة | صفحة $pageNumber | التالية: ${if(data.optBoolean("hasMore")) "متاحة" else "لا توجد"}\\n"+
                        "المتبقي بحسب العملة: ${data.getJSONObject("outstandingTotalsByCurrency")}\\n"+lines
                }
                val message=describe("المبيعات",sales)+"\\n\\n"+describe("المشتريات",purchases)+
                    "\\n\\nالأرصدة أولية للقراءة فقط، دون ترحيل مالي."
                runOnUiThread {
                    if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                        .setTitle("الفواتير غير المسددة من الأرشيف")
                        .setMessage(message).setPositiveButton("إغلاق",null).show()
                }
            }
        }
        val results=column()
        body.addView(results)
        var offset=0
        var previousCategory=-1
        var previousTerm=""
        fun refresh() {
            if(previousCategory!=choice.selectedItemPosition) { offset=0;previousCategory=choice.selectedItemPosition }
            results.removeAllViews()
            val category=categories[choice.selectedItemPosition.coerceIn(categories.indices)]
            val term=search.text.toString().trim()
            if(term!=previousTerm) { offset=0;previousTerm=term }
            val total=if(category=="repairs") {
                if(term.isBlank()) store.scalar("SELECT count(*) FROM repairs")
                else store.scalar("SELECT count(*) FROM repairs WHERE instr(details,?)>0",term)
            } else {
                if(term.isBlank()) store.scalar("SELECT count(*) FROM legacy_records WHERE category=?",category)
                else store.scalar("SELECT count(*) FROM legacy_records WHERE category=? AND instr(payload,?)>0",category,term)
            }
            if(total==0L) offset=0
            else if(offset.toLong()>=total) offset=((total-1)/30*30).toInt()
            val entries=if(category=="repairs") store.repairPage(term,30,offset)
                else store.legacyPage(category,term,30,offset)
            text("الصفحة ${offset/30+1} من ${((total+29)/30).coerceAtLeast(1)} · مجموع السجلات $total",results,14f)
            if(entries.isEmpty()) text("لا توجد سجلات مطابقة للبحث.",results,16f)
            entries.forEach { record ->
                val raw=record["payload"].orEmpty()
                val obj=try { org.json.JSONObject(raw) } catch (_:Exception) { org.json.JSONObject() }
                val name=obj.optString("customer").ifBlank {
                    obj.optString("customerName").ifBlank {
                        obj.optString("supplierName").ifBlank { obj.optString("partnerName") }
                    }
                }
                val number=obj.optString("invoiceNo")
                val date=obj.optString("date").ifBlank { obj.optString("createdAt").take(10) }
                val amount=obj.optString("total").ifBlank { obj.optString("totalUSD").ifBlank { obj.optString("amount") } }
                val status=if(category=="repairs") obj.optString("status") else ""
                val currency=obj.optString("currency")
                val summary=listOf(number,date,name,amount,currency,status).filter { it.isNotBlank() }
                    .joinToString(" · ").ifBlank { record["legacy_id"].orEmpty() }
                button(summary.take(110),results) {
                    AlertDialog.Builder(this).setTitle("السجل الأصلي · $category")
                        .setMessage(obj.toString(2).take(15000))
                        .setPositiveButton("إغلاق",null).show()
                }
            }
        }
        choice.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) { refresh() }
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
        }
        button("بحث / تحديث"){ offset=0;refresh() }
        button("الصفحة السابقة"){ offset=(offset-30).coerceAtLeast(0);refresh() }
        button("الصفحة التالية"){ if(offset<=Int.MAX_VALUE-30) offset+=30;refresh() }
        refresh()
    }
    /** Tablet dashboard tiles, built with native views and the original warm AlSafa palette. */
    private fun dashboardTiles(items:List<Triple<String,String,String>>) {
        val tablet=resources.configuration.smallestScreenWidthDp>=600
        val columns=if(tablet)3 else 2
        var grid:LinearLayout?=null
        items.forEachIndexed { index,item ->
            if(index%columns==0) {
                grid=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                    gravity=Gravity.TOP
                }
                body.addView(grid,LinearLayout.LayoutParams(-1,-2))
            }
            val tile=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                gravity=Gravity.CENTER
                setPadding(dp(10),dp(17),dp(10),dp(14))
                background=background(paper,16)
                isClickable=true
                isFocusable=true
                contentDescription=item.second
            }
            val glyph=TextView(this).apply {
                text=item.first
                textSize=27f
                gravity=Gravity.CENTER
                setTextColor(gold)
                typeface=font
            }
            tile.addView(glyph,LinearLayout.LayoutParams(-1,dp(42)))
            val title=TextView(this).apply {
                text=item.second
                textSize=13f
                typeface=font
                setTextColor(ink)
                gravity=Gravity.CENTER
                maxLines=2
            }
            tile.addView(title,LinearLayout.LayoutParams(-1,dp(44)))
            tile.setOnClickListener { if(!busy)go(item.third) }
            grid!!.addView(tile,LinearLayout.LayoutParams(0,dp(123),1f).apply {
                setMargins(dp(4),dp(5),dp(4),dp(5))
            })
        }
        val remaining=items.size%columns
        if(remaining!=0) repeat(columns-remaining) {
            grid?.addView(android.view.View(this),LinearLayout.LayoutParams(0,dp(1),1f))
        }
    }
    private fun home(){
        text("مرحباً بك في الصفا",size=21f)
        text("اختر طريقة العمل المناسبة",size=12f)
        dashboardTiles(listOf(
            Triple("⚡","الوضع المبسط","simple"),
            Triple("◆","الوضع الكامل","advanced"),
            Triple("▣","المنتجات","products"),
            Triple("◷","الصيانة","repairs"),
            Triple("＄","المبيع","sale"),
            Triple("▤","الإقفال اليومي","vaults")
        ))
        text("تعمل هذه النسخة على التابلت دون اتصال بالإنترنت.",size=12f)
    }
    private fun simple(){
        text("العمل السريع",size=23f)
        dashboardTiles(listOf(
            Triple("＄","مبيع","sale"),
            Triple("◷","استلام وتسليم صيانة","repairs"),
            Triple("▤","الفواتير وقبض الديون","invoices"),
            Triple("▣","الخزنات والإقفال اليومي","vaults")
        ))
    }
    private fun advanced(){
        text("الوضع الكامل",size=23f)
        dashboardTiles(listOf(
            Triple("＄","مبيع","sale"),
            Triple("▣","المنتجات وقطع الصيانة","products"),
            Triple("▤","الفواتير والمرتجعات","invoices"),
            Triple("◷","استلام وتسليم صيانة","repairs"),
            Triple("◈","الخزنات والمصاريف والإقفال","vaults"),
            Triple("▦","الموردون والمشتريات","suppliers"),
            Triple("▥","تقرير شهر واحد","reports"),
            Triple("▥","تقرير بين تاريخين","period-reports"),
            Triple("♧","الشركاء ونسب الأرباح","partners"),
            Triple("≡","سجل العمليات","audit"),
            Triple("▧","النسخ الاحتياطي والاستعادة","backup"),
            Triple("◫","أرشيف الفواتير القديمة","legacy-history")
        ))
    }
    /** Keeps specialist audit tools accessible without pushing daily operations off screen. */
    private fun collapsibleTools(title:String,host:LinearLayout=body,build:()->Unit) {
        val content=column().apply {
            setPadding(dp(9),dp(5),dp(9),dp(9))
            background=background(paper,12)
            visibility=View.GONE
        }
        var initialized=false
        button("▸ "+title,host) {
            if(content.visibility==View.VISIBLE) {
                content.visibility=View.GONE
            } else {
                if(!initialized) {
                    // Large supplier/repair menus should not construct hundreds of
                    // buttons and query ledgers until the user actually opens them.
                    val previous=body
                    body=content
                    try {
                        build()
                        initialized=true
                    } finally {
                        body=previous
                    }
                }
                content.visibility=View.VISIBLE
            }
        }
        host.addView(content,LinearLayout.LayoutParams(-1,-2))
    }
    private fun sectionHeading(title:String,subtitle:String="",parent:LinearLayout=body) {
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(13),dp(11),dp(13),dp(11))
            background=background(sand,12)
        }
        val heading=TextView(this).apply {
            typeface=font
            text=title
            textSize=19f
            setTextColor(gold)
        }
        panel.addView(heading)
        if(subtitle.isNotBlank()){
            val description=TextView(this).apply {
                typeface=font
                text=subtitle
                textSize=12f
                setTextColor(muted)
                setPadding(0,dp(5),0,0)
            }
            panel.addView(description)
        }
        parent.addView(panel,LinearLayout.LayoutParams(-1,-2).apply {
            setMargins(0,dp(8),0,dp(10))
        })
    }
    private fun productCard(p:Product,parent:LinearLayout,variantCount:Int=0) {
        val card=column().apply {
            setPadding(dp(14),dp(12),dp(14),dp(12))
            background=background(paper,15)
        }
        val heading=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
        }
        val name=TextView(this).apply {
            style(this)
            text=p.name
            textSize=16f
            maxLines=2
            setPadding(dp(4),0,dp(4),0)
        }
        heading.addView(name,LinearLayout.LayoutParams(0,-2,1f))
        val price=TextView(this).apply {
            style(this)
            text=money(p.price,p.currency)
            textSize=15f
            setTextColor(gold)
            setPadding(dp(8),dp(5),dp(8),dp(5))
        }
        heading.addView(price)
        card.addView(heading)
        // Show the first locally restored image without decoding full-size bitmaps.
        val productRow=store.rows("SELECT kind,details FROM products WHERE id=?",p.id).firstOrNull()
        val productDetails=runCatching {
            org.json.JSONObject(productRow?.get("details").orEmpty())
        }.getOrDefault(org.json.JSONObject())
        val photoPath=productDetails.optString("imagePath").ifBlank {
            productDetails.optJSONArray("imagePaths")?.optString(0).orEmpty()
        }
        if(photoPath.isNotBlank()) {
            val photo=ImageView(this).apply {
                scaleType=ImageView.ScaleType.CENTER_INSIDE
                contentDescription="صورة "+p.name
            }
            card.addView(photo,LinearLayout.LayoutParams(-1,dp(120)))
            // Decode thumbnail off the UI thread; discard results when the card
            // has been removed by a new search or screen navigation.
            imageIo.execute {
                val bitmap=runCatching {
                    val file=localPhoto(photoPath)
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath,bounds)
                    if(bounds.outWidth<=0 || bounds.outHeight<=0) null else {
                        val options=android.graphics.BitmapFactory.Options().apply {
                            inSampleSize=maxOf(1,maxOf(bounds.outWidth/240,bounds.outHeight/240))
                        }
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath,options)
                    }
                }.getOrNull()
                if(bitmap!=null) runOnUiThread {
                    if(!isFinishing && !isDestroyed && photo.isAttachedToWindow) {
                        photo.setImageBitmap(bitmap)
                    } else bitmap.recycle()
                }
            }
        }
        val stockInfo=TextView(this).apply {
            style(this)
            text="المتوفر: ${p.stock}   •   الباركود: ${p.barcode}"
            textSize=12f
            setTextColor(if(p.stock<=0L)Color.rgb(157,62,52) else muted)
            background=background(if(p.stock<=0L)Color.rgb(255,239,235) else cream,10)
            setPadding(dp(10),dp(7),dp(10),dp(7))
        }
        card.addView(stockInfo)
        val colour=listOf("المينا" to "dialColor","الهيكل" to "caseColor","الحزام" to "strapColor")
            .mapNotNull { (label,key) ->
                productDetails.optString(key).takeIf { it.isNotBlank() }?.let { "$label: $it" }
            }.joinToString(" · ")
        if(colour.isNotBlank()) text("الألوان: "+colour,card,12f)
        if(variantCount>0) {
            text("ألوان إضافية مستقلة: "+variantCount,card,12f)
            button("عرض ألوان هذه الساعة ومخزونها",card) {
                val variants=store.colorVariants(p.id)
                val lines=variants.joinToString("\n\n") { row ->
                    val colour=store.productColorLabel(row.getValue("id"))
                    (colour.ifBlank { "لون غير محدد" })+
                        "\nالباركود: "+row.getValue("barcode")+
                        " · المتوفر: "+row.getValue("stock")+
                        " · السعر: "+money(row.getValue("price").toLong(),
                            Currency.valueOf(row.getValue("currency")))
                }
                AlertDialog.Builder(this).setTitle("ألوان "+p.name)
                    .setMessage(lines).setPositiveButton("حسناً",null).show()
            }
        }
        val actions=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
        }
        fun action(label:String,onClick:()->Unit) {
            val control=Button(this).apply {
                style(this)
                text=label
                textSize=12f
                isAllCaps=false
                gravity=Gravity.CENTER
                background=background(cream,10)
                setOnClickListener { if(!busy)onClick() }
            }
            actions.addView(control,LinearLayout.LayoutParams(0,dp(46),1f).apply {
                setMargins(dp(3),dp(4),dp(3),dp(2))
            })
        }
        val menu=Button(this).apply {
            style(this);text="إجراءات ▾";textSize=12f;isAllCaps=false
            background=background(sand,10)
        }
        card.addView(menu,LinearLayout.LayoutParams(-1,dp(44)))
        actions.visibility=View.GONE
        menu.setOnClickListener {
            actions.visibility=if(actions.visibility==View.VISIBLE)View.GONE else View.VISIBLE
            menu.text=if(actions.visibility==View.VISIBLE)"إخفاء الإجراءات ▴" else "إجراءات ▾"
        }
        action("تعديل"){productForm(p)}
        action("الحركات"){selectedProduct=p;go("stock-history")}
        action("جرد"){stocktakeForm(p)}
        if(productRow?.get("kind")=="watch" && !productDetails.has("variantParentId")) {
            action("لون إضافي"){variantForm(p)}
        }
        card.addView(actions)
        parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply {
            setMargins(0,dp(5),0,dp(6))
        })
    }
    private fun variantForm(parent:Product) {
        form("إضافة لون مستقل · "+parent.name){box->
            val barcode=field("باركود اللون",box)
            button("▣ قراءة باركود اللون بالكاميرا",box) {
                barcodeTarget=barcode
                startActivityForResult(Intent(this,BarcodeScanActivity::class.java),67)
            }
            text("كل لون منتج مستقل. لا يُخصم رصيده من الساعة الأساسية.",box,13f)
            val dial=field("لون المينا",box)
            val caseColor=field("لون الهيكل",box)
            val strap=field("لون الحزام",box)
            val price=field("سعر البيع",box,Money(parent.price,parent.currency).display().substringBefore(' '),true)
            val cost=field("تكلفة الوحدة",box,"0",true)
            val qty=field("الكمية الافتتاحية",box,"0",true)
            ({
                val b=barcode.text.toString()
                val d=dial.text.toString()
                val c=caseColor.text.toString()
                val st=strap.text.toString()
                val pr=amount(price,parent.currency)
                val co=amount(cost,parent.currency)
                val q=qty.text.toString().trim().toLong()
                work { store.addColorVariant(parent.id,b,pr,co,q,d,c,st) }
            })
        }
    }
    private fun imageMime(path:String):String = when(java.io.File(path).extension.lowercase(java.util.Locale.ROOT)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
    private fun products(onlyKind:String?="watch"){
        sectionHeading(if(onlyKind=="part") "قطع الصيانة" else "المنتجات","إدارة الأصناف والباركود والكميات والجرد")
        button(if(onlyKind=="part") "＋ إضافة قطعة صيانة" else "＋ إضافة ساعة"){productForm(defaultKind=onlyKind)}
        button("▣ مسح باركود للبحث"){startActivityForResult(Intent(this,BarcodeScanActivity::class.java),65)}
        val shortcuts=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
        fun shortcut(label:String,route:String){
            val control=Button(this).apply {
                style(this);text=label;textSize=12f;isAllCaps=false
                background=background(sand,12)
                setOnClickListener{if(!busy)go(route)}
            }
            shortcuts.addView(control,LinearLayout.LayoutParams(0,dp(48),1f).apply{
                setMargins(dp(3),dp(4),dp(3),dp(4))
            })
        }
        shortcut("تقييم المخزون","inventory")
        shortcut("تسويات الجرد","stocktake-history")
        body.addView(shortcuts)
        text(if(onlyKind=="part") "كتالوج قطع الصيانة" else "كتالوج المنتجات",size=20f).setTextColor(gold)
        if(onlyKind=="part") button("العودة إلى الساعات"){go("products")}
        else button("الانتقال إلى قطع الصيانة"){go("maintenance-parts")}
        val query=field("بحث بالاسم أو الباركود أو REF",body)
        val list=column().apply {setPadding(0,dp(4),0,dp(4))}
        body.addView(list)
        val variantCounts=store.colorVariantCounts()
        var visibleCount=24
        fun show(){
            list.removeAllViews()
            val results=store.searchCatalog(query.text.toString().trim(),onlyKind)
            text(if(onlyKind=="part") "قطع الصيانة: ${results.size}" else "المنتجات: ${results.size}",list,13f)
            if(results.isEmpty())text("لا توجد نتائج مطابقة.",list,14f)
            results.take(visibleCount).forEach{p->productCard(p,list,variantCounts[p.id]?:0)}
            if(results.size>visibleCount) {
                button("عرض المزيد (${minOf(visibleCount,results.size)} من ${results.size})",list) {
                    visibleCount+=24
                    show()
                }
            }
        }
        val searchHandler=android.os.Handler(android.os.Looper.getMainLooper())
        val refresh=Runnable { if(query.isAttachedToWindow && !isFinishing && !isDestroyed) show() }
        query.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                visibleCount=24
                searchHandler.removeCallbacks(refresh)
                searchHandler.postDelayed(refresh,180L)
            }
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        show()
    }
    private fun stocktakeForm(p:Product){
        form("جرد فعلي · ${p.name}","حفظ تسوية الجرد"){box->
            text("الكمية المسجلة حالياً: ${p.stock}",box)
            text("أي فرق سيُسجل بحركة مخزون وقيد محاسبي قابل للتدقيق.",box)
            val count=field("الكمية التي تم عدّها فعلياً",box,p.stock.toString(),true)
            val reason=field("سبب الفرق (إلزامي)",box)
            ({
                val counted=count.text.toString().trim().toLong()
                val why=reason.text.toString().trim()
                require(counted>=0 && counted!=p.stock){"أدخل كمية مختلفة وغير سالبة"}
                require(why.length>=5){"اكتب سبب الفرق بوضوح"}
                AlertDialog.Builder(this)
                    .setTitle("تأكيد تسوية المخزون")
                    .setMessage("المنتج: ${p.name}\nالكمية المسجلة: ${p.stock}\nالكمية الفعلية: $counted\nالفرق: ${counted-p.stock}\nالسبب: $why")
                    .setNegativeButton("تراجع",null)
                    .setPositiveButton("تأكيد وحفظ"){_,_->
                        work{store.adjustStockCount(p.id,counted,why,expectedStock=p.stock)}
                    }.show()
            })
        }
    }
    private fun stocktakeHistoryScreen(){
        text("سجل تسويات الجرد",size=25f)
        val from=field("من YYYY-MM-DD",body,YearMonth.now().atDay(1).toString())
        val until=field("إلى YYYY-MM-DD",body,Store.today())
        val output=column();body.addView(output)
        button("عرض تسويات الجرد"){
            try{
                output.removeAllViews()
                val start=from.text.toString().trim()
                val end=until.text.toString().trim()
                val entries=store.stocktakeHistory(start,end)
                val reconciles=store.stocktakeHistoryReconciles(start,end)
                text(if(reconciles)"قيود الجرد مطابقة لحركات المخزون"
                    else "تحذير: بعض قيود الجرد لا تطابق حركة المخزون",output,size=20f)
                if(entries.isEmpty())text("لا توجد تسويات جرد في الفترة",output)
                entries.forEach{row->
                    val c=Currency.valueOf(row.getValue("currency"))
                text(row.getValue("date")+" · "+row.getValue("product_name"),output)
                    text("فرق الكمية "+row.getValue("quantity_delta")+
                        " · فرق القيمة "+money(row.getValue("value_delta").toLong(),c),output)
                    text(row.getValue("memo"),output)
                }
            }catch(e:Exception){error(e)}
        }
    }
    private fun inventoryDashboard(){
        text("تقييم المخزون والتنبيهات",size=25f)
        text("تقييم التكلفة وسعر البيع حسب عملة كل منتج، دون خلط العملات. هامش الربح المتوقع ليس ربحاً محققاً.",size=15f)
        val threshold=field("تنبيه عندما تكون الكمية أقل من أو تساوي",body,"2",true)
        val output=column();body.addView(output)
        button("كشف قيود المخزون بين تاريخين"){
            form("قيود المخزون","عرض"){box->
                val from=field("من YYYY-MM-DD",box,YearMonth.now().atDay(1).toString())
                val until=field("إلى YYYY-MM-DD",box,Store.today())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                ({
                    val c=Currency.entries[currency.selectedItemPosition]
                    val entries=store.inventoryJournalEntries(c,from.text.toString().trim(),until.text.toString().trim())
                    val total=entries.fold(0L){sum,row->Math.addExact(sum,row.getValue("amount").toLong())}
                    AlertDialog.Builder(this).setTitle("قيود المخزون · ${c.name}")
                        .setMessage("صافي الحركة "+money(total,c)+"\n"+
                            (if(entries.isEmpty())"لا توجد قيود في الفترة" else entries.joinToString("\n"){row->
                                row.getValue("date")+" · "+row.getValue("kind")+" · "+
                                    money(row.getValue("amount").toLong(),c)+" · "+row.getValue("memo")
                            }))
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("مطابقة قيمة المخزون مع دفتر الأستاذ"){
            try{
                output.removeAllViews()
                Currency.entries.forEach{currency->
                    val audit=store.inventoryJournalReconciliation(currency)
                    text("العملة ${currency.name}",output,size=21f)
                    text("قيمة المنتجات "+money(audit.getValue("stock_value"),currency),output)
                    text("حساب المخزون في دفتر الأستاذ "+money(audit.getValue("journal_value"),currency),output)
                    text("الفرق "+money(audit.getValue("difference"),currency),output)
                    if(audit.getValue("difference")!=0L)
                        text("تحذير: يجب مراجعة القيود والحركات قبل اعتماد الأرقام، ولا يتم التصحيح تلقائياً.",output)
                    else text("متطابق",output)
                }
            }catch(e:Exception){error(e)}
        }
        button("فحص فروقات المخزون"){
            try{
                output.removeAllViews()
                var total=0
                Currency.entries.forEach{currency->
                    listOf("watch" to "الساعات","part" to "قطع الصيانة").forEach{(kind,title)->
                        val discrepancies=store.inventoryDiscrepancies(currency,kind)
                        if(discrepancies.isNotEmpty()){
                            text("فروقات $title · ${currency.name} · ${discrepancies.size} صنف",output,size=20f)
                            discrepancies.forEach{row->
                                total++
                                text(row.getValue("name")+" · "+row.getValue("barcode")+
                                    "\nكمية فعلية "+row.getValue("actual_qty")+
                                    " · حسب الحركات "+row.getValue("ledger_qty")+
                                    " · فرق "+row.getValue("qty_difference"),output)
                                text("قيمة فعلية "+money(row.getValue("actual_value").toLong(),currency)+
                                    " · حسب الحركات "+money(row.getValue("ledger_value").toLong(),currency)+
                                    " · فرق "+money(row.getValue("value_difference").toLong(),currency),output)
                            }
                        }
                    }
                }
                if(total==0)text("جميع كميات وقيم المخزون تطابق سجل الحركات.",output)
                else text("هذا كشف تدقيق فقط؛ لم تُعدّل أي كميات أو قيود.",output)
            }catch(e:Exception){error(e)}
        }
        button("عرض تقييم المخزون"){
            try{
                val limit=threshold.text.toString().trim().toLong()
                require(limit>=0){"حد التنبيه غير صالح"}
                output.removeAllViews()
                Currency.entries.forEach{currency->
                    text("العملة ${currency.name}",output,size=21f)
                    listOf("watch" to "الساعات","part" to "قطع الصيانة").forEach{(kind,title)->
                        val summary=store.inventorySummary(currency,kind)
                        require(store.inventoryValuationReconciles(currency,kind)){"تقييم المخزون لا يطابق الأرصدة المخزنة"}
                        text("$title · أصناف ${summary.getValue("products")} · قطع ${summary.getValue("quantity")}",output)
                        text("التكلفة ${money(summary.getValue("cost_value"),currency)} · قيمة البيع ${money(summary.getValue("retail_value"),currency)}",output)
                        text("هامش متوقع ${money(summary.getValue("potential_margin"),currency)} · نافد ${summary.getValue("out_of_stock")}",output)
                        val alerts=store.inventoryAlerts(currency,limit,kind)
                        text("تنبيهات المخزون: ${alerts.size}",output)
                        alerts.forEach{p->text("${p.name} · المتبقي ${p.stock} · ${p.barcode}",output)}
                    }
                }
            }catch(e:Exception){error(e)}
        }
    }
    private fun stockHistory(p:Product){
        text("حركة المخزون · ${p.name}",size=25f)
        text("الباركود: ${p.barcode} · العملة ${p.currency.name}",size=15f)
        try{
            val valid=store.productStockHistoryReconciles(p.id)
            text(if(valid)"حركات المخزون تطابق الرصيد الحالي" else "تحذير: الحركات لا تطابق الرصيد الحالي")
            val history=store.productStockHistory(p.id)
            var qty=0L
            var value=0L
            history.forEach{row->
                qty=Math.addExact(qty,row.getValue("quantity").toLong())
                value=Math.addExact(value,row.getValue("value_delta").toLong())
                text(row.getValue("date")+" · "+row.getValue("kind")+" · "+row.getValue("memo"))
                text("حركة "+row.getValue("quantity")+" · قيمة "+money(row.getValue("value_delta").toLong(),p.currency)+
                    " · الرصيد "+qty+" · القيمة "+money(value,p.currency))
            }
            if(history.isEmpty())text("لا توجد حركات مخزون مسجلة")
        }catch(e:Exception){error(e)}
    }
    private var selectedProduct:Product?=null
    private fun productForm(p:Product?=null,defaultKind:String?=null){form(if(p==null)"إضافة منتج" else "تعديل المنتج"){box->
        if(p!=null){
            val photo=store.productImage(p.id)
            if(photo!=null && java.io.File(photo).isFile){
                val thumbnail=android.widget.ImageView(this).apply {
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeFile(photo,bounds)
                    if(bounds.outWidth>0 && bounds.outHeight>0) {
                        var sample=1
                        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>480) sample*=2
                        setImageBitmap(android.graphics.BitmapFactory.decodeFile(photo,
                            android.graphics.BitmapFactory.Options().apply { inSampleSize=sample }))
                    }
                    adjustViewBounds=true
                    maxHeight=dp(160)
                    contentDescription="اضغط لتكبير صورة المنتج"
                    setOnClickListener { showRepairPhoto(photo) }
                }
                box.addView(thumbnail)
            }
            val gallery=store.productImages(p.id)
            if(gallery.isNotEmpty()) {
                button("اختيار الصورة الرئيسية",box) {
                    AlertDialog.Builder(this).setTitle("الصورة الرئيسية للمنتج")
                        .setItems(gallery.mapIndexed { i,_ -> "صورة "+(i+1) }.toTypedArray()) { _,index ->
                            work { store.primaryProductImage(p.id,gallery[index]) }
                        }.setNegativeButton("رجوع",null).show()
                }
                button("إدارة صور المنتج وحذف ارتباط صورة",box) {
                    AlertDialog.Builder(this).setTitle("صور المنتج")
                        .setItems(gallery.mapIndexed { i,_ -> "صورة "+(i+1) }.toTypedArray()) { _,index ->
                            val selected=gallery[index]
                            AlertDialog.Builder(this).setTitle("فصل الصورة عن المنتج")
                                .setMessage("سيُزال ارتباط الصورة بهذا المنتج دون حذف الملف من النسخة الاحتياطية.")
                                .setNegativeButton("رجوع",null)
                                .setPositiveButton("تأكيد") { _,_ ->
                                    work { store.unlinkProductImage(p.id,selected) }
                                }.show()
                        }.setNegativeButton("رجوع",null).show()
                }
            }
            if(gallery.size>1) {
                text("صور المنتج: "+gallery.size+" من 8",box,13f)
                gallery.drop(1).forEachIndexed { index,path ->
                    if(java.io.File(path).isFile) {
                        val preview=ImageView(this).apply {
                            val opts=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                            android.graphics.BitmapFactory.decodeFile(path,opts)
                            var sample=1
                            while(maxOf(opts.outWidth,opts.outHeight)/sample>400) sample*=2
                            setImageBitmap(android.graphics.BitmapFactory.decodeFile(path,
                                android.graphics.BitmapFactory.Options().apply { inSampleSize=sample }))
                            adjustViewBounds=true
                            maxHeight=dp(160)
                            contentDescription="تكبير صورة المنتج "+(index+2)
                            setOnClickListener { showRepairPhoto(path) }
                        }
                        box.addView(preview)
                    }
                }
            }
            if(gallery.size>1) button("إرسال صورة محددة عبر واتساب",box) {
                AlertDialog.Builder(this).setTitle("اختر الصورة للإرسال")
                    .setItems(gallery.mapIndexed { i,_ -> "صورة "+(i+1) }.toTypedArray()) { _,index ->
                        try {
                            CustomerShare.send(this,java.io.File(gallery[index]),
                                imageMime(gallery[index]),"الصفا للساعات - "+p.name)
                        } catch(e:Exception) { error(e) }
                    }.setNegativeButton("رجوع",null).show()
            }
            button("إرسال صورة المنتج عبر واتساب",box){
                try {
                    val path=store.productImage(p.id) ?: throw IllegalStateException("لا توجد صورة لهذا المنتج")
                    CustomerShare.send(this,java.io.File(path),imageMime(path),"الصفا للساعات - ${p.name}")
                }catch(e:Exception){error(e)}
            }
            button("إضافة صورة بالكاميرا أو المعرض (حتى 8 صور)",box){
                photoProductId=p.id
                startActivityForResult(Intent(this,MediaCaptureActivity::class.java),69)
            }
        } else text("احفظ المنتج أولاً، ثم افتحه لإضافة صورته.",box,14f)

        val name=field("الاسم",box,p?.name?:"");val barcode=field("الباركود",box,p?.barcode?:"");button("▣ قراءة الباركود بالكاميرا",box){barcodeTarget=barcode;startActivityForResult(Intent(this,BarcodeScanActivity::class.java),67)};val curr=select("العملة",Currency.entries.map{it.name},box);curr.setSelection(p?.currency?.ordinal?:0);curr.isEnabled=p==null
        val price=field("سعر البيع",box,p?.let{Money(it.price,it.currency).display().substringBefore(' ')}?:"0",true)
        val saved=if(p==null) org.json.JSONObject() else org.json.JSONObject(
            store.rows("SELECT details FROM products WHERE id=?",p.id).single().getValue("details"))
        val stored=if(p==null) emptyMap<String,String>() else store.rows(
            "SELECT brand,model FROM products WHERE id=?",p.id).single()
        val brand=field("الماركة",box,stored["brand"].orEmpty())
        val model=field("رقم الموديل REF",box,stored["model"].orEmpty())
        val description=field("تفاصيل الساعة للعميل",box,saved.optString("customerDescription"))
        val dial=field("لون المينا",box,saved.optString("dialColor"))
        val caseColor=field("لون الهيكل",box,saved.optString("caseColor"))
        val strap=field("لون الحزام",box,saved.optString("strapColor"))
        val gender=select("الفئة",listOf("غير محدد","رجالي","نسائي","للجنسين"),box)
        gender.setSelection(when(saved.optString("gender").lowercase()) {
            "male","men","رجالي" -> 1
            "female","women","نسائي" -> 2
            "unisex","للجنسين" -> 3
            else -> 0
        })
        val displayType=select("نوع العرض",listOf("غير محدد","عقارب","رقمية","عقارب ورقمية"),box)
        displayType.setSelection(when(saved.optString("displayType").lowercase()) {
            "analog","عقارب" -> 1
            "digital","رقمية" -> 2
            "analog-digital","عقارب ورقمية" -> 3
            else -> 0
        })
        val movement=field("نوع الحركة",box,saved.optString("movement"))
        val warranty=field("الضمان بالأشهر",box,saved.optString("warrantyMonths"))
        val watchType=select("نوع الساعة",listOf("عادية","إسلامية"),box)
        watchType.setSelection(if(saved.optString("watchType")=="إسلامية") 1 else 0)
        val cost=if(p==null)field("التكلفة",box,"0",true)else null;val qty=if(p==null)field("رصيد افتتاحي",box,"0",true)else null
        val kind=if(p==null)select("النوع",listOf("ساعة","قطعة صيانة"),box)else null;
        if(defaultKind=="part") kind?.setSelection(1)
        if(defaultKind!=null) kind?.isEnabled=false
        {
            val c=Currency.entries[curr.selectedItemPosition]
            val n=name.text.toString();val b=barcode.text.toString();val value=amount(price,c)
            val costValue=cost?.let{amount(it,c)}?:0;val q=qty?.text?.toString()?.toLong()?:0
            val br=brand.text.toString();val ref=model.text.toString()
            val desc=description.text.toString();val dc=dial.text.toString()
            val cc=caseColor.text.toString();val sc=strap.text.toString()
            val wt=if(watchType.selectedItemPosition==1) "إسلامية" else "عادية"
            val gen=listOf("","male","female","unisex")[gender.selectedItemPosition]
            val disp=listOf("","analog","digital","analog-digital")[displayType.selectedItemPosition]
            val mv=movement.text.toString().trim()
            val warr=warranty.text.toString().trim()
            require(listOf(br,ref,desc,dc,cc,sc).all { it.length<=500 }){"تفاصيل المنتج طويلة جداً"}
            work {
                if(p==null) {
                    val details=org.json.JSONObject()
                        .put("customerDescription",desc.trim()).put("dialColor",dc.trim())
                        .put("caseColor",cc.trim()).put("strapColor",sc.trim())
                        .put("watchType",wt).put("gender",gen).put("displayType",disp).put("movement",mv).put("warrantyMonths",warr).toString()
                    store.addProduct(n,b,c,value,costValue,q,
                        if(kind?.selectedItemPosition==1)"part" else "watch",
                        details=details,brand=br,model=ref)
                } else {
                    store.editProductFull(p.id,n,b,value,br,ref,desc,dc,cc,sc,wt,gender=gen,displayType=disp,movement=mv,warrantyMonths=warr)
                }
            }
        }
    }}
    private fun addSaleProduct(p:Product){
        form("إضافة ${p.name} · ${store.productColorLabel(p.id)}"){box->
            val qty=field("الكمية",box,"1",true)
            val price=field("سعر الوحدة ${p.currency}",box,Money(p.price,p.currency).display().substringBefore(' '),true)
            ({
                val q=qty.text.toString().toLong()
                require(q>0){"الكمية يجب أن تكون موجبة"}
                require(q<=store.product(p.id).stock){"الكمية المطلوبة أكبر من المخزون المتاح"}
                require(basket.isEmpty()||basketCurrency==p.currency){"لا يمكن خلط عملات مختلفة في الفاتورة نفسها. أتمم الفاتورة الحالية أو فرّغ السلة أولاً."}
                val unit=amount(price,p.currency)
                require(unit>=0L){"سعر البيع لا يمكن أن يكون سالباً"}
                val previous=basket.indexOfFirst { it.productId==p.id }
                if(previous>=0) {
                    val existing=basket[previous]
                    require(existing.unitPrice==unit){"الصنف موجود في السلة بسعر مختلف؛ عدّل السطر السابق أولاً"}
                    val combined=Math.addExact(existing.qty,q)
                    require(combined<=store.product(p.id).stock){"إجمالي الكمية يتجاوز المخزون"}
                    basket[previous]=existing.copy(qty=combined)
                } else basket.add(SaleItem(p.id,q,unit))
                basketCurrency=p.currency
                render()
            })
        }
    }
    private fun saleBasketRow(item:SaleItem) {
        val product=store.product(item.productId)
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(11),dp(8),dp(11),dp(8))
            background=background(paper,12)
        }
        val details=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
        }
        text(product.name+" × "+item.qty,details,15f).setTextColor(ink)
        text(if(product.kind=="part") "قطعة صيانة مباعة" else "ساعة",details,12f).setTextColor(muted)
        text("سعر الوحدة: "+money(item.unitPrice,basketCurrency),details,12f)
        val colour=store.productColorLabel(product.id)
        if(colour.isNotBlank()) text(colour+" · "+product.barcode,details,12f)
        text(money(mul(item.qty,item.unitPrice),basketCurrency),details,14f).setTextColor(gold)
        if(product.stock>0L && item.qty<=product.stock) {
            val estimatedCost=java.math.BigInteger.valueOf(product.value)
                .multiply(java.math.BigInteger.valueOf(item.qty))
                .divide(java.math.BigInteger.valueOf(product.stock)).longValueExact()
            text("تكلفة تقديرية: "+money(estimatedCost,basketCurrency),details,12f)
        }
        panel.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        val remove=Button(this).apply {
            style(this)
            text="إزالة"
            isAllCaps=false
            textSize=12f
            setTextColor(Color.rgb(157,62,52))
            background=background(Color.rgb(255,239,235),10)
            setOnClickListener { if(!busy){basket.remove(item);render()} }
        }
        val edit=Button(this).apply {
            style(this)
            text="تعديل"
            isAllCaps=false
            textSize=12f
            background=background(cream,10)
            setOnClickListener {
                if(!busy) {
                    form("تعديل "+product.name){box->
                        val quantity=field("الكمية",box,item.qty.toString(),true)
                        val unitPrice=field("سعر الوحدة "+product.currency,box,Money(item.unitPrice,product.currency).display().substringBefore(' '),true)
                        ({
                            val q=quantity.text.toString().trim().toLong()
                            require(q>0 && q<=store.product(product.id).stock){"الكمية غير متوفرة"}
                            val unit=amount(unitPrice,product.currency)
                            require(unit>=0L){"سعر الوحدة غير صالح"}
                            val index=basket.indexOfFirst { it.productId==item.productId }
                            require(index>=0){"الصنف لم يعد موجوداً في السلة"}
                            basket[index]=item.copy(qty=q,unitPrice=unit)
                            render()
                        })
                    }
                }
            }
        }
        panel.addView(edit,LinearLayout.LayoutParams(dp(76),dp(42)))
        panel.addView(remove,LinearLayout.LayoutParams(dp(76),dp(42)))
        body.addView(panel,LinearLayout.LayoutParams(-1,-2).apply {
            setMargins(0,dp(4),0,dp(4))
        })
    }
    private fun sale(){
        sectionHeading("فاتورة مبيعات","اختر الأصناف، راجع السلة ثم احفظ الفاتورة")
        button("إضافة أصناف إلى الفاتورة ▾") {
            val actions=arrayOf("اختيار ساعة من المنتجات","اختيار قطعة صيانة للبيع","مسح باركود الصنف")
            AlertDialog.Builder(this).setTitle("طريقة إضافة الصنف")
                .setItems(actions){_,which->
                    when(which) {
                        0->chooseProduct(inStockOnly=true,kind="watch"){p->addSaleProduct(p)}
                        1->chooseProduct(inStockOnly=true,kind="part"){p->addSaleProduct(p)}
                        2->startActivityForResult(Intent(this,BarcodeScanActivity::class.java),66)
                    }
                }.setNegativeButton("رجوع",null).show()
        }
        button("بحث سريع عن صنف بالاسم أو الباركود أو REF") {
            chooseProduct(inStockOnly=true){p->addSaleProduct(p)}
        }
        if(basket.isNotEmpty()) button("تفريغ سلة المبيع") {
            AlertDialog.Builder(this).setTitle("تفريغ السلة")
                .setMessage("سيتم حذف الأصناف من السلة الحالية فقط، دون تسجيل فاتورة أو تعديل المخزون.")
                .setNegativeButton("رجوع",null)
                .setPositiveButton("تفريغ"){_,_->basket.clear();render()}.show()
        }
        if(basket.isNotEmpty()) {
            val estimatedCost=basket.fold(0L){sum,item->
                val product=store.product(item.productId)
                require(product.stock>0L && item.qty in 1L..product.stock){"كمية السلة تتجاوز المخزون"}
                Math.addExact(sum,java.math.BigInteger.valueOf(product.value)
                    .multiply(java.math.BigInteger.valueOf(item.qty))
                    .divide(java.math.BigInteger.valueOf(product.stock)).longValueExact())
            }
            val gross=basket.fold(0L){sum,item->Math.addExact(sum,mul(item.qty,item.unitPrice))}
            text("التكلفة التقديرية للأصناف: "+money(estimatedCost,basketCurrency),body,14f)
            text("هامش الربح المتوقع قبل الخصم والمصاريف: "+money(Math.subtractExact(gross,estimatedCost),basketCurrency),body,14f)
        }
        if(basket.isEmpty())text("السلة فارغة. أضف ساعة أو قطعة صيانة للبيع، أو امسح الباركود.",body,14f)
        basket.toList().forEach{line->saleBasketRow(line)}
        if(basket.isNotEmpty()){val total=basket.fold(0L){a,i->add(a,mul(i.qty,i.unitPrice))};text("إجمالي الفاتورة: ${money(total,basketCurrency)}",size=21f).apply { setTextColor(gold);background=background(sand,14);setPadding(dp(18),dp(15),dp(18),dp(15)) };button("إتمام الفاتورة"){form("حفظ فاتورة مبيعات"){box->val customer=field("العميل",box);val phone=field("الهاتف",box);val discount=field("الخصم ${basketCurrency.name}",box,"0",true);val paid=field("المدفوع ${basketCurrency.name}",box,Money(total,basketCurrency).display().substringBefore(' '),true);val vault=select("يُقبض في",vaultNames,box);val invoiceDate=field("تاريخ فاتورة البيع YYYY-MM-DD",box,Store.today());val receiptDate=field("تاريخ استلام النقود الفعلي YYYY-MM-DD (إذا دُفع مبلغ)",box,Store.today());text("للمبيعات الأونلاين غير المحصلة: اجعل المدفوع صفراً، ثم سجّل القبض من الفاتورة عند وصول النقود.",box);
            {val customerText=customer.text.toString();val phoneText=phone.text.toString();val d=amount(discount,basketCurrency);val pay=amount(paid,basketCurrency);val v=Store.vaults[vault.selectedItemPosition];val items=basket.toList();val c=basketCurrency;val gross=items.fold(0L){sum,item->Math.addExact(sum,Math.multiplyExact(item.qty,item.unitPrice))};require(gross>0 && d>=0 && d<gross){"الإجمالي أو الخصم غير صالح"};require(pay>=0 && pay<=gross-d){"المدفوع يتجاوز المبلغ المستحق بعد الخصم"};require(pay==gross-d || customerText.isNotBlank()){"اسم العميل مطلوب عند وجود مبلغ آجل"};items.forEach{item->require(item.qty<=store.product(item.productId).stock){"تغير مخزون أحد الأصناف؛ راجع السلة"}};val issued=java.time.LocalDate.parse(invoiceDate.text.toString().trim()).toString();val collected=java.time.LocalDate.parse(receiptDate.text.toString().trim()).toString();work{store.sale(items,customerText,phoneText,c,pay,v,d,date=issued,receiptDate=collected);runOnUiThread{basket.clear();page="invoices"}}}
        }}}}
    private fun chooseProduct(inStockOnly:Boolean=false,kind:String?=null,action:(Product)->Unit){val box=column();val search=field("الاسم أو الباركود أو REF أو اللون",box);val results=column();box.addView(results);val sc=ScrollView(this);sc.addView(box);val dialog=AlertDialog.Builder(this).setTitle("اختيار منتج").setView(sc).setNegativeButton("رجوع",null).create()
        fun show(){results.removeAllViews();val found=store.searchCatalog(search.text.toString(),kind).filter{!inStockOnly || it.stock>0};if(found.isEmpty())text("لا توجد أصناف مطابقة للبحث.",results,14f);found.take(100).forEach{p->button(p.name+" · "+store.productColorLabel(p.id)+" · "+p.barcode+" · المتوفر: "+p.stock+" · "+money(p.price,p.currency),results){dialog.dismiss();action(p)}};if(found.size>100)text("تُعرض أول 100 نتيجة. اكتب اسمًا أو باركودًا أدق.",results,13f)};button("بحث",box){show()};button("▣ مسح باركود",box){chooserBarcodeField=search;chooserRefresh={show()};startActivityForResult(Intent(this,BarcodeScanActivity::class.java),68)};dialog.setOnDismissListener{chooserBarcodeField=null;chooserRefresh=null};show();dialog.show()}
    private fun invoiceCard(inv:Invoice,parent:LinearLayout) {
        val panel=column().apply {
            setPadding(dp(14),dp(10),dp(14),dp(10))
            background=background(paper,14)
        }
        val first=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
        }
        val number=TextView(this).apply {
            style(this)
            text="#"+inv.number+" · "+label(inv.kind)
            textSize=16f
            setTextColor(ink)
            setPadding(dp(5),dp(5),dp(5),dp(5))
        }
        first.addView(number,LinearLayout.LayoutParams(0,-2,1f))
        val status=TextView(this).apply {
            style(this)
            text=label(inv.status)
            textSize=12f
            setTextColor(if(inv.status=="cancelled")Color.rgb(157,62,52) else Color.rgb(32,111,66))
            background=background(if(inv.status=="cancelled")Color.rgb(255,238,234) else Color.rgb(235,248,237),10)
            setPadding(dp(9),dp(4),dp(9),dp(4))
        }
        first.addView(status)
        panel.addView(first)
        text(inv.party.ifBlank { "عميل نقدي" },panel,14f).setTextColor(ink)
        if(inv.kind=="sale" || inv.kind=="repair") {
            val issued=store.rows("SELECT date FROM events WHERE id=?",inv.id).firstOrNull()
            if(issued!=null)text("تاريخ الفاتورة: "+issued.getValue("date"),panel,12f)
        }
        text(money(inv.total,inv.currency),panel,17f).setTextColor(gold)
        if(inv.kind!="purchase") {
            val collected=store.paid(inv.id)
            if(collected>0L)text("المقبوض: "+money(collected,inv.currency),panel,13f)
            val outstanding=store.due(inv.id)
            if(outstanding>0L)text("المتبقي للتحصيل: "+money(outstanding,inv.currency),panel,13f)
                .setTextColor(Color.rgb(157,62,52))
            val refund=store.refundable(inv.id)
            if(refund>0L)text("مستحق الرد للعميل: "+money(refund,inv.currency),panel,13f)
                .setTextColor(Color.rgb(157,62,52))
        }
        if(inv.kind=="sale") {
            val profit=store.saleProfitAfterReturns(inv.id)
            text("صافي التكلفة: "+money(profit.getValue("net_cost"),inv.currency),panel,13f)
            text("صافي المبيع بعد المرتجعات: "+money(profit.getValue("net_revenue"),inv.currency),panel,13f)
            text("هامش الربح قبل المصاريف: "+money(profit.getValue("gross_margin"),inv.currency),panel,13f)
                .setTextColor(gold)
        } else if(inv.kind=="repair") {
            val cost=store.rows("SELECT cost FROM invoices WHERE id=?",inv.id).singleOrNull()
                ?.get("cost")?.toLongOrNull()
            if(cost!=null) {
                text("تكلفة قطع الصيانة: "+money(cost,inv.currency),panel,13f)
                text("هامش الصيانة قبل المصاريف: "+money(Math.subtractExact(inv.total,cost),inv.currency),panel,13f)
                    .setTextColor(gold)
            }
        }
        val open=Button(this).apply {
            style(this)
            text="تفاصيل الفاتورة والإجراءات ▾"
            textSize=13f
            isAllCaps=false
            background=background(sand,11)
            setOnClickListener { if(!busy)invoiceDetail(inv) }
        }
        panel.addView(open,LinearLayout.LayoutParams(-1,dp(44)).apply {
            setMargins(0,dp(7),0,0)
        })
        parent.addView(panel,LinearLayout.LayoutParams(-1,-2).apply {
            setMargins(0,dp(5),0,dp(5))
        })
    }
    private fun invoices(){
        sectionHeading("الفواتير والمرتجعات","البحث في الفواتير وعمليات القبض والاسترداد")
        collapsibleTools("تقارير الفواتير والتحصيلات") {
        button("كشف الحركات المالية لفاتورة"){
            form("كشف الفاتورة","عرض"){box->
                val number=field("رقم الفاتورة",box)
                ({
                    val n=number.text.toString().trim().toLong()
                    val invoice=store.invoiceByNumber(n)
                    val entries=store.invoiceFinancialHistory(invoice.id)
                    val details=if(entries.isEmpty()) "لا توجد حركات مالية."
                    else entries.joinToString("\n\n"){row->
                        row.getValue("date")+" · "+row.getValue("kind")+
                            "\n"+row.getValue("memo")+
                            "\nالدين: "+money(row.getValue("receivable_balance").toLong(),invoice.currency)+
                            " · الاسترداد: "+money(row.getValue("refund_liability").toLong(),invoice.currency)
                    }
                    AlertDialog.Builder(this).setTitle("كشف فاتورة #"+n)
                        .setMessage(details).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("كشف تحصيلات العملاء الفعلية"){
            form("كشف التحصيل","عرض"){box->
                val date=field("اليوم YYYY-MM-DD",box,Store.today())
                ({
                    val receipts=store.customerReceiptsForDay(date.text.toString().trim())
                    val details=if(receipts.isEmpty())"لا توجد تحصيلات" else receipts.joinToString("\n"){r->
                        r.getValue("actual_date")+" · #"+r.getValue("invoice_number")+" · "+
                        r.getValue("customer")+" · "+money(r.getValue("amount").toLong(),Currency.valueOf(r.getValue("currency")))+" · "+r.getValue("vault")
                    }
                    AlertDialog.Builder(this).setTitle("مقبوضات العملاء").setMessage(details).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("ملخص التحصيل الشهري"){
            form("تحصيلات الشهر","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val totals=store.customerReceiptsMonthTotals(month.text.toString().trim())
                    val details=if(totals.isEmpty())"لا توجد تحصيلات" else totals.joinToString("\n"){r->
                        val c=Currency.valueOf(r.getValue("currency"))
                        r.getValue("vault")+" · "+money(r.getValue("amount").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("تحصيلات الشهر").setMessage(details).setPositiveButton("حسناً",null).show()
                })
            }
        }
        }
        button("فواتير الصفا القديمة — سجل منظم") {
            form("سجل الفواتير القديمة","إغلاق"){box->
                val section=select("نوع الفاتورة",listOf("مبيع","شراء"),box)
                val from=field("من تاريخ YYYY-MM-DD (اختياري)",box)
                val to=field("إلى تاريخ YYYY-MM-DD (اختياري)",box)
                val query=field("بحث بالرقم أو الاسم أو الهاتف أو الصنف",box)
                val searchButton=Button(this).apply { style(this);text="بحث وعرض النتائج";isAllCaps=false }
                box.addView(searchButton,LinearLayout.LayoutParams(-1,dp(46)))
                val list=column()
                val controls=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                }
                box.addView(controls)
                box.addView(list)
                var currentPage=0
                val pageLabel=TextView(this).apply { style(this);textSize=13f }
                val previous=Button(this).apply { style(this);text="السابق";isAllCaps=false }
                val next=Button(this).apply { style(this);text="التالي";isAllCaps=false }
                controls.addView(previous,LinearLayout.LayoutParams(0,dp(44),1f))
                controls.addView(pageLabel,LinearLayout.LayoutParams(0,dp(44),1f))
                controls.addView(next,LinearLayout.LayoutParams(0,dp(44),1f))
                fun load() {
                    val key=if(section.selectedItemPosition==0)"sales" else "purchases"
                    val pageNumber=currentPage
                    val startDate=from.text.toString().trim()
                    val endDate=to.text.toString().trim()
                    val term=query.text.toString().trim()
                    work {
                        val result=LegacyInvoiceSnapshot.page(store,key,startDate,endDate,term,pageNumber*20,20)
                        val rows=result.getJSONArray("rows")
                        val total=result.optInt("total")
                        runOnUiThread {
                            if(isFinishing||isDestroyed)return@runOnUiThread
                            list.removeAllViews()
                            pageLabel.text="صفحة ${pageNumber+1} / ${maxOf(1,(total+19)/20)}"
                            previous.isEnabled=pageNumber>0
                            next.isEnabled=(pageNumber+1)*20<total
                            text("عدد الفواتير: $total",list,13f)
                            for(index in 0 until rows.length()) {
                                val invoice=rows.getJSONObject(index)
                                val card=column().apply {
                                    setPadding(dp(12),dp(10),dp(12),dp(10))
                                    background=background(paper,12)
                                }
                                text("#"+invoice.optString("invoiceNo")+" · "+invoice.optString("date"),card,16f)
                                text(if(key=="sales")invoice.optString("customer") else invoice.optString("supplierName"),card,14f)
                                text("الإجمالي: "+invoice.optString("total")+" "+invoice.optString("currency"),card,14f)
                                val detail=column()
                                card.addView(detail)
                                detail.visibility=View.GONE
                                text("المدفوع: "+invoice.optString("paid")+" · المتبقي: "+invoice.optString("outstanding"),detail,13f)
                                button("تفاصيل ▾",card) {
                                    detail.visibility=if(detail.visibility==View.GONE)View.VISIBLE else View.GONE
                                }
                                button("عرض الفاتورة الأصلية",card) {
                                    val archiveKey=invoice.optString("archiveKey")
                                    work {
                                        val original=LegacyInvoiceSnapshot.detail(store,key,archiveKey)
                                        runOnUiThread {
                                            if(!isFinishing&&!isDestroyed) {
                                                val record=original?.optJSONObject("invoice")
                                                val items=record?.optJSONArray("items")
                                                val itemText=if(items==null) "لا توجد أصناف مسجلة" else
                                                    (0 until items.length()).joinToString("\n") { j ->
                                                        val item=items.optJSONObject(j)
                                                        if(item==null) "" else
                                                            (item.optString("name").ifBlank { item.optString("productName") })+
                                                            " × "+item.optString("quantity",item.optString("qty","1"))
                                                    }
                                                AlertDialog.Builder(this)
                                                    .setTitle("الفاتورة الأصلية #"+invoice.optString("invoiceNo"))
                                                    .setMessage("التاريخ: "+invoice.optString("date")+
                                                        "\nالعميل/المورد: "+(if(key=="sales")invoice.optString("customer") else invoice.optString("supplierName"))+
                                                        "\nالهاتف: "+invoice.optString("phone")+
                                                        "\nالإجمالي: "+invoice.optString("total")+" "+invoice.optString("currency")+
                                                        "\nالمدفوع: "+invoice.optString("paid")+
                                                        "\nالمتبقي: "+invoice.optString("outstanding")+
                                                        "\n\nالأصناف:\n"+itemText)
                                                    .setPositiveButton("إغلاق",null).show()
                                            }
                                        }
                                    }
                                }
                                list.addView(card,LinearLayout.LayoutParams(-1,-2).apply {setMargins(0,dp(4),0,dp(4))})
                            }
                            if(rows.length()==0)text("لا توجد فواتير مطابقة.",list,14f)
                        }
                    }
                }
                previous.setOnClickListener { if(currentPage>0){currentPage--;load()} }
                next.setOnClickListener { currentPage++;load() }
                searchButton.setOnClickListener { currentPage=0;load() }
                section.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) { currentPage=0;load() }
                    override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
                }
                load()
                text("الفواتير التاريخية معروضة من بياناتها الأصلية؛ لا تُحتسب مرتين في الأرصدة النشطة.",box,12f)
                ({})
            }
        }
        button("تفاصيل فاتورة قديمة والمدفوعات المرتبطة بها — قراءة فقط") {
            form("البحث عن فاتورة قديمة","بحث"){box->
                val kind=select("النوع",listOf("مبيع","شراء"),box)
                val search=field("رقم الفاتورة أو الاسم أو رقم الهاتف",box)
                ({
                    val section=if(kind.selectedItemPosition==0)"sales" else "purchases"
                    val term=search.text.toString().trim()
                    require(term.isNotEmpty()){"أدخل رقم الفاتورة أو اسم العميل"}
                    work {
                        val rows=store.legacyPage(section,term,50,0)
                        val labels=rows.map { row ->
                            val item=org.json.JSONObject(row.getValue("payload"))
                            "#"+item.optString("invoiceNo")+" · "+item.optString("date")+
                                " · "+item.optString("customer",item.optString("supplierName"))
                        }.toTypedArray()
                        runOnUiThread {
                            if(!isFinishing&&!isDestroyed) {
                                if(labels.isEmpty()) AlertDialog.Builder(this)
                                    .setMessage("لا توجد فاتورة قديمة مطابقة")
                                    .setPositiveButton("إغلاق",null).show()
                                else AlertDialog.Builder(this)
                                    .setTitle("اختر فاتورة قديمة")
                                    .setItems(labels){_,index->
                                        val key=rows[index].getValue("legacy_id")
                                        work {
                                            val details=LegacyInvoiceSnapshot.detail(store,section,key)
                                            val invoice=details?.getJSONObject("invoice")
                                            val payments=details?.getJSONArray("linkedPayments")
                                            val message=if(invoice==null)"السجل غير متوفر" else
                                                "رقم الفاتورة: "+invoice.optString("invoiceNo")+
                                                "\\nالتاريخ: "+invoice.optString("date")+
                                                "\\nالعميل/المورد: "+invoice.optString("customer",invoice.optString("supplierName"))+
                                                "\\nالعملة: "+invoice.optString("currency")+
                                                "\\nالإجمالي: "+invoice.optString("total",invoice.optString("price"))+
                                                "\\nالمدفوع: "+invoice.optString("paid",invoice.optString("received"))+
                                                "\\nالدفعات المرتبطة: "+(payments?.length() ?: 0)+
                                                "\\n\\nسجل أصلي محفوظ للقراءة فقط، دون إنشاء قبض أو قيد جديد."
                                            runOnUiThread {
                                                if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                                                    .setTitle("تفاصيل الفاتورة التاريخية")
                                                    .setMessage(message).setPositiveButton("إغلاق",null).show()
                                            }
                                        }
                                    }.setNegativeButton("رجوع",null).show()
                            }
                        }
                    }
                })
            }
        }
        text("البحث في الفواتير",size=20f).setTextColor(gold)
        val query=field("الاسم أو الهاتف أو المنتج أو تفاصيل الصيانة",body)
        val dateFilters=column()
        val fromDate=field("من تاريخ YYYY-MM-DD",dateFilters,Store.today())
        val toDate=field("إلى تاريخ YYYY-MM-DD",dateFilters,Store.today())
        body.addView(dateFilters)
        button("اختيار التاريخ من التقويم ▾") {
            AlertDialog.Builder(this).setTitle("اختر التاريخ المطلوب")
                .setItems(arrayOf("تاريخ البداية","تاريخ النهاية")){_,which->
                    val target=if(which==0)fromDate else toDate
                    val current=runCatching { java.time.LocalDate.parse(target.text.toString().trim()) }.getOrElse { java.time.LocalDate.now() }
                    android.app.DatePickerDialog(this,{_,year,month,day->
                        target.setText(java.time.LocalDate.of(year,month+1,day).toString())
                    },current.year,current.monthValue-1,current.dayOfMonth).apply { datePicker.maxDate=System.currentTimeMillis() }.show()
                }.setNegativeButton("رجوع",null).show()
        }
        val kinds=listOf("all","sale","purchase","repair")
        val statuses=listOf("all","posted","cancelled")
        val filters=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            background=background(paper,12)
            setPadding(dp(8),dp(8),dp(8),dp(8))
        }
        val kindBox=column().apply {setPadding(dp(4),0,dp(4),0)}
        val statusBox=column().apply {setPadding(dp(4),0,dp(4),0)}
        val kind=select("نوع الفاتورة",listOf("الكل","مبيع","شراء","صيانة"),kindBox)
        val status=select("حالة الفاتورة",listOf("الكل","مرحلة","ملغاة"),statusBox)
        filters.addView(kindBox,LinearLayout.LayoutParams(0,-2,1f))
        filters.addView(statusBox,LinearLayout.LayoutParams(0,-2,1f))
        body.addView(filters)
        val output=column().apply{setPadding(0,dp(8),0,dp(8))}
        body.addView(output)
        val pageSize=50
        var pageIndex=0
        var lastSearchKey=""
        fun show(){
            val start=runCatching { java.time.LocalDate.parse(fromDate.text.toString().trim()).toString() }
                .getOrElse { throw IllegalArgumentException("تاريخ البداية غير صالح؛ اختره من التقويم بصيغة YYYY-MM-DD") }
            val end=runCatching { java.time.LocalDate.parse(toDate.text.toString().trim()).toString() }
                .getOrElse { throw IllegalArgumentException("تاريخ النهاية غير صالح؛ اختره من التقويم بصيغة YYYY-MM-DD") }
            require(start<=end){"تاريخ البداية يجب ألا يتجاوز تاريخ النهاية"}
            require(end<=Store.today()){"تاريخ النهاية لا يمكن أن يكون في المستقبل"}
            val searchKey=listOf(query.text.toString().trim(),start,end,
                kinds[kind.selectedItemPosition],statuses[status.selectedItemPosition]).joinToString("\u001F")
            if(searchKey!=lastSearchKey) {
                pageIndex=0
                lastSearchKey=searchKey
            }
            val offset=Math.multiplyExact(pageIndex,pageSize)
            // Fetch one extra row to detect a following page without counting the entire database.
            val fetched=store.searchInvoices(query.text.toString(),kinds[kind.selectedItemPosition],
                statuses[status.selectedItemPosition],limit=pageSize+1,
                startDate=start,endDate=end,offset=offset)
            val hasNext=fetched.size>pageSize
            val results=fetched.take(pageSize)
            val dates=if(results.isEmpty()) emptyMap<String,String>() else {
                val ids=results.map { it.id }
                val placeholders=ids.joinToString(","){"?"}
                store.rows("""SELECT id,date FROM events WHERE id IN ($placeholders)""",*ids.toTypedArray())
                    .associate { it.getValue("id") to it.getValue("date") }
            }
            output.removeAllViews()
            text("الفترة: "+start+" ← "+end+" · الصفحة "+(pageIndex+1)+
                " · الفواتير المعروضة: "+results.size,output,14f)
            // Per-page gross totals are separated by currency, not presented as cash receipts.
            val active=results.filter { it.status=="posted" }
            results.count { it.status=="cancelled" }.takeIf { it>0 }?.let { cancelled->
                text("فواتير ملغاة في هذه الصفحة: "+cancelled+" (مستبعدة من الإجماليات)",output,13f)
            }
            text("الملخص لهذه الصفحة فقط؛ لا يمثل إجمالي الفترة أو المقبوضات.",output,12f)
            active.groupBy { it.kind to it.currency }.forEach { (key,invoices)->
                val net=invoices.fold(0L){sum,inv->
                    val value=if(inv.kind=="sale")
                        store.saleProfitAfterReturns(inv.id).getValue("net_revenue")
                    else inv.total
                    Math.addExact(sum,value)
                }
                val title=if(key.first=="sale")"صافي المبيع بعد المرتجعات"
                    else "إجمالي فواتير "+label(key.first)
                text(title+" ("+key.second.name+"): "+
                    money(net,key.second)+" · "+invoices.size+" فاتورة",output,13f)
            }
            if(results.isEmpty()) {
                text(if(pageIndex>0)"لا توجد فواتير إضافية في هذه الصفحة؛ ارجع للصفحة السابقة."
                    else "لا توجد فواتير مطابقة ضمن الفترة المختارة.",output,14f)
            }
            var previousDate=""
            val dailyCounts=results.groupingBy { dates[it.id].orEmpty() }.eachCount()
            results.forEach{inv->
                val day=dates[inv.id].orEmpty()
                if(day.isNotEmpty() && day!=previousDate) {
                    sectionHeading("فواتير يوم "+day,
                        "عدد الفواتير المعروضة لهذا اليوم في الصفحة: "+dailyCounts.getValue(day),output)
                    previousDate=day
                }
                invoiceCard(inv,output)
            }
            if(pageIndex>0)button("العودة إلى أول صفحة",output){
                pageIndex=0
                try{show()}catch(e:Exception){error(e)}
            }
            if(pageIndex>0)button("◀ الصفحة السابقة",output){
                pageIndex--
                try{show()}catch(e:Exception){error(e)}
            }
            if(hasNext)button("الصفحة التالية ▶",output){
                pageIndex++
                try{show()}catch(e:Exception){error(e)}
            }
        }
        button("اختيار الفترة السريعة ▾") {
            val options=arrayOf("اليوم فقط","أمس","آخر 7 أيام","آخر 30 يوماً","الشهر الحالي","الشهر السابق")
            AlertDialog.Builder(this).setTitle("فترة عرض الفواتير")
                .setItems(options){_,which->
                    val today=java.time.LocalDate.now()
                    val from=when(which) {
                        0->today
                        1->today.minusDays(1)
                        2->today.minusDays(6)
                        3->today.minusDays(29)
                        4->today.withDayOfMonth(1)
                        else->today.minusMonths(1).withDayOfMonth(1)
                    }
                    val to=when(which){
                        1->today.minusDays(1)
                        5->today.withDayOfMonth(1).minusDays(1)
                        else->today
                    }
                    fromDate.setText(from.toString())
                    toDate.setText(to.toString())
                    pageIndex=0
                    try{show()}catch(e:Exception){error(e)}
                }.setNegativeButton("رجوع",null).show()
        }
        button("بحث في الفواتير"){pageIndex=0;try{show()}catch(e:Exception){error(e)}}
        try{show()}catch(e:Exception){error(e)}
    }
    private fun shareInvoicePdf(inv:Invoice) {
        if(busy)return
        busy=true
        io.execute {
            try {
                val folder=java.io.File(cacheDir,"shared_invoices").apply { mkdirs() }
                folder.listFiles()?.filter { it.isFile && it.lastModified()<System.currentTimeMillis()-7L*24*60*60*1000 }?.forEach { it.delete() }
                val file=java.io.File(folder,"AlSafa-${inv.number}-${java.util.UUID.randomUUID()}.pdf")
                file.outputStream().use { InvoicePdf(this,store).write(inv.id,it) }
                runOnUiThread {
                    busy=false
                    try { CustomerShare.send(this,file,"application/pdf","فاتورة الصفا رقم ${inv.number}") }
                    catch(e:Exception){error(e)}
                }
            }catch(e:Exception){runOnUiThread{busy=false;error(e)}}
        }
    }
    private fun invoiceDetail(inv:Invoice) {
        val box=column()
        sectionHeading("فاتورة #"+inv.number+" · "+label(inv.kind),"بيانات داخلية للمحل — لا تظهر تكاليف الشراء في PDF العميل",box)
        text("العميل: "+inv.party.ifBlank { "عميل نقدي" },box,16f).setTextColor(ink)
        text("إجمالي الفاتورة: "+money(inv.total,inv.currency),box,18f).setTextColor(gold)
        text("حالة الفاتورة: "+label(inv.status),box,14f)
        if(inv.kind=="sale" || inv.kind=="repair") {
            val contact=store.rows("SELECT phone FROM invoices WHERE id=?",inv.id).firstOrNull()
            val phone=contact?.get("phone").orEmpty()
            if(phone.isNotBlank())text("هاتف العميل: "+phone,box,14f)
        }
        if(inv.kind=="sale" || inv.kind=="repair") {
            val event=store.rows("SELECT date FROM events WHERE id=?",inv.id).firstOrNull()
            if(event!=null)text("تاريخ الفاتورة: "+event.getValue("date"),box,14f)
        }
        if(inv.kind!="purchase") {
            val received=store.paid(inv.id)
            val due=store.due(inv.id)
            val refund=store.refundable(inv.id)
            text("المقبوض فعلياً: "+money(received,inv.currency),box,15f).setTextColor(ink)
            text("المتبقي للتحصيل: "+money(due,inv.currency),box,15f)
                .setTextColor(if(due>0L)Color.rgb(157,62,52) else ink)
            text("مستحق الرد للعميل: "+money(refund,inv.currency),box,14f)
                .setTextColor(if(refund>0L)Color.rgb(157,62,52) else ink)
            val paymentState=when {
                refund>0L->"يوجد مبلغ يجب إرجاعه للعميل"
                due>0L && received>0L->"مدفوع جزئياً"
                due>0L->"غير محصل بالكامل"
                else->"لا توجد ذمة مستحقة حالياً"
            }
            text("حالة التحصيل: "+paymentState,box,14f).setTextColor(gold)
        }
        if(inv.kind=="sale") {
            val lines=store.rows("""SELECT l.name,l.qty,l.net,l.cost,
                coalesce((SELECT sum(r.qty) FROM returns r WHERE r.invoice_id=l.invoice_id AND r.line=l.line),0) returned_qty,
                coalesce((SELECT sum(r.credit) FROM returns r WHERE r.invoice_id=l.invoice_id AND r.line=l.line),0) returned_net,
                coalesce((SELECT sum(r.cost) FROM returns r WHERE r.invoice_id=l.invoice_id AND r.line=l.line),0) returned_cost
                FROM invoice_lines l WHERE l.invoice_id=? ORDER BY l.line""",inv.id)
            var netTotal=0L
            var costTotal=0L
            lines.forEach { line->
                val net=line.getValue("net").toLong()
                val cost=line.getValue("cost").toLong()
                netTotal=Math.addExact(netTotal,net)
                costTotal=Math.addExact(costTotal,cost)
                text(line.getValue("name")+" × "+line.getValue("qty"),box,15f).setTextColor(ink)
                text("سعر البيع: "+money(net,inv.currency)+" · تكلفة الصنف: "+money(cost,inv.currency),box,13f)
                val returnedQty=line.getValue("returned_qty").toLong()
                val remainingQty=Math.subtractExact(line.getValue("qty").toLong(),returnedQty)
                val remainingNet=Math.subtractExact(net,line.getValue("returned_net").toLong())
                val remainingCost=Math.subtractExact(cost,line.getValue("returned_cost").toLong())
                require(remainingQty>=0L && remainingNet>=0L && remainingCost>=0L){"سجل مرتجعات الصنف غير متسق"}
                text("المتبقي بعد المرتجع: "+remainingQty+" · صافي البيع: "+money(remainingNet,inv.currency),box,13f)
                text("صافي التكلفة: "+money(remainingCost,inv.currency)+" · هامش الصنف: "+
                    money(Math.subtractExact(remainingNet,remainingCost),inv.currency),box,13f)
            }
            text("إجمالي تكلفة الأصناف عند البيع: "+money(costTotal,inv.currency),box,15f).setTextColor(ink)
            val returns=store.rows("""SELECT l.name,r.qty,r.credit,r.cost,e.date
                FROM returns r JOIN invoice_lines l ON l.invoice_id=r.invoice_id AND l.line=r.line
                JOIN events e ON e.id=r.event_id
                WHERE r.invoice_id=? ORDER BY e.date,e.created,e.id""",inv.id)
            if(returns.isNotEmpty()) {
                sectionHeading("الأصناف المرتجعة","حركات المرتجع المسجلة فعلياً",box)
                returns.forEach { row->
                    text(row.getValue("date")+" · "+row.getValue("name")+" × "+row.getValue("qty"),box,14f)
                        .setTextColor(ink)
                    text("قيمة المرتجع: "+money(row.getValue("credit").toLong(),inv.currency)+
                        " · تكلفته: "+money(row.getValue("cost").toLong(),inv.currency),box,13f)
                }
            }
            val profit=store.saleProfitAfterReturns(inv.id)
            if(profit.getValue("returned_revenue")>0L) {
                text("قيمة المرتجعات: "+money(profit.getValue("returned_revenue"),inv.currency),box,14f)
                text("تكلفة الأصناف المرتجعة: "+money(profit.getValue("returned_cost"),inv.currency),box,14f)
            }
            text("صافي المبيع بعد المرتجعات: "+money(profit.getValue("net_revenue"),inv.currency),box,15f).setTextColor(ink)
            text("صافي تكلفة الأصناف: "+money(profit.getValue("net_cost"),inv.currency),box,15f).setTextColor(ink)
            text("هامش الربح بعد المرتجعات وقبل المصاريف: "+money(profit.getValue("gross_margin"),inv.currency),box,16f).setTextColor(gold)
            if(netTotal!=inv.total)text("ملاحظة: مجموع صافي الأصناف لا يساوي إجمالي الفاتورة؛ يلزم تدقيق السجل.",box,12f)
        }
        if(inv.kind=="repair") {
            val repair=store.rows("SELECT id FROM repairs WHERE invoice_id=?",inv.id).firstOrNull()
            if(repair!=null) {
                val repairId=repair.getValue("id")
                val parts=store.repairPartsUsed(repairId)
                if(parts.isEmpty())text("لم تُسجل قطع غيار لهذا الطلب.",box,13f)
                parts.forEach { part->
                    val currency=Currency.valueOf(part.getValue("currency"))
                    val quantity=part.getValue("quantity").toLong()
                    val value=Math.negateExact(part.getValue("value_delta").toLong())
                    text("قطعة: "+part.getValue("name")+" × "+Math.abs(quantity),box,14f).setTextColor(ink)
                    text("التكلفة الفعلية: "+money(value,currency)+" · تاريخ التركيب: "+part.getValue("date"),box,12f)
                }
                val profit=store.repairProfit(repairId)
                if(profit!=null) {
                    val currency=Currency.valueOf(profit.getValue("currency"))
                    text("أجرة الصيانة: "+money(profit.getValue("revenue").toLong(),currency),box,15f).setTextColor(ink)
                    text("تكلفة قطع الغيار: "+money(profit.getValue("parts_cost").toLong(),currency),box,15f).setTextColor(ink)
                    text("هامش الصيانة قبل المصاريف: "+money(profit.getValue("margin").toLong(),currency),box,16f).setTextColor(gold)
                }
            } else text("لا يوجد طلب صيانة مرتبط بهذه الفاتورة.",box,13f)
        }
        val sc=ScrollView(this)
        sc.addView(box)
        val dialog=AlertDialog.Builder(this).setTitle("تفاصيل الفاتورة").setView(sc).setNegativeButton("رجوع",null).create()
        val choices=mutableListOf<Pair<String,()->Unit>>()
        if(inv.kind=="sale" || inv.kind=="repair")choices.add("كشف الحركات المالية والذمم" to {
            val entries=store.invoiceFinancialHistory(inv.id)
            val detail=if(entries.isEmpty())"لا توجد حركات مالية."
            else entries.joinToString("\n\n"){row->
                row.getValue("date")+" · "+row.getValue("kind")+
                    "\n"+row.getValue("memo")+
                    "\nذمة العميل: "+money(row.getValue("receivable_balance").toLong(),inv.currency)+
                    " · التزام الاسترداد: "+money(row.getValue("refund_liability").toLong(),inv.currency)
            }
            AlertDialog.Builder(this).setTitle("الحركات المالية · فاتورة #"+inv.number)
                .setMessage(detail).setPositiveButton("حسناً",null).show()
        })
        choices.add("سجل قبض النقود وتواريخه" to {
            val receipts=store.invoiceReceiptHistory(inv.id)
            AlertDialog.Builder(this).setTitle("القبض الفعلي · فاتورة #"+inv.number)
                .setMessage(if(receipts.isEmpty())"لم يتم تحصيل أي مبلغ حتى الآن." else receipts.joinToString("\n"){row->
                    row.getValue("actual_date")+" · "+money(row.getValue("amount").toLong(),inv.currency)+" · "+row.getValue("vault")
                }).setPositiveButton("حسناً",null).show()
        })
        if(inv.kind=="sale" || inv.kind=="repair")choices.add("سجل المبالغ المعادة للعميل" to {
            val refunds=store.rows("""SELECT e.date,f.amount,f.vault
                FROM refunds f JOIN events e ON e.id=f.event_id
                WHERE f.invoice_id=? ORDER BY e.date,e.created,e.id""",inv.id)
            val details=if(refunds.isEmpty())"لم يتم تسجيل أي رد نقدي لهذه الفاتورة."
                else refunds.joinToString("\n"){row->
                    row.getValue("date")+" · "+money(row.getValue("amount").toLong(),inv.currency)+
                        " · "+row.getValue("vault")
                }
            AlertDialog.Builder(this).setTitle("سجل رد النقود · فاتورة #"+inv.number)
                .setMessage(details).setPositiveButton("حسناً",null).show()
        })
        choices.add("مشاركة فاتورة PDF عبر واتساب" to { shareInvoicePdf(inv) })
        choices.add("حفظ فاتورة PDF" to {
            pdfInvoice=inv.id
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="application/pdf"
                putExtra(Intent.EXTRA_TITLE,"AlSafa-${inv.number}.pdf")
            },104)
        })
        if(inv.kind!="purchase" && store.due(inv.id)>0)choices.add("قبض دفعة" to {
            dialog.dismiss()
            paymentForm("قبض دين",inv.currency,store.due(inv.id)){n,v,date->store.collect(inv.id,n,v,date)}
        })
        if(inv.kind=="sale" && inv.status=="posted") {
            choices.add("مرتجع جزئي" to { dialog.dismiss();returnForm(inv) })
            choices.add("إلغاء الفاتورة وإرجاع الأصناف للمخزون" to {
                dialog.dismiss()
                form("تأكيد استلام الأصناف المرتجعة فعلياً","إلغاء الفاتورة"){b->
                    val reason=field("سبب الإلغاء",b)
                    val date=field("تاريخ استلام الأصناف فعلياً YYYY-MM-DD",b,Store.today())
                    text("لا يتم رد المال تلقائياً. سجّل الاسترداد كحركة مستقلة بعد إرجاع الأصناف.",b)
                    ({
                        val value=reason.text.toString().trim()
                        require(value.isNotBlank()){"سبب الإلغاء مطلوب"}
                        val actualDate=java.time.LocalDate.parse(date.text.toString().trim()).toString()
                        work{store.cancelSale(inv.id,value,date=actualDate)}
                    })
                }
            })
        }
        if(inv.kind!="purchase" && store.refundable(inv.id)>0)choices.add("رد مبلغ للعميل" to {
            dialog.dismiss()
            paymentForm("رد نقدي",inv.currency,store.refundable(inv.id)){n,v,date->store.refund(inv.id,n,v,date)}
        })
        val labels=choices.map { it.first }.toTypedArray()
        button("الإجراءات ▾",box) {
            AlertDialog.Builder(this).setTitle("اختر إجراء الفاتورة")
                .setItems(labels){_,index->
                    try { choices[index].second() } catch(e:Exception) { error(e) }
                }.setNegativeButton("رجوع",null).show()
        }
        dialog.show()
    }
    private fun returnForm(inv:Invoice) {
        val lines=store.rows("""SELECT l.line,l.name,l.qty,
            coalesce((SELECT sum(r.qty) FROM returns r
                WHERE r.invoice_id=l.invoice_id AND r.line=l.line),0) returned
            FROM invoice_lines l WHERE l.invoice_id=? ORDER BY l.line""",inv.id)
        if(lines.none { it.getValue("qty").toLong()>it.getValue("returned").toLong() }) {
            AlertDialog.Builder(this).setTitle("لا يوجد مرتجع متاح")
                .setMessage("جميع أصناف هذه الفاتورة أُعيدت بالفعل؛ لا يمكن إرجاعها مرة ثانية.")
                .setPositiveButton("حسناً",null).show()
            return
        }
        form("استلام مرتجع جزئي"){box->
            sectionHeading("الأصناف القابلة للإرجاع","أدخل الكمية التي وصلت إلى المحل فعلياً",box)
            val fields=lines.filter { it.getValue("qty").toLong()>it.getValue("returned").toLong() }
                .associate { row->
                    val remaining=Math.subtractExact(row.getValue("qty").toLong(),row.getValue("returned").toLong())
                    row.getValue("line").toInt() to
                        field(row.getValue("name")+" · المتبقي "+remaining,box,"0",true)
                }
            val reason=field("سبب المرتجع",box)
            val date=field("تاريخ استلام المرتجع YYYY-MM-DD",box,Store.today())
            ({
                val quantities=fields.mapValues { it.value.text.toString().trim().toLong() }
                    .filterValues { it>0L }
                require(quantities.isNotEmpty()){"حدد كمية مرتجعة واحدة على الأقل"}
                lines.forEach { row->
                    val index=row.getValue("line").toInt()
                    val remaining=Math.subtractExact(row.getValue("qty").toLong(),row.getValue("returned").toLong())
                    require((quantities[index]?:0L) in 0L..remaining){"كمية مرتجع "+row.getValue("name")+" تتجاوز المتبقي"}
                }
                val why=reason.text.toString().trim()
                require(why.isNotBlank()){"سبب المرتجع مطلوب"}
                val actualDate=java.time.LocalDate.parse(date.text.toString().trim()).toString()
                work{store.returnSale(inv.id,quantities,why,date=actualDate)}
            })
        }
    }
    private fun paymentForm(title:String,c:Currency,max:Long,action:(Long,String,String)->Unit){form(title){box->val n=field("المبلغ ${c.name} · المتبقي ${money(max,c)}",box,"0",true);val v=select("الخزنة",vaultNames,box);val received=field("تاريخ حركة النقود الفعلي YYYY-MM-DD",box,Store.today());{val amount=amount(n,c);require(amount>0L && amount<=max){"المبلغ يجب أن يكون أكبر من الصفر وألا يتجاوز الرصيد المستحق"};val vault=Store.vaults[v.selectedItemPosition];val actualDate=java.time.LocalDate.parse(received.text.toString().trim()).toString();work{action(amount,vault,actualDate)}}}}
    private fun vaults(){sectionHeading("الخزنات والإقفال اليومي","الأرصدة الفعلية والمحاسبية بحسب الخزنة والعملة");
        collapsibleTools("تقارير وتدقيق الخزنات المتقدم") {
        button("تدقيق فروقات الإقفال لجميع الخزنات"){
            form("تدقيق الإقفالات الشهري","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=YearMonth.parse(month.text.toString().trim()).toString()
                    val issues=store.allClosingDriftIssues(selected)
                    val detail=if(issues.isEmpty()) "لا توجد فروقات بين الإقفالات المحفوظة والرصيد الدفتري لنهاية أيامها."
                    else issues.take(100).joinToString("\n"){row->
                        val currency=Currency.valueOf(row.getValue("currency"))
                        val vaultName=vaultNames.getOrElse(Store.vaults.indexOf(row.getValue("vault"))) { row.getValue("vault") }
                        row.getValue("date")+" · "+vaultName+" · "+currency.name+
                            " · الفرق "+money(row.getValue("book_drift").toLong(),currency)
                    } + if(issues.size>100) "\n... توجد نتائج إضافية" else ""
                    AlertDialog.Builder(this).setTitle("فروقات إقفالات "+selected)
                        .setMessage(detail).setPositiveButton("حسناً",null).show()
                })
            }
        }

        button("تدقيق الأيام ذات الرصيد السالب"){
            try {
                val issues=store.negativeVaultDays()
                val detail=if(issues.isEmpty()) "لا توجد أيام برصيد دفتري سالب في سجلات الخزنات."
                else issues.take(100).joinToString("\n"){ row ->
                    val currency=Currency.valueOf(row.getValue("currency"))
                    row.getValue("date")+" · "+row.getValue("vault")+" · "+currency.name+
                        " · "+money(row.getValue("closing_balance").toLong(),currency)
                } + if(issues.size>100) "\n... توجد "+(issues.size-100)+" نتيجة إضافية" else ""
                AlertDialog.Builder(this).setTitle("تدقيق أرصدة الخزنات")
                    .setMessage(detail).setPositiveButton("حسناً",null).show()
            } catch(e:Exception) { AlertDialog.Builder(this).setMessage(e.message ?: "تعذر فحص الأرصدة").setPositiveButton("حسناً",null).show() }
        }

        button("مراجعة تغير الأرصدة بعد الإقفالات السابقة"){
            form("مراجعة الإقفالات","عرض"){box->
                val v=select("الخزنة",vaultNames,box)
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val vault=Store.vaults[v.selectedItemPosition]
                    val lines=store.closingDriftMonth(vault,month.text.toString().trim())
                    val changed=lines.filter{it.getValue("book_drift").toLong()!=0L}
                    val details=if(changed.isEmpty())"لا توجد فروقات دفترية مكتشفة في إقفالات الشهر."
                    else changed.joinToString("\n"){row->
                        val c=Currency.valueOf(row.getValue("currency"))
                        row.getValue("date")+" · "+c.name+
                        " · فرق "+money(row.getValue("book_drift").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("مراجعة "+vault)
                        .setMessage(details).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("كشف خزنة بين تاريخين"){
            form("كشف الخزنة للفترة","عرض"){box->
                val v=select("الخزنة",vaultNames,box)
                val currency=select("العملة",Currency.entries.map{it.name},box)
                val first=field("من YYYY-MM-DD",box,java.time.LocalDate.now().withDayOfMonth(1).toString())
                val last=field("إلى YYYY-MM-DD",box,Store.today())
                ({
                    val vault=Store.vaults[v.selectedItemPosition]
                    val unit=Currency.entries[currency.selectedItemPosition]
                    val data=store.vaultStatementRange(vault,unit,first.text.toString().trim(),last.text.toString().trim())
                    val lines=listOf(
                        "الرصيد المرحّل: "+money(data.getValue("opening"),unit),
                        "المقبوضات: "+money(data.getValue("inflows"),unit),
                        "المدفوعات: "+money(data.getValue("outflows"),unit),
                        "الرصيد الختامي: "+money(data.getValue("closing"),unit),
                        "عدد الحركات: "+data.getValue("movements")
                    ).joinToString("\n")
                    AlertDialog.Builder(this).setTitle("كشف "+vault+" · "+unit.name).setMessage(lines)
                        .setNeutralButton("تفاصيل الحركات"){_,_->
                            try {
                                val entries=store.vaultStatementEntries(vault,unit,first.text.toString().trim(),last.text.toString().trim())
                                val details=entries.take(200).joinToString("\n\n"){entry->
                                    val delta=entry.getValue("amount").toLong()
                                    entry.getValue("date")+" · "+entry.getValue("memo")+"\n"+
                                        (if(delta>=0) "مقبوض " else "مدفوع ")+money(if(delta>=0) delta else Math.negateExact(delta),unit)+
                                        "\nالرصيد بعد الحركة: "+money(entry.getValue("running_balance").toLong(),unit)
                                }
                                val more=if(entries.size>200) "\n\nتُعرض أول 200 حركة من أصل "+entries.size+"؛ قلّص الفترة لعرض الباقي." else ""
                                AlertDialog.Builder(this).setTitle("حركات "+vault+" · "+unit.name)
                                    .setMessage(if(entries.isEmpty()) "لا توجد حركات في الفترة" else details+more)
                                    .setPositiveButton("حسناً",null).show()
                            } catch(e:Exception){error(e)}
                        }.setPositiveButton("حسناً",null).show()
                })
            }
        }
Store.vaults.forEach{v->text(v+" · إقفالات تحتاج عدّاً: "+store.automaticPendingReviewCount(v))};button("الفروقات الفعلية التي تحتاج تحقيقاً"){val lines=Store.vaults.flatMap{v->store.automaticReviewExceptions(v).map{row->v+" · "+row.getValue("closing_date")+" · "+row.getValue("currency")+" · فرق "+row.getValue("variance")+" · "+row.getValue("note")}};AlertDialog.Builder(this).setTitle("فروقات لم تسوَّ محاسبياً").setMessage(if(lines.isEmpty())"لا توجد فروقات مراجعة" else lines.joinToString("\n")).setPositiveButton("حسناً",null).show()};button("تدقيق إقفال حسب اليوم والعملة"){
            form("تدقيق إقفال","عرض"){box->
                val v=select("الخزنة",vaultNames,box)
                val day=field("التاريخ YYYY-MM-DD",box,java.time.LocalDate.now().minusDays(1).toString())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                ({
                    val vault=Store.vaults[v.selectedItemPosition]
                    val unit=Currency.entries[currency.selectedItemPosition]
                    val data=store.automaticClosingAudit(vault,day.text.toString().trim(),unit)
                    val lines=data.entries.joinToString("\n"){it.key+": "+it.value}
                    AlertDialog.Builder(this).setTitle("تدقيق إقفال "+vault).setMessage(lines).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("ملخص فروقات العد المراجع"){val lines=Store.vaults.flatMap{v->Currency.entries.map{currency->v+" · "+currency.name+" · نقص "+money(store.automaticReviewSummary(v,currency).getValue("shortages"),currency)+" · زيادة "+money(store.automaticReviewSummary(v,currency).getValue("surpluses"),currency)+" · صافي "+money(store.automaticReviewDifference(v,currency),currency)}};AlertDialog.Builder(this).setTitle("فروقات العد حسب الخزنة والعملة").setMessage(lines.joinToString("\n")).setPositiveButton("حسناً",null).show()};button("سجل مراجعات الإقفال التلقائي"){
            val lines=Store.vaults.flatMap{v->store.automaticClosingReviews(v).map{row->
                val unit=Currency.valueOf(row.getValue("currency"))
                v+" · إقفال "+row.getValue("closing_date")+" · مراجعة "+row.getValue("review_date")+" · "+unit.name+" · متوقع "+money(row.getValue("book_balance").toLong(),unit)+" · معدود "+money(row.getValue("actual_count").toLong(),unit)+" · فرق "+money(row.getValue("variance").toLong(),unit)+" · "+row.getValue("note")
            }}
            AlertDialog.Builder(this).setTitle("سجل مراجعات العد").setMessage(if(lines.isEmpty())"لا توجد مراجعات مسجلة" else lines.joinToString("\n")).setPositiveButton("حسناً",null).show()
        }
        button("تسجيل العد الفعلي لإقفال تلقائي سابق"){
            form("مراجعة إقفال تلقائي"){box->
                val vault=select("الخزنة",vaultNames,box)
                val day=field("تاريخ الإقفال YYYY-MM-DD",box,java.time.LocalDate.now().minusDays(1).toString())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                val counted=field("المبلغ الموجود الآن فعلياً",box,"0",true)
                val note=field("ملاحظة المراجعة",box,"عد فعلي في اليوم التالي")
                ({
                    val v=Store.vaults[vault.selectedItemPosition]
                    val unit=Currency.entries[currency.selectedItemPosition]
                    val value=amount(counted,unit)
                    val selected=java.time.LocalDate.parse(day.text.toString().trim()).toString()
                    val memo=note.text.toString().trim()
                    val preview=store.automaticClosingReviewPreview(v,selected,unit,value)
                    val expected=preview.getValue("book_now")
                    AlertDialog.Builder(this).setTitle("تأكيد تسجيل العد")
                        .setMessage("المتوقع الآن: "+money(expected,unit)+"\nالمعدود: "+money(value,unit)+"\nالفرق: "+money(Math.subtractExact(value,expected),unit)+"\nلن يتغير رصيد الخزنة تلقائياً.")
                        .setNegativeButton("رجوع",null)
                        .setPositiveButton("حفظ المراجعة"){_,_->work{store.reviewAutomaticClosing(v,selected,unit,value,memo)}}
                        .show()
                })
            }
        }
        button("عرض الإقفالات التلقائية التي تحتاج مراجعة"){val lines=Store.vaults.flatMap{v->store.automaticClosingsNeedingReview(v).map{row->v+" · "+row.getValue("date")+" · "+row.getValue("currency")+" · رصيد دفتري "+row.getValue("expected")+" · تغير لاحق "+(store.closingBookDrift(v,row.getValue("date"),Currency.valueOf(row.getValue("currency")))?:0L)}};AlertDialog.Builder(this).setTitle("إقفالات بلا عد فعلي").setMessage(if(lines.isEmpty())"لا توجد إقفالات تلقائية خلال آخر 30 يوماً" else lines.joinToString("\n")).setPositiveButton("حسناً",null).show()};button("استكمال الإقفالات الدفترية الناقصة لآخر 31 يوماً"){work{Store.vaults.forEach{store.autoCloseMissingDays(it,31)}}};Store.vaults.forEachIndexed{i,v->text(vaultNames[i],size=22f);Currency.entries.forEach{c->text(money(store.balance(v,c),c))}}
        button("كشف حركة الخزنات الشهري"){
            form("تقرير أرصدة الخزنات","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=YearMonth.parse(month.text.toString().trim()).toString()
                    val lines=Store.vaults.flatMap { v ->
                        Currency.entries.map { currency ->
                            require(store.vaultMonthReconciles(v,currency,selected)){"رصيد الخزنة غير متوازن"}
                            "$v · ${currency.name}: سابق ${money(store.vaultMonthOpening(v,currency,selected),currency)}، حركة ${money(store.vaultMonthMovement(v,currency,selected),currency)}، نهائي ${money(store.vaultMonthClosing(v,currency,selected),currency)}"
                        }
                    }
                    AlertDialog.Builder(this).setTitle("أرصدة الخزنات · $selected")
                        .setMessage(lines.joinToString("\n"))
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("كشف خزنة ليوم محدد"){
            form("كشف الحركة اليومية","عرض"){box->
                val v=select("الخزنة",vaultNames,box)
                val day=field("التاريخ YYYY-MM-DD",box,Store.today())
                ({
                    val vault=Store.vaults[v.selectedItemPosition]
                    val selected=java.time.LocalDate.parse(day.text.toString().trim()).toString()
                    val counted=store.closingForDay(vault,selected).associateBy{it.getValue("currency")}
                    val reviews=Currency.entries.associateWith{store.automaticClosingReviewForDay(vault,selected,it)}
                    val lines=Currency.entries.flatMap{currency->
                        require(store.vaultDayReconciles(vault,currency,selected)){"حركة اليوم لا تطابق الرصيد"}
                        val opening=store.vaultDayOpening(vault,currency,selected)
                        val closing=store.vaultDayClosing(vault,currency,selected)
                        val incoming=store.vaultDayInflows(vault,currency,selected)
                        val outgoing=store.vaultDayOutflows(vault,currency,selected)
                        val header="${currency.name}: سابق ${money(opening,currency)} · داخل ${money(incoming,currency)} · خارج ${money(outgoing,currency)} · نهائي ${money(closing,currency)}"+(if(counted[currency.name]?.get("kind")=="AUTO_CLOSING")" · إقفال تلقائي غير معدود" else "")
                        listOf(header)+listOfNotNull(reviews[currency]?.let{"مراجعة "+it.getValue("review_date")+" · معدود "+money(it.getValue("actual_count").toLong(),currency)+" · فرق "+money(it.getValue("variance").toLong(),currency)})+listOfNotNull(counted[currency.name]?.takeIf{it["kind"]!="AUTO_CLOSING"}?.get("counted")?.let{"العد الفعلي المسجل: "+money(it.toLong(),currency)})+store.vaultDayEntries(vault,currency,selected).map{row->
                            row.getValue("kind")+" · "+money(row.getValue("amount").toLong(),currency)
                        }
                    }
                    AlertDialog.Builder(this).setTitle("حركة $vault · $selected")
                        .setMessage(lines.joinToString("\n")).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("تفاصيل حركات خزنة خلال شهر"){
            form("تفاصيل الخزنة","عرض"){box->
                val v=select("الخزنة",vaultNames,box)
                val c=select("العملة",Currency.entries.map{it.name},box)
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=YearMonth.parse(month.text.toString().trim()).toString()
                    val vault=Store.vaults[v.selectedItemPosition]
                    val currency=Currency.entries[c.selectedItemPosition]
                    require(store.vaultMonthEntriesReconcile(vault,currency,selected)){"حركات الخزنة لا تطابق صافي الشهر"}
                    require(store.vaultMonthFlowReconciles(vault,currency,selected)){"إجمالي النقد الداخل والخارج لا يطابق صافي الشهر"}
                    val entries=store.vaultMonthEntries(vault,currency,selected)
                    val opening=store.vaultMonthOpening(vault,currency,selected)
                    val closing=store.vaultMonthClosing(vault,currency,selected)
                    val inflows=store.vaultMonthInflows(vault,currency,selected)
                    val outflows=store.vaultMonthOutflows(vault,currency,selected)
                    val detail=entries.joinToString("\n"){ row ->
                        "${row.getValue("date")} · ${row.getValue("kind")} · ${money(row.getValue("amount").toLong(),currency)}"
                    }
                    AlertDialog.Builder(this).setTitle("كشف $vault · ${currency.name} · $selected")
                        .setMessage("سابق: ${money(opening,currency)}\nداخل: ${money(inflows,currency)}\nخارج: ${money(outflows,currency)}\nنهائي: ${money(closing,currency)}\n"+(if(entries.isEmpty())"لا توجد حركات" else detail))
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        }
        sectionHeading("عمليات الخزنة اليومية","إضافة الحركات المالية والصرف والإقفال")
        button("إضافة رصيد / مصروف / سحب / نقل"){form("حركة مالية"){box->val type=select("نوع العملية",listOf("رصيد افتتاحي","مصروف","سحب شريك","نقل بين الخزنات"),box);val v=select("الخزنة",vaultNames,box);val c=select("العملة",Currency.entries.map{it.name},box);val n=field("المبلغ",box,"0",true);val dest=select("إلى خزنة (للنقل)",vaultNames,box);val partnerRows=store.rows("SELECT id,name FROM partner_members ORDER BY name");val partner=select("الشريك (للسحب)",if(partnerRows.isEmpty())listOf("لا يوجد شركاء مسجلون") else partnerRows.map{it.getValue("name")},box);val note=field("البيان",box);val date=field("تاريخ الحركة YYYY-MM-DD",box,Store.today());
            {val currency=Currency.entries[c.selectedItemPosition];val amount=amount(n,currency);val source=Store.vaults[v.selectedItemPosition];val kind=listOf("opening","expense","withdrawal","transfer")[type.selectedItemPosition];val destination=if(kind=="withdrawal"){require(partnerRows.isNotEmpty()){"أضف الشريك أولاً من شاشة الشركاء"};partnerRows[partner.selectedItemPosition].getValue("id")}else Store.vaults[dest.selectedItemPosition];val memo=note.text.toString();val day=date.text.toString();work{store.cash(kind,source,currency,amount,memo,day,destination)}}}}
        button("صرف عملة بين الخزنات"){form("صرف عملة"){box->val from=select("العملة المدفوعة",Currency.entries.map{it.name},box);val source=select("من خزنة",vaultNames,box);val spent=field("المبلغ المدفوع",box,"0",true);val to=select("العملة المقبوضة",Currency.entries.map{it.name},box);val dest=select("إلى خزنة",vaultNames,box);val received=field("المبلغ المقبوض فعلياً",box,"0",true);{val fc=Currency.entries[from.selectedItemPosition];val tc=Currency.entries[to.selectedItemPosition];val a=amount(spent,fc);val b=amount(received,tc);val sv=Store.vaults[source.selectedItemPosition];val dv=Store.vaults[dest.selectedItemPosition];work{store.exchange(sv,fc,a,dv,tc,b)}}}}
        button("الإقفال اليومي"){form("عدّ الخزنة"){box->val v=select("الخزنة",vaultNames,box);val counts=Currency.entries.associateWith{field("العد الفعلي ${it.name}",box,"0",true)};text("يحفظ العد والفرق ولا يغير الرصيد الدفتري تلقائياً.",box);{val vault=Store.vaults[v.selectedItemPosition];val values=counts.mapValues{amount(it.value,it.key)};require(!store.closingExists(vault)){"هذه الخزنة مقفلة اليوم بالفعل"};val preview=store.closingPreview(vault,values);val summary=Currency.entries.joinToString("\n"){currency->val (expected,difference)=preview.getValue(currency);"${currency.name} · متوقع ${money(expected,currency)} · معدود ${money(values.getValue(currency),currency)} · فرق ${money(difference,currency)}"};AlertDialog.Builder(this).setTitle("تأكيد إقفال الخزنة").setMessage(summary+"\nلن يغيّر العد رصيد الخزنة الدفتري.").setNegativeButton("رجوع",null).setPositiveButton("تأكيد الإقفال"){_,_->work{store.closeDay(vault,values)}}.show()}}}
        button("سجل إقفالات خزنة"){form("سجل الإقفال","عرض"){box->val v=select("الخزنة",vaultNames,box);val month=field("الشهر YYYY-MM",box,YearMonth.now().toString());({val vault=Store.vaults[v.selectedItemPosition];val selected=YearMonth.parse(month.text.toString().trim()).toString();val lines=store.closingHistoryMonth(vault,selected);AlertDialog.Builder(this).setTitle("سجل إقفالات $vault").setMessage(if(lines.isEmpty())"لا توجد إقفالات" else lines.joinToString("\n"){row->val currency=Currency.valueOf(row.getValue("currency"));row.getValue("date")+" · "+(if(row["kind"]=="AUTO_CLOSING")"تلقائي / يحتاج مراجعة" else "يدوي")+" · "+currency.name+" · متوقع "+money(row.getValue("expected").toLong(),currency)+(if(row["kind"]=="AUTO_CLOSING")" · لا يوجد عد فعلي أو فرق مؤكد" else " · معدود "+money(row.getValue("counted").toLong(),currency)+" · فرق "+money(row.getValue("variance").toLong(),currency))}).setPositiveButton("حسناً",null).show()})}}
        store.rows("SELECT e.date,c.* FROM closings c JOIN events e ON e.id=c.event_id ORDER BY e.created DESC LIMIT 30").forEach{r->val c=Currency.valueOf(r.getValue("currency"));text("${r.getValue("date")} · ${r.getValue("vault")} · ${c.name}\nمتوقع ${money(r.getValue("expected").toLong(),c)} · "+(if(r["counted"].isNullOrBlank()) "إقفال تلقائي · لم يجرِ عد فعلي" else "معدود ${money(r.getValue("counted").toLong(),c)} · فرق ${money(Math.subtractExact(r.getValue("counted").toLong(),r.getValue("expected").toLong()),c)}"))}
    }
    private fun suppliers(){text("الموردون",size=25f);
        button("دفتر الموردين المستعاد من التطبيق القديم — قراءة فقط") {
            form("دفتر الموردين القديم","عرض"){box->
                val supplier=field("معرف المورد (اختياري)",box)
                val from=field("من تاريخ YYYY-MM-DD",box)
                val to=field("إلى تاريخ YYYY-MM-DD",box)
                ({
                    val supplierId=supplier.text.toString().trim()
                    val start=from.text.toString().trim()
                    val end=to.text.toString().trim()
                    work {
                        val report=LegacySupplierLedgerView.page(store,supplierId,start,end,0,30)
                        val records=report.getJSONArray("records")
                        val lines=(0 until records.length()).joinToString("\\n"){index->
                            val row=records.getJSONObject(index)
                            row.optString("date")+" · "+row.optString("kind")+
                                " · "+row.optString("amount")+" "+row.optString("currency")
                        }
                        val message="عدد القيود: "+report.getInt("totalMatching")+
                            "\\nالإجماليات حسب النوع والعملة: "+
                            report.getJSONObject("totalsByKindAndCurrency").toString()+
                            "\\n\\n"+(if(lines.isBlank())"لا توجد نتائج" else lines)+
                            "\\n\\nللقراءة فقط؛ لا تُضاف المبالغ إلى الدين مرة ثانية."
                        runOnUiThread {
                            if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                                .setTitle("دفتر الموردين التاريخي")
                                .setMessage(message).setPositiveButton("إغلاق",null).show()
                        }
                    }
                })
            }
        }

        button("تدقيق أرصدة الموردين لشهر محدد"){
            form("مطابقة الموردين الشهرية","تدقيق"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=month.text.toString().trim()
                    val issues=store.supplierMonthIssues(selected)
                    val details=if(issues.isEmpty()) "جميع كشوف الموردين مطابقة في الشهر المحدد."
                    else issues.take(100).joinToString("\n"){row->
                        row.getValue("name")+" · "+row.getValue("currency")+" · "+row.getValue("issue")
                    } + if(issues.size>100) "\n... توجد نتائج إضافية" else ""
                    AlertDialog.Builder(this).setTitle("تدقيق الموردين · "+selected)
                        .setMessage(details).setPositiveButton("حسناً",null).show()
                })
            }
        }

        button("كشف حسابات الموردين غير المرتبطة"){
            try {
                val issues=store.orphanSupplierAccounts()
                val detail=if(issues.isEmpty()) "جميع حسابات ديون الموردين مرتبطة بسجلات موردين معروفة."
                else issues.take(100).joinToString("\n"){row->
                    val c=Currency.valueOf(row.getValue("currency"))
                    "معرّف "+row.getValue("supplier_id")+" · "+c.name+
                        " · رصيد القيد "+money(row.getValue("journal_balance").toLong(),c)+
                        " · عدد الحركات "+row.getValue("event_count")
                } + if(issues.size>100) "\n... توجد نتائج إضافية" else ""
                AlertDialog.Builder(this).setTitle("حسابات الموردين غير المرتبطة")
                    .setMessage(detail).setPositiveButton("حسناً",null).show()
            } catch(e:Exception) {
                AlertDialog.Builder(this).setMessage(e.message ?: "تعذر الفحص")
                    .setPositiveButton("حسناً",null).show()
            }
        }

        button("تدقيق أيام ديون الموردين السالبة"){
            try {
                val issues=store.negativeSupplierDebtDays()
                val detail=if(issues.isEmpty()) "لا توجد أيام يظهر فيها دين مورد سالب."
                else issues.take(100).joinToString("\n"){row->
                    val currency=Currency.valueOf(row.getValue("currency"))
                    row.getValue("date")+" · "+row.getValue("supplier_name")+
                        " · "+money(row.getValue("remaining_debt").toLong(),currency)
                } + if(issues.size>100) "\n... توجد "+(issues.size-100)+" نتيجة إضافية" else ""
                AlertDialog.Builder(this).setTitle("تدقيق ديون الموردين")
                    .setMessage(detail).setPositiveButton("حسناً",null).show()
            } catch(e:Exception) {
                AlertDialog.Builder(this).setMessage(e.message ?: "تعذر تدقيق ديون الموردين")
                    .setPositiveButton("حسناً",null).show()
            }
        }
button("＋ مورد"){form("مورد جديد"){box->val n=field("الاسم",box);val p=field("الهاتف",box);{val name=n.text.toString();val phone=p.text.toString();work{store.addSupplier(name,phone)}}}}
        store.rows("SELECT * FROM suppliers ORDER BY name").forEach{r->val id=r.getValue("id");collapsibleTools("المورد: "+r.getValue("name")) { button("تسجيل دين سابق للمورد"){form("رصيد المورد الافتتاحي"){box->val c=select("العملة",Currency.entries.map{it.name},box);val n=field("الدين السابق",box,"0",true);val day=field("تاريخ الدين YYYY-MM-DD",box,Store.today());{val currency=Currency.entries[c.selectedItemPosition];val amount=amount(n,currency);val date=java.time.LocalDate.parse(day.text.toString().trim()).toString();work{store.supplierOpening(id,currency,amount,date)}}}};Currency.entries.forEach{c->val due=store.supplierDue(id,c);button("كشف المورد الكامل ${c.name}"){val lines=store.supplierLedgerWithBalances(id,c);AlertDialog.Builder(this).setTitle("كشف المورد · كامل السجل").setMessage(lines.joinToString("\n"){it.getValue("date")+" : "+it.getValue("kind")+" : "+money(Math.negateExact(it.getValue("amount").toLong()),c)+" · المتبقي "+money(it.getValue("remaining").toLong(),c)}).setPositiveButton("حسناً",null).show()};button("رصيد المورد بتاريخ محدد ${c.name}"){form("دين المورد بتاريخ","عرض"){box->val day=field("التاريخ YYYY-MM-DD",box,Store.today());{val date=day.text.toString().trim();val balance=store.supplierDueOn(id,c,date);val available=if(!java.time.LocalDate.parse(date).isAfter(java.time.LocalDate.now())) store.supplierAvailableOn(id,c,date) else 0L;AlertDialog.Builder(this).setTitle("رصيد المورد · "+date+" · "+c.name).setMessage("الدين في نهاية اليوم: "+money(balance,c)+"\nالحد المتاح لدفعة مؤرخة بهذا اليوم: "+money(available,c)).setPositiveButton("حسناً",null).show()}}};button("كشف مورد بين تاريخين ${c.name}") {
            form("كشف المورد حسب الفترة","عرض"){box->
                val from=field("من YYYY-MM-DD",box,java.time.LocalDate.now().withDayOfMonth(1).toString())
                val to=field("إلى YYYY-MM-DD",box,Store.today())
                ({
                    val start=java.time.LocalDate.parse(from.text.toString().trim()).toString()
                    val end=java.time.LocalDate.parse(to.text.toString().trim()).toString()
                    val summary=store.supplierStatementRange(id,c,start,end)
                    val entries=store.supplierStatementEntries(id,c,start,end)
                    val details=entries.map{entry->
                        val amount=entry.getValue("amount").toLong()
                        val debtChange=Math.negateExact(amount)
                        entry.getValue("date")+" · "+entry.getValue("kind")+" · "+money(debtChange,c)+
                            " · المتبقي "+money(entry.getValue("remaining").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("كشف مورد ${c.name} · $start إلى $end")
                        .setMessage("الرصيد السابق: "+money(summary.getValue("opening"),c)+
                            "\nديون خلال الفترة: "+money(summary.getValue("charges"),c)+
                            "\nدفعات خلال الفترة: "+money(summary.getValue("payments"),c)+
                            "\nالرصيد النهائي: "+money(summary.getValue("closing"),c)+
                            "\n"+(if(details.isEmpty())"لا توجد حركات خلال الفترة" else details.joinToString("\n")))
                        .setPositiveButton("حسناً",null).show()
                })
            }
        };button("كشف شهر محدد ${c.name}"){form("كشف المورد الشهري","عرض"){box->val month=field("الشهر YYYY-MM",box,YearMonth.now().toString());{val selected=month.text.toString().trim();val lines=store.supplierLedgerMonth(id,c,selected);val statement=store.supplierStatement(id,c,selected);val datedPayments=store.supplierPaymentsMonth(id,c,selected);val verifiedPayments=store.supplierPaymentTotalMonth(id,c,selected);val opening=statement.getValue("opening");val closing=statement.getValue("closing");val movement=statement.getValue("current_month_net");val charges=statement.getValue("charges");val payments=statement.getValue("payments");AlertDialog.Builder(this).setTitle("كشف ${c.name} · $selected").setMessage("الرصيد السابق (من أشهر سابقة): "+money(opening,c)+"\nديون افتتاحية أضيفت هذا الشهر: "+money(statement.getValue("new_opening_debt"),c)+"\nمشتريات هذا الشهر: "+money(statement.getValue("purchases"),c)+"\nتغيرات دين أخرى: "+money(statement.getValue("other_debt_changes"),c)+"\nديون مضافة: "+money(charges,c)+"\nدفعات المورد: "+money(payments,c)+"\nصافي حركة الشهر: "+money(movement,c)+"\nرصيد نهاية الشهر: "+money(closing,c)+"\nالدفعات المؤرخة للمورد: "+money(verifiedPayments,c)+"\n"+(if(datedPayments.isEmpty())"لا توجد دفعات خلال الشهر" else datedPayments.joinToString("\n"){x->x.getValue("date")+" · دفعة "+money(x.getValue("amount").toLong(),c)})+"\n"+(if(lines.isEmpty())"لا توجد حركات في الشهر المحدد" else lines.joinToString("\n"){x->x.getValue("date")+" · "+x.getValue("kind")+" · "+money(Math.negateExact(x.getValue("amount").toLong()),c)+" · المتبقي "+money(x.getValue("remaining").toLong(),c)})).setPositiveButton("حسناً",null).show()}}};button("${c.name} · الدين ${money(due,c)} · دفع"){form("دفع للمورد"){box->val n=field("المبلغ ${c.name} · الدين ${money(due,c)}",box,"0",true);val day=field("تاريخ الدفع YYYY-MM-DD",box,Store.today());val v=select("خزنة الدفع",vaultNames,box);{val paid=amount(n,c);val date=java.time.LocalDate.parse(day.text.toString().trim()).toString();require(!java.time.LocalDate.parse(date).isAfter(java.time.LocalDate.now())){"تاريخ الدفع في المستقبل"};val permitted=store.supplierAvailableOn(id,c,date);require(paid>0L&&paid<=permitted){"الحد المتاح في هذا التاريخ: "+money(permitted,c)};val vault=Store.vaults[v.selectedItemPosition];work{store.paySupplier(id,c,paid,vault,date)}}}}};button("شراء بضاعة من ${r.getValue("name")}"){chooseProduct{p->form("شراء ${p.name}"){box->val qty=field("الكمية",box,"1",true);val cost=field("كلفة الوحدة ${p.currency}",box,"0",true);val paid=field("المدفوع",box,"0",true);val date=field("تاريخ الشراء YYYY-MM-DD",box,Store.today());val paymentDay=field("تاريخ دفع النقود الفعلي YYYY-MM-DD",box,Store.today());val v=select("خزنة الدفع",vaultNames,box);{val q=qty.text.toString().toLong();val value=amount(cost,p.currency);val pay=amount(paid,p.currency);val vault=Store.vaults[v.selectedItemPosition];val purchaseDate=java.time.LocalDate.parse(date.text.toString().trim()).toString();require(!java.time.LocalDate.parse(purchaseDate).isAfter(java.time.LocalDate.now())){"تاريخ الشراء في المستقبل"};val actualPayment=java.time.LocalDate.parse(paymentDay.text.toString().trim()).toString();work{store.purchase(id,listOf(SaleItem(p.id,q,value)),p.currency,pay,vault,purchaseDate,paymentDate=actualPayment)}}}}}}}
    }
    /** Only display or attach photos created inside this installation's private image store. */
    private fun localPhoto(path:String):java.io.File {
        val root=java.io.File(filesDir,"images").canonicalFile
        val file=java.io.File(path).canonicalFile
        require(file.parentFile==root && file.name.matches(Regex("[a-fA-F0-9-]{36}\\.(jpg|png|webp)",RegexOption.IGNORE_CASE))) {
            "مسار صورة الساعة غير آمن"
        }
        require(file.isFile && file.length() in 1L..(12L*1024*1024)) {
            "صورة الساعة غير متوفرة أو حجمها غير صالح"
        }
        return file
    }
    private fun showRepairPhoto(path:String) {
        try {
            localPhoto(path)
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeFile(path,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0) { "تعذر قراءة صورة الساعة" }
            var sample=1
            while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048) sample*=2
            val bitmap=android.graphics.BitmapFactory.decodeFile(path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize=sample })
                ?: error("تعذر فتح صورة الساعة")
            val photo=ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds=true
                scaleType=ImageView.ScaleType.FIT_CENTER
                contentDescription="صورة الساعة مكبرة"
            }
            val container=android.widget.FrameLayout(this).apply {
                setPadding(dp(12),dp(12),dp(12),dp(12))
                addView(photo,android.widget.FrameLayout.LayoutParams(-1,-1))
            }
            val dialog=AlertDialog.Builder(this).setTitle("معاينة صورة الساعة").setView(container).create()
            photo.setOnClickListener { dialog.dismiss() }
            val close=Button(this).apply {
                text="×"
                textSize=28f
                contentDescription="إغلاق الصورة والعودة للصيانة"
                setOnClickListener { dialog.dismiss() }
            }
            container.addView(close,android.widget.FrameLayout.LayoutParams(dp(56),dp(56),Gravity.TOP or Gravity.END))
            dialog.setOnDismissListener {
                photo.setImageDrawable(null)
                if(!bitmap.isRecycled) bitmap.recycle()
            }
            dialog.show()
            dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.94).toInt(),
                (resources.displayMetrics.heightPixels*0.86).toInt())
        } catch(e:Exception) { error(e) }
    }
    /** Legacy tablet list-row: 38dp image, customer/watch, problem/date, and status. */
    private fun repairIdentity(row:Map<String,String>, target:LinearLayout) {
        val id=row.getValue("id")
        val path=store.repairImage(id)
        val line=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(13),dp(12),dp(13),dp(12))
            background=background(paper,15)
        }
        val icon=ImageView(this).apply {
            isClickable=true
            isFocusable=true
            scaleType=ImageView.ScaleType.CENTER_CROP
            contentDescription="صورة ساعة "+row.getValue("customer")
            background=GradientDrawable().apply {
                setColor(Color.rgb(248,237,219))
                cornerRadius=dp(11).toFloat()
                setStroke(dp(1),Color.rgb(226,209,179))
            }
            clipToOutline=true
        }
        icon.setImageResource(android.R.drawable.ic_menu_gallery)
        icon.setOnClickListener {
            if(path!=null && runCatching { localPhoto(path) }.isSuccess) {
                showRepairPhoto(path)
            } else {
                photoRepairId=id
                startActivityForResult(Intent(this,MediaCaptureActivity::class.java),70)
            }
        }
        if(path!=null) {
            imageIo.execute {
                val thumbnail=runCatching {
                    val file=localPhoto(path)
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath,bounds)
                    require(bounds.outWidth>0 && bounds.outHeight>0)
                    var sample=1
                    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>160) sample*=2
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath,
                        android.graphics.BitmapFactory.Options().apply { inSampleSize=sample })
                }.getOrNull()
                if(thumbnail!=null) runOnUiThread {
                    if(!isFinishing && !isDestroyed && icon.isAttachedToWindow) {
                        icon.setImageBitmap(thumbnail)
                    } else thumbnail.recycle()
                }
            }
        }
        line.addView(icon,LinearLayout.LayoutParams(dp(64),dp(64)).apply { marginEnd=dp(12) })
        // The image remains focusable for keyboard and accessibility navigation.
        val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val heading=TextView(this).apply {
            text=row.getValue("customer")+" · "+row.getValue("watch")
            textSize=15f
            typeface=font
            setTextColor(ink)
            maxLines=2
            ellipsize=android.text.TextUtils.TruncateAt.END
        }
        val subtitle=TextView(this).apply {
            val problem=row["problem"].orEmpty()
            text=(if(problem.isBlank())"" else "$problem · ")+row.getValue("date")+" · "+row.getValue("phone")
            textSize=12f
            setTextColor(Color.DKGRAY)
            maxLines=2
            ellipsize=android.text.TextUtils.TruncateAt.END
        }
        details.addView(heading)
        details.addView(subtitle)
        line.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        val status=TextView(this).apply {
            val ready=row.getValue("status")=="ready"
            val delivered=row.getValue("status")=="delivered"
            text=label(row.getValue("status"))
            textSize=12f
            setTextColor(when {
                ready->Color.rgb(32,111,66)
                delivered->muted
                else->Color.rgb(128,88,26)
            })
            background=background(when {
                ready->Color.rgb(232,247,234)
                delivered->Color.rgb(240,238,235)
                else->Color.rgb(249,240,222)
            },10)
            setPadding(dp(9),dp(6),dp(9),dp(6))
        }
        line.addView(status)
        target.addView(line,LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(4),0,dp(4)) })
    }
    private fun repairs(){sectionHeading("الصيانة","استلام الطلبات ومتابعة الحالة والتسليم");button("استلام صيانة"){
            intakePhotoPath=null
            intakePhotoPreview=null
            intakePhotoStatus=null
            form("استلام الساعة"){box->
                val n=field("العميل",box)
                val p=field("الهاتف",box)
                val w=field("الساعة",box)
                val problem=field("المشكلة",box)
                // Fast repair intake: one tap selects a common service while
                // leaving the description fully editable for unusual repairs.
                val quickRow=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                }
                box.addView(quickRow,LinearLayout.LayoutParams(-1,-2))
                listOf("تبديل بطارية","تبديل حزام","تعديل حزام","تبديل زجاج","تبديل ماكينة","تنظيف الساعة")
                    .chunked(3).forEachIndexed { rowIndex,services ->
                        val target=if(rowIndex==0) quickRow else LinearLayout(this).apply {
                            orientation=LinearLayout.HORIZONTAL
                            layoutDirection=View.LAYOUT_DIRECTION_RTL
                            box.addView(this,LinearLayout.LayoutParams(-1,-2))
                        }
                        services.forEach { label ->
                            val shortcut=Button(this).apply {
                                style(this);text=label;textSize=11f;isAllCaps=false
                                minHeight=dp(44)
                                setOnClickListener {
                                    problem.setText(label)
                                    problem.setSelection(problem.text.length)
                                }
                            }
                            target.addView(shortcut,LinearLayout.LayoutParams(0,dp(46),1f))
                        }
                    }
                val date=field("تاريخ الاستلام YYYY-MM-DD",box,Store.today())
                intakePhotoStatus=TextView(this).also { it.text="لم تُضف صورة";box.addView(it) }
                intakePhotoPreview=ImageView(this).also { preview ->
                    preview.adjustViewBounds=true
                    preview.maxHeight=dp(160)
                    preview.contentDescription="اضغط لتكبير صورة الساعة قبل تسجيل الصيانة"
                    preview.setOnClickListener { intakePhotoPath?.let { showRepairPhoto(it) } }
                    box.addView(preview)
                }
                button("تصوير الساعة أو اختيار صورة",box){
                    startActivityForResult(Intent(this,MediaCaptureActivity::class.java),71)
                }
                ({
                    val name=n.text.toString().trim()
                    val phone=p.text.toString().trim()
                    val watch=w.text.toString().trim()
                    val why=problem.text.toString().trim()
                    require(name.isNotEmpty()){"اسم العميل مطلوب لاستلام الصيانة"}
                    require(watch.isNotEmpty()){"وصف الساعة مطلوب لاستلام الصيانة"}
                    require(why.isNotEmpty()){"يرجى توضيح مشكلة الساعة"}
                    val received=java.time.LocalDate.parse(date.text.toString().trim()).toString()
                    val photo=intakePhotoPath
                    if(photo!=null)localPhoto(photo)
                    work{
                        store.receiveRepair(name,phone,watch,why,received,imagePath=photo)
                        intakePhotoPath=null
                    }
                })
            }
        }
        collapsibleTools("تقارير وتدقيق الصيانة المتقدم") {
        button("كشف تفصيلي لمقبوضات الصيانة الشهرية"){form("كشف الدفعات","عرض"){box->val m=field("الشهر YYYY-MM",box,YearMonth.now().toString());({val rows=store.repairCashReceivedMonthDetails(m.text.toString().trim());val details=rows.joinToString("\n"){it.getValue("receipt_date")+" (تسليم "+it.getValue("delivery_date")+") · #"+it.getValue("invoice_number")+" · "+it.getValue("customer")+" · "+money(it.getValue("amount").toLong(),Currency.valueOf(it.getValue("currency")))+" · "+it.getValue("vault")};AlertDialog.Builder(this).setTitle("الدفعات الفعلية").setMessage(details.ifBlank{"لا توجد دفعات"}).setPositiveButton("حسناً",null).show()})}}
        button("عدد دفعات الصيانة الشهرية"){form("عدد الدفعات","عرض"){box->val m=field("الشهر YYYY-MM",box,YearMonth.now().toString());({val selected=m.text.toString().trim();val count=store.repairCashReceiptCount(selected);val old=store.repairOldDebtReceiptCount(selected);val current=store.repairReceiptCurrentMonthCount(selected);AlertDialog.Builder(this).setTitle("دفعات الصيانة").setMessage("عدد عمليات القبض: $count · فواتير الشهر: $current · ديون قديمة: $old").setPositiveButton("حسناً",null).show()})}}
        button("مقارنة تحصيل الصيانة الحالي والقديم"){
            form("مصادر تحصيل الصيانة","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=month.text.toString().trim()
                    val details=Currency.entries.joinToString("\n"){c->
                        c.name+" · إجمالي "+money(store.repairCashReceivedTotal(selected,c),c)+
                            " · قديم "+money(store.repairOldDebtCollectedTotal(selected,c),c)+
                            " · فواتير الشهر "+money(store.repairCurrentMonthCollectedTotal(selected,c),c)
                    }
                    AlertDialog.Builder(this).setTitle("تحصيل الصيانة حسب تاريخ الفاتورة")
                        .setMessage(details).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("تحصيل ديون صيانة من أشهر سابقة"){
            form("تحصيل الديون القديمة","عرض"){box->
                val month=field("شهر القبض YYYY-MM",box,YearMonth.now().toString())
                ({
                    val entries=store.repairOldDebtCollectedMonth(month.text.toString().trim())
                    val details=if(entries.isEmpty())"لا توجد تحصيلات لديون صيانة قديمة."
                    else entries.joinToString("\n"){row->
                        val c=Currency.valueOf(row.getValue("currency"))
                        row.getValue("vault")+" · "+money(row.getValue("amount").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("ديون قديمة تم تحصيلها")
                        .setMessage(details).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("ملخص أعمار ديون الصيانة"){
            try{
                val lines=store.repairDebtAgingTotals()
                val details=if(lines.isEmpty())"لا توجد ديون صيانة مفتوحة."
                else lines.joinToString("\n"){row->
                    val c=Currency.valueOf(row.getValue("currency"))
                    row.getValue("age_band")+" يوم · "+row.getValue("invoice_count")+
                        " فواتير · "+money(row.getValue("outstanding").toLong(),c)
                }
                AlertDialog.Builder(this).setTitle("شرائح أعمار الديون")
                    .setMessage(details).setPositiveButton("حسناً",null).show()
            }catch(e:Exception){error(e)}
        }
        button("ديون الصيانة حسب مدة التأخير"){
            form("أعمار ديون الصيانة","عرض"){box->
                val days=field("الحد الأدنى للأيام",box,"30",true)
                ({
                    val entries=store.repairOverdueDays(days.text.toString().toLong())
                    val details=if(entries.isEmpty())"لا توجد ديون بهذه المدة."
                    else entries.joinToString("\n"){row->
                        val c=Currency.valueOf(row.getValue("currency"))
                        row.getValue("customer")+" · "+row.getValue("age_days")+
                            " يوم · "+money(row.getValue("outstanding").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("ديون الصيانة حسب العمر")
                        .setMessage(details).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("ديون الصيانة حسب العميل"){
            try{
                val lines=store.repairDebtByCustomer()
                val details=if(lines.isEmpty())"لا توجد ديون صيانة مفتوحة."
                else lines.joinToString("\n"){row->
                    val c=Currency.valueOf(row.getValue("currency"))
                    row.getValue("customer")+" · "+row.getValue("invoice_count")+
                        " فواتير · "+money(row.getValue("outstanding").toLong(),c)
                }
                AlertDialog.Builder(this).setTitle("ديون العملاء للصيانة")
                    .setMessage(details).setPositiveButton("حسناً",null).show()
            }catch(e:Exception){error(e)}
        }
        button("ديون الصيانة حسب شهر التسليم"){
            try{
                val entries=store.repairDebtByDeliveryMonth()
                val details=if(entries.isEmpty())"لا توجد ديون صيانة مفتوحة."
                else entries.joinToString("\n"){row->
                    val c=Currency.valueOf(row.getValue("currency"))
                    row.getValue("month")+" · "+row.getValue("invoice_count")+
                        " فواتير · "+money(row.getValue("outstanding").toLong(),c)
                }
                AlertDialog.Builder(this).setTitle("أعمار ديون الصيانة")
                    .setMessage(details).setPositiveButton("حسناً",null).show()
            }catch(e:Exception){error(e)}
        }
        button("مطابقة قبض الصيانة مع قيود النقد"){
            val issues=store.repairCashJournalMonthIssues(YearMonth.now().toString())
            val report=if(issues.isEmpty())"لا توجد فروقات في الشهر الحالي."
            else issues.joinToString("\n"){row->
                val c=Currency.valueOf(row.getValue("currency"))
                row.getValue("vault")+" · "+c.name+" · "+
                    money(row.getValue("receipt_total").toLong()-
                        row.getValue("cash_posted").toLong(),c)
            }
            AlertDialog.Builder(this).setTitle("مطابقة القبض مع دفتر النقد")
                .setMessage(report).setPositiveButton("حسناً",null).show()
        }
        button("عدد فروقات مقبوضات الصيانة لهذا الشهر"){
            val month=YearMonth.now().toString()
            val issues=store.repairReceiptMonthReconciliation(month)
                .count{it.getValue("difference").toLong()!=0L}
            AlertDialog.Builder(this).setTitle("مطابقة المقبوضات")
                .setMessage("عدد فروقات الشهر الحالي: $issues")
                .setPositiveButton("حسناً",null).show()
        }
        button("فحص رد مبالغ الصيانة الزائدة"){
            val issues=store.repairReceivableOverAllocation()
            val report=if(issues.isEmpty())"لا توجد مبالغ معادة تتجاوز إجمالي المقبوضات."
            else issues.joinToString("\n"){row->
                "فاتورة "+row.getValue("invoice_number")+" · "+row.getValue("currency")
            }
            AlertDialog.Builder(this).setTitle("فحص مبالغ الرد")
                .setMessage(report).setPositiveButton("حسناً",null).show()
        }
        button("فواتير صيانة دون سجل مرتبط"){
            val totalLinks=store.repairInvoiceLinkAuditCount();val missingLinks=store.repairMissingInvoiceLinks().size;val duplicates=store.repairDuplicateInvoiceLinks().size;val wrong=store.repairWrongInvoiceLinks().size;val count=store.repairInvoiceLinkIssues().size
            AlertDialog.Builder(this).setTitle("مطابقة ربط الصيانة")
                .setMessage("إجمالي ملاحظات الربط: $totalLinks\nفواتير مرحلة دون سجل صيانة: $count\nروابط مكررة: $duplicates\nروابط لنوع فاتورة خاطئ: $wrong\nروابط لفواتير مفقودة: $missingLinks")
                .setPositiveButton("حسناً",null).show()
        }
        button("مطابقة إيراد فواتير الصيانة"){
            form("مطابقة الإيرادات","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val lines=store.repairMonthRevenueVariance(month.text.toString().trim());val issues=store.repairMonthRevenueIssues(month.text.toString().trim())
                    val report=if(lines.isEmpty())"لا توجد فواتير صيانة مرحلة في الشهر."
                    else lines.joinToString("\n"){row->
                        val c=Currency.valueOf(row.getValue("currency"))
                        c.name+" · الفرق "+money(row.getValue("difference").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("مطابقة إيرادات الصيانة")
                        .setMessage("عدد العملات ذات الفروقات: ${issues.size}\n"+report).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("حالات فواتير الصيانة خلال شهر"){
            form("حالات الفواتير","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val entries=store.repairInvoiceStatusMonth(month.text.toString().trim())
                    val report=if(entries.isEmpty())"لا توجد فواتير في هذا الشهر."
                    else entries.joinToString("\n"){row->
                        val c=Currency.valueOf(row.getValue("currency"))
                        row.getValue("status")+" · "+row.getValue("invoice_count")+
                            " فواتير · "+money(row.getValue("invoice_total").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("حالات فواتير الصيانة")
                        .setMessage(report).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("عدد فواتير الصيانة غير المرحلة"){
            val count=store.repairCancelledInvoices().size
            AlertDialog.Builder(this).setTitle("فواتير غير مرحلة")
                .setMessage("عدد الفواتير: $count")
                .setPositiveButton("حسناً",null).show()
        }
        button("تدقيق قبض فواتير الصيانة"){
            val all=store.repairReceiptAuditCount();val duplicate=store.repairDuplicateReceiptEventIssues();val cancelled=store.repairCancelledInvoiceReceiptIssues();val missing=store.repairMissingInvoiceReceiptIssues();val orphan=store.repairOrphanReceiptIssues();val over=store.repairOverpaymentIssues()
            val dates=store.repairReceiptDateIssues()
            val amounts=store.repairReceiptAmountIssues();val journal=store.repairReceiptJournalIssues()
            val message="إجمالي ملاحظات التدقيق: $all\n"+"تجاوز قيمة الفاتورة: ${over.size}\n"+
                "قبض قبل التسليم: ${dates.size}\n"+
                "مبالغ قبض غير صالحة: ${amounts.size}\n"+"قيود قبض غير متطابقة: ${journal.size}\n"+"دفعات بلا حدث محاسبي: ${orphan.size}\n"+"دفعات لفواتير ملغاة: ${cancelled.size}\n"+"دفعات بفواتير مفقودة: ${missing.size}\n"+"أحداث قبض مكررة: ${duplicate.size}"
            AlertDialog.Builder(this).setTitle("تدقيق المقبوضات")
                .setMessage(message).setPositiveButton("حسناً",null).show()
        }
        button("أرصدة ديون الصيانة حسب حالة السداد"){
            val lines=store.repairDebtStatusTotals()
            val report=if(lines.isEmpty())"لا توجد ديون صيانة مفتوحة."
            else lines.joinToString("\n"){row->
                val c=Currency.valueOf(row.getValue("currency"))
                (if(row.getValue("status")=="partial")"مدفوع جزئياً" else "غير مدفوع")+
                    " · "+row.getValue("invoice_count")+" فواتير · "+
                    money(row.getValue("outstanding").toLong(),c)
            }
            AlertDialog.Builder(this).setTitle("أرصدة الذمم حسب حالة السداد")
                .setMessage(report).setPositiveButton("حسناً",null).show()
        }
        button("ملخص حالة سداد ديون الصيانة"){
            val classified=store.repairDebtStatusCount();val partial=store.repairDebtPartiallyPaid().size
            val unpaid=store.repairDebtNeverPaid().size
            AlertDialog.Builder(this).setTitle("حالة السداد")
                .setMessage("إجمالي الفواتير المصنفة: $classified\nفواتير مدفوعة جزئياً: $partial\nفواتير دون أي دفعة: $unpaid")
                .setPositiveButton("حسناً",null).show()
        }
        button("المدفوع والمتبقي من فواتير الصيانة"){
            val lines=store.repairDebtCollectionProgress()
            val report=if(lines.isEmpty())"لا توجد فواتير صيانة غير مسددة."
            else lines.joinToString("\n"){row->
                val c=Currency.valueOf(row.getValue("currency"))
                "#"+row.getValue("invoice_number")+" · "+row.getValue("customer")+
                    " · مدفوع "+money(row.getValue("collected").toLong(),c)+
                    " · متبقٍ "+money(row.getValue("outstanding").toLong(),c)
            }
            AlertDialog.Builder(this).setTitle("تحصيل فواتير الصيانة")
                .setMessage(report).setPositiveButton("حسناً",null).show()
        }
        button("جميع ديون الصيانة المفتوحة"){
            try{
                val entries=store.repairOutstandingAll();val totals=store.repairOutstandingCurrencies().sorted().joinToString("\n"){code->val c=Currency.valueOf(code);code+" · "+money(store.repairOutstandingTotal(c),c)}
                val details=if(entries.isEmpty())"لا توجد ذمم صيانة مفتوحة."
                else entries.joinToString("\n"){row->
                    val c=Currency.valueOf(row.getValue("currency"))
                    "#"+row.getValue("invoice_number")+" · "+row.getValue("customer")+
                        " · "+row.getValue("phone")+" · "+row.getValue("delivery_date")+
                        " · "+money(row.getValue("outstanding").toLong(),c)
                }
                AlertDialog.Builder(this).setTitle("ديون الصيانة · جميع الأشهر")
                    .setMessage(totals+"\n\n"+details).setPositiveButton("حسناً",null).show()
            }catch(e:Exception){error(e)}
        }
        button("ذمم الصيانة غير المحصلة"){
            form("ذمم الصيانة","عرض"){box->
                val month=field("شهر تسليم الصيانة YYYY-MM",box,YearMonth.now().toString())
                ({
                    val entries=store.repairOutstandingMonth(month.text.toString().trim())
                    val detail=if(entries.isEmpty())"لا توجد ذمم صيانة متبقية لهذا الشهر."
                    else entries.joinToString("\n"){row->
                        val c=Currency.valueOf(row.getValue("currency"))
                        row.getValue("customer")+" · "+money(row.getValue("outstanding").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("ذمم الصيانة المتبقية")
                        .setMessage(detail).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("كشف قبض الصيانة ليوم محدد"){
            form("مقبوضات الصيانة","عرض"){box->
                val date=field("تاريخ القبض YYYY-MM-DD",box,Store.today())
                ({
                    val entries=store.repairCashReceivedDay(date.text.toString().trim())
                    val detail=if(entries.isEmpty())"لا توجد مقبوضات صيانة في هذا اليوم."
                    else entries.joinToString("\n"){row->
                        val c=Currency.valueOf(row.getValue("currency"))
                        "#"+row.getValue("invoice_number")+" · "+row.getValue("customer")+
                            " · "+money(row.getValue("amount").toLong(),c)+" · "+row.getValue("vault")
                    }
                    AlertDialog.Builder(this).setTitle("مقبوضات الصيانة اليومية")
                        .setMessage(detail).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("تحصيل أجور الصيانة خلال شهر"){
            form("تحصيل الصيانة","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val entries=store.repairCashReceivedMonth(month.text.toString().trim())
                    val details=if(entries.isEmpty())"لا توجد تحصيلات صيانة في الشهر."
                    else entries.joinToString("\n"){row->
                        val c=Currency.valueOf(row.getValue("currency"))
                        row.getValue("vault")+" · "+money(row.getValue("amount").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("أجور الصيانة المقبوضة فعلياً")
                        .setMessage(details).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("فروقات تكلفة قطع الصيانة"){form("مراجعة التكلفة","عرض"){box->val m=field("الشهر YYYY-MM",box,YearMonth.now().toString());({val issues=store.repairProfitMonthCostVariance(m.text.toString().trim());AlertDialog.Builder(this).setTitle("فروقات التكلفة").setMessage(if(issues.isEmpty())"لا توجد فروقات" else issues.joinToString("\n"){it.getValue("customer")+" · مسجل "+it.getValue("cost")+" · فعلي "+it.getValue("parts_cost")+" "+it.getValue("currency")}).setPositiveButton("حسناً",null).show()})}}
        button("تقرير ربح الصيانة حسب شهر التسليم"){
            form("ربح الصيانة","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=month.text.toString().trim();val entries=store.repairProfitMonth(selected);val totals=store.repairProfitMonthTotals(selected)
                    val summary=totals.joinToString("\n"){r->val c=Currency.valueOf(r.getValue("currency"));c.name+" · "+r.getValue("repair_count")+" صيانة · ربح "+money(r.getValue("margin").toLong(),c)};val detail=if(entries.isEmpty())"لا توجد صيانات مسلمة خلال الشهر."
                    else entries.joinToString("\n"){row->
                        val c=Currency.valueOf(row.getValue("currency"))
                        row.getValue("customer")+" · أجرة "+money(row.getValue("total").toLong(),c)+
                            " · تكلفة "+money(row.getValue("parts_cost").toLong(),c)+
                            " · هامش "+money(row.getValue("margin").toLong(),c)
                    }
                    AlertDialog.Builder(this).setTitle("ربح الصيانة")
                        .setMessage(summary+"\n\n"+detail).setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("أرشيف استلام الصيانات حسب الشهر"){
            form("صيانة شهر محدد","عرض"){box->
                val month=field("الشهر YYYY-MM",box,YearMonth.now().toString())
                ({
                    val selected=YearMonth.parse(month.text.toString().trim()).toString()
                    val entries=store.repairReceivedMonth(selected)
                    AlertDialog.Builder(this).setTitle("صيانات مستلمة · $selected")
                        .setMessage(if(entries.isEmpty())"لا توجد صيانات" else entries.joinToString("\n"){it.getValue("date")+" · "+it.getValue("customer")+" · "+it.getValue("watch")+" · "+label(it.getValue("status"))})
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        }
        button("تقرير الساعات غير المستلمة") {
            form("الساعات الموجودة لدينا","عرض"){box->
                val days=field("عدد أيام الانتظار على الأقل",box,"2",true)
                val page=field("صفحة التقرير (50 نتيجة)",box,"1",true)
                val stage=select("حالة الساعات",listOf("الكل","مستلمة","قيد العمل","جاهزة للاستلام"),box)
                ({
                    val pageNumber=page.text.toString().trim().toInt()
                    require(pageNumber in 1..20000){"رقم الصفحة غير صالح"}
                    val selectedStatus=listOf("all","received","in_progress","ready")[stage.selectedItemPosition]
                    val minimumDays=days.text.toString().trim().toLong()
                    val total=store.uncollectedRepairCount(minimumDays=minimumDays,status=selectedStatus)
                    val rows=store.uncollectedRepairs(minimumDays=minimumDays,limit=51,offset=(pageNumber-1)*50,
                        status=selectedStatus)
                    val details=rows.take(50).joinToString("\n") {
                        it.getValue("customer")+" · "+it.getValue("phone")+" · "+
                        it.getValue("watch")+" · "+label(it.getValue("status"))+
                        " · "+it.getValue("age_days")+" يوم"
                    }
                    AlertDialog.Builder(this).setTitle("الساعات غير المسلمة: "+total+" · صفحة "+pageNumber)
                        .setMessage(details.ifBlank { "لا توجد نتائج" } + if(rows.size>50)"\n\nتوجد صفحة تالية؛ اختر رقم الصفحة التالي." else "")
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        sectionHeading("طلبات الصيانة","الصور والحالة والتواصل مع العميل")
        text("الطلبات الحالية — اضغط على صورة الساعة لتكبيرها، ثم اضغط مجددًا أو × للعودة.",body,14f)
        val search=field("بحث بالعميل أو الهاتف أو الساعة أو المشكلة",body)
        val status=select("حالة الصيانة",listOf("الكل","مستلمة","قيد العمل","جاهزة","تم التسليم"),body)
        val from=field("من تاريخ الاستلام YYYY-MM-DD",body,Store.today())
        val until=field("إلى تاريخ الاستلام YYYY-MM-DD",body,Store.today())
        button("عرض طلبات آخر 30 يوماً") {
            from.setText(java.time.LocalDate.now().minusDays(29).toString())
            until.setText(Store.today())
        }
        button("عرض جميع طلبات الصيانة السابقة") {
            from.setText("2000-01-01")
            until.setText(Store.today())
        }
        button("اختيار فترة الصيانة من التقويم") {
            AlertDialog.Builder(this).setTitle("اختر التاريخ")
                .setItems(arrayOf("تاريخ البداية","تاريخ النهاية")) { _,index ->
                    val target=if(index==0)from else until
                    val day=runCatching { java.time.LocalDate.parse(target.text.toString()) }
                        .getOrElse { java.time.LocalDate.now() }
                    android.app.DatePickerDialog(this,{_,y,m,d->
                        target.setText(java.time.LocalDate.of(y,m+1,d).toString())
                    },day.year,day.monthValue-1,day.dayOfMonth).apply {
                        datePicker.maxDate=System.currentTimeMillis()
                    }.show()
                }.show()
        }
        val searchNow=Button(this).apply {
            style(this);text="بحث وعرض الصيانات";isAllCaps=false
            background=background(sand,12)
        }
        body.addView(searchNow,LinearLayout.LayoutParams(-1,dp(46)))
        val results=column();body.addView(results)
        var repairPage=0
        var repairSearchKey=""
        fun showRepairResults(){
            val states=listOf("all","received","in_progress","ready","delivered")
            val key=listOf(search.text.toString(),states[status.selectedItemPosition],
                from.text.toString().trim(),until.text.toString().trim()).joinToString("|")
            if(key!=repairSearchKey) { repairPage=0;repairSearchKey=key }
            results.removeAllViews()
            val fetched=store.repairListByDates(search.text.toString(),states[status.selectedItemPosition],
                from.text.toString().trim(),until.text.toString().trim(),limit=51,offset=repairPage*50)
            val matches=fetched.take(50)
            val hasNext=fetched.size>50
            text("صفحة "+(repairPage+1)+" · عدد الطلبات المعروضة: "+matches.size,results,16f)
            if(matches.isEmpty())text("لا توجد طلبات مطابقة ضمن الفترة المختارة.",results,14f)
            matches.forEach{row->
                val id=row.getValue("id")
                repairIdentity(row,results)
                val actions=column().apply {
                    visibility=View.GONE
                    background=background(cream,12)
                    setPadding(dp(8),dp(7),dp(8),dp(7))
                }
                button("▸ خيارات الطلب · "+row.getValue("customer"),results) {
                    actions.visibility=if(actions.visibility==View.VISIBLE)View.GONE else View.VISIBLE
                }
                results.addView(actions)
                button("فتح واتساب العميل",actions){
                    try {
                        CustomerChat.open(this,row.getValue("phone"),"مرحباً، معكم الصفا للساعات. بخصوص صيانة ساعتكم: "+row.getValue("watch")+"، الحالة الحالية: "+label(row.getValue("status"))+".")
                    }catch(e:Exception){error(e)}
                }
                button("مشاركة فاتورة الصيانة PDF",actions){
                    try {
                        val invoice=store.repairInvoice(id)
                        if(invoice==null)throw IllegalStateException("لم تُنشأ فاتورة لهذا الطلب بعد")
                        shareInvoicePdf(store.invoice(invoice.getValue("id")))
                    }catch(e:Exception){error(e)}
                }
                button("إرسال صورة الصيانة عبر واتساب",actions){
                    try {
                        val path=store.repairImage(id) ?: throw IllegalStateException("لا توجد صورة لهذا الطلب")
                        CustomerShare.send(this,localPhoto(path),imageMime(path),"صورة ساعة الصيانة - الصفا للساعات")
                    }catch(e:Exception){error(e)}
                }
                button("صورة الصيانة",actions){
                    photoRepairId=id
                    startActivityForResult(Intent(this,MediaCaptureActivity::class.java),70)
                }
                button("تفاصيل الصيانة",actions){val detail=store.repairDetail(id);val invoice=store.repairInvoice(id);val profit=store.repairProfit(id);val profitText=if(profit==null)"" else {val c=Currency.valueOf(profit.getValue("currency"));"\nأجرة الصيانة: "+money(profit.getValue("revenue").toLong(),c)+"\nتكلفة القطع: "+money(profit.getValue("parts_cost").toLong(),c)+"\nهامش الصيانة: "+money(profit.getValue("margin").toLong(),c)+"\nالمقبوض: "+money(profit.getValue("collected").toLong(),c)+"\nالمتبقي: "+money(profit.getValue("outstanding").toLong(),c)};val info="العميل: "+detail.getValue("customer")+"\nالهاتف: "+detail.getValue("phone")+"\nالساعة: "+detail.getValue("watch")+"\nالمشكلة: "+detail.getValue("problem")+"\nالحالة: "+label(detail.getValue("status"))+"\nالفاتورة: "+(invoice?.get("number")?:"لا توجد")+profitText;AlertDialog.Builder(this).setTitle("تفاصيل الصيانة").setMessage(info)
                    .setNeutralButton("عرض الصورة"){_,_->
                        val path=store.repairImage(id)
                        if(path==null || runCatching { localPhoto(path) }.isFailure){
                            Toast.makeText(this,"لا توجد صورة محفوظة لهذا الطلب",Toast.LENGTH_SHORT).show()
                        }else{
                            showRepairPhoto(path)
                        }
                    }.setPositiveButton("حسناً",null).show()}
            }
            if(repairPage>0) button("الصفحة السابقة",results) {
                repairPage--
                showRepairResults()
            }
            if(hasNext) button("الصفحة التالية",results) {
                repairPage++
                showRepairResults()
            }
        }
        text("الطلبات المفتوحة",body,20f).setTextColor(gold)
        val counts=store.repairStatusCounts()
        text("مستلمة: ${counts.getValue("received")} · قيد العمل: ${counts.getValue("in_progress")} · جاهزة: ${counts.getValue("ready")} · مسلّمة: ${counts.getValue("delivered")}",size=15f)
        val openRepairs=store.repairList().filter{it.getValue("status")!="delivered"}
        if(openRepairs.isEmpty())text("لا توجد طلبات صيانة مفتوحة حالياً.",body,14f)
        var openVisible=24
        val openList=column()
        body.addView(openList)
        fun showOpenRepairs() {
        openList.removeAllViews()
        if(openRepairs.size>openVisible)text("تُعرض أول ${openVisible} صيانة مفتوحة؛ استخدم البحث أعلاه أو عرض المزيد.",openList,13f)
        openRepairs.take(openVisible).forEach{r->
            val id=r.getValue("id")
            repairIdentity(r,openList)
            collapsibleTools("إجراءات صيانة · "+r.getValue("customer"),openList) {
                if(store.repairImage(id).let { it==null || runCatching { localPhoto(it) }.isFailure })button("إضافة صورة الساعة"){photoRepairId=id;startActivityForResult(Intent(this,MediaCaptureActivity::class.java),70)};button("استخدام قطعة صيانة"){
            chooseProduct(inStockOnly=true,kind="part"){part->
                form("تركيب قطعة للصيانة"){box->
                    val quantity=field("الكمية",box,"1",true)
                    val date=field("تاريخ التركيب YYYY-MM-DD",box,Store.today())
                    ({
                        require(part.kind=="part"){"المنتج المختار ليس قطعة صيانة"}
                        val q=quantity.text.toString().toLong()
                        val actual=java.time.LocalDate.parse(date.text.toString().trim()).toString()
                        work{store.useRepairPart(id,part.id,q,actual)}
                    })
                }
            }
        };if(r.getValue("status")=="received")button("بدء الصيانة"){work{store.startRepair(id)}};if(r.getValue("status") in setOf("received","in_progress"))button("جاهزة للاستلام"){work{store.repairReady(id)}};if(r.getValue("status")=="ready")button("تسليم وفاتورة أجرة عمل"){form("تسليم الصيانة"){box->val c=select("العملة",Currency.entries.map{it.name},box);val n=field("أجرة العمل",box,"0",true);val paid=field("المدفوع",box,"0",true);val v=select("خزنة القبض",vaultNames,box);val delivered=field("تاريخ تسليم الساعة YYYY-MM-DD",box,Store.today());val received=field("تاريخ قبض الأجرة الفعلي YYYY-MM-DD",box,Store.today());{val curr=Currency.entries[c.selectedItemPosition];val value=amount(n,curr);val pay=amount(paid,curr);require(value>=0L){"أجرة الصيانة لا يمكن أن تكون سالبة"};require(pay>=0L && pay<=value){"المدفوع يجب أن يكون بين الصفر وأجرة الصيانة"};val vault=Store.vaults[v.selectedItemPosition];val deliveryDate=java.time.LocalDate.parse(delivered.text.toString().trim()).toString();val receiptDate=java.time.LocalDate.parse(received.text.toString().trim()).toString();work{store.deliverRepair(id,curr,value,pay,vault,deliveryDate=deliveryDate,receiptDate=receiptDate)}}}}}
            }
        }
        if(openRepairs.size>openVisible) button("عرض المزيد من الصيانات المفتوحة",openList) {
            openVisible+=24
            showOpenRepairs()
        }
        }
        showOpenRepairs()
        searchNow.setOnClickListener { if(!busy) showRepairResults() }
    }
    private fun reports(){sectionHeading("التقارير الشهرية","اختر شهراً واحداً للمراجعة والتحليل");
        collapsibleTools("تدقيق الحسابات والفواتير المتقدم") {
        button("تدقيق ربط حسابات الفواتير"){
            try {
                val issues=store.invoiceAccountReferenceIssues()
                val details=if(issues.isEmpty()) "جميع حسابات الذمم والاسترداد مرتبطة بفواتير وعملات صحيحة."
                else issues.take(100).joinToString("\n"){row->
                    row.getValue("account")+" · "+row.getValue("currency")+
                        " · عدد القيود "+row.getValue("entry_count")
                } + if(issues.size>100) "\n... توجد نتائج إضافية" else ""
                AlertDialog.Builder(this).setTitle("تدقيق حسابات الفواتير")
                    .setMessage(details).setPositiveButton("حسناً",null).show()
            } catch(e:Exception) {
                AlertDialog.Builder(this).setMessage(e.message ?: "تعذر تدقيق الحسابات")
                    .setPositiveButton("حسناً",null).show()
            }
        }
        button("تدقيق مطابقة كشوف الفواتير"){
            try {
                val issues=store.invoiceStatementIssues()
                val details=if(issues.isEmpty()) "كشوف الفواتير مطابقة لأرصدة دفتر القيود."
                else issues.take(100).joinToString("\n"){row->
                    "#"+row.getValue("number")+" · "+row.getValue("issue")
                } + if(issues.size>100) "\n... توجد نتائج إضافية" else ""
                AlertDialog.Builder(this).setTitle("مطابقة كشوف الفواتير")
                    .setMessage(details).setPositiveButton("حسناً",null).show()
            } catch(e:Exception) {
                AlertDialog.Builder(this).setMessage(e.message ?: "تعذر تدقيق الفواتير")
                    .setPositiveButton("حسناً",null).show()
            }
        }

        button("تدقيق الأيام ذات الذمم السالبة"){
            try {
                val issues=store.negativeReceivableDays()
                val details=if(issues.isEmpty()) "لا توجد أيام برصيد ذمة عميل سالب."
                else issues.take(100).joinToString("\n"){row->
                    val c=Currency.valueOf(row.getValue("currency"))
                    row.getValue("date")+" · فاتورة "+row.getValue("invoice_id")+
                        " · "+money(row.getValue("receivable").toLong(),c)
                } + if(issues.size>100) "\n... توجد نتائج إضافية" else ""
                AlertDialog.Builder(this).setTitle("تدقيق الذمم التاريخية")
                    .setMessage(details).setPositiveButton("حسناً",null).show()
            } catch(e:Exception) {
                AlertDialog.Builder(this).setMessage(e.message ?: "تعذر تدقيق الذمم")
                    .setPositiveButton("حسناً",null).show()
            }
        }

        button("تدقيق تواريخ التزامات الاسترداد"){
            try {
                val issues=store.negativeRefundLiabilityDays()
                val details=if(issues.isEmpty()) "لا توجد أيام سُجل فيها التزام استرداد سالب."
                else issues.take(100).joinToString("\n"){row->
                    val c=Currency.valueOf(row.getValue("currency"))
                    row.getValue("date")+" · فاتورة "+row.getValue("invoice_id")+
                        " · "+money(row.getValue("liability").toLong(),c)
                } + if(issues.size>100) "\n... توجد نتائج إضافية" else ""
                AlertDialog.Builder(this).setTitle("التزامات الاسترداد حسب التاريخ")
                    .setMessage(details).setPositiveButton("حسناً",null).show()
            } catch(e:Exception) {
                AlertDialog.Builder(this).setMessage(e.message ?: "تعذر تدقيق الاسترداد")
                    .setPositiveButton("حسناً",null).show()
            }
        }

        button("مطابقة التزامات رد الأموال للعملاء"){
            try {
                val issues=store.refundLiabilityIssues()
                val details=if(issues.isEmpty()) "لا توجد فروقات بين التزامات الاسترداد والقيود المسجلة."
                else issues.take(100).joinToString("\n"){row->
                    val c=Currency.valueOf(row.getValue("currency"))
                    "فاتورة "+row.getValue("number")+" · "+c.name+
                        " · التزام دفتري "+money(row.getValue("ledger_liability").toLong(),c)+
                        " · المتوقع "+money(row.getValue("expected_liability").toLong(),c)
                } + if(issues.size>100) "\n... توجد نتائج إضافية" else ""
                AlertDialog.Builder(this).setTitle("مطابقة رد الأموال")
                    .setMessage(details).setPositiveButton("حسناً",null).show()
            } catch(e:Exception) {
                AlertDialog.Builder(this).setMessage(e.message ?: "تعذر تدقيق التزامات الاسترداد")
                    .setPositiveButton("حسناً",null).show()
            }
        }

        button("تدقيق أرصدة ذمم العملاء"){
            try {
                val issues=store.customerReceivableIssues()
                val details=if(issues.isEmpty()) "تتطابق ذمم الفواتير مع حساباتها في دفتر الأستاذ."
                else issues.take(100).joinToString("\n"){row->
                    val c=Currency.valueOf(row.getValue("currency"))
                    "فاتورة "+row.getValue("number")+" · "+c.name+
                        " · دفتر "+money(row.getValue("ledger_due").toLong(),c)+
                        " · متوقع "+money(row.getValue("expected_due").toLong(),c)
                } + if(issues.size>100) "\n... توجد نتائج إضافية" else ""
                AlertDialog.Builder(this).setTitle("مطابقة ذمم العملاء")
                    .setMessage(details).setPositiveButton("حسناً",null).show()
            } catch(e:Exception) {
                AlertDialog.Builder(this).setMessage(e.message ?: "تعذر تدقيق الذمم")
                    .setPositiveButton("حسناً",null).show()
            }
        }
        }
        sectionHeading("التقارير الأساسية","ميزان المراجعة ودفتر الأستاذ والتقارير الشهرية")
        button("ميزان المراجعة بين تاريخين"){go("trial-balance")};button("دفتر الأستاذ التفصيلي"){go("account-ledger")};button("تدقيق توازن القيود"){go("journal-integrity")};button("فحص سلامة قاعدة البيانات"){go("structural-audit")};val month=field("الشهر YYYY-MM",body,YearMonth.now().toString());val result=column();body.addView(result);button("عرض"){try{val rows=store.monthReport(month.text.toString());result.removeAllViews();Currency.entries.forEach{c->val selected=rows.filter{it.getValue("currency")==c.name};val revenue=-selected.filter{it.getValue("account").startsWith("revenue:")}.sumOf{it.getValue("amount").toLong()};val expense=selected.filter{it.getValue("account").startsWith("expense:")}.sumOf{it.getValue("amount").toLong()};text("${c.name} · صافي الربح الدفتري ${money(revenue-expense,c)}",result);selected.filter{it.getValue("account").startsWith("equity:withdrawal:")}.forEach{text("سحب ${it.getValue("account").substringAfterLast(':')} في الشهر: ${money(it.getValue("amount").toLong(),c)}",result)}}}catch(e:Exception){error(e)}}}

    private fun structuralAuditScreen(){
        text("فحص سلامة قاعدة البيانات",size=25f)
        text("فحص الروابط والباركود والمخزون دون حذف أو تعديل أي سجل.",size=15f)
        val output=column();body.addView(output)
        button("تشغيل الفحص"){
            try{
                output.removeAllViews()
                val issues=store.accountingIntegrityIssues()+store.sqliteIntegrityIssues()+store.inventoryAccountingIssues()+store.customerReceiptJournalIssues()+store.customerReceivableJournalIssues()+store.customerReceiptDateIssues()+store.repairPartsAccountingIssues()+store.repairInvoiceCostIssues()+store.repairPartDateIssues()
                if(issues.isEmpty())text("لم تُكتشف مخالفات ضمن الفحوص المحددة.",output)
                else{
                    text("عدد المخالفات: ${issues.size}",output,size=21f)
                    issues.forEach{issue->
                        text(issue.getValue("category")+" · "+issue.getValue("reference"),output)
                        text(issue.getValue("detail"),output)
                    }
                    text("لا تُصحح المخالفات تلقائياً. يجب مراجعتها قبل اعتماد البيانات.",output)
                }
            }catch(e:Exception){error(e)}
        }
    }
    private fun journalIntegrityScreen(){
        text("تدقيق توازن القيود المحاسبية",size=25f)
        text("فحص كل عملية على حدة، دون تغيير أو ترحيل أي قيد.",size=15f)
        val from=field("من YYYY-MM-DD",body,YearMonth.now().atDay(1).toString())
        val until=field("إلى YYYY-MM-DD",body,Store.today())
        val output=column();body.addView(output)
        button("فحص القيود"){
            try{
                output.removeAllViews()
                val start=from.text.toString().trim()
                val end=until.text.toString().trim()
                Currency.entries.forEach{c->
                    val summary=store.journalIntegritySummary(c,start,end)
                    val problems=store.unbalancedEvents(c,start,end)
                    text("${c.name} · "+(if(problems.isEmpty() && summary.getValue("difference")==0L)
                        "قيود الفترة متوازنة" else "تحذير: توجد فروقات"),output,size=21f)
                    text("مدين "+money(summary.getValue("debits"),c)+
                        " · دائن "+money(summary.getValue("credits"),c)+
                        " · الفرق "+money(summary.getValue("difference"),c),output)
                    text("عمليات غير متوازنة: ${summary.getValue("unbalanced_events")}",output)
                    problems.forEach{row->
                        text(row.getValue("date")+" · "+row.getValue("kind")+" · "+row.getValue("memo"),output)
                        text("رقم العملية "+row.getValue("event_id")+
                            " · فرق "+money(row.getValue("difference").toLong(),c),output)
                    }
                }
            }catch(e:Exception){error(e)}
        }
    }
    private fun accountLedgerScreen(){
        text("دفتر الأستاذ التفصيلي",size=25f)
        val currency=select("العملة",Currency.entries.map{it.name},body)
        val account=field("اسم الحساب المحاسبي كما يظهر في ميزان المراجعة",body)
        val from=field("من YYYY-MM-DD",body,YearMonth.now().atDay(1).toString())
        val until=field("إلى YYYY-MM-DD",body,Store.today())
        val output=column();body.addView(output)
        button("عرض حسابات العملة"){
            try{
                output.removeAllViews()
                val c=Currency.entries[currency.selectedItemPosition]
                store.accountBalances(c).forEach{row->
                    button(row.getValue("account")+" · "+money(row.getValue("balance").toLong(),c),output){
                        account.setText(row.getValue("account"))
                    }
                }
            }catch(e:Exception){error(e)}
        }
        button("عرض حركات الحساب"){
            try{
                output.removeAllViews()
                val c=Currency.entries[currency.selectedItemPosition]
                val name=account.text.toString().trim()
                val start=from.text.toString().trim()
                val end=until.text.toString().trim()
                val lines=store.accountEntries(name,c,start,end)
                val opening=store.trialBalanceRange(start,end,c).firstOrNull{it.getValue("account")==name}
                    ?.getValue("opening")?.toLong()?:0L
                var running=opening
                text("رصيد افتتاحي "+money(opening,c),output)
                lines.forEach{row->
                    running=Math.addExact(running,row.getValue("amount").toLong())
                    text(row.getValue("date")+" · "+row.getValue("kind")+" · "+row.getValue("memo"),output)
                    text("حركة "+money(row.getValue("amount").toLong(),c)+" · رصيد "+money(running,c),output)
                }
                if(lines.isEmpty())text("لا توجد حركات ضمن الفترة",output)
                text("الرصيد الختامي "+money(running,c),output)
            }catch(e:Exception){error(e)}
        }
    }
    private fun trialBalanceScreen(){
        text("ميزان المراجعة",size=25f)
        text("الأرصدة الافتتاحية والحركات المدينة والدائنة والرصيد النهائي لكل حساب وعملة.",size=15f)
        val from=field("من YYYY-MM-DD",body,YearMonth.now().atDay(1).toString())
        val until=field("إلى YYYY-MM-DD",body,Store.today())
        val output=column();body.addView(output)
        button("إعداد ميزان المراجعة"){
            try{
                val start=from.text.toString().trim()
                val end=until.text.toString().trim()
                output.removeAllViews()
                Currency.entries.forEach{c->
                    val lines=store.trialBalanceRange(start,end,c)
                    val valid=store.trialBalanceReconciles(start,end,c)
                    text("${c.name} · "+(if(valid)"متوازن" else "تحذير: ميزان غير متوازن"),output,size=21f)
                    var debit=0L
                    var credit=0L
                    lines.forEach{row->
                        debit=Math.addExact(debit,row.getValue("debits").toLong())
                        credit=Math.addExact(credit,row.getValue("credits").toLong())
                        text(row.getValue("account")+
                            "\nافتتاحي "+money(row.getValue("opening").toLong(),c)+
                            " · مدين "+money(row.getValue("debits").toLong(),c)+
                            " · دائن "+money(row.getValue("credits").toLong(),c)+
                            " · نهائي "+money(row.getValue("closing").toLong(),c),output)
                    }
                    text("إجمالي مدين "+money(debit,c)+" · إجمالي دائن "+money(credit,c),output)
                }
            }catch(e:Exception){error(e)}
        }
    }
    private fun periodReports(){
        text("تقرير الأرباح بين تاريخين",size=25f)
        text("كل عملة مستقلة، والنتيجة من قيود Native 6 فقط؛ لا تشمل الأرشيف القديم.",size=15f)
        val start=field("من YYYY-MM-DD",body,YearMonth.now().atDay(1).toString())
        val end=field("إلى YYYY-MM-DD",body,Store.today())
        val result=column();body.addView(result)
        button("تفصيل ربح كل عملية في يوم محدد"){
            form("ربح العمليات اليومية","عرض"){box->
                val day=field("التاريخ YYYY-MM-DD",box,Store.today())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                ({
                    val date=java.time.LocalDate.parse(day.text.toString().trim()).toString()
                    val unit=Currency.entries[currency.selectedItemPosition]
                    require(store.operatingDayEventsReconcile(date,unit)){"تفصيل العمليات لا يطابق الربح اليومي"}
                    val entries=store.operatingDayEventBreakdown(date,unit)
                    AlertDialog.Builder(this).setTitle("ربح كل عملية · $date · ${unit.name}")
                        .setMessage(if(entries.isEmpty())"لا توجد عمليات ربح أو مصاريف أو سحوبات" else
                            entries.joinToString("\n\n"){row->
                                row.getValue("kind")+" · "+row.getValue("memo")+
                                    "\nإيراد "+money(row.getValue("revenue").toLong(),unit)+
                                    " · تكلفة "+money(row.getValue("cost_of_sales").toLong(),unit)+
                                    " · مصاريف "+money(row.getValue("operating_expenses").toLong(),unit)+
                                    " · صافي "+money(row.getValue("net_result").toLong(),unit)+
                                    " · سحوبات "+money(row.getValue("drawings").toLong(),unit)
                            })
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("تدقيق قيود أرباح يوم محدد"){
            form("تدقيق الأرباح اليومية","عرض"){box->
                val day=field("التاريخ YYYY-MM-DD",box,Store.today())
                val currency=select("العملة",Currency.entries.map{it.name},box)
                ({
                    val date=java.time.LocalDate.parse(day.text.toString().trim()).toString()
                    val unit=Currency.entries[currency.selectedItemPosition]
                    require(store.operatingDayAuditReconciles(date,unit)){"قيود اليوم لا تطابق التقرير"}
                    val entries=store.operatingDayAudit(date,unit)
                    AlertDialog.Builder(this).setTitle("تدقيق $date · ${unit.name}")
                        .setMessage(if(entries.isEmpty())"لا توجد قيود ربح أو مصاريف أو سحوبات" else
                            entries.joinToString("\n"){row->
                                row.getValue("kind")+" · "+row.getValue("account")+
                                    " · "+money(row.getValue("amount").toLong(),unit)+
                                    " · "+row.getValue("memo")
                            })
                        .setPositiveButton("حسناً",null).show()
                })
            }
        }
        button("عرض تفصيل الأرباح اليومي"){
            try {
                val from=start.text.toString().trim()
                val until=end.text.toString().trim()
                result.removeAllViews()
                Currency.entries.forEach{currency->
                    require(store.operatingDailyReconciles(from,until,currency)){"مجموع الأرباح اليومية لا يطابق تقرير الفترة"}
                    val days=store.operatingDailyRange(from,until,currency)
                    text("تفصيل ${currency.name} · ${days.size} يوم بحركات",result)
                    if(days.isEmpty())text("لا توجد قيود إيرادات أو مصاريف أو سحوبات خلال الفترة",result)
                    days.forEach{day->
                        val date=day.getValue("date_epoch").toString()
                        val formatted=date.substring(0,4)+"-"+date.substring(4,6)+"-"+date.substring(6,8)
                        text(formatted+" · إيراد "+money(day.getValue("revenue"),currency)+
                            " · تكلفة "+money(day.getValue("cost_of_sales"),currency)+
                            " · مصاريف "+money(day.getValue("operating_expenses"),currency)+
                            " · صافي "+money(day.getValue("net_result"),currency)+
                            " · سحوبات "+money(day.getValue("drawings"),currency),result)
                    }
                }
            }catch(e:Exception){error(e)}
        }
        button("احتساب الفترة"){
            try {
                val from=start.text.toString().trim()
                val until=end.text.toString().trim()
                result.removeAllViews()
                Currency.entries.forEach { currency ->
                    val report=store.operatingResultRange(from,until,currency)
                    text("${currency.name} · الإيرادات ${money(report.getValue("revenue"),currency)}",result)
                    text("تكلفة البضاعة المباعة ${money(report.getValue("cost_of_sales"),currency)}",result)
                    text("مجمل الربح ${money(report.getValue("gross_profit"),currency)}",result)
                    text("المصاريف التشغيلية ${money(report.getValue("operating_expenses"),currency)}",result)
                    text("صافي النتيجة ${money(report.getValue("net_result"),currency)}",result)
                    text("سحوبات الشركاء ${money(report.getValue("partner_drawings"),currency)} · لا تُخصم مرة ثانية من الربح",result)
                }
            } catch(e:Exception){error(e)}
        }
    }
    private fun partners(){
        sectionHeading("الشركاء ونسب الأرباح","تقرير كل شهر مستقل مع فصل الأرصدة السابقة")
        text("النسب تُسجل بتاريخ سريانها، ولا تُعدّل قيود الأشهر السابقة.",size=15f)
        button("＋ إضافة شريك"){
            form("إضافة شريك"){box->
                val name=field("اسم الشريك",box)
                ({work { com.alsafa.native6.accounting.PartnerRegistry(store.db).addPartner(name.text.toString()) }})
            }
        }
        val partners=store.rows("SELECT id,name FROM partner_members ORDER BY name")
        partners.forEach { text(it.getValue("name")+" · "+it.getValue("id"),size=15f) }
        if(partners.isNotEmpty()) button("تسجيل نسب الشركاء بتاريخ سريان"){
            form("نسب الشركاء"){box->
                val date=field("تاريخ السريان YYYY-MM-DD",box,Store.today())
                val entries=partners.map { it.getValue("id") to field(it.getValue("name")+" %",box,"0",true) }
                ({work {
                    val shares=entries.associate { (id,input)-> id to java.math.BigDecimal(input.text.toString()) }
                    com.alsafa.native6.accounting.PartnerRegistry(store.db).setShares(java.time.LocalDate.parse(date.text.toString()),shares)
                }})
            }
        }
        button("تدقيق السحوبات غير المرتبطة"){
            try {
                val pending=com.alsafa.native6.accounting.PartnerRegistry(store.db).unlinkedWithdrawals()
                AlertDialog.Builder(this)
                    .setTitle("سحوبات تحتاج مراجعة · ${pending.size}")
                    .setMessage(if(pending.isEmpty()) "لا توجد سحوبات غير مرتبطة" else pending.take(50).joinToString("\n"))
                    .setPositiveButton("حسناً",null).show()
            } catch(e:Exception){error(e)}
        }
        button("ربط السحوبات السابقة المسجلة"){
            AlertDialog.Builder(this).setTitle("مراجعة السحوبات القديمة")
                .setMessage("سيُربط فقط السحب الذي يحمل معرف شريك مطابقًا وقيدًا محاسبيًا واضحًا. لن تتغير مبالغ الخزنات أو القيود القديمة.")
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("متابعة"){_,_->work {
                    com.alsafa.native6.accounting.PartnerRegistry(store.db).backfillPostedDrawings()
                }}.show()
        }
        val month=field("شهر التقرير YYYY-MM",body,YearMonth.now().toString())
        button("عرض سحوبات الشركاء من النسخة القديمة (الشهر المحدد)") {
            try {
                val selected=YearMonth.parse(month.text.toString().trim())
                work {
                    val report=LegacyArchivedFinancialPosition.monthlyPartnerWithdrawals(store,selected.toString())
                    val entries=report.getJSONArray("withdrawals")
                    val details=(0 until minOf(entries.length(),100)).map { index ->
                        val item=entries.getJSONObject(index)
                        "${item.optString("date")} | ${item.optString("partner")} | ${item.optString("amount")} ${item.optString("currency")}"
                    }.joinToString("\n")
                    val message="الشهر: ${report.getString("month")}\n"+
                        "عدد السحوبات: ${entries.length()}\n"+
                        "الإجماليات لكل شريك وعملة: ${report.getJSONObject("totalsByPartnerCurrency")}\n"+
                        "الأشهر الأخرى مستبعدة: ${report.getInt("excludedOtherMonths")}\n"+
                        "تفاصيل السحوبات (أول 100 حركة):\n"+details+"\n"+
                        "هذه بيانات تاريخية للقراءة فقط، ولا تدخل في أرباح أو سحوبات Native 6."
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("سحوبات الشركاء القديمة")
                            .setMessage(message).setPositiveButton("إغلاق",null).show()
                    }
                }
            } catch(e:Exception){error(e)}
        }
        val firstMonth=field("أول شهر للحسابات السابقة YYYY-MM",body,YearMonth.now().toString())
        val currency=select("عملة التقرير",Currency.entries.map { it.name },body)
        val result=column();body.addView(result)
        button("عرض تقرير الشركاء"){
            try {
                val selected=YearMonth.parse(month.text.toString().trim())
                val first=YearMonth.parse(firstMonth.text.toString().trim())
                require(!first.isAfter(selected)){"شهر بداية الأرصدة السابقة يجب ألا يكون بعد شهر التقرير"}
                val curr=Currency.entries[currency.selectedItemPosition]
                val lines=store.partnerBalanceSummary(selected.toString(),first.toString(),curr)
                result.removeAllViews()
                text("تقرير ${selected} · ${curr.name}",result,20f)
                text("الأرباح والسحوبات تخص الشهر المختار فقط، والأرصدة السابقة منفصلة.",result,15f)
                if(lines.isEmpty()) text("لا توجد حسابات شركاء مسجلة لهذا التقرير.",result,15f)
                lines.forEach { line->
                    val name=partners.firstOrNull { it["id"]==line.partnerId }?.get("name")?:line.partnerId
                    val partnerCard=column().apply {
                        background=background(paper,14)
                        setPadding(dp(15),dp(12),dp(15),dp(12))
                    }
                    text(name,partnerCard,19f).setTextColor(gold)
                    text("ربح الشهر: ${money(line.selectedMonthProfitMinor,curr)}",partnerCard,15f).setTextColor(ink)
                    text("سحوبات الشهر: ${money(line.selectedMonthDrawingsMinor,curr)}",partnerCard,15f).setTextColor(ink)
                    text("المتبقي للشهر: ${money(line.selectedMonthRemainderMinor,curr)}",partnerCard,16f).setTextColor(gold)
                    val previous=TextView(this).apply {
                        style(this)
                        text="رصيد الأشهر السابقة (من بداية الفترة المحددة): ${money(line.olderUnpaidMinor,curr)}"
                        textSize=13f
                        setTextColor(muted)
                        background=background(cream,10)
                    }
                    partnerCard.addView(previous)
                    result.addView(partnerCard,LinearLayout.LayoutParams(-1,-2).apply {
                        setMargins(0,dp(7),0,dp(7))
                    })
                }
            } catch(e:Exception){error(e)}
        }
    }
    private fun audit(){text("سجل العمليات",size=25f);store.rows("SELECT * FROM events ORDER BY created DESC LIMIT 250").forEach{text("${it.getValue("date")} · ${it.getValue("kind")}\n${it.getValue("memo")}")}}
    private fun confirmFactoryReset() {
        if(busy) {
            Toast.makeText(this,"انتظر انتهاء العملية الجارية قبل إعادة التهيئة",Toast.LENGTH_LONG).show()
            return
        }
        val confirmation=EditText(this).apply {
            hint="اكتب تهيئة للتأكيد"
            setSingleLine(true)
        }
        val wrapper=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(12),dp(20),0)
            addView(confirmation)
        }
        AlertDialog.Builder(this)
            .setTitle("إعادة تهيئة التطبيق بالكامل")
            .setMessage("تحذير: ستحذف هذه العملية قاعدة البيانات المحلية، وجميع الصور، ونسخ الصفا المحفوظة داخل التطبيق. لن يمكن التراجع عنها. احفظ نسخة احتياطية خارج التطبيق أولاً. لن تُحذف الملفات الموجودة في Google Drive أو الملفات التي صدّرتها بنفسك.")
            .setView(wrapper)
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("حذف البيانات وإعادة التهيئة"){_,_->
                if(confirmation.text.toString().trim()!="تهيئة") {
                    Toast.makeText(this,"لم تتم التهيئة: يجب كتابة كلمة تهيئة تماماً",Toast.LENGTH_LONG).show()
                } else if(busy) {
                    Toast.makeText(this,"عملية أخرى جارية؛ أُلغيت إعادة التهيئة",Toast.LENGTH_LONG).show()
                } else {
                    work {
                        // The database is closed before deletion; no SQL triggers
                        // or partial table deletes can leave an inconsistent ledger.
                        store.helper.close()
                        require(deleteDatabase("alsafa_native6.db")) { "تعذر حذف قاعدة البيانات المحلية" }
                        for(folder in listOf("images","legacy_snapshots")) {
                            val directory=java.io.File(filesDir,folder)
                            if(directory.exists()) require(directory.deleteRecursively()) {
                                "تعذر تنظيف مجلد $folder؛ أعد المحاولة قبل استيراد أي نسخة"
                            }
                        }
                        // Recreate the empty schema before reporting completion.
                        val fresh=Store(StoreDb(this@MainActivity))
                        fresh.scalar("SELECT count(*) FROM events")
                        runOnUiThread {
                            store=fresh
                            basket.clear()
                            selectedProduct=null
                            photoProductId=null
                            photoRepairId=null
                            intakePhotoPath=null
                            pdfInvoice=null
                            lastScrollY=0
                            savedInputs=null
                            page="home"
                        }
                    }
                }
            }
            .show()
    }
    private fun backup(){text("النسخ الاحتياطي",size=25f);text("اختر Google Drive من منتقي Android إن كان مثبتاً. إنشاء الملف محلي؛ رفعه إلى Drive يحتاج اتصالاً.")
        button("تصدير نسخة كاملة"){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"AlSafa-Native6-${Store.today()}.json")},101)}
        button("استعادة Native 6 إلى قاعدة فارغة"){pick(102)}
        button("إعادة تهيئة التطبيق وحذف بياناته المحلية"){confirmFactoryReset()}
        button("فحص نسخة التطبيق القديم وحفظها معزولة"){pick(103)}
        button("اختيار نسخة الصفا القديمة واستعادة البيانات المتاحة"){pick(105)}
        button("النسخ القديمة المحفوظة للفحص"){go("legacy-audit")}
        text("يمكن استعادة المخزون والموردين وسجل الصيانة من الأرشيف إلى قاعدة محاسبية فارغة بعد التأكيد. لا تزال الفواتير والديون والصناديق وأرباح الشركاء خارج الاستعادة.")
    }
    private fun legacyAudit(){
        text("أرشيف نسخ التطبيق القديم",size=25f)
        text("يمكن نقل المخزون والموردين والصيانة وحفظ السجلات المالية القديمة كأرشيف مستقل. لا تُرحّل القيود والأرصدة المالية إلى دفتر الأستاذ.",size=15f)
        val items=store.rows("SELECT hash,created,summary FROM legacy_imports ORDER BY created DESC LIMIT 50")
        if(items.isEmpty()) text("لا توجد نسخ قديمة محفوظة بعد.")
        val archived=store.rows("SELECT category,count(*) AS count FROM legacy_records GROUP BY category ORDER BY category")
        if(archived.isNotEmpty()) {
            text("السجلات المالية القديمة المنقولة للأرشيف المحلي (لا تدخل في الحسابات):",size=16f)
            archived.forEach { record -> text("${record.getValue("category")}: ${record.getValue("count")}",size=14f) }
        }

        text("إجمالي النسخ المحفوظة: ${store.scalar("SELECT count(*) FROM legacy_imports")}",size=15f)
        text("عدد النسخ المعروضة: ${items.size}",size=15f)
        if(items.size==50) text("تُعرض آخر 50 نسخة فقط؛ قد توجد نسخ أقدم.",size=14f)
        items.forEach { item->
            val summary=try {
                val data=org.json.JSONObject(item.getValue("summary"))
                listOf("products","sales","repairs","suppliers","customers").mapNotNull { key->
                    if(data.has(key)) "$key: ${data.optInt(key)}" else null
                }.joinToString(" · ")
            } catch (_:Exception) { "تعذر قراءة الملخص" }
            text("البصمة: ${item.getValue("hash").take(16)}",size=16f)
            button("فحص جاهزية هذه النسخة",body) {
                val hash=item.getValue("hash")
                work {
                    val report=LegacyReadiness.inspect(Backup(this,store).readStagedLegacy(hash))
                    runOnUiThread { AlertDialog.Builder(this).setTitle("تدقيق النسخة القديمة").setMessage(report).setPositiveButton("إغلاق",null).show() }
                }
            }
            button("عرض تدقيق الحركات المالية القديمة",body) {
                val hash=item.getValue("hash")
                work {
                    val report=LegacyFinancialAudit.inspect(Backup(this,store).readStagedLegacy(hash))
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("السجلات المالية القديمة — قراءة فقط")
                            .setMessage(report).setPositiveButton("إغلاق",null).show()
                    }
                }
            }
            button("تدقيق ازدواج النقد والسحوبات الشهرية — قراءة فقط",body) {
                val hash=item.getValue("hash")
                work {
                    val result=LegacyFinancialLinkAudit.inspect(Backup(this,store).readStagedLegacy(hash))
                    val summary=result.getJSONObject("cashMirrorEvidence")
                    val mirrors=summary.getJSONObject("bySourceSection")
                    val counts=result.getJSONObject("withdrawalCountsByTransactionMonth")
                    val report="حركات النقد: "+summary.getInt("cashRows")+
                        "\\nالفواتير/الصيانة/المصروفات ذات الحركات المرتبطة:\\n"+
                        mirrors.toString(2)+
                        "\\n\\nسحوبات الشركاء حسب شهر الحركة الأصلي:\\n"+counts.toString(2)+
                        "\\n\\nتدقيق فقط، لم يُنشأ أي قيد مالي."
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("مطابقة تاريخية للنقد والشركاء")
                            .setMessage(report).setPositiveButton("إغلاق",null).show()
                    }
                }
            }
            button("مطابقة مراجع الفواتير وحركات الصناديق",body) {
                val hash=item.getValue("hash")
                work {
                    val report=LegacyReconciliation.inspect(Backup(this,store).readStagedLegacy(hash))
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("مطابقة المراجع القديمة — قراءة فقط")
                            .setMessage(report).setPositiveButton("إغلاق",null).show()
                    }
                }
            }
            button("مقارنة نسخة أحدث دون إضافة سجلات مكررة",body) {
                val hash=item.getValue("hash")
                work {
                    val payload=Backup(this,store).readStagedLegacy(hash)
                    val report=LegacySnapshotImport.incrementalPreview(store,payload)
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("مقارنة السجلات حسب المعرّف")
                            .setMessage(report)
                            .setPositiveButton("إغلاق",null)
                            .show()
                    }
                }
            }
            button("استعادة جزئية من نسخة الصفا القديمة (لا تشمل الأرصدة)",body) {
                val hash=item.getValue("hash")
                work {
                    val payload=Backup(this,store).readStagedLegacy(hash)
                    val preview=LegacySnapshotImport.quickPreview(payload)
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("استعادة جزئية — لا تشمل الحسابات")
                            .setMessage(preview+"\\n\\nتتطلب قاعدة محاسبية فارغة، ولا تستبدل أي بيانات قائمة. هل تريد التنفيذ؟")
                            .setNegativeButton("إلغاء",null)
                            .setPositiveButton("استعادة البيانات"){_,_->restoreWithProgress(payload)}.show()
                    }
                }
            }
            text("السجلات: $summary",size=15f)
            val stamp=item.getValue("created").toLongOrNull()
            if(stamp!=null) text("تاريخ الحفظ: ${java.time.Instant.ofEpochMilli(stamp).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}",size=14f)
        }
    }
    private fun pick(code:Int){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json"},code)}
    private fun handleDocumentResult(request:Int,result:Int,data:Intent?){if(result!=RESULT_OK)return;val uri=data?.data?:return
        work{val backup=Backup(this,store);when(request){104->contentResolver.openOutputStream(uri,"wt")!!.use{InvoicePdf(this,store).write(requireNotNull(pdfInvoice),it)};101->contentResolver.openOutputStream(uri,"wt")!!.use{backup.export(it)};102->contentResolver.openInputStream(uri)!!.use{ input ->
            val bytes=Backup.read(input)
            val payload=String(bytes,Charsets.UTF_8).removePrefix("\uFEFF")
            val root=org.json.JSONObject(payload)
            when(root.optString("format")) {
                "alsafa-native6-backup" -> backup.restore(bytes.inputStream())
                "alsafa-accounting-backup" -> {
                    // Use the bytes already read from the document. Reopening the
                    // SAF stream and decoding a second 66MB buffer doubles peak RAM.
                    val preview=LegacySnapshotImport.quickPreviewRoot(root)
                    val hash=backup.stageLegacyBytes(bytes)
                    runOnUiThread {
                        if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                            .setTitle("استعادة نسخة الصفا القديمة — جزئية حالياً")
                            .setMessage(preview+"\n\nحُفظت النسخة الأصلية بأمان: "+hash.take(12)+
                                "\nلن تُفعّل الأرصدة المالية القديمة. هل تريد المتابعة؟")
                            .setNegativeButton("إلغاء",null)
                            .setPositiveButton("استعادة المخزون والصيانة"){_,_->restoreWithProgress(payload)}
                            .show()
                    }
                }
                else -> error("نوع النسخة غير مدعوم؛ اختر ملف نسخة الصفا JSON الأصلي")
            }
        };103->contentResolver.openInputStream(uri)!!.use{val report=backup.stageLegacy(it);runOnUiThread{AlertDialog.Builder(this).setMessage(report).setPositiveButton("حسناً",null).show()}};105->contentResolver.openInputStream(uri)!!.use{ input ->
            val bytes=Backup.read(input)
            val payload=String(bytes,Charsets.UTF_8).removePrefix("\uFEFF")
            // Parse once for preview, then stage original bytes without a second parse.
            val root=org.json.JSONObject(payload)
            val preview=LegacySnapshotImport.quickPreviewRoot(root)
            backup.stageLegacyBytes(bytes)
            runOnUiThread {
                if(!isFinishing&&!isDestroyed) AlertDialog.Builder(this)
                    .setTitle("استعادة بيانات التطبيق القديم — جزئية حالياً")
                    .setMessage(preview+"\n\nلن تُفعّل الديون أو أرصدة الصناديق أو أرباح الشركاء. تتطلب الاستعادة قاعدة فارغة، ولن تستبدل البيانات الموجودة. هل تريد المتابعة؟")
                    .setNegativeButton("إلغاء",null)
                    .setPositiveButton("استعادة المخزون والصيانة والموردين"){_,_->restoreWithProgress(payload)}.show()
            }
        }}}
    }
}
