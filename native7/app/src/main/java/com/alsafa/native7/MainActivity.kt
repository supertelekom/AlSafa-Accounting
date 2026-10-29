package com.alsafa.native7

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Button
import android.text.InputType

/** UI-only prototype. No accounting transactions, persistence or network calls. */
class MainActivity : Activity() {
    private val ink = Color.rgb(62,52,41)
    private val gold = Color.rgb(169,121,41)
    private val pearl = Color.rgb(248,243,235)
    private val paper = Color.rgb(255,253,249)
    private var screen = "chooser"
    private var previousScreen = "chooser"
    private val navigationHistory=mutableListOf<String>()
    private fun navigateTo(target:String) {
        if(target==screen) return
        navigationHistory.add(screen)
        screen=target
        render()
    }
    private fun returnToPrevious() {
        screen=if(navigationHistory.isNotEmpty()) navigationHistory.removeAt(navigationHistory.lastIndex)
            else if(screen.startsWith("preview:")) previousScreen else "chooser"
        render()
    }
    private var selectedQuickRepair: String? = null
    private var repairPhotoUri:android.net.Uri? = null
    private var pendingCameraUri:android.net.Uri? = null
    private var selectedQuickPrice: Int? = null
    private var activeRoot:View? = null
    private var repairDraftTexts:List<String>? = null
    private var repairDraftSelections:List<Int>? = null
    private var repairDraftScreen:String? = null
    private val newProductPhotos=mutableMapOf<String,String>()
    private var pendingProductPhotoKey:String?=null
    private var pendingProductCameraUri:android.net.Uri?=null
    private var photoPreview:android.widget.ImageView?=null
    private fun selectProductPhoto(key:String,camera:Boolean) {
        pendingProductPhotoKey=key
        val intent=if(camera) {
            val values=android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"alsafa-product-"+System.currentTimeMillis()+".jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
            }
            pendingProductCameraUri=contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
                ?: throw IllegalStateException("تعذر تجهيز الكاميرا")
            android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT,pendingProductCameraUri)
                addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type="image/*";addCategory(android.content.Intent.CATEGORY_OPENABLE)
            }
        try {
            startActivityForResult(intent,if(camera)7312 else 7311)
        } catch(e:Exception) {
            if(camera)pendingProductCameraUri?.let { contentResolver.delete(it,null,null) }
            pendingProductCameraUri=null
            pendingProductPhotoKey=null
            android.app.AlertDialog.Builder(this).setTitle("تعذر فتح الصورة")
                .setMessage(e.message ?: "تأكد من وجود تطبيق كاميرا أو استوديو")
                .setPositiveButton("حسناً",null).show()
        }
    }
    private fun productPhotoData(bitmap:android.graphics.Bitmap):String {
        val width=bitmap.width
        val height=bitmap.height
        val ratio=minOf(1.0,1024.0/maxOf(width,height).toDouble())
        val scaled=if(ratio<1)android.graphics.Bitmap.createScaledBitmap(bitmap,
            (width*ratio).toInt().coerceAtLeast(1),(height*ratio).toInt().coerceAtLeast(1),true) else bitmap
        val out=java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG,78,out)
        return "data:image/jpeg;base64,"+android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP)
    }
    private val legacyBackupStore by lazy { LegacyBackupStore(this) }
    private fun openLegacyRestore() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, 7107)
    }
    private val repairStore by lazy { RepairStore(this) }
    private val originalPages by lazy { OriginalPageStructure(this,{ route -> navigateTo(OriginalRoutes.screen(route)) },{ returnToPrevious() }) }

    private fun walkForm(view:View,edit:(EditText)->Unit,spin:(Spinner)->Unit) {
        when(view) {
            is EditText -> edit(view)
            is Spinner -> spin(view)
            is android.view.ViewGroup -> for(i in 0 until view.childCount) walkForm(view.getChildAt(i),edit,spin)
        }
    }
    private fun snapshotRepairDraft() {
        if(screen !in listOf("preview:طلب صيانة","preview:استلام صيانة")) return
        val texts=mutableListOf<String>()
        val selections=mutableListOf<Int>()
        activeRoot?.let { walkForm(it,{ texts.add(it.text.toString()) },{ selections.add(it.selectedItemPosition) }) }
        repairDraftTexts=texts
        repairDraftSelections=selections
        repairDraftScreen=screen
    }
    private fun restoreRepairDraft() {
        if(screen !in listOf("preview:طلب صيانة","preview:استلام صيانة")) return
        if(repairDraftScreen!=screen) return
        val texts=repairDraftTexts ?: return
        val selections=repairDraftSelections ?: emptyList()
        var editIndex=0
        var spinIndex=0
        activeRoot?.let { walkForm(it,{
            if(editIndex<texts.size) it.setText(texts[editIndex])
            editIndex++
        },{
            if(spinIndex<selections.size && selections[spinIndex] in 0 until it.count)
                it.setSelection(selections[spinIndex])
            spinIndex++
        }) }
    }

    private val legacyTypeface by lazy { Typeface.createFromAsset(assets,"Tajawal-Medium.ttf") }

    private fun dp(value:Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun text(value:String, size:Float, color:Int = ink):TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        includeFontPadding = false
        breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY // Android line-break quality
        hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
        setPadding(dp(9),dp(8),dp(9),dp(8))
        typeface = legacyTypeface
    }
    private fun panel(color:Int, radius:Int=20, border:Int=Color.rgb(234,223,206)) =
        GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat(); setStroke(dp(1),border) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(android.os.Build.VERSION.SDK_INT>=30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        if(android.os.Build.VERSION.SDK_INT>=33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if(screen!="chooser") returnToPrevious() else finish()
            }
        }
        screen=savedInstanceState?.getString("screen") ?: "chooser"
        previousScreen=savedInstanceState?.getString("previousScreen") ?: "chooser"
        navigationHistory.addAll(savedInstanceState?.getStringArrayList("navigationHistory") ?: emptyList())
        repairPhotoUri=savedInstanceState?.getString("repairPhotoUri")?.let { android.net.Uri.parse(it) }
        repairDraftScreen=savedInstanceState?.getString("repairDraftScreen")
        repairDraftTexts=savedInstanceState?.getStringArrayList("repairDraftTexts")
        repairDraftSelections=savedInstanceState?.getIntegerArrayList("repairDraftSelections")
        pendingCameraUri=savedInstanceState?.getString("pendingCameraUri")?.let { android.net.Uri.parse(it) }
        render()
        restoreRepairDraft()
    }
    private var pendingBarcodeField:EditText?=null
    private var pendingBarcodeImageUri:android.net.Uri?=null
    private fun barcodeScanButton(parent:LinearLayout,target:EditText) {
        parent.addView(Button(this).apply {
            text="قراءة الباركود بالكاميرا"
            setOnClickListener {
                pendingBarcodeField=target
                try {
                    val values=android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"alsafa_barcode_"+System.currentTimeMillis()+".jpg")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                    }
                    val uri=contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
                        ?: throw IllegalStateException("تعذر تجهيز صورة الباركود")
                    pendingBarcodeImageUri=uri
                    val camera=android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri)
                        addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivityForResult(camera,7313)
                } catch(e:Exception) {
                    pendingBarcodeImageUri?.let { contentResolver.delete(it,null,null) }
                    pendingBarcodeImageUri=null
                    pendingBarcodeField=null
                    android.widget.Toast.makeText(this@MainActivity,"تعذر فتح الكاميرا: "+e.message,android.widget.Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    private fun readBarcodeWithMlKit(uri:android.net.Uri) {
        try {
            val image=com.google.mlkit.vision.common.InputImage.fromFilePath(this,uri)
            val options=com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ITF
                ).build()
            val scanner=com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)
            scanner.process(image)
                .addOnSuccessListener { codes ->
                    val value=codes.firstOrNull()?.rawValue
                    if(!value.isNullOrBlank())pendingBarcodeField?.setText(value)
                    else android.widget.Toast.makeText(this,"لم يُعثر على باركود واضح. أعد التصوير.",android.widget.Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    android.widget.Toast.makeText(this,"فشلت قراءة الباركود: "+e.message,android.widget.Toast.LENGTH_LONG).show()
                }
                .addOnCompleteListener {
                    scanner.close()
                    contentResolver.delete(uri,null,null)
                    pendingBarcodeImageUri=null
                    pendingBarcodeField=null
                }
        } catch(e:Exception) {
            contentResolver.delete(uri,null,null)
            pendingBarcodeImageUri=null
            pendingBarcodeField=null
            android.widget.Toast.makeText(this,"تعذر تحليل الباركود: "+e.message,android.widget.Toast.LENGTH_LONG).show()
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==7313) {
            val uri=pendingBarcodeImageUri
            if(resultCode==RESULT_OK && uri!=null) readBarcodeWithMlKit(uri)
            else {
                uri?.let { contentResolver.delete(it,null,null) }
                pendingBarcodeImageUri=null
                pendingBarcodeField=null
            }
            return
        }
        if(requestCode in 7311..7312 && resultCode!=RESULT_OK) {
            if(requestCode==7312)pendingProductCameraUri?.let { contentResolver.delete(it,null,null) }
            pendingProductCameraUri=null
            pendingProductPhotoKey=null
            photoPreview=null
        }
        if(requestCode in 7311..7312 && resultCode==RESULT_OK) {
            val key=pendingProductPhotoKey
            if(key!=null)try {
                val uri=if(requestCode==7312)pendingProductCameraUri else data?.data
                require(uri!=null) { "لم تُحدد صورة" }
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it,null,bounds)
                }
                var sample=1
                while(bounds.outWidth/sample>1600 || bounds.outHeight/sample>1600)sample*=2
                val bitmap=contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it,null,
                        android.graphics.BitmapFactory.Options().apply { inSampleSize=sample })
                } ?: throw IllegalArgumentException("تعذر قراءة الصورة")
                newProductPhotos[key]=productPhotoData(bitmap)
                photoPreview?.setImageBitmap(bitmap)
            } catch(e:Exception) {
                android.app.AlertDialog.Builder(this).setMessage(e.message ?: "تعذر قراءة الصورة")
                    .setPositiveButton("حسناً",null).show()
            }
            if(requestCode==7312)pendingProductCameraUri=null
            pendingProductPhotoKey=null
            photoPreview=null
        }
        if (requestCode == 7107 && resultCode == RESULT_OK && data?.data != null) {
            val uri = requireNotNull(data.data)
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalArgumentException("تعذر قراءة الملف")
                val info = legacyBackupStore.inspect(text)
                val summary = info.counts.entries.joinToString("\\n") { "${it.key}: ${it.value}" }
                android.app.AlertDialog.Builder(this)
                    .setTitle("تأكيد استعادة نسخة الصفا")
                    .setMessage("تم فحص النسخة.\\n${summary}\\n\\nسيتم حفظ جميع الأقسام محلياً. لا يتم حذف بيانات سابقة.")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("استعادة") { _, _ ->
                        try {
                            legacyBackupStore.importInitial(text)
                            android.app.AlertDialog.Builder(this).setTitle("تم حفظ النسخة")
                                .setMessage("حُفظت بيانات النسخة الأصلية في SQLite. ربطها بصفحات المبيع والمخزون والصيانة لم يكتمل بعد.")
                                .setPositiveButton("حسناً", null).show()
                        } catch (error: Exception) {
                            android.app.AlertDialog.Builder(this).setTitle("لم تُنفذ الاستعادة")
                                .setMessage(error.message ?: "تعذر حفظ النسخة؛ لم يتم تغيير البيانات")
                                .setPositiveButton("حسناً", null).show()
                        }
                    }.show()
            } catch (error: Exception) {
                android.app.AlertDialog.Builder(this).setTitle("ملف غير صالح")
                    .setMessage(error.message ?: "تعذر قراءة النسخة")
                    .setPositiveButton("حسناً", null).show()
            }
        }
        if(requestCode==702) {
            val pending=pendingCameraUri
            pendingCameraUri=null
            if(resultCode==Activity.RESULT_OK && pending!=null) {
                snapshotRepairDraft()
                repairPhotoUri=pending
                render()
                restoreRepairDraft()
            } else if(pending!=null) contentResolver.delete(pending,null,null)
        }
        if(requestCode==701 && resultCode==Activity.RESULT_OK) {
            val uri=data?.data
            if(uri!=null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_:SecurityException) {
                    // Some document providers grant only temporary access.
                }
                snapshotRepairDraft()
                repairPhotoUri=uri
                
                render()
                restoreRepairDraft()
            }
        }
    }
    override fun onSaveInstanceState(outState:Bundle) {
        snapshotRepairDraft()
        outState.putString("repairDraftScreen",repairDraftScreen)
        outState.putStringArrayList("repairDraftTexts",repairDraftTexts?.let { ArrayList(it) })
        outState.putIntegerArrayList("repairDraftSelections",repairDraftSelections?.let { ArrayList(it) })
        outState.putString("screen",screen)
        outState.putString("previousScreen",previousScreen)
        outState.putStringArrayList("navigationHistory",ArrayList(navigationHistory))
        outState.putString("repairPhotoUri",repairPhotoUri?.toString())
        outState.putString("pendingCameraUri",pendingCameraUri?.toString())
        super.onSaveInstanceState(outState)
    }
    @Deprecated("Legacy navigation fallback on Android versions before predictive back")
    override fun onBackPressed() {
        if(android.os.Build.VERSION.SDK_INT>=33) {
            super.onBackPressed()
        } else if(screen!="chooser") {
            returnToPrevious()
        } else {
            super.onBackPressed()
        }
    }
    private fun card(parent:LinearLayout, title:String, description:String, target:String, dark:Boolean=false) {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            background=panel(if(dark) Color.rgb(46,40,34) else paper,26)
            setPadding(dp(14),dp(18),dp(14),dp(18))
            isClickable=true
            setOnClickListener { navigateTo(target) }
        }
        card.addView(text(title,23f,if(dark) Color.WHITE else ink))
        card.addView(text(description,13f,if(dark) Color.rgb(231,216,197) else ink))
        parent.addView(card,LinearLayout.LayoutParams(-1,dp(138)).apply { bottomMargin=dp(13) })
    }
    private fun chooserCard(parent:LinearLayout,title:String,description:String,symbol:String,target:String,dark:Boolean) {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            background=panel(if(dark) Color.rgb(46,40,34) else Color.rgb(255,250,242),26,
                if(dark) Color.rgb(47,41,36) else Color.rgb(216,189,136))
            setPadding(dp(14),dp(16),dp(14),dp(16))
            isClickable=true
            setOnClickListener { navigateTo(target) }
        }
        card.addView(text(symbol,22f,if(dark) Color.rgb(234,217,192) else gold).apply {
            background=panel(if(dark) Color.rgb(75,68,60) else Color.rgb(245,230,203),18)
        },LinearLayout.LayoutParams(dp(58),dp(58)))
        val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        labels.addView(text(title,20f,if(dark) Color.WHITE else ink).apply { gravity=Gravity.RIGHT })
        labels.addView(text(description,11f,if(dark) Color.rgb(213,201,187) else Color.rgb(138,123,108)).apply { gravity=Gravity.RIGHT })
        card.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        card.addView(text("دخول",10f,if(dark) Color.rgb(213,185,143) else gold))
        parent.addView(card,LinearLayout.LayoutParams(0,dp(122),1f))
    }
    private fun actionGrid(parent:LinearLayout,items:List<Triple<String,String,String>>,simple:Boolean,management:Boolean=false) {
        val columns=if(resources.configuration.screenWidthDp>=720) { if(management) 4 else if(!simple) 3 else 2 } else 2
        items.chunked(columns).forEach { pair ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
            parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(if(simple) 4 else if(management) 10 else 9) })
            pair.forEachIndexed { index,item ->
                val box=LinearLayout(this).apply {
                    orientation=if(simple || management) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity=if(management) Gravity.CENTER_VERTICAL else Gravity.CENTER_VERTICAL
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                    background=panel(if(simple) Color.rgb(255,250,242) else paper,
                        if(simple) 12 else if(management) 16 else 14,
                        if(simple) Color.rgb(216,189,136) else Color.rgb(234,223,206))
                    setPadding(dp(if(simple) 4 else if(management) 15 else 13),
                        dp(if(simple) 4 else if(management) 15 else 13),
                        dp(if(simple) 4 else if(management) 15 else 13),
                        dp(if(simple) 4 else if(management) 15 else 13))
                    isClickable=true
                    setOnClickListener { navigateTo(if(screen=="full" && item.first=="مبيع") "preview:مبيع كامل" else "preview:" + item.first) }
                }
                when {
                    simple -> {
                        box.addView(text(item.third,16f,gold).apply { gravity=Gravity.CENTER })
                        box.addView(text(item.first,12f).apply { gravity=Gravity.CENTER;maxLines=1 })
                        box.addView(text(item.second,8f,Color.rgb(138,123,108)).apply { gravity=Gravity.CENTER;maxLines=1 })
                    }
                    management -> {
                        box.addView(text(item.third,25f,gold).apply {
                            gravity=Gravity.RIGHT
                            setPadding(0,0,0,dp(8))
                        })
                        box.addView(text(item.first,14f).apply {
                            gravity=Gravity.RIGHT
                            setPadding(0,0,0,0)
                        })
                        box.addView(text(item.second,10f,Color.rgb(138,123,108)).apply {
                            gravity=Gravity.RIGHT
                            setPadding(0,dp(5),0,0)
                        })
                    }
                    else -> {
                        box.addView(text(item.third,20f,gold).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(dp(32),dp(32)))
                        val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL }
                        labels.addView(text(item.first,13f).apply { gravity=Gravity.RIGHT;setPadding(dp(8),0,dp(8),0) })
                        labels.addView(text(item.second,10f,Color.rgb(138,123,108)).apply { gravity=Gravity.RIGHT;setPadding(dp(8),dp(4),dp(8),0) })
                        box.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
                    }
                }
                row.addView(box,LinearLayout.LayoutParams(0,dp(if(simple) 47 else if(management) 115 else 72),1f).apply {
                    if(index<pair.lastIndex) marginEnd=dp(if(simple) 4 else if(management) 10 else 9)
                })
            }
            repeat(columns-pair.size) { row.addView(View(this),LinearLayout.LayoutParams(0,dp(1),1f)) }
        }
    }
    private fun currencySummary(parent:LinearLayout) {
        // Original style.css: three ledger blocks on tablet, one column below 760px.
        val columns=if(resources.displayMetrics.widthPixels>=dp(760)) 3 else 1
        val row=LinearLayout(this).apply {
            orientation=if(columns==3) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
        }
        parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14);bottomMargin=dp(18) })
        val titles=listOf("↪" to "المدوّر من الإقفال السابق", "🛒" to "مبيعات اليوم", "🔧" to "صيانة اليوم")
        titles.forEachIndexed { index,(symbol,label) ->
            val section=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                layoutDirection=View.LAYOUT_DIRECTION_RTL
                background=panel(paper,16,Color.rgb(234,223,206))
                setPadding(dp(12),dp(12),dp(12),dp(12))
            }
            section.addView(text("$symbol  $label",13f,gold).apply { gravity=Gravity.RIGHT })
            val currencies=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
            listOf("USD","SYP","TRY").forEachIndexed { currencyIndex,currency ->
                val cell=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    gravity=Gravity.CENTER
                    background=panel(Color.rgb(245,243,240),10,Color.rgb(245,243,240))
                    setPadding(dp(5),dp(8),dp(5),dp(8))
                }
                cell.addView(text(currency,11f,Color.rgb(138,123,108)))
                cell.addView(text("—",14f,gold))
                currencies.addView(cell,LinearLayout.LayoutParams(0,-2,1f).apply {
                    if(currencyIndex>0) marginStart=dp(6)
                })
            }
            section.addView(currencies)
            row.addView(section,if(columns==3)
                LinearLayout.LayoutParams(0,-2,1f).apply { if(index>0) marginStart=dp(12) }
                else LinearLayout.LayoutParams(-1,-2).apply { if(index>0) topMargin=dp(12) })
        }
    }
    /**
     * Original v3.9.359 form-grid: two columns on the tablet.
     * Only pairs created by field()/select() are grouped; headings, notes and
     * actions retain their original sequence and labels.
     */
    private fun arrangeOriginalFormGrid(body:LinearLayout) {
        fun isPair(index:Int):Boolean {
            if(index+1>=body.childCount) return false
            val label=body.getChildAt(index) as? TextView ?: return false
            val control=body.getChildAt(index+1)
            if(control !is EditText && control !is Spinner) return false
            return label.currentTextColor==Color.rgb(138,123,108)
        }
        var i=0
        while(i<body.childCount-3) {
            if(!isPair(i) || !isPair(i+2)) { i++;continue }
            val firstLabel=body.getChildAt(i)
            val firstControl=body.getChildAt(i+1)
            val secondLabel=body.getChildAt(i+2)
            val secondControl=body.getChildAt(i+3)
            body.removeViewAt(i+3)
            body.removeViewAt(i+2)
            body.removeViewAt(i+1)
            body.removeViewAt(i)
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                layoutDirection=View.LAYOUT_DIRECTION_RTL
            }
            listOf(firstLabel to firstControl,secondLabel to secondControl).forEachIndexed { index,pair ->
                val cell=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                }
                cell.addView(pair.first,LinearLayout.LayoutParams(-1,-2))
                cell.addView(pair.second,LinearLayout.LayoutParams(-1,dp(42)).apply { bottomMargin=dp(11) })
                row.addView(cell,LinearLayout.LayoutParams(0,-2,1f).apply {
                    if(index==0) marginEnd=dp(11)
                })
            }
            body.addView(row,i,LinearLayout.LayoutParams(-1,-2))
            i++
        }
    }
    /** Preserve the original Cancel / Save action order in one horizontal row. */
    private fun arrangeOriginalFormActions(body:LinearLayout) {
        for(i in 0 until body.childCount-1) {
            val cancel=body.getChildAt(i) as? TextView ?: continue
            val save=body.getChildAt(i+1) as? TextView ?: continue
            if(cancel.text.toString()!="إلغاء" ||
                save.text.toString() !in listOf("حفظ الفاتورة","حفظ طلب الصيانة")) continue
            body.removeViewAt(i+1)
            body.removeViewAt(i)
            val actions=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                layoutDirection=View.LAYOUT_DIRECTION_RTL
                gravity=Gravity.CENTER_VERTICAL
            }
            cancel.background=panel(paper,10,Color.rgb(234,223,206))
            cancel.gravity=Gravity.CENTER
            save.gravity=Gravity.CENTER
            actions.addView(cancel,LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(9) })
            actions.addView(save,LinearLayout.LayoutParams(0,dp(44),1f))
            body.addView(actions,i,LinearLayout.LayoutParams(-1,-2).apply {
                topMargin=dp(14)
                bottomMargin=dp(12)
            })
            return
        }
    }
    /** Original .form-card visual treatment. */
    private fun legacyFormCard(parent:LinearLayout,content:(LinearLayout)->Unit) {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            background=panel(paper,17,Color.rgb(234,223,206))
            val formPadding=15
            setPadding(dp(formPadding),dp(formPadding),dp(formPadding),dp(formPadding))
        }
        parent.addView(card,LinearLayout.LayoutParams(-1,-2))
        content(card)
    }
    private fun formSection(parent:LinearLayout,label:String) {
        val heading=text(label,13f,Color.rgb(122,81,24)).apply {
            gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
            typeface=Typeface.create(typeface,Typeface.BOLD)
            setPadding(dp(2),0,dp(2),0)
        }
        parent.addView(heading,LinearLayout.LayoutParams(-1,-2).apply {
            topMargin=dp(if(parent.childCount==0) 0 else 20)
            bottomMargin=dp(14)
        })
    }
    /** Two-column field row follows the original .form-grid layout (11px gap). */
    private fun formPair(parent:LinearLayout,first:(LinearLayout)->Unit,second:(LinearLayout)->Unit) {
        val narrow=resources.configuration.screenWidthDp<=380
        val row=LinearLayout(this).apply {
            orientation=if(narrow) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.TOP
        }
        val right=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val left=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        if(narrow) {
            row.addView(right,LinearLayout.LayoutParams(-1,-2))
            row.addView(left,LinearLayout.LayoutParams(-1,-2))
        } else {
            row.addView(right,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(11) })
            row.addView(left,LinearLayout.LayoutParams(0,-2,1f))
        }
        parent.addView(row)
        first(right)
        second(left)
    }
    private fun formHeader(parent:LinearLayout,title:String,description:String,backLabel:String,target:String) {
        val heading=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.TOP
        }
        val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        labels.addView(text(title,20f,ink).apply {
            gravity=Gravity.RIGHT
            typeface=Typeface.create(typeface,Typeface.BOLD)
            setPadding(0,0,0,0)
        })
        labels.addView(text(description,12f,Color.rgb(138,123,108)).apply {
            gravity=Gravity.RIGHT
            setPadding(0,dp(5),0,0)
        })
        heading.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(text(backLabel,12f,Color.rgb(122,81,24)).apply {
            background=panel(Color.rgb(247,237,221),12,Color.rgb(223,201,164))
            isClickable=true
            setOnClickListener { navigateTo(target) }
        })
        parent.addView(heading,LinearLayout.LayoutParams(-1,-2).apply {
            topMargin=dp(2)
            bottomMargin=dp(14)
        })
    }
    private var compactProductCreationForm=false
    private fun field(parent:LinearLayout,label:String,hint:String=""):EditText {
        parent.addView(text(label,11f,Color.rgb(138,123,108)).apply {
            gravity=Gravity.RIGHT
            setPadding(dp(2),dp(if(compactProductCreationForm) 1 else 5),dp(2),dp(if(compactProductCreationForm) 2 else 6))
        })
        return EditText(this).apply {
            this.hint=hint
            typeface=legacyTypeface
            textSize=14f
            gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
            setTextColor(ink)
            setHintTextColor(Color.rgb(138,123,108))
            background=panel(Color.rgb(255,252,247),10,Color.rgb(234,223,206))
            setPadding(dp(10),dp(9),dp(10),dp(9))
            parent.addView(this,LinearLayout.LayoutParams(-1,dp(if(compactProductCreationForm) 36 else 42)).apply { bottomMargin=dp(if(compactProductCreationForm) 4 else 11) })
        }
    }
    private fun multilineField(parent:LinearLayout,label:String,hint:String=""):EditText {
        val input=field(parent,label,hint)
        input.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.gravity=Gravity.TOP or Gravity.RIGHT
        input.minLines=3
        input.maxLines=6
        input.layoutParams=LinearLayout.LayoutParams(-1,dp(78)).apply { bottomMargin=dp(11) }
        return input
    }
    private fun select(parent:LinearLayout,label:String,options:List<String>):Spinner {
        parent.addView(text(label,11f,Color.rgb(138,123,108)).apply {
            gravity=Gravity.RIGHT
            setPadding(dp(2),dp(if(compactProductCreationForm) 1 else 5),dp(2),dp(if(compactProductCreationForm) 2 else 6))
        })
        val spinner=Spinner(this).apply {
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            textAlignment=View.TEXT_ALIGNMENT_VIEW_START
            background=panel(Color.rgb(255,252,247),10,Color.rgb(234,223,206))
            setPadding(dp(10),dp(9),dp(10),dp(9))
        }
        spinner.adapter=object:ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options) {
            private fun style(view:View):View = view.apply {
                (this as? TextView)?.let { label ->
                    label.typeface=legacyTypeface;label.textSize=14f;label.setTextColor(ink)
                    label.gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
                }
            }
            override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup):View =
                style(super.getView(position,convertView,parent))
            override fun getDropDownView(position:Int,convertView:View?,parent:android.view.ViewGroup):View =
                style(super.getDropDownView(position,convertView,parent))
        }
        parent.addView(spinner,LinearLayout.LayoutParams(-1,dp(if(compactProductCreationForm) 36 else 42)).apply { bottomMargin=dp(if(compactProductCreationForm) 4 else 11) })
        return spinner
    }
    private fun finalAmountCurrency(parent:LinearLayout, defaultCurrency:String) {
        parent.addView(text("المبلغ النهائي وعملته",15f).apply { gravity=Gravity.RIGHT })
        val amount=field(parent,"","أدخل المبلغ النهائي")
        amount.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        select(parent,"عملة التحصيل",listOf("₺ تركية","$ دولار","ل.س سورية").let { if(defaultCurrency=="USD") listOf("$ دولار","₺ تركية","ل.س سورية") else it })
        parent.addView(text("يمكن تعديل السعر بعد تحويله. يُسجّل المبلغ النهائي بالعملة المختارة.",12f))
        parent.addView(text("اختر الصنف أو الخدمة أولاً",13f,gold))
    }
    private fun paymentChoices(parent:LinearLayout) {
        val options=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
        var selected=0
        val cells=mutableListOf<TextView>()
        listOf("💵 نقدي","📱 شام كاش").forEachIndexed { index,label ->
            val choice=text(label,16f).apply {
                background=panel(if(index==0) Color.rgb(239,228,208) else paper,14)
                isClickable=true
                setOnClickListener {
                    selected=index
                    cells.forEachIndexed { i,view -> view.background=panel(if(i==selected) Color.rgb(239,228,208) else paper,14) }
                }
            }
            cells.add(choice)
            options.addView(choice,LinearLayout.LayoutParams(0,dp(56),1f).apply { marginEnd=dp(6) })
        }
        parent.addView(options)
    }
    /** Legacy father-step: 31dp numbered circle, 17sp title, 10dp gap. */
    private fun quickStep(parent:LinearLayout,number:Int,label:String) {
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
        }
        row.addView(text(number.toString(),16f,Color.rgb(122,81,24)).apply {
            background=panel(Color.rgb(243,227,197),30,Color.rgb(243,227,197))
            typeface=Typeface.create(typeface,Typeface.BOLD)
            setPadding(0,0,0,0)
        },LinearLayout.LayoutParams(dp(31),dp(31)))
        row.addView(text(label,17f,ink).apply {
            gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
            typeface=Typeface.create(typeface,Typeface.BOLD)
            setPadding(dp(10),0,0,0)
        },LinearLayout.LayoutParams(0,-2,1f))
        parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply {
            topMargin=dp(4);bottomMargin=dp(12)
        })
    }
    private fun salePreview(parent:LinearLayout) {
        formHeader(parent,"مبيع سريع","ابحث عن الساعة أو امسح الباركود، ثم اختر طريقة الدفع وعملة البيع.","رجوع","simple")
        quickStep(parent,1,"اختر الساعة")
        val search=field(parent,"بحث بالاسم أو REF أو الرقم أو الباركود","اكتب للبحث فوراً")
        val barcode=field(parent,"الباركود","امسح أو اكتب الباركود")
        barcodeScanButton(parent,barcode)
        val results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(results)
        val selected=text("لم يتم اختيار ساعة بعد",14f,gold)
        parent.addView(selected)
        var chosen:LegacyProductRepository.Product?=null
        lateinit var price:EditText
        lateinit var currency:Spinner
        lateinit var productCost:android.widget.TextView
        fun updateResults(query:String) {
            results.removeAllViews()
            if(query.isBlank()) return
            try {
                val products=LegacyProductRepository.list(legacyBackupStore.readableDatabase,query,0,20)
                products.forEach { product ->
                    results.addView(text(product.name+" · "+product.barcode+" · المخزون "+product.stock+" · "+product.salePrice+" "+product.currency,13f,gold).apply {
                        isClickable=true
                        setPadding(dp(8),dp(9),dp(8),dp(9))
                        setOnClickListener {
                            if(product.stock<=0) {
                                android.widget.Toast.makeText(this@MainActivity,"المخزون غير كافٍ",android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                chosen=product
                                selected.text=product.name+" · "+product.raw.optString("color")+" · المتاح "+product.stock
                                price.setText(product.salePrice.toString())
                                productCost.text="كلفة القطعة: "+product.cost+" "+product.currency
                                currency.setSelection(listOf("USD","TRY","SYP").indexOf(product.currency).coerceAtLeast(0))
                                results.removeAllViews()
                            }
                        }
                    })
                }
                if(products.isEmpty()) results.addView(text("لا توجد نتائج مطابقة",12f))
            } catch(e:Exception) {
                results.addView(text("تعذر البحث: "+(e.message ?: ""),12f))
            }
        }
        productCost=text("كلفة القطعة: —",13f,gold)
        parent.addView(productCost)
        quickStep(parent,2,"سعر البيع النهائي")
        price=field(parent,"سعر البيع","0").apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val quantity=field(parent,"الكمية","1").apply {
            setText("1")
            inputType=InputType.TYPE_CLASS_NUMBER
        }
        currency=select(parent,"العملة",listOf("USD","TRY","SYP"))
        quickStep(parent,3,"طريقة الدفع")
        val account=select(parent,"طريقة الدفع",listOf("درج المحل","الصندوق الخارجي","شام كاش","البنك"))
        val paymentStatus=select(parent,"حالة الدفع",listOf("مدفوع بالكامل","دفعة جزئية","عند الاستلام","آجل"))
        val payment=field(parent,"المبلغ المقبوض الآن","0").apply {
            setText("0")
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val changeAccount=select(parent,"إرجاع الباقي عبر",listOf("درج المحل","الصندوق الخارجي","شام كاش","البنك"))
        val changeCurrency=select(parent,"عملة الباقي",listOf("USD","TRY","SYP"))
        val changeRate=field(parent,"سعر صرف الباقي (وحدات عملة الباقي لكل وحدة من عملة الفاتورة)","1").apply {
            setText("1")
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val changePreview=text("الباقي للعميل: 0",14f,gold)
        parent.addView(changePreview)
        val recalcChange= {
            val total=(price.text.toString().toDoubleOrNull() ?: 0.0)*(quantity.text.toString().toIntOrNull() ?: 0)
            val tender=payment.text.toString().toDoubleOrNull() ?: 0.0
            val rate=changeRate.text.toString().toDoubleOrNull() ?: 0.0
            changePreview.text="الباقي للعميل: "+((tender-total).coerceAtLeast(0.0)*rate)+" "+changeCurrency.selectedItem.toString()
        }
        listOf(price,quantity,payment,changeRate).forEach { field ->
            field.addTextChangedListener(object:android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun afterTextChanged(s:android.text.Editable?) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { recalcChange() }
            })
        }
        val customer=field(parent,"اسم العميل","اختياري")
        val phone=field(parent,"رقم العميل","اختياري").apply { inputType=InputType.TYPE_CLASS_PHONE }
        val online=select(parent,"نوع البيع",listOf("بيع من المحل","بيع أونلاين"))
        val watcher=object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(s:android.text.Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { updateResults(search.text.toString().trim()) }
        }
        search.addTextChangedListener(watcher)
        barcode.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(s:android.text.Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                val code=s?.toString()?.trim().orEmpty()
                if(code.isNotBlank()) search.setText(code)
            }
        })
        parent.addView(Button(this).apply {
            text="✓ إتمام الفاتورة"
            setOnClickListener {
                try {
                    val product=chosen ?: error("اختر المنتج أولاً")
                    val current=LegacyProductRepository.findById(legacyBackupStore.readableDatabase,product.id)
                        ?: error("المنتج غير موجود")
                    val qty=quantity.text.toString().toIntOrNull() ?: 0
                    require(qty>0 && current.stock>=qty) { "المخزون غير كافٍ" }
                    val amount=price.text.toString().toDoubleOrNull() ?: -1.0
                    val paid=payment.text.toString().toDoubleOrNull() ?: -1.0
                    val total=amount*qty
                    require(paid.isFinite() && paid>=0) { "المبلغ المقبوض غير صالح" }
                    require(paymentStatus.selectedItemPosition<2 || paid==0.0) { "لا يمكن قبض مبلغ لفاتورة آجل أو عند الاستلام" }
                    require(paymentStatus.selectedItemPosition!=0 || paid+0.000001>=total) { "حالة مدفوع بالكامل تتطلب قبض قيمة الفاتورة" }
                    val id=NativeSalesLedger.saveWithChange(legacyBackupStore.writableDatabase,
                        listOf(NativeSalesLedger.Line(product.id,qty,amount)),currency.selectedItem.toString(),
                        online.selectedItem.toString(),customer.text.toString(),phone.text.toString(),paid,
                        account.selectedItem.toString(),changeCurrency.selectedItem.toString(),
                        changeAccount.selectedItem.toString(),changeRate.text.toString().toDoubleOrNull() ?: 0.0)
                    android.widget.Toast.makeText(this@MainActivity,"تم حفظ الفاتورة: "+id,android.widget.Toast.LENGTH_LONG).show()
                    navigateTo("preview:المبيعات والفواتير")
                } catch(e:Exception) {
                    android.widget.Toast.makeText(this@MainActivity,e.message ?: "تعذر الحفظ",android.widget.Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    private fun handoverPreview(parent:LinearLayout) {
        parent.addView(text("تسليم الوردية",26f,gold))
        parent.addView(text("تسليم النقود وكل ما يجب أن يتابعه الموظف التالي.",14f))
        field(parent,"المسلّم *")
        field(parent,"المستلم *")
        parent.addView(text("النقود المسلّمة",20f,gold))
        listOf("USD","TRY","SYP").forEach { field(parent,it,"0").inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        parent.addView(text("الساعات والعمليات المنتظرة",20f,gold))
        listOf("📌 المحجوزات","⌚ الساعات التي لم تُسلّم","📦 بانتظار التسليم").forEach { parent.addView(text(it,16f)) }
        field(parent,"ملاحظات الوردية")
        parent.addView(text("تأكيد التسليم والاستلام",18f,gold))
        parent.addView(text("معاينة شكلية فقط — لا يتم تسجيل تسليم.",12f))
    }
    private fun quickRepairPreview(parent:LinearLayout) {
        formHeader(parent,"صيانة سريعة","اضغط على الخدمة ثم اختر الدفع والعملة.","رجوع","simple")
        val chosen=text("اختر خدمة من الأعلى",16f)
        val finalAmount=EditText(this).apply { hint="أدخل المبلغ النهائي"; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val custom=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
        val customAmount=field(custom,"سعر الصيانة الأساسي (ثم اختر عملة الصيانة بالأسفل)","اكتب المبلغ").apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        field(custom,"العمل الذي تم *","مثال: تبديل ماكينة، تنظيف الساعة، تثبيت العقارب...")
        customAmount.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { if(selectedQuickPrice==null) finalAmount.setText(s?.toString() ?: "") }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        val options=listOf(
            Triple("بطارية",50,"🔋"),Triple("بطارية",70,"🔋"),
            Triple("بطارية",100,"🔋"),Triple("بطارية",150,"🔋"),
            Triple("حزام بلاستك",100,"⌚"),Triple("حزام جلد",250,"⌚"),
            Triple("قفل ساعة جلد",30,"🔗"),Triple("قفل ساعة قلاب",70,"🔗"),
            Triple("صيانة مشكلة",-1,"🛠️")
        )
        options.chunked(3).forEach { group ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
            parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(9) })
            group.forEach { (name,price,emoji) ->
                val label=if(price<0) name else "$name $price ليرة"
                val tile=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
                    background=panel(paper,14)
                    setPadding(dp(5),dp(9),dp(5),dp(9))
                    isClickable=true
                    setOnClickListener {
                        selectedQuickRepair=label
                        selectedQuickPrice=if(price<0) null else price
                        chosen.text=if(price<0) name else "$label — $price TRY"
                        finalAmount.setText(if(price<0) "" else price.toString())
                        custom.visibility=if(price<0) View.VISIBLE else View.GONE
                        if(price>=0)customAmount.setText("")
                    }
                }
                tile.addView(text(emoji,25f,gold))
                tile.addView(text(label,12f))
                tile.addView(text(if(price<0) "مبلغ يدوي" else "$price TRY",12f,gold))
                row.addView(tile,LinearLayout.LayoutParams(0,dp(124),1f).apply { marginEnd=dp(5) })
            }
            repeat(3-group.size){row.addView(View(this),LinearLayout.LayoutParams(0,dp(1),1f))}
        }
        parent.addView(custom)
        parent.addView(chosen)
        formSection(parent,"2  حالة تسليم الساعة")
        val customerFields=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
        field(customerFields,"اسم العميل *")
        field(customerFields,"رقم الهاتف")
        val delivered=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
        listOf("تم تسليم الساعة","ترك الساعة لدينا").forEachIndexed { i,label ->
            val option=text(label,15f).apply {
                background=panel(paper,12);isClickable=true
                setOnClickListener { customerFields.visibility=if(i==1) View.VISIBLE else View.GONE }
            }
            delivered.addView(option,LinearLayout.LayoutParams(0,dp(58),1f).apply { marginEnd=dp(5) })
        }
        parent.addView(delivered)
        parent.addView(customerFields)
        formSection(parent,"3  مبلغ الصيانة النهائي")
        parent.addView(text("المبلغ النهائي وعملته",15f).apply { gravity=Gravity.RIGHT })
        finalAmount.background=panel(paper,12)
        finalAmount.setPadding(dp(13),dp(10),dp(13),dp(10))
        parent.addView(finalAmount,LinearLayout.LayoutParams(-1,dp(52)))
        select(parent,"عملة التحصيل",listOf("₺ تركية","$ دولار","ل.س سورية"))
        parent.addView(text("يمكن تعديل السعر بعد تحويله. يُسجّل المبلغ النهائي بالعملة المختارة.",12f))
        formSection(parent,"4  طريقة الدفع")
        select(parent,"طريقة الدفع",listOf("💵 نقدي","📱 شام كاش"))
        parent.addView(text("✓ إتمام العملية",13f,Color.WHITE).apply { background=panel(Color.rgb(139,96,31),12) },LinearLayout.LayoutParams(-1,dp(44)))
        parent.addView(text("معاينة فقط — لا يتم حفظ صيانة أو خصم قطع.",12f))
    }
    private fun deliveryPreview(parent:LinearLayout) {
        parent.addView(text("تسليم الساعات",26f,gold))
        parent.addView(text("الساعات الجاهزة أو المتبقية لدينا بانتظار العميل.",14f))
        parent.addView(text("لا توجد ساعات بانتظار التسليم.",16f))
        parent.addView(text("هذه قائمة غير متصلة بالبيانات بعد، ولا تعكس حالة الساعات الفعلية.",12f))
    }
    private fun manualSaleItemPreview(parent:LinearLayout,index:Int,removable:Boolean,onChange:()->Unit = {}) {
        val item=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            background=panel(Color.rgb(252,247,239),12,Color.rgb(234,223,206))
            setPadding(dp(11),dp(11),dp(11),dp(11))
        }
        item.addView(text("الصنف $index",12f,Color.rgb(122,81,24)).apply { typeface=Typeface.create(typeface,Typeface.BOLD) })
        field(item,"اسم الساعة / الصنف *","مثال: ساعة كاسيو")
        field(item,"الموديل","اختياري")
        val qty=field(item,"الكمية *","1").apply {
            inputType=InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        val price=field(item,"سعر البيع *","0").apply {
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val cost=field(item,"تكلفة الصنف","0").apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText("0") }
        val total=text("إجمالي الصنف: —",11f,Color.rgb(122,81,24)).apply { typeface=Typeface.create(typeface,Typeface.BOLD);setPadding(dp(2),dp(9),dp(2),0) }
        item.tag=Pair(qty,price)
        item.addView(total)
        val update=object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                val q=qty.text.toString().toIntOrNull()
                val p=price.text.toString().toBigDecimalOrNull()
                onChange()
                total.text=if(q!=null && q>=1 && p!=null && p.signum()>=0)
                    "إجمالي الصنف: "+p.multiply(java.math.BigDecimal(q)).stripTrailingZeros().toPlainString()
                    else "إجمالي الصنف: —"
            }
            override fun afterTextChanged(s:android.text.Editable?) {}
        }
        qty.addTextChangedListener(update)
        price.addTextChangedListener(update)
        if(removable) item.addView(text("حذف الصنف",11f,Color.rgb(165,53,43)).apply {
            isClickable=true
            setOnClickListener { parent.removeView(item);onChange() }
        })
        parent.addView(item,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    private fun fullSalePreview(parent:LinearLayout) {
        formHeader(parent,"فاتورة مبيعات","اختر من المنتجات أو امسح الباركود بالكاميرا لإضافة الساعة فوراً.","قائمة الفواتير","preview:المبيعات والفواتير")
        formSection(parent,"بيانات الفاتورة")
        val saleType=select(parent,"نوع البيع *",listOf("بيع من المحل","بيع أونلاين"))
        parent.addView(text("الأونلاين يخرج من المخزون فوراً، والمال يدخل الخزنة فقط عند التحصيل.",12f))
        formPair(parent,{ column ->
            field(column,"التاريخ *","YYYY-MM-DD").apply {
                setText(java.time.LocalDate.now().toString())
                isFocusable=false
                isClickable=true
                setOnClickListener {
                    val now=runCatching { java.time.LocalDate.parse(text.toString()) }.getOrDefault(java.time.LocalDate.now())
                    android.app.DatePickerDialog(this@MainActivity,{ _,y,m,d ->
                        setText(java.time.LocalDate.of(y,m+1,d).toString())
                    },now.year,now.monthValue-1,now.dayOfMonth).show()
                }
            }
        },{ column ->
            field(column,"الوقت *","HH:MM").apply {
                setText(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                isFocusable=false
                isClickable=true
                setOnClickListener {
                    val now=runCatching { java.time.LocalTime.parse(text.toString()) }.getOrDefault(java.time.LocalTime.now())
                    android.app.TimePickerDialog(this@MainActivity,{ _,h,m ->
                        setText(String.format(java.util.Locale.US,"%02d:%02d",h,m))
                    },now.hour,now.minute,true).show()
                }
            }
        })
        lateinit var saleCurrency:Spinner
        lateinit var paymentMethod:Spinner
        formPair(parent,{ column ->
            saleCurrency=select(column,"عملة البيع / القبض *",listOf("USD","TRY","SYP"))
            column.addView(text("المبلغ يدخل صندوق هذه العملة نفسها.",11f,Color.rgb(138,123,108)))
        },{ column ->
            paymentMethod=select(column,"طريقة الدفع *",listOf("نقدي","شام كاش","تحويل بنكي"))
            column.addView(text("اختر نقدي أو شام كاش قبل حفظ الفاتورة.",11f,Color.rgb(138,123,108)))
        })
        lateinit var saleCustomer:EditText
        lateinit var salePhone:EditText
        formPair(parent,{ saleCustomer=field(it,"اسم العميل","مهم للضمان والفاتورة") },{ salePhone=field(it,"رقم واتساب العميل","مثال: 09xxxxxxxx").apply { inputType=InputType.TYPE_CLASS_PHONE } })
        select(parent,"مدة الضمان",listOf("بدون ضمان","3 أشهر","6 أشهر","سنة","سنتان")).setSelection(3)
        parent.addView(text("يرتبط الضمان تلقائياً بالعميل والفاتورة.",12f))
        formSection(parent,"إضافة من المخزون")
        val liveQuery=field(parent,"بحث سريع عن المنتج","اكتب جزءاً من الاسم أو REF أو الرقم أو الباركود")
        val liveMatches=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(liveMatches)
        val saleBarcode=field(parent,"","امسح أو اكتب باركود الساعة")
        barcodeScanButton(parent,saleBarcode)
        saleBarcode.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(s:android.text.Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                val code=s?.toString()?.trim().orEmpty()
                if(code.isBlank()) return
                try {
                    val matched=LegacyProductRepository.list(legacyBackupStore.readableDatabase,code,0,30).firstOrNull { it.barcode==code }
                    if(matched!=null) saleBarcode.post {
                        liveQuery.setText(code)
                        for(i in 0 until liveMatches.childCount) {
                            val candidate=liveMatches.getChildAt(i) as? TextView ?: continue
                            if(candidate.getTag(android.R.id.content)==matched.id) {
                                candidate.performClick()
                                break
                            }
                        }
                        saleBarcode.setText("")
                    }
                } catch(_:Exception) {}
            }
        })
        parent.addView(text("عند العثور على الباركود يضاف المنتج بسعره وبياناته المحفوظة تلقائياً.",12f))
        formSection(parent,"أصناف الفاتورة")
        val lines=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(lines)
        val liveSubtotal=text("مجموع الأصناف: —",16f,gold)
        val updateSubtotal= {
            var sum=java.math.BigDecimal.ZERO
            var valid=false
            for(i in 0 until lines.childCount) {
                val pair=lines.getChildAt(i).tag as? Pair<*,*> ?: continue
                val qty=(pair.first as? EditText)?.text?.toString()?.toIntOrNull()
                val price=(pair.second as? EditText)?.text?.toString()?.toBigDecimalOrNull()
                if(qty!=null && qty>0 && price!=null && price.signum()>=0) {
                    sum=sum.add(price.multiply(java.math.BigDecimal(qty)))
                    valid=true
                }
            }
            liveSubtotal.text=if(valid) "مجموع الأصناف: "+sum.stripTrailingZeros().toPlainString() else "مجموع الأصناف: —"
        }
        var nextLine=2
        liveQuery.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(s:android.text.Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                liveMatches.removeAllViews()
                val query=s?.toString()?.trim().orEmpty()
                if(query.isBlank()) return
                try {
                    LegacyProductRepository.list(legacyBackupStore.readableDatabase,query,0,20).forEach { product ->
                        liveMatches.addView(text(product.name+" · "+product.barcode+" · "+product.stock,13f,gold).apply {
                            setTag(android.R.id.content,product.id)
                            gravity=Gravity.RIGHT
                            isClickable=true
                            setOnClickListener {
                                if(product.stock<=0) {
                                    android.widget.Toast.makeText(this@MainActivity,"المخزون غير كافٍ",android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    manualSaleItemPreview(lines,nextLine++,true,updateSubtotal)
                                    val card=lines.getChildAt(lines.childCount-1)
                                    val edits=mutableListOf<EditText>()
                                    fun collect(v:View) {
                                        if(v is EditText) edits.add(v)
                                        if(v is android.view.ViewGroup) for(i in 0 until v.childCount) collect(v.getChildAt(i))
                                    }
                                    collect(card)
                                    if(edits.size>=4) {
                                        edits[0].setText(product.name)
                                        edits[1].setText(product.raw.optString("model"))
                                        edits[2].setText("1")
                                        edits[3].setText(product.salePrice.toString())
                                        card.setTag(android.R.id.content,product.id)
                                    }
                                    updateSubtotal()
                                }
                            }
                        })
                    }
                } catch(e:Exception) {
                    liveMatches.addView(text("تعذر البحث: "+e.message,12f))
                }
            }
        })
        parent.addView(text("＋ إضافة صنف يدوي",16f,gold).apply {
            isClickable=true
            setOnClickListener { manualSaleItemPreview(lines,nextLine++,true,updateSubtotal);updateSubtotal() }
        })
        parent.addView(liveSubtotal)
        formSection(parent,"الدفع والإرسال")
        val paymentStatus=select(parent,"حالة الدفع",listOf("مدفوع بالكامل","دفعة جزئية","عند الاستلام","آجل"))
        val savedDiscountRules=LegacyDiscountRules.list(legacyBackupStore.readableDatabase)
        val discountRule=select(parent,"قاعدة الخصم",listOf("بدون قاعدة خصم")+savedDiscountRules.map { it.name })
        parent.addView(text("قاعدة الخصم = خصم محفوظ مسبقاً لتستخدمه بسرعة، مثل «عميل مميز 10%».",12f))
        disabledAction(parent,"＋ إنشاء قاعدة خصم")
        val manualDiscount=field(parent,"خصم الفاتورة اليدوي","0").apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        field(parent,"نقاط الولاء المستخدمة","0").apply {
            isEnabled=false
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        parent.addView(text("برنامج الولاء متوقف حالياً.",12f))
        val taxRate=field(parent,"نسبة الضريبة %","0").apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        parent.addView(text("اختياري ومجهز للمستقبل. اتركه 0 حالياً.",12f))
        val collectionMode=select(parent,"نوع القبض",listOf("عملة واحدة","دفع مختلط (أكثر من عملة)"))
        parent.addView(text("للدفع مثل 55$ + 100₺ اختر دفع مختلط.",12f))
        val singlePayment=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(singlePayment)
        val initialPayment=field(singlePayment,"المبلغ المقبوض الآن","0").apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val changeAccount=select(parent,"إرجاع الباقي عبر",listOf("درج المحل","الصندوق الخارجي","شام كاش","البنك"))
        val changeCurrency=select(parent,"عملة الباقي",listOf("USD","TRY","SYP"))
        val changeRate=field(parent,"سعر صرف الباقي (عملة الباقي لكل وحدة من عملة الفاتورة)","1").apply {
            setText("1")
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val changePreview=text("الباقي للعميل: 0",14f,gold)
        parent.addView(changePreview)
        val updateChange= {
            val total=(0 until lines.childCount).sumOf { index ->
                val pair=lines.getChildAt(index).tag as? Pair<*,*>
                val qty=(pair?.first as? EditText)?.text?.toString()?.toIntOrNull() ?: 0
                val price=(pair?.second as? EditText)?.text?.toString()?.toDoubleOrNull() ?: 0.0
                qty*price
            }-(manualDiscount.text.toString().toDoubleOrNull() ?: 0.0)
            val tender=initialPayment.text.toString().toDoubleOrNull() ?: 0.0
            val rate=changeRate.text.toString().toDoubleOrNull() ?: 0.0
            changePreview.text="الباقي للعميل: "+((tender-total).coerceAtLeast(0.0)*rate)+" "+changeCurrency.selectedItem.toString()
        }
        listOf(initialPayment,manualDiscount,changeRate).forEach { edit ->
            edit.addTextChangedListener(object:android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun afterTextChanged(s:android.text.Editable?) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { updateChange() }
            })
        }
        val mixedPayment=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(mixedPayment)
        mixedPayment.addView(text("المبالغ المقبوضة فعلياً",16f,gold))
        val mixedRows=listOf("USD","TRY","SYP").map { money ->
            val value=field(mixedPayment,"المقبوض "+money,"0").apply {
                setText("0")
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            val rate=field(mixedPayment,"كم "+saleCurrency.selectedItem.toString()+" لكل "+money,"1").apply {
                setText("1")
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            val account=select(mixedPayment,"خزنة "+money,listOf("درج المحل","الصندوق الخارجي","شام كاش","البنك"))
            Triple(money,Pair(value,rate),account)
        }
        mixedPayment.visibility=View.GONE
        collectionMode.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
                singlePayment.visibility=if(position==0) View.VISIBLE else View.GONE
                mixedPayment.visibility=if(position==1) View.VISIBLE else View.GONE
            }
        }
        multilineField(parent,"ملاحظات","تفاصيل الضمان أو اللون أو أي ملاحظة")
        parent.addView(text("عند وجود رقم العميل تُرسل الفاتورة تلقائياً عبر واتساب أعمال بعد الحفظ.",12f))
        saleCalculationBox(parent)
        formActions(parent,{ returnToPrevious() },"حفظ الفاتورة") {
            try {
                val saleLines=mutableListOf<NativeSalesLedger.Line>()
                for(i in 0 until lines.childCount) {
                    val card=lines.getChildAt(i)
                    val productId=card.getTag(android.R.id.content) as? String
                    val pair=card.tag as? Pair<*,*> ?: error("بيانات الصنف غير مكتملة")
                    val qty=(pair.first as? EditText)?.text?.toString()?.toIntOrNull() ?: 0
                    val price=(pair.second as? EditText)?.text?.toString()?.toDoubleOrNull() ?: -1.0
                    val edits=mutableListOf<EditText>()
                    fun gather(v:View) {
                        if(v is EditText) edits.add(v)
                        if(v is android.view.ViewGroup) for(j in 0 until v.childCount) gather(v.getChildAt(j))
                    }
                    gather(card)
                    val name=edits.getOrNull(0)?.text?.toString()?.trim().orEmpty()
                    val model=edits.getOrNull(1)?.text?.toString()?.trim().orEmpty()
                    require(!productId.isNullOrBlank() || name.isNotBlank()) { "اسم الصنف مطلوب" }
                    val unitCost=if(productId.isNullOrBlank()) edits.getOrNull(4)?.text?.toString()?.toDoubleOrNull() ?: 0.0 else 0.0
                    saleLines.add(NativeSalesLedger.Line(productId.orEmpty(),qty,price,name,model,unitCost))
                }
                val discount=manualDiscount.text.toString().toDoubleOrNull() ?: 0.0
                require(discount.isFinite() && discount>=0) { "الخصم غير صالح" }
                val enteredTaxRate=taxRate.text.toString().toDoubleOrNull() ?: 0.0
                require(enteredTaxRate.isFinite() && enteredTaxRate>=0) { "نسبة الضريبة غير صالحة" }
                val subtotal=saleLines.sumOf { it.quantity*it.unitPrice }
                val savedRule=savedDiscountRules.getOrNull(discountRule.selectedItemPosition-1)
                val ruleDiscount=LegacyDiscountRules.amount(savedRule,subtotal,saleCurrency.selectedItem.toString())
                // Legacy saveSale treats the entered discount as the total requested
                // discount, inclusive of the saved rule, not an additional amount.
                val manualPart=(discount-ruleDiscount).coerceAtLeast(0.0)
                    .coerceAtMost((subtotal-ruleDiscount).coerceAtLeast(0.0))
                val combinedDiscount=(ruleDiscount+manualPart).coerceAtMost(subtotal)
                require(subtotal>0) { "قيمة الفاتورة غير صالحة" }
                val amount=initialPayment.text.toString().toDoubleOrNull() ?: 0.0
                val finalTotal=(subtotal-combinedDiscount)*(1.0+enteredTaxRate/100.0)
                val deferred=paymentStatus.selectedItemPosition>=2
                val id=if(collectionMode.selectedItemPosition==1) {
                    val tenders=mixedRows.mapNotNull { (money,values,account) ->
                        val paid=values.first.text.toString().toDoubleOrNull() ?: 0.0
                        if(paid<=0) null else NativeSalesLedger.Tender(paid,money,account.selectedItem.toString(),
                            values.second.text.toString().toDoubleOrNull() ?: 0.0)
                    }
                    val received=if(deferred) 0.0 else tenders.sumOf { it.amount*it.rate }
                    if(deferred) tenders.clear()
                    // Original saveSale: a fully paid mixed tender below the total
                    // becomes a bargaining discount, with tax recalculated.
                    val bargainingDiscount=if(paymentStatus.selectedItemPosition==0 &&
                        tenders.isNotEmpty() && received>0 && received<finalTotal-0.005) {
                        val taxable=(subtotal-combinedDiscount).coerceAtLeast(0.0)
                        val targetTaxable=received/(1.0+enteredTaxRate/100.0)
                        (taxable-targetTaxable).coerceIn(0.0,taxable)
                    } else 0.0
                    val actualTotal=(subtotal-combinedDiscount-bargainingDiscount)*(1.0+enteredTaxRate/100.0)
                    require(received<=actualTotal+0.01) { "مجموع الدفعات المختلطة يتجاوز قيمة الفاتورة" }
                    require(paymentStatus.selectedItemPosition!=0 || kotlin.math.abs(received-actualTotal)<0.01) {
                        "حالة مدفوع بالكامل تتطلب قبض إجمالي الفاتورة"
                    }
                    if(tenders.isEmpty()) NativeSalesLedger.save(legacyBackupStore.writableDatabase,saleLines,
                        saleCurrency.selectedItem.toString(),saleType.selectedItem.toString(),
                        saleCustomer.text.toString(),salePhone.text.toString(),manualDiscount=manualPart,ruleDiscount=ruleDiscount,taxRate=enteredTaxRate)
                    else NativeSalesLedger.saveMixed(legacyBackupStore.writableDatabase,saleLines,
                        saleCurrency.selectedItem.toString(),saleType.selectedItem.toString(),
                        saleCustomer.text.toString(),salePhone.text.toString(),tenders,manualPart+bargainingDiscount,ruleDiscount,enteredTaxRate)
                } else {
                    val effectiveAmount=if(deferred) 0.0 else if(paymentStatus.selectedItemPosition==0) finalTotal else amount
                    NativeSalesLedger.saveWithChange(
                    legacyBackupStore.writableDatabase,saleLines,saleCurrency.selectedItem.toString(),
                    saleType.selectedItem.toString(),saleCustomer.text.toString(),salePhone.text.toString(),effectiveAmount,
                    when(paymentMethod.selectedItemPosition) { 1 -> "شام كاش"; 2 -> "البنك"; else -> "درج المحل" },
                    changeCurrency.selectedItem.toString(),changeAccount.selectedItem.toString(),
                    changeRate.text.toString().toDoubleOrNull() ?: 0.0,manualPart,ruleDiscount,enteredTaxRate
                )
                }
                android.widget.Toast.makeText(this,"تم حفظ الفاتورة: "+id,android.widget.Toast.LENGTH_LONG).show()
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("فاتورة الصفا")
                    .setMessage("تم حفظ الفاتورة. هل تريد طباعتها أم مشاركتها؟")
                    .setPositiveButton("واتساب") { _,_ ->
                        runCatching { SalesInvoiceDocument.share(this@MainActivity,legacyBackupStore.readableDatabase,id) }
                            .onFailure { android.widget.Toast.makeText(this@MainActivity,it.message,android.widget.Toast.LENGTH_LONG).show() }
                    }
                    .setNeutralButton("طباعة") { _,_ ->
                        runCatching { SalesInvoiceDocument.print(this@MainActivity,legacyBackupStore.readableDatabase,id) }
                            .onFailure { android.widget.Toast.makeText(this@MainActivity,it.message,android.widget.Toast.LENGTH_LONG).show() }
                    }
                    .setNegativeButton("لاحقاً",null).show()
                navigateTo("preview:المبيعات والفواتير")
            } catch(e:Exception) {
                android.widget.Toast.makeText(this,e.message ?: "تعذر حفظ الفاتورة",android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
    }
    private fun disabledAction(parent:LinearLayout,label:String) {
        val action=text(label,13f,Color.rgb(122,81,24)).apply {
            typeface=Typeface.create(typeface,Typeface.BOLD)
            background=panel(Color.rgb(247,237,221),12,Color.rgb(223,201,164))
            setOnClickListener { android.widget.Toast.makeText(this@MainActivity,"هذه الوظيفة غير مفعلة في معاينة Native 7",android.widget.Toast.LENGTH_SHORT).show() }
            isClickable=true
        }
        parent.addView(action,LinearLayout.LayoutParams(-1,dp(44)).apply { bottomMargin=dp(9) })
    }
    /** Original .form-actions: two equal-width buttons with a 9px gap. */
    private fun formActions(parent:LinearLayout,cancel:()->Unit,saveLabel:String,onSave:()->Unit) {
        val actions=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
        }
        val cancelButton=text("إلغاء",13f,Color.rgb(122,81,24)).apply {
            background=panel(Color.rgb(247,237,221),12,Color.rgb(223,201,164))
            isClickable=true
            setOnClickListener { cancel() }
        }
        val saveButton=text(saveLabel,13f,Color.WHITE).apply {
            background=GradientDrawable(Orientation.TL_BR,intArrayOf(Color.rgb(179,131,50),Color.rgb(139,96,31))).apply {
                cornerRadius=dp(12).toFloat()
            }
            isClickable=true
            setOnClickListener { onSave() }
        }
        actions.addView(cancelButton,LinearLayout.LayoutParams(0,dp(44),1f).apply { marginEnd=dp(9) })
        actions.addView(saveButton,LinearLayout.LayoutParams(0,dp(44),1f))
        parent.addView(actions,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18) })
    }
    /** Original .calculation-box: dashed sand border and five rows. */
    private fun saleCalculationBox(parent:LinearLayout) {
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            background=GradientDrawable().apply {
                setColor(Color.rgb(251,245,235))
                cornerRadius=dp(12).toFloat()
                setStroke(dp(1),Color.rgb(223,201,164),dp(5).toFloat(),dp(4).toFloat())
            }
            setPadding(dp(13),dp(13),dp(13),dp(13))
        }
        listOf("خصم قاعدة الخصم","خصم إضافي يدوي","خصم الولاء","إجمالي الفاتورة","المتبقي على العميل").forEach { label ->
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                layoutDirection=View.LAYOUT_DIRECTION_RTL
                gravity=Gravity.CENTER_VERTICAL
            }
            row.addView(text(label,12f).apply { gravity=Gravity.RIGHT },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(text("—",12f,Color.rgb(122,81,24)))
            box.addView(row)
        }
        parent.addView(box,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(15);bottomMargin=dp(15) })
    }
    private fun repairFormPreview(parent:LinearLayout) {
        formHeader(parent,"طلب صيانة","سجل الاستلام، الأجرة، كلفة الصيانة، وحالة الدفع.","الطلبات","preview:كل الصيانة")
        formSection(parent,"بيانات العميل")
        formPair(parent,{ column ->
            field(column,"التاريخ *","YYYY-MM-DD").apply {
                setText(java.time.LocalDate.now().toString())
                isFocusable=false
                isClickable=true
                setOnClickListener {
                    val today=runCatching { java.time.LocalDate.parse(text.toString()) }.getOrDefault(java.time.LocalDate.now())
                    android.app.DatePickerDialog(this@MainActivity,{ _,y,m,d ->
                        setText(java.time.LocalDate.of(y,m+1,d).toString())
                    },today.year,today.monthValue-1,today.dayOfMonth).show()
                }
            }
        },{ column ->
            field(column,"وقت الاستلام *","HH:MM").apply {
                setText(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                isFocusable=false
                isClickable=true
                setOnClickListener {
                    val now=runCatching { java.time.LocalTime.parse(text.toString()) }.getOrDefault(java.time.LocalTime.now())
                    android.app.TimePickerDialog(this@MainActivity,{ _,h,m ->
                        setText(String.format(java.util.Locale.US,"%02d:%02d",h,m))
                    },now.hour,now.minute,true).show()
                }
            }
        })
        select(parent,"عملة الصيانة / القبض *",listOf("USD","TRY","SYP"))
        parent.addView(text("المبلغ يدخل صندوق هذه العملة نفسها.",12f))
        select(parent,"طريقة الدفع الأولى *",listOf("نقدي","شام كاش","تحويل بنكي"))
        parent.addView(text("اختر نقدي أو شام كاش قبل الحفظ.",12f))
        formPair(parent,{ field(it,"اسم العميل *") },{ field(it,"رقم واتساب العميل","09xxxxxxxx").inputType=InputType.TYPE_CLASS_PHONE })
        select(parent,"حالة الطلب",listOf("قيد الصيانة","بانتظار القطعة","جاهز للاستلام"))
        formSection(parent,"تفاصيل الساعة والتكلفة")
        formPair(parent,{ field(it,"الماركة / نوع الساعة *","مثال: كاسيو") },{ field(it,"الموديل أو الرقم") })
        multilineField(parent,"العطل أو المطلوب *","مثال: تغيير بطارية وإصلاح السوار")
        multilineField(parent,"نتيجة الفحص أو تحديث العميل","اختياري — اكتب هنا أي مشكلة تظهر لاحقاً ليتم إرسالها للعميل")
        parent.addView(text("صورة ساعة العميل عند الاستلام",16f,gold))
        parent.addView(text("📷 الكاميرا",16f,gold).apply {
            isClickable=true
            setOnClickListener {
                snapshotRepairDraft()
                val values=android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"alsafa_repair_"+System.currentTimeMillis()+".jpg")
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                    if(android.os.Build.VERSION.SDK_INT>=29) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AlSafa")
                }
                val uri=contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
                if(uri==null) {
                    android.widget.Toast.makeText(this@MainActivity,"تعذر إنشاء ملف الصورة",android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    pendingCameraUri=uri
                    val camera=android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri)
                        addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { startActivityForResult(camera,702) }
                    catch(e:Exception) {
                        pendingCameraUri=null
                        contentResolver.delete(uri,null,null)
                        android.widget.Toast.makeText(this@MainActivity,"تعذر تشغيل الكاميرا",android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
        parent.addView(text("🖼️ اختيار من الاستوديو",16f,gold).apply {
            isClickable=true
            setOnClickListener {
                val intent=android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type="image/*"
                }
                startActivityForResult(intent,701)
            }
        })
        parent.addView(text("حذف الصورة",16f,gold).apply {
            isClickable=true
            setOnClickListener { snapshotRepairDraft();repairPhotoUri=null;render();restoreRepairDraft() }
        })
        repairPhotoUri?.let { uri ->
            parent.addView(android.widget.ImageView(this).apply {
                setImageURI(uri)
                adjustViewBounds=true
                maxHeight=dp(200)
                isClickable=true
                setOnClickListener {
                    val photo=android.widget.ImageView(this@MainActivity).apply {
                        setImageURI(uri)
                        adjustViewBounds=true
                        scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
                    }
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setView(photo)
                        .setPositiveButton("✕ إغلاق"){ dialog,_ -> dialog.dismiss() }
                        .show()
                }
            })
        }
        parent.addView(text("تُحفظ داخل طلب الصيانة وتُرسل مع رسالة الاستلام عند اختيار واتساب.",12f))
        formSection(parent,"قطع الصيانة المستخدمة")
        parent.addView(text("اختيار قطع الصيانة اختياري عند الاستلام.",14f))
        field(parent,"نسبة الضريبة %","0").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        parent.addView(text("اختياري ومجهز للمستقبل. اتركه 0 حالياً.",12f))
        field(parent,"المبلغ المقبوض الآن","0").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        parent.addView(text("عملة الصيانة والتحصيل",16f))
        parent.addView(text("هي نفسها عملة الفاتورة المختارة أعلاه، وتدخل إلى صندوق العملة نفسها.",12f))
        select(parent,"حالة الدفع",listOf("عند الاستلام","مدفوع جزئياً","مدفوع بالكامل"))
        multilineField(parent,"ملاحظات","لون السوار، موعد التسليم، تفاصيل مهمة")
        parent.addView(text("عند وجود رقم العميل تُفتح محادثته في واتساب أعمال مع صورة الساعة ورسالة الاستلام بعد الحفظ.",12f))
        parent.addView(text("اختيار قطع الصيانة اختياري عند الاستلام. يمكنك حفظ الطلب بدون قطع، ثم بعد الفحص تحديث ما تم عمله وأجرة العمل، وإضافة القطع عند معرفتها.",12f))
        val repairActions=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
        }
        repairActions.addView(text("إلغاء",14f,Color.rgb(122,81,24)).apply {
            background=panel(Color.rgb(247,237,221),11,Color.rgb(223,201,164))
            isClickable=true
            setOnClickListener { returnToPrevious() }
        },LinearLayout.LayoutParams(0,dp(45),1f).apply { marginEnd=dp(9) })
        repairActions.addView(text("حفظ طلب الصيانة",14f,Color.WHITE).apply {
            background=GradientDrawable(Orientation.TL_BR,intArrayOf(Color.rgb(179,131,50),Color.rgb(139,96,31))).apply {
                cornerRadius=dp(11).toFloat()
            }
            isClickable=true
            setOnClickListener {
                snapshotRepairDraft()
                val values=repairDraftTexts ?: emptyList()
                val customer=values.getOrNull(2)?.trim().orEmpty()
                val watch=values.getOrNull(4)?.trim().orEmpty()
                if(customer.isBlank() || watch.isBlank()) {
                    android.widget.Toast.makeText(this@MainActivity,"تحقق من بيانات العميل والساعة",android.widget.Toast.LENGTH_LONG).show()
                } else try {
                    val payload=org.json.JSONObject().apply {
                        put("form_texts",org.json.JSONArray(values))
                        put("form_selections",org.json.JSONArray(repairDraftSelections ?: emptyList<Int>()))
                    }
                    val photo=repairPhotoUri?.toString()
                    val id=repairStore.save(payload,photo,"قيد الصيانة")
                    android.widget.Toast.makeText(this@MainActivity,"تم حفظ طلب الصيانة رقم $id محلياً",android.widget.Toast.LENGTH_LONG).show()
                } catch(e:Exception) {
                    android.widget.Toast.makeText(this@MainActivity,"فشل حفظ الطلب: "+e.message,android.widget.Toast.LENGTH_LONG).show()
                }
            }
        },LinearLayout.LayoutParams(0,dp(45),1f))
        
        parent.addView(repairActions,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18) })
    }
    private fun invoiceArchivePreview(parent:LinearLayout,repair:Boolean) {
        parent.addView(text(if(repair) "قسم الصيانة" else "المبيعات والفواتير",26f,gold))
        parent.addView(text(if(repair) "رقم فاتورة الصيانة ظاهر ويمكن البحث به وتعديل بياناتها." else "رقم الفاتورة ظاهر ويمكن البحث به وتعديل بياناتها بأمان.",14f))
        val newLabel=if(repair) "＋ طلب" else "＋ فاتورة"
        parent.addView(text(newLabel,16f,gold).apply {
            isClickable=true
            setOnClickListener { navigateTo(if(repair) "preview:طلب صيانة" else "preview:مبيع كامل") }
        })
        if(!repair) {
            formSection(parent,"قبض / إلغاء فاتورة")
            val invoiceId=field(parent,"رقم الفاتورة","أدخل رقم الفاتورة")
            val invoiceSearch=field(parent,"بحث بالفاتورة أو العميل أو الهاتف أو المنتج","تظهر النتائج تلقائياً")
            val searchFrom=field(parent,"من تاريخ","YYYY-MM-DD")
            val searchTo=field(parent,"إلى تاريخ","YYYY-MM-DD")
            fun searchDay(value:String):Long? {
                if(value.isBlank()) return null
                val format=java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).apply {
                    isLenient=false
                    timeZone=java.util.TimeZone.getTimeZone("Asia/Damascus")
                }
                return format.parse(value)?.time
            }
            val invoiceMatches=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            parent.addView(invoiceMatches)
            invoiceSearch.addTextChangedListener(object:android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun afterTextChanged(s:android.text.Editable?) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                    invoiceMatches.removeAllViews()
                    val term=s?.toString()?.trim().orEmpty()
                    if(term.isBlank()) return
                    try {
                        val db=legacyBackupStore.readableDatabase
                        NativeSalesLedger.create(db)
                        LegacyDomainTables.create(db)
                        NativeProductChanges.create(db)
                        val start=searchDay(searchFrom.text.toString().trim())
                        val end=searchDay(searchTo.text.toString().trim())?.plus(86400000L)
                        val like="%"+term+"%"
                        db.rawQuery("""SELECT DISTINCT s.id,s.customer,s.phone,s.total,s.currency,s.status
                            FROM native_sales s
                            WHERE (s.id LIKE ? OR s.customer LIKE ? OR s.phone LIKE ?
                                OR EXISTS(SELECT 1 FROM native_sale_lines l
                                    WHERE l.sale_id=s.id AND
                                    (l.product_id LIKE ? OR EXISTS(
                                        SELECT 1 FROM domain_products p WHERE p.id=l.product_id AND
                                        COALESCE((SELECT json_extract(e.payload,'$.name') FROM native_product_edits e WHERE e.product_id=p.id),p.name) LIKE ?)
                                    OR EXISTS(SELECT 1 FROM native_new_products n
                                        WHERE n.product_id=l.product_id AND
                                        COALESCE((SELECT json_extract(e.payload,'$.name') FROM native_product_edits e WHERE e.product_id=n.product_id),
                                        json_extract(n.payload,'$.name')) LIKE ?))))
                                AND (? IS NULL OR s.created_at>=?)
                                AND (? IS NULL OR s.created_at<?)
                            ORDER BY s.created_at DESC LIMIT 30""".trimIndent(),
                            arrayOf(like,like,like,like,like,like,
                                start?.toString(),start?.toString(),end?.toString(),end?.toString())).use { cursor ->
                            while(cursor.moveToNext()) {
                                val id=cursor.getString(0)
                                invoiceMatches.addView(text(cursor.getString(1)+" · "+cursor.getString(2)+" · "+cursor.getDouble(3)+" "+cursor.getString(4)+" · "+cursor.getString(5),13f,gold).apply {
                                    isClickable=true
                                    setOnClickListener { invoiceId.setText(id) }
                                })
                            }
                        }
                    } catch(e:Exception) {
                        invoiceMatches.addView(text("تعذر البحث: "+e.message,12f))
                    }
                }
            })
            val dateRefresh=object:android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                    invoiceSearch.setText(invoiceSearch.text.toString())
                }
                override fun afterTextChanged(s:android.text.Editable?) {}
            }
            searchFrom.addTextChangedListener(dateRefresh)
            searchTo.addTextChangedListener(dateRefresh)

            parent.addView(Button(this).apply {
                text="طباعة الفاتورة"
                setOnClickListener {
                    try {
                        SalesInvoiceDocument.print(this@MainActivity,legacyBackupStore.readableDatabase,
                            invoiceId.text.toString().trim(),getSharedPreferences("alsafa_printer",MODE_PRIVATE).getInt("roll_width_mm",80))
                    } catch(e:Exception) {
                        android.widget.Toast.makeText(this@MainActivity,e.message ?: "تعذرت الطباعة",android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            })
            parent.addView(Button(this).apply {
                text="طباعة بلوتوث مباشرة"
                setOnClickListener {
                    try {
                        val prefs=getSharedPreferences("alsafa_printer",MODE_PRIVATE)
                        val mac=prefs.getString("printer_mac",null) ?: error("اختر الطابعة من صفحة الطابعة الحرارية أولاً")
                        val width=prefs.getInt("roll_width_mm",80)
                        val file=SalesInvoiceDocument.createPdf(this@MainActivity,legacyBackupStore.readableDatabase,
                            invoiceId.text.toString().trim(),width)
                        ThermalBluetoothPrinter.print(this@MainActivity,file,mac,width) { error ->
                            android.widget.Toast.makeText(this@MainActivity,error ?: "أُرسلت بيانات الفاتورة إلى الطابعة",android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch(e:Exception) {
                        android.widget.Toast.makeText(this@MainActivity,e.message ?: "تعذرت الطباعة",android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            })
            parent.addView(Button(this).apply {
                text="إرسال الفاتورة عبر واتساب"
                setOnClickListener {
                    try {
                        SalesInvoiceDocument.share(this@MainActivity,legacyBackupStore.readableDatabase,
                            invoiceId.text.toString().trim(),getSharedPreferences("alsafa_printer",MODE_PRIVATE).getInt("roll_width_mm",80))
                    } catch(e:Exception) {
                        android.widget.Toast.makeText(this@MainActivity,e.message ?: "تعذرت المشاركة",android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            })
            val collectedAmount=field(parent,"المبلغ المقبوض","0").apply {
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            val cancellationReason=field(parent,"سبب الإلغاء","مطلوب عند الإلغاء")
            parent.addView(Button(this).apply {
                text="قبض"
                setOnClickListener {
                    try {
                        NativeSalesLedger.collect(legacyBackupStore.writableDatabase,
                            invoiceId.text.toString().trim(),
                            collectedAmount.text.toString().toDoubleOrNull() ?: -1.0,"درج المحل")
                        android.widget.Toast.makeText(this@MainActivity,"تم تسجيل القبض",android.widget.Toast.LENGTH_LONG).show()
                    } catch(e:Exception) {
                        android.widget.Toast.makeText(this@MainActivity,e.message ?: "تعذر القبض",android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            })
            parent.addView(Button(this).apply {
                text="إلغاء فاتورة"
                setOnClickListener {
                    val id=invoiceId.text.toString().trim()
                    val reason=cancellationReason.text.toString().trim()
                    if(id.isBlank() || reason.isBlank()) {
                        android.widget.Toast.makeText(this@MainActivity,"رقم الفاتورة وسبب الإلغاء مطلوبان",android.widget.Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setMessage("تأكيد إلغاء الفاتورة وعكس المخزون والقبض؟")
                        .setNegativeButton("رجوع",null)
                        .setPositiveButton("إلغاء الفاتورة") { _,_ ->
                            try {
                                NativeSalesLedger.cancel(legacyBackupStore.writableDatabase,id,reason)
                                android.widget.Toast.makeText(this@MainActivity,"تم إلغاء الفاتورة",android.widget.Toast.LENGTH_LONG).show()
                            } catch(e:Exception) {
                                android.widget.Toast.makeText(this@MainActivity,e.message ?: "تعذر الإلغاء",android.widget.Toast.LENGTH_LONG).show()
                            }
                        }.show()
                }
            })
        }
        val date=field(parent,"يوم الفواتير","YYYY-MM-DD").apply {
            inputType=InputType.TYPE_NULL
            isFocusable=false
            isClickable=true
            setText(java.time.LocalDate.now().toString())
            setOnClickListener {
                val current=runCatching { java.time.LocalDate.parse(text.toString()) }.getOrDefault(java.time.LocalDate.now())
                android.app.DatePickerDialog(this@MainActivity,{ _,year,month,day ->
                    setText(java.time.LocalDate.of(year,month+1,day).toString())
                },current.year,current.monthValue-1,current.dayOfMonth).show()
            }
        }
        val search=field(parent,"بحث في جميع الفواتير","رقم الفاتورة أو اسم العميل أو الهاتف...")
        val hint=text("بدون بحث: تظهر فواتير اليوم المختار. عند الكتابة: يتم البحث في جميع التواريخ ويظهر تاريخ الفاتورة الأصلي.",12f)
        parent.addView(hint)
        val filterHint=text("يوم الفواتير: "+date.text.toString(),13f,gold)
        parent.addView(filterHint)
        val update= {
            filterHint.text=if(search.text.isNullOrBlank()) "يوم الفواتير: "+date.text.toString() else "بحث في جميع الفواتير: "+search.text.toString()
        }
        search.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { update() }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        date.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { update() }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        if(repair) {
            val entries=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            parent.addView(entries)
            val refresh= {
                entries.removeAllViews()
                try {
                    val rows=repairStore.list()
                    var shown=0
                    for(i in 0 until rows.length()) {
                        val item=rows.getJSONObject(i)
                        val payload=item.getJSONObject("payload")
                        val values=payload.optJSONArray("form_texts")
                        val name=values?.optString(2).orEmpty()
                        val watch=values?.optString(4).orEmpty()
                        val phone=values?.optString(3).orEmpty()
                        val dateValue=values?.optString(0).orEmpty()
                        val query=search.text.toString().trim()
                        if(query.isEmpty() && dateValue!=date.text.toString()) continue
                        if(query.isNotEmpty() && !listOf(name,watch,phone,item.getLong("id").toString()).any { it.contains(query,true) }) continue
                        shown++
                        val id=item.getLong("id")
                        entries.addView(text("#$id — $name — $watch — $dateValue — "+item.getString("status"),15f))
                        if(item.isNull("delivered_at")) {
                            entries.addView(text("تسليم",15f,gold).apply {
                                isClickable=true
                                setOnClickListener {
                                    android.app.AlertDialog.Builder(this@MainActivity)
                                        .setMessage("تأكيد تسليم طلب الصيانة رقم $id؟ لا يتم تسجيل قبض تلقائي.")
                                        .setNegativeButton("إلغاء",null)
                                        .setPositiveButton("تأكيد"){ _,_ ->
                                            try { repairStore.deliver(id); render() }
                                            catch(e:Exception) { android.widget.Toast.makeText(this@MainActivity,e.message,android.widget.Toast.LENGTH_LONG).show() }
                                        }.show()
                                }
                            })
                        }
                    }
                    if(shown==0) entries.addView(text("لا توجد طلبات مطابقة للبحث.",14f))
                } catch(e:Exception) { entries.addView(text("تعذر قراءة طلبات الصيانة: "+e.message,14f)) }
            }
            refresh()
            val watcher=object:android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { refresh() }
                override fun afterTextChanged(s:android.text.Editable?) {}
            }
            search.addTextChangedListener(watcher)
            date.addTextChangedListener(watcher)
        } else parent.addView(text("الأرشيف غير متصل بالبيانات بعد؛ لا يمكن الاستنتاج أن الفواتير غير موجودة.",14f))
    }
    private fun expenseFormPreview(parent:LinearLayout) {
        formHeader(parent,"تسجيل مصروف","يسجل المصروف في التقرير ويُخصم فوراً من الحافظة التي تختارها.","القائمة","preview:المصاريف")
        field(parent,"التاريخ *","YYYY-MM-DD").apply {
            setText(java.time.LocalDate.now().toString())
            isFocusable=false
            isClickable=true
            setOnClickListener {
                val today=runCatching { java.time.LocalDate.parse(text.toString()) }.getOrDefault(java.time.LocalDate.now())
                android.app.DatePickerDialog(this@MainActivity,{ _,y,m,d ->
                    setText(java.time.LocalDate.of(y,m+1,d).toString())
                },today.year,today.monthValue-1,today.dayOfMonth).show()
            }
        }
        field(parent,"الوقت *","HH:MM").apply {
            setText(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
            isFocusable=false
            isClickable=true
            setOnClickListener {
                val now=runCatching { java.time.LocalTime.parse(text.toString()) }.getOrDefault(java.time.LocalTime.now())
                android.app.TimePickerDialog(this@MainActivity,{ _,h,m ->
                    setText(String.format(java.util.Locale.US,"%02d:%02d",h,m))
                },now.hour,now.minute,true).show()
            }
        }
        select(parent,"العملة",listOf("USD","TRY","SYP"))
        field(parent,"المبلغ *").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        select(parent,"دُفع من",listOf("نقدي","شام كاش"))
        select(parent,"نوع المصروف",listOf("إيجار","نقل وشحن","كهرباء وإنترنت","أجور وصيانة","مصروف آخر"))
        field(parent,"التفاصيل","مثال: أجرة نقل أو كهرباء")
        disabledAction(parent,"حفظ وخصم المصروف")
        parent.addView(text("معاينة فقط — لا تُخصم أي مبالغ.",12f))
    }
    private fun supplierPurchasesPreview(parent:LinearLayout) {
        parent.addView(text("المشتريات والموردون",26f,gold))
        parent.addView(text("فاتورة المورد الواحدة يمكن أن تحتوي عدة أصناف. الشراء يزيد المخزون ولا يُسجل كمصروف تشغيلي.",14f))
        disabledAction(parent,"＋ مورد")
        val go=text("＋ فاتورة شراء",17f,gold).apply {
            isClickable=true;setOnClickListener { navigateTo("preview:فاتورة شراء") }
        }
        parent.addView(go)
        listOf("قيمة المخزون بالتكلفة","ديون الموردين","رأس المال المحاسبي التقريبي").forEach { parent.addView(text(it+"    —",16f)) }
        parent.addView(text("مخزون + خزائن + ديون لنا − ديون الموردين",12f))
        parent.addView(text("الموردون",21f,gold))
        parent.addView(text("لم يتم تحميل بيانات الموردين بعد.",14f))
        parent.addView(text("فواتير الشراء",21f,gold))
        parent.addView(text("لم يتم تحميل فواتير الشراء بعد.",14f))
    }
    private fun purchaseInvoicePreview(parent:LinearLayout) {
        formHeader(parent,"فاتورة شراء متعددة الأصناف","أضف كل منتجات المورد في فاتورة واحدة وسيُحسب الإجمالي تلقائياً.","القائمة","preview:المشتريات والموردون")
        field(parent,"التاريخ *","YYYY-MM-DD")
        field(parent,"الوقت *","HH:MM")
        select(parent,"المورد",listOf("اختر المورد"))
        field(parent,"رقم فاتورة المورد")
        select(parent,"عملة الفاتورة",listOf("USD","TRY","SYP"))
        formSection(parent,"أصناف الفاتورة")
        disabledAction(parent,"⚡ إضافة سريعة")
        disabledAction(parent,"＋ إضافة منتج آخر لنفس الفاتورة")
        formSection(parent,"الدفع والإجمالي")
        listOf("إجمالي الفاتورة تلقائياً","المدفوع الآن","المتبقي للمورد").forEach { field(parent,it,"0").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        select(parent,"الدفع من خزنة المحل",listOf("درج المحل","الصندوق الخارجي","شام كاش","الحساب البنكي"))
        field(parent,"ملاحظات")
        parent.addView(text("يمكنك اختيار صنف موجود أو إنشاء منتج/قطعة جديدة من الفاتورة.",12f))
        disabledAction(parent,"حفظ فاتورة الشراء كاملة")
    }
    private fun productsPreview(parent:LinearLayout) {
        parent.addView(text("المنتجات والمخزون",22f,gold).apply { gravity=Gravity.RIGHT })
        val addProduct=text("＋ منتج",16f,gold).apply {
            gravity=Gravity.CENTER_VERTICAL or Gravity.RIGHT
            background=panel(paper,12)
            setPadding(dp(14),dp(8),dp(14),dp(8))
            isClickable=true
            setOnClickListener { newProductPhotos.clear();navigateTo("preview:إضافة منتج جديد") }
        }
        parent.addView(addProduct,LinearLayout.LayoutParams(-1,dp(44)).apply { bottomMargin=dp(6) })
        val search=field(parent,"بحث المنتجات","ابحث باسم المنتج أو الباركود أو REF / Model...").apply {
            isSingleLine=true
            imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        val countLabel=text("",13f)
        parent.addView(countLabel)
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
        parent.addView(list)
        var offset=0
        fun load(reset:Boolean) {
            if(reset) { offset=0;list.removeAllViews() }
            NativeProductChanges.create(legacyBackupStore.writableDatabase)
            val nativeCount=legacyBackupStore.readableDatabase.rawQuery("SELECT COUNT(*) FROM native_new_products n WHERE NOT EXISTS (SELECT 1 FROM native_deleted_products d WHERE d.product_id=n.product_id)",null).use {
                if(it.moveToFirst())it.getInt(0) else 0
            }
            if(!legacyBackupStore.hasData() && nativeCount==0) {
                countLabel.text="لا توجد منتجات محفوظة بعد."
                return
            }
            try {
                val db=legacyBackupStore.readableDatabase
                if(reset) {
                    val count=db.rawQuery("SELECT COUNT(*) FROM domain_products p WHERE NOT EXISTS (SELECT 1 FROM native_deleted_products d WHERE d.product_id=p.id)",null).use {
                        if(it.moveToFirst())it.getInt(0) else 0
                    }
                    countLabel.text="${count+nativeCount} منتج محفوظ."
                }
                val products=LegacyProductRepository.list(db,search.text.toString(),offset,24)
                // Keep product cards full-width on the tablet: two half-width cards
                // squeezed product details and made action buttons hard to use.
                val twoColumns=false
                var cardRow:LinearLayout?=null
                for((cardIndex,p) in products.withIndex()) {
                    if(twoColumns && cardIndex%2==0) {
                        cardRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;gravity=Gravity.TOP }
                        list.addView(cardRow,LinearLayout.LayoutParams(-1,-2))
                    }
                    val row=LinearLayout(this).apply {
                        orientation=LinearLayout.VERTICAL
                        background=panel(paper,14)
                        layoutDirection=View.LAYOUT_DIRECTION_RTL
                        setPadding(dp(14),dp(12),dp(14),dp(12))
                    }
                    val header=LinearLayout(this).apply {
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                        layoutDirection=View.LAYOUT_DIRECTION_RTL
                    }
                    val photo=ProductImageView.create(this,p.raw.optString("photo"),dp(84),dp(84))
                    photo.setOnClickListener {
                        ProductImageView.show(this@MainActivity,p.raw.optString("photo"),p.name)
                    }
                    header.addView(photo,LinearLayout.LayoutParams(dp(84),dp(84)))
                    val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                    labels.addView(text(p.name,16f).apply { gravity=Gravity.RIGHT })
                    labels.addView(text(p.raw.optString("brand")+" · "+p.raw.optString("model"),12f).apply { gravity=Gravity.RIGHT })
                    header.addView(labels,LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=dp(12) })
                    row.addView(header)
                    val stock=p.stock
                    // Reserved/available quantities are not shown until the legacy reservation logic is ported.
                    row.addView(text("المخزون الفعلي: $stock    سعر البيع: ${p.salePrice} ${p.currency}",13f).apply { gravity=Gravity.RIGHT })
                    row.addView(text("كلفة الشراء للقطعة: ${p.cost} ${p.currency}",13f).apply { gravity=Gravity.RIGHT })
                    row.addView(text("الباركود: "+p.barcode.ifBlank { "—" },12f).apply { gravity=Gravity.RIGHT })
                    val extraPhotos=listOf("photo2","photo3").mapNotNull { key ->
                        p.raw.optString(key).takeIf { it.isNotBlank() }?.let { key to it }
                    }
                    if(extraPhotos.isNotEmpty()) {
                        val photos=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
                        extraPhotos.forEachIndexed { index,pair ->
                            photos.addView(ProductImageView.create(this,pair.second,dp(76),dp(76)).apply {
                                setOnClickListener { ProductImageView.show(this@MainActivity,pair.second,p.name) }
                            },LinearLayout.LayoutParams(dp(76),dp(76)).apply { marginEnd=dp(8) })
                        }
                        row.addView(photos)
                    }
                    if(stock<=LegacyAccountingRules.number(p.raw.opt("minStock")))
                        row.addView(text("تنبيه: المخزون وصل للحد الأدنى",12f,Color.RED))
                    val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;gravity=Gravity.RIGHT }
                    val edit=Button(this).apply {
                        text="تعديل"
                        setOnClickListener { newProductPhotos.clear();selectedInventoryProduct=p.id;navigateTo("native:product-edit") }
                    }
                    actions.addView(edit)
                    if(p.variantGroupId.isNotBlank())actions.addView(Button(this).apply {
                        text="تعديل ألوان المنتج"
                        setOnClickListener { newProductPhotos.clear();selectedInventoryProduct=p.id;navigateTo("native:group-edit") }
                    })
                    actions.addView(Button(this).apply {
                        text="تسوية المخزون"
                        setOnClickListener { selectedInventoryProduct=p.id;navigateTo("native:stock-adjustment") }
                    })
                    actions.addView(Button(this).apply {
                        text="حذف المنتج"
                        setOnClickListener {
                            val reason=EditText(this@MainActivity).apply {
                                hint="سبب الحذف"
                                typeface=legacyTypeface
                            }
                            android.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("حذف المنتج — "+p.name)
                                .setMessage("يُخفى المنتج من المخزون دون حذف سجل الفواتير. يجب أن تكون كميته صفراً.")
                                .setView(reason)
                                .setNegativeButton("إلغاء",null)
                                .setPositiveButton("تأكيد الحذف") { _,_ ->
                                    try {
                                        NativeProductChanges.deleteProduct(
                                            legacyBackupStore.writableDatabase,p.id,reason.text.toString().trim())
                                        load(true)
                                    } catch(e:Exception) {
                                        android.app.AlertDialog.Builder(this@MainActivity)
                                            .setMessage(e.message ?: "تعذر حذف المنتج")
                                            .setPositiveButton("حسناً",null).show()
                                    }
                                }.show()
                        }
                    })
                    actions.addView(Button(this).apply {
                        text="طباعة باركود"
                        setOnClickListener {
                            try { BarcodeLabelPrinter.print(this@MainActivity,p.raw) }
                            catch(e:Exception) {
                                android.app.AlertDialog.Builder(this@MainActivity)
                                    .setMessage(e.message ?: "تعذرت طباعة الباركود")
                                    .setPositiveButton("حسناً",null).show()
                            }
                        }
                    })
                    val barcodePhoto=p.raw.optString("barcodePhoto")
                    if(barcodePhoto.isNotBlank())actions.addView(Button(this).apply {
                        text="صورة الباركود"
                        setOnClickListener { ProductImageView.show(this@MainActivity,barcodePhoto,"صورة الباركود") }
                    })
                    val actionScroller=android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false;layoutDirection=View.LAYOUT_DIRECTION_RTL }
                    for(i in 0 until actions.childCount) (actions.getChildAt(i) as? Button)?.apply {
                        typeface=legacyTypeface
                        textSize=11f
                        isAllCaps=false
                        minWidth=0
                        minimumWidth=0
                        setPadding(dp(7),dp(5),dp(7),dp(5))
                    }
                    actionScroller.addView(actions)
                    row.addView(actionScroller,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
                    if(twoColumns) {
                        cardRow?.addView(row,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(6);bottomMargin=dp(8) })
                        if(cardIndex==products.lastIndex && cardIndex%2==0)
                            cardRow?.addView(View(this),LinearLayout.LayoutParams(0,dp(1),1f))
                    } else list.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
                }
                if(reset && products.isEmpty()) {
                    list.addView(text("لا توجد منتجات مطابقة للبحث",13f).apply { gravity=Gravity.CENTER })
                }
                offset+=products.size
            } catch(e:Exception) {
                list.addView(text(e.message ?: "تعذر تحميل المنتجات",13f))
            }
        }
        // Debounce searches: querying SQLite and rebuilding product cards on each
        // keystroke blocks the UI thread, especially with a large legacy backup.
        val searchHandler=android.os.Handler(android.os.Looper.getMainLooper())
        var pendingSearch:Runnable?=null
        search.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                pendingSearch?.let { searchHandler.removeCallbacks(it) }
                val task=Runnable {
                    if(search.isAttachedToWindow)load(true)
                }
                pendingSearch=task
                searchHandler.postDelayed(task,180L)
            }
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        parent.addView(Button(this).apply { text="عرض المزيد";setOnClickListener { load(false) } })
        load(true)
    }
    private var selectedInventoryProduct:String?=null
    private fun productCreatePage(parent:LinearLayout) {
        compactProductCreationForm=true
        parent.addView(text("إضافة منتج جديد",20f,gold))
        lateinit var name:EditText
        lateinit var brand:EditText
        formPair(parent,{ name=field(it,"اسم الساعة / المنتج *") },
            { brand=field(it,"الماركة") })
        lateinit var model:EditText
        lateinit var sku:EditText
        formPair(parent,{ model=field(it,"REF / الموديل") },
            { sku=field(it,"كود المنتج") })
        val barcode=field(parent,"باركود المنتج")
        barcodeScanButton(parent,barcode)
        val stock=field(parent,"الكمية بالمخزون *").apply {
            setText("1");inputType=InputType.TYPE_CLASS_NUMBER
        }
        val currency=select(parent,"عملة الأسعار",listOf("USD","TRY","SYP"))
        lateinit var sale:EditText
        lateinit var cost:EditText
        formPair(parent,{ sale=field(it,"سعر البيع *").apply {
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        } },{ cost=field(it,"كلفة الشراء للقطعة").apply {
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        } })
        lateinit var color:EditText
        lateinit var dial:EditText
        formPair(parent,{ color=field(it,"اللون") },{ dial=field(it,"لون المينا") })
        val strap=field(parent,"لون الحزام")
        val details=linkedMapOf<String,Spinner>()
        listOf(
            "watchType" to "نوع الساعة",
            "gender" to "الجنس",
            "movement" to "نوع الحركة",
            "displayType" to "نوع العرض",
            "diameter" to "قطر الساعة",
            "caseMaterial" to "مادة الهيكل",
            "strapMaterial" to "مادة الحزام",
            "glassType" to "نوع الزجاج",
            "waterResistance" to "مقاومة الماء",
            "warrantyMonths" to "مدة الكفالة"
        ).chunked(2).forEach { pair ->
            formPair(parent,
                { host ->
                    val (key,label)=pair[0]
                    details[key]=select(host,label,listOf("غير محدد")+originalWatchDetails[key].orEmpty())
                },
                { host ->
                    if(pair.size>1) {
                        val (key,label)=pair[1]
                        details[key]=select(host,label,listOf("غير محدد")+originalWatchDetails[key].orEmpty())
                    }
                }
            )
        }
        lateinit var description:EditText
        lateinit var specs:EditText
        formPair(parent,{ description=field(it,"وصف الساعة") },{ specs=field(it,"مواصفات إضافية") })
        val notes=field(parent,"ملاحظات داخلية")
        val minStock=field(parent,"حد التنبيه الأدنى").apply {
            setText("1");inputType=InputType.TYPE_CLASS_NUMBER
        }
        val imageKeys=listOf("photo" to "الصورة الرئيسية للساعة",
            "photo2" to "صورة إضافية 1","photo3" to "صورة إضافية 2",
            "barcodePhoto" to "صورة الباركود")
        val imageGrid=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
        }
        parent.addView(imageGrid)
        imageKeys.chunked(2).forEach { pair ->
            val imageRow=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                layoutDirection=View.LAYOUT_DIRECTION_RTL
                gravity=Gravity.TOP
            }
            imageGrid.addView(imageRow,LinearLayout.LayoutParams(-1,-2))
            pair.forEach { (key,label) ->
                val tile=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                    background=panel(paper,12)
                    setPadding(dp(8),dp(8),dp(8),dp(8))
                }
                tile.addView(text(label,13f).apply { gravity=Gravity.RIGHT })
                val preview=ProductImageView.create(this,newProductPhotos[key].orEmpty(),dp(78),dp(78))
                tile.addView(preview,LinearLayout.LayoutParams(dp(78),dp(78)))
                preview.setOnClickListener { ProductImageView.show(this,newProductPhotos[key].orEmpty(),label) }
                val controls=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                }
                controls.addView(Button(this).apply {
                    text="الكاميرا";textSize=11f
                    setOnClickListener { photoPreview=preview;selectProductPhoto(key,true) }
                },LinearLayout.LayoutParams(0,-2,1f))
                controls.addView(Button(this).apply {
                    text="الاستوديو";textSize=11f
                    setOnClickListener { photoPreview=preview;selectProductPhoto(key,false) }
                },LinearLayout.LayoutParams(0,-2,1f))
                controls.addView(Button(this).apply {
                    text="حذف الصورة";textSize=11f
                    setOnClickListener { newProductPhotos.remove(key);preview.setImageDrawable(null) }
                },LinearLayout.LayoutParams(0,-2,1f))
                tile.addView(controls)
                imageRow.addView(tile,LinearLayout.LayoutParams(0,-2,1f).apply {
                    marginEnd=dp(6);bottomMargin=dp(8)
                })
            }
        }
        data class VariantDraft(val color:EditText,val qty:EditText,val price:EditText,
            val cost:EditText,val barcode:EditText,val photoKey:String)
        val variantDrafts=mutableListOf<VariantDraft>()
        val variantContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(variantContainer)
        parent.addView(Button(this).apply {
            text="＋ إضافة لون آخر"
            setOnClickListener {
                val card=LinearLayout(this@MainActivity).apply {
                    orientation=LinearLayout.VERTICAL
                    background=panel(paper,12)
                }
                val c=field(card,"اللون العام الإضافي *")
                val q=field(card,"الكمية *").apply { setText("1");inputType=InputType.TYPE_CLASS_NUMBER }
                val pr=field(card,"سعر بيع الإضافي").apply {
                    inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                val co=field(card,"كلفة شراء الإضافي").apply {
                    inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                val bc=field(card,"باركود اللون")
                barcodeScanButton(card,bc)
                val key="variant-photo-"+java.util.UUID.randomUUID().toString()
                val preview=ProductImageView.create(this@MainActivity,"",dp(84),dp(84))
                card.addView(preview,LinearLayout.LayoutParams(dp(84),dp(84)))
                card.addView(Button(this@MainActivity).apply {
                    text="صورة اللون من الاستوديو"
                    setOnClickListener { photoPreview=preview;selectProductPhoto(key,false) }
                })
                card.addView(Button(this@MainActivity).apply {
                    text="تصوير اللون"
                    setOnClickListener { photoPreview=preview;selectProductPhoto(key,true) }
                })
                val draft=VariantDraft(c,q,pr,co,bc,key)
                variantDrafts.add(draft)
                card.addView(Button(this@MainActivity).apply {
                    text="حذف اللون"
                    setOnClickListener { variantDrafts.remove(draft);variantContainer.removeView(card);newProductPhotos.remove(key) }
                })
                variantContainer.addView(card)
            }
        })
        parent.addView(Button(this).apply {
            text="حفظ المنتج"
            setOnClickListener {
                try {
                    val qty=stock.text.toString().toIntOrNull()
                    require(qty!=null && qty>=0){"الكمية غير صالحة"}
                    val price=sale.text.toString().toDoubleOrNull()
                    val unitCost=cost.text.toString().toDoubleOrNull() ?: 0.0
                    require(price!=null && price.isFinite() && price>=0){"سعر البيع غير صالح"}
                    require(unitCost.isFinite() && unitCost>=0){"الكلفة غير صالحة"}
                    val id=java.util.UUID.randomUUID().toString()
                    val now=System.currentTimeMillis()
                    val funding=org.json.JSONArray()
                    if(qty>0)funding.put(org.json.JSONObject().apply {
                        put("id",java.util.UUID.randomUUID().toString())
                        put("purchaseId","")
                        put("source","shop")
                        put("qtyOriginal",qty)
                        put("qtyRemaining",qty)
                        put("unitCost",unitCost)
                        put("currency",currency.selectedItem.toString())
                        put("createdAt",now)
                    })
                    val product=org.json.JSONObject().apply {
                        put("id",id);put("name",name.text.toString().trim())
                        put("brand",brand.text.toString().trim())
                        put("model",model.text.toString().trim())
                        put("sku",sku.text.toString().trim())
                        put("barcode",barcode.text.toString().trim())
                        put("stock",qty);put("currency",currency.selectedItem.toString())
                        put("salePrice",price);put("cost",unitCost)
                        put("color",color.text.toString().trim())
                        put("dialColor",dial.text.toString().trim())
                        put("strapColor",strap.text.toString().trim())
                        put("fundingLots",funding);put("createdAt",now)
                        put("description",description.text.toString())
                        put("specs",specs.text.toString())
                        put("notes",notes.text.toString())
                        put("minStock",minStock.text.toString().toIntOrNull() ?: 1)
                        for((key,spinner) in details) {
                            val value=spinner.selectedItem.toString()
                            if(value!="غير محدد")put(key,if(key=="warrantyMonths")value.toIntOrNull() ?: 0 else value)
                        }
                        for(key in listOf("photo","photo2","photo3","barcodePhoto")) {
                            newProductPhotos[key]?.let { put(key,it) }
                        }
                    }
                    val all=mutableListOf(product)
                    if(variantDrafts.isNotEmpty()) {
                        val groupId=java.util.UUID.randomUUID().toString()
                        product.put("variantGroupId",groupId)
                        product.put("variantRole","base")
                        product.put("variantOrder",0)
                        product.put("variantColor",color.text.toString().trim())
                        product.put("variantKey",LegacyProductRules.variantIdentity(
                            color.text.toString(),color.text.toString(),dial.text.toString(),strap.text.toString()))
                        variantDrafts.forEachIndexed { index,v ->
                            val vColor=v.color.text.toString().trim()
                            require(vColor.isNotBlank()){"اسم اللون الإضافي مطلوب"}
                            val vQty=v.qty.text.toString().toIntOrNull()
                            require(vQty!=null && vQty>=0){"كمية اللون غير صالحة"}
                            val vPrice=v.price.text.toString().toDoubleOrNull() ?: price
                            val vCost=v.cost.text.toString().toDoubleOrNull() ?: unitCost
                            require(vPrice.isFinite() && vPrice>=0 && vCost.isFinite() && vCost>=0){"السعر أو الكلفة غير صالح"}
                            val vId=java.util.UUID.randomUUID().toString()
                            val vFunding=org.json.JSONArray()
                            if(vQty>0)vFunding.put(org.json.JSONObject().apply {
                                put("id",java.util.UUID.randomUUID().toString())
                                put("source","shop");put("purchaseId","")
                                put("qtyOriginal",vQty);put("qtyRemaining",vQty)
                                put("unitCost",vCost);put("currency",currency.selectedItem.toString())
                                put("createdAt",now)
                            })
                            val variant=org.json.JSONObject(product.toString()).apply {
                                put("id",vId);put("name",LegacyProductRules.variantName(
                                    name.text.toString().trim(),vColor,vColor,"",""))
                                put("color",vColor);put("variantColor",vColor)
                                put("dialColor","");put("strapColor","")
                                put("variantGroupId",groupId);put("variantRole","variant")
                                put("variantOrder",index+1)
                                put("variantKey",LegacyProductRules.variantIdentity(vColor,vColor,"",""))
                                put("stock",vQty);put("salePrice",vPrice);put("cost",vCost)
                                put("barcode",v.barcode.text.toString().trim())
                                put("fundingLots",vFunding)
                                if(newProductPhotos.containsKey(v.photoKey))put("photo",newProductPhotos.getValue(v.photoKey))
                                else put("photo",product.optString("photo"))
                            }
                            all.add(variant)
                        }
                    }
                    NativeProductChanges.createProducts(legacyBackupStore.writableDatabase,all)
                    newProductPhotos.clear()
                    android.widget.Toast.makeText(this@MainActivity,"تم حفظ المنتج",android.widget.Toast.LENGTH_SHORT).show()
                    returnToPrevious()
                } catch(e:Exception) {
                    android.app.AlertDialog.Builder(this@MainActivity).setTitle("لم يُحفظ المنتج")
                        .setMessage(e.message ?: "تعذر الحفظ").setPositiveButton("حسناً",null).show()
                }
            }
        })
        compactProductCreationForm=false
    }
    private fun productGroupEditPage(parent:LinearLayout) {
        val id=selectedInventoryProduct ?: return
        val db=legacyBackupStore.writableDatabase
        val current=NativeProductChanges.get(db,id) ?: return
        val groupId=current.optString("variantGroupId")
        if(groupId.isBlank()) {
            inventoryEditPage(parent)
            return
        }
        val members=mutableListOf<org.json.JSONObject>()
        db.rawQuery("SELECT p.id FROM domain_products p WHERE p.variantGroupId=? UNION SELECT n.product_id FROM native_new_products n WHERE json_extract(n.payload,'$.variantGroupId')=?",
            arrayOf(groupId,groupId)).use { c ->
            while(c.moveToNext()) {
                val memberId=c.getString(0)
                if(!NativeProductChanges.isDeleted(db,memberId))
                    NativeProductChanges.get(db,memberId)?.let { members.add(it) }
            }
        }
        parent.addView(text("تعديل ألوان المنتج",22f,gold))
        parent.addView(text("لكل لون صورة وسعر وتكلفة وباركود مستقل. تسوية الكميات تتم من صفحة المخزون.",13f))
        data class Entry(val original:org.json.JSONObject,val color:EditText,val barcode:EditText,
            val sale:EditText,val cost:EditText,val photoKey:String)
        val entries=mutableListOf<Entry>()
        for(member in members) {
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                background=panel(paper,12)
                setPadding(dp(12),dp(12),dp(12),dp(12))
            }
            val key="group-"+member.optString("id")
            card.addView(text(member.optString("name"),17f,gold))
            val color=field(card,"اللون العام").apply {
                setText(member.optString("variantColor").ifBlank { member.optString("color") })
            }
            val barcode=field(card,"باركود اللون").apply { setText(member.optString("barcode")) }
            barcodeScanButton(card,barcode)
            val sale=field(card,"سعر البيع").apply {
                setText(member.optString("salePrice"))
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            val cost=field(card,"كلفة الشراء للقطعة").apply {
                setText(member.optString("cost"))
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            card.addView(text("المخزون الفعلي: "+LegacyProductRules.stock(member),13f))
            val preview=ProductImageView.create(this,member.optString("photo"),dp(92),dp(92))
            card.addView(preview,LinearLayout.LayoutParams(dp(92),dp(92)))
            preview.setOnClickListener { ProductImageView.show(this,newProductPhotos[key] ?: member.optString("photo"),member.optString("name")) }
            card.addView(Button(this).apply {
                text="اختيار صورة من الاستوديو"
                setOnClickListener { photoPreview=preview;selectProductPhoto(key,false) }
            })
            card.addView(Button(this).apply {
                text="الكاميرا"
                setOnClickListener { photoPreview=preview;selectProductPhoto(key,true) }
            })
            entries.add(Entry(member,color,barcode,sale,cost,key))
            parent.addView(card)
        }
        parent.addView(Button(this).apply {
            text="حفظ ألوان المنتج"
            setOnClickListener {
                try {
                    val changes=entries.map { entry ->
                        val newColor=entry.color.text.toString().trim()
                        val newSale=entry.sale.text.toString().toDoubleOrNull()
                        val newCost=entry.cost.text.toString().toDoubleOrNull()
                        require(newColor.isNotBlank() && newSale!=null && newSale.isFinite() &&
                            newSale>=0 && newCost!=null && newCost.isFinite() && newCost>=0) {
                            "تحقق من اللون والسعر والتكلفة"
                        }
                        org.json.JSONObject(entry.original.toString()).apply {
                            put("variantColor",newColor)
                            put("color",newColor)
                            put("variantKey",LegacyProductRules.variantIdentity(newColor,newColor,
                                optString("dialColor"),optString("strapColor")))
                            put("barcode",entry.barcode.text.toString().trim())
                            put("salePrice",newSale)
                            put("cost",newCost)
                            newProductPhotos[entry.photoKey]?.let { put("photo",it) }
                        }
                    }
                    NativeProductChanges.saveColorGroup(db,changes,"تعديل ألوان المنتج")
                    newProductPhotos.clear()
                    android.widget.Toast.makeText(this@MainActivity,"تم حفظ الألوان",android.widget.Toast.LENGTH_SHORT).show()
                    returnToPrevious()
                } catch(e:Exception) {
                    android.app.AlertDialog.Builder(this@MainActivity).setTitle("لم تحفظ الألوان")
                        .setMessage(e.message ?: "تعذر الحفظ").setPositiveButton("حسناً",null).show()
                }
            }
        })
    }
    private fun inventoryEditPage(parent:LinearLayout) {
        val id=selectedInventoryProduct ?: return
        val db=legacyBackupStore.writableDatabase
        val product=NativeProductChanges.get(db,id) ?: return
        parent.addView(text("تعديل",23f,gold))
        val name=field(parent,"اسم الساعة / المنتج *").apply { setText(product.optString("name")) }
        val brand=field(parent,"الماركة").apply { setText(product.optString("brand")) }
        val model=field(parent,"REF / الموديل").apply { setText(product.optString("model")) }
        val sku=field(parent,"كود المنتج").apply { setText(product.optString("sku")) }
        val color=field(parent,"اللون").apply { setText(product.optString("color")) }
        val dialColor=field(parent,"لون المينا").apply { setText(product.optString("dialColor")) }
        val strapColor=field(parent,"لون الحزام").apply { setText(product.optString("strapColor")) }
        val variantColor=field(parent,"لون النسخة").apply { setText(product.optString("variantColor")) }
        val barcode=field(parent,"باركود المنتج").apply { setText(product.optString("barcode")) }
        barcodeScanButton(parent,barcode)
        val cost=field(parent,"كلفة الشراء للقطعة").apply {
            setText(product.optString("cost"))
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val price=field(parent,"سعر البيع *").apply {
            setText(product.optString("salePrice"))
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val editPhotoKeys=listOf("photo" to "الصورة الرئيسية للساعة",
            "photo2" to "صورة إضافية 1","photo3" to "صورة إضافية 2",
            "barcodePhoto" to "صورة الباركود")
        for((key,label) in editPhotoKeys) {
            parent.addView(text(label,13f))
            val preview=ProductImageView.create(this,product.optString(key),dp(90),dp(90))
            parent.addView(preview,LinearLayout.LayoutParams(dp(90),dp(90)))
            preview.setOnClickListener { ProductImageView.show(this,newProductPhotos[key] ?: product.optString(key),label) }
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            row.addView(Button(this).apply {
                text="الكاميرا"
                setOnClickListener { photoPreview=preview;selectProductPhoto(key,true) }
            })
            row.addView(Button(this).apply {
                text="الاستوديو"
                setOnClickListener { photoPreview=preview;selectProductPhoto(key,false) }
            })
            row.addView(Button(this).apply {
                text="حذف الصورة"
                setOnClickListener { newProductPhotos[key]="";preview.setImageDrawable(null) }
            })
            parent.addView(row)
        }
        parent.addView(text("المخزون الفعلي: "+LegacyProductRules.stock(product),13f))
        parent.addView(Button(this).apply {
            text="حفظ"
            setOnClickListener {
                try {
                    val amount=price.text.toString().toDoubleOrNull()
                    require(amount!=null && amount.isFinite() && amount>=0){"سعر البيع غير صالح"}
                    val costAmount=cost.text.toString().toDoubleOrNull()
                    require(costAmount!=null && costAmount.isFinite() && costAmount>=0){"كلفة الشراء غير صالحة"}
                    val candidate=org.json.JSONObject(product.toString()).apply {
                        put("name",name.text.toString().trim())
                        put("brand",brand.text.toString().trim())
                        put("model",model.text.toString().trim())
                        put("sku",sku.text.toString().trim())
                        for((key,value) in newProductPhotos)if(key in listOf("photo","photo2","photo3","barcodePhoto"))put(key,value)
                        put("color",color.text.toString().trim())
                        put("dialColor",dialColor.text.toString().trim())
                        put("strapColor",strapColor.text.toString().trim())
                        if(optString("variantGroupId").isNotBlank()) {
                            put("variantColor",variantColor.text.toString().trim())
                            put("variantKey",LegacyProductRules.variantIdentity(
                                variantColor.text.toString(),color.text.toString(),
                                dialColor.text.toString(),strapColor.text.toString()))
                        }
                        put("barcode",barcode.text.toString().trim())
                        put("salePrice",amount)
                        put("cost",costAmount)
                    }
                    NativeProductChanges.save(db,candidate,"تعديل بيانات المنتج")
                    newProductPhotos.clear()
                    android.widget.Toast.makeText(this@MainActivity,"تم الحفظ",android.widget.Toast.LENGTH_SHORT).show()
                    returnToPrevious()
                } catch(e:Exception) {
                    android.app.AlertDialog.Builder(this@MainActivity).setMessage(e.message ?: "تعذر الحفظ")
                        .setPositiveButton("حسناً",null).show()
                }
            }
        })
    }
    private fun inventoryAdjustmentPage(parent:LinearLayout) {
        val id=selectedInventoryProduct ?: return
        val db=legacyBackupStore.writableDatabase
        val product=NativeProductChanges.get(db,id) ?: return
        parent.addView(text("تسوية المخزون — "+product.optString("name"),22f,gold))
        val effectiveStock=LegacyProductRules.stock(product)+NativeSalesLedger.stockDelta(db,id)
        parent.addView(text("الكمية المسجلة: "+effectiveStock,14f))
        val actual=field(parent,"الكمية الفعلية في المحل *").apply {
            setText(effectiveStock.toString())
            inputType=InputType.TYPE_CLASS_NUMBER
        }
        val reason=select(parent,"سبب التسوية *",listOf(
            "جرد فعلي","خطأ إدخال سابق","تلف أو فقد","تصحيح بعد مراجعة المخزون"))
        val notes=field(parent,"ملاحظة")
        parent.addView(text("هذه العملية لا تعدّل أو تحذف فواتير البيع والشراء.",12f))
        val previousAdjustments=NativeProductChanges.stockAdjustmentHistory(db,id)
        if(previousAdjustments.isNotEmpty()) {
            parent.addView(text("سجل التسويات",16f,gold))
            previousAdjustments.take(20).forEach { movement ->
                parent.addView(text("${movement.before.toInt()} ← ${movement.after.toInt()} · ${movement.reason}",12f))
            }
        }
        parent.addView(Button(this).apply {
            text="تأكيد التسوية"
            setOnClickListener {
                try {
                    val qty=actual.text.toString().toIntOrNull()
                        ?: throw IllegalArgumentException("الكمية الفعلية غير صالحة")
                    NativeProductChanges.adjustStock(db,id,qty,reason.selectedItem.toString(),notes.text.toString())
                    android.widget.Toast.makeText(this@MainActivity,"تمت تسوية المخزون",android.widget.Toast.LENGTH_SHORT).show()
                    returnToPrevious()
                } catch(e:Exception) {
                    android.app.AlertDialog.Builder(this@MainActivity).setTitle("لم تحفظ التسوية")
                        .setMessage(e.message ?: "تعذر الحفظ").setPositiveButton("حسناً",null).show()
                }
            }
        })
    }
    // v3.9.359 assets/main.js: WATCH_DETAIL_DEFAULTS (same order and labels).
    private val originalWatchDetails = mapOf(
        "watchType" to listOf("عادية","إسلامية","ذكية","رياضية"),
        "gender" to listOf("رجالي","نسائي","أطفال","للجنسين"),
        "color" to listOf("أسود","فضي","ذهبي","ذهبي وردي","أبيض","أزرق","أخضر","أحمر","بني","رمادي","بيج","وردي","بنفسجي","متعدد الألوان"),
        "dialColor" to listOf("أسود","أبيض","فضي","ذهبي","شمبانيا","أزرق","أخضر","أحمر","بني","رمادي","بيج","وردي","بنفسجي","متعدد الألوان"),
        "movement" to listOf("كوارتز","أوتوماتيك","ميكانيكية","سولار","رقمية","ذكية"),
        "displayType" to listOf("عقارب","رقمية","هجينة"),
        "diameter" to listOf("أقل من 30 mm","30 mm","32 mm","34 mm","36 mm","38 mm","40 mm","42 mm","44 mm","46 mm","48 mm","50 mm","أكبر من 50 mm"),
        "caseMaterial" to listOf("ستانلس ستيل","راتنج","بلاستيك","تيتانيوم","معدن","ألمنيوم","سيراميك","مزيج مواد"),
        "strapMaterial" to listOf("ستانلس ستيل","جلد","سيليكون","راتنج","مطاط","قماش","نايلون","تيتانيوم","سيراميك","معدن"),
        "strapColor" to listOf("أسود","فضي","ذهبي","ذهبي وردي","أبيض","أزرق","أخضر","أحمر","بني","رمادي","بيج","وردي","بنفسجي","متعدد الألوان"),
        "glassType" to listOf("معدني","سافاير","أكريليك","ريزن","زجاج مقوى","Hardlex"),
        "waterResistance" to listOf("غير مقاومة للماء","مقاومة للرذاذ","30 متر / 3 ATM","50 متر / 5 ATM","100 متر / 10 ATM","200 متر / 20 ATM","300 متر / 30 ATM"),
        "warrantyMonths" to listOf("0","3","6","12","18","24","36","60")
    )

    private fun productHelp(parent:LinearLayout,message:String) {
        parent.addView(text(message,11f,Color.rgb(138,123,108)).apply {
            gravity=Gravity.RIGHT
            setPadding(0,dp(4),0,dp(10))
        })
    }
    private fun previewOnly() {
        android.widget.Toast.makeText(this,"هذه الوظيفة غير مفعلة في معاينة Native 7",android.widget.Toast.LENGTH_SHORT).show()
    }
    /** Original photoPicker: preview beside camera/gallery/clear, not three full-width rows. */
    private fun productPhotoPicker(parent:LinearLayout,label:String,help:String,
        empty:String="لا توجد صورة",camera:String="📷 الكاميرا") {
        parent.addView(text(label,11f,Color.rgb(138,123,108)).apply { gravity=Gravity.RIGHT })
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
        }
        row.addView(text(empty,11f,Color.rgb(138,123,108)).apply {
            background=panel(Color.rgb(255,250,242),12)
        },LinearLayout.LayoutParams(dp(116),dp(84)).apply { marginEnd=dp(12) })
        val actions=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        listOf(camera,"🖼️ اختيار من الاستوديو","حذف الصورة").forEach { disabledAction(actions,it) }
        row.addView(actions,LinearLayout.LayoutParams(0,-2,1f))
        parent.addView(row,LinearLayout.LayoutParams(-1,-2))
        if(help.isNotEmpty()) productHelp(parent,help)
    }
    private fun originalDetailChoice(parent:LinearLayout,label:String,key:String,variant:Boolean=false):Spinner {
        val values=mutableListOf(if(variant) "اختر اللون" else "غير محدد")
        values.addAll(originalWatchDetails.getValue(key).map {
            if(key=="warrantyMonths") { if(it=="0") "حسب الفاتورة" else "$it شهر" } else it
        })
        val addLabel=if(variant) "＋ إضافة لون جديد…" else "＋ إضافة خيار جديد…"
        values.add(addLabel)
        val spinner=select(parent,label,values)
        if(key=="warrantyMonths") spinner.setSelection(1)
        var previous=if(key=="warrantyMonths") 1 else 0
        spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
                if(position!=values.lastIndex) { previous=position;return }
                val input=EditText(this@MainActivity).apply {
                    typeface=legacyTypeface
                    if(key=="warrantyMonths") inputType=InputType.TYPE_CLASS_NUMBER
                }
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("أدخل خياراً جديداً لـ $label")
                    .setView(input)
                    .setNegativeButton("إلغاء") { _,_ -> spinner.setSelection(previous) }
                    .setPositiveButton("إضافة") { _,_ ->
                        val value=input.text.toString().trim()
                        if(value.isNotEmpty()) {
                            val display=if(key=="warrantyMonths") "$value شهر" else value
                            var index=values.indexOf(display)
                            if(index<0) {
                                index=values.lastIndex
                                values.add(index,display)
                                (spinner.adapter as ArrayAdapter<*>).notifyDataSetChanged()
                            }
                            spinner.setSelection(index)
                        } else spinner.setSelection(previous)
                    }
                    .setOnCancelListener { spinner.setSelection(previous) }.show()
            }
        }
        return spinner
    }
    private fun productProfileChoices(parent:LinearLayout,label:String,values:List<String>) {
        parent.addView(text(label,11f,Color.rgb(138,123,108)).apply { gravity=Gravity.RIGHT })
        val columns=if(resources.configuration.screenWidthDp>=720) 4 else 2
        values.chunked(columns).forEach { chunk ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
            chunk.forEachIndexed { index,value ->
                row.addView(android.widget.CheckBox(this).apply {
                    text=value;typeface=legacyTypeface;textSize=12f;setTextColor(ink)
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                    background=panel(Color.rgb(255,252,247),10)
                    setPadding(dp(5),dp(6),dp(5),dp(6))
                },LinearLayout.LayoutParams(0,-2,1f).apply { if(index<chunk.lastIndex) marginEnd=dp(7) })
            }
            repeat(columns-chunk.size) { row.addView(View(this),LinearLayout.LayoutParams(0,1,1f)) }
            parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(7) })
        }
    }
    private fun productVariantPreview(parent:LinearLayout) {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL
            background=panel(paper,10);setPadding(dp(8),dp(8),dp(8),dp(8))
        }
        parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8);bottomMargin=dp(8) })
        formPair(card,{ originalDetailChoice(it,"اللون العام الإضافي *","color",true) },{
            field(it,"الكمية *").apply { inputType=InputType.TYPE_CLASS_NUMBER;setText("1") }
        })
        field(card,"باركود اللون","اختياري الآن").inputType=InputType.TYPE_CLASS_NUMBER
        card.addView(text("حذف",12f,Color.rgb(184,88,75)).apply {
            background=panel(Color.rgb(250,241,227),7)
            isClickable=true;setOnClickListener { parent.removeView(card) }
        },LinearLayout.LayoutParams(-2,dp(36)))
        formPair(card,{ originalDetailChoice(it,"لون الهيكل","color") },{ originalDetailChoice(it,"لون المينا","dialColor") })
        formPair(card,{ originalDetailChoice(it,"لون الحزام","strapColor") },{
            field(it,"سعر بيع الإضافي","فارغ = سعر الأساسي").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        })
        field(card,"كلفة شراء الإضافي","فارغ = كلفة الأساسي").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        productPhotoPicker(card,"صورة هذا اللون (اختيارية)","إذا تركتها فارغة سيستخدم التطبيق صورة الموديل الرئيسية تلقائياً.",
            "لا توجد صورة خاصة لهذا اللون","📷 تصوير اللون")
        productPhotoPicker(card,"صورة إضافية لهذا اللون (اختيارية)","","لا توجد صورة إضافية لهذا اللون","📷 تصوير صورة إضافية")
    }
    private fun productFormPreview(parent:LinearLayout) {
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
        header.addView(text("إضافة منتج جديد",18f).apply { gravity=Gravity.RIGHT;typeface=Typeface.create(typeface,Typeface.BOLD) },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text("×",19f,Color.rgb(122,81,24)).apply {
            background=panel(Color.rgb(247,237,221),10)
            contentDescription="إغلاق"
            isClickable=true;setOnClickListener { returnToPrevious() }
        },LinearLayout.LayoutParams(dp(36),dp(36)))
        parent.addView(header,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) })
        lateinit var brand:Spinner
        lateinit var customBrand:LinearLayout
        lateinit var casio:LinearLayout
        formPair(parent,{ field(it,"اسم الساعة / المنتج *") },{ column ->
            brand=select(column,"الماركة",listOf("اختر الماركة","فيترون","SKMEI","كاسيو","فوسيل","تومي هيلفيغر","سيتيزن","بوس","بوليس","سيروتي 1881","إمبوريو أرماني","أرماني إكستشينج","مايكل كورس","لاكوست","تيمبرلاند","كالفن كلاين","＋ إنشاء ماركة جديدة"))
            customBrand=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
            field(customBrand,"","اكتب اسم الماركة الجديدة")
            disabledAction(customBrand,"حفظ الماركة")
            column.addView(customBrand)
        })
        formPair(parent,{
            field(it,"REF / الموديل (اختياري عند استخدام الرابط)","يُستخرج تلقائياً من صفحة المنتج عند توفره")
        },{
            casio=it
            select(it,"فئة ساعة كاسيو",listOf("تلقائي من REF","كاسيو","Edifice","G-Shock","Baby-G","Pro Trek","Sheen","Oceanus","Vintage"))
            productHelp(it,"اختياري — تحديدها يسرّع بحث كاسيو.")
            it.visibility=View.GONE
        })
        brand.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
                casio.visibility=if(brand.selectedItem=="كاسيو") View.VISIBLE else View.GONE
                customBrand.visibility=if(position==brand.count-1) View.VISIBLE else View.GONE
            }
        }
        field(parent,"رابط صفحة المنتج (اختياري)","الصق رابط صفحة الساعة من أي ماركة").inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        disabledAction(parent,"لصق الرابط")
        productHelp(parent,"عند وضع الرابط يجلب من الصفحة نفسها فقط، دون تغيير محرك بحث الماركة الأساسي.")
        disabledAction(parent,"✦ جلب معلومات الساعة تلقائيًا")
        productHelp(parent,"أدخل الماركة وREF ثم اضغط مرة واحدة. يجلب المعلومات والمستشار والصور المتاحة في عملية واحدة، مع الحفاظ على أي بيانات أدخلتها يدويًا.")
        field(parent,"كود المنتج")
        field(parent,"باركود المنتج","امسحه بالكاميرا أو اكتبه").inputType=InputType.TYPE_CLASS_NUMBER
        disabledAction(parent,"⌁ قراءة بالكاميرا")
        productHelp(parent,"يحفظ مرة واحدة ويستخدم لاستدعاء المنتج والسعر عند البيع.")
        productPhotoPicker(parent,"الصورة الرئيسية للساعة","ستظهر أولاً في بطاقة QR للعميل.")
        productPhotoPicker(parent,"صورة إضافية 1 (اختيارية)","زاوية أخرى للساعة أو العلبة.")
        productPhotoPicker(parent,"صورة إضافية 2 (اختيارية)","صورة ثالثة اختيارية للعرض للعميل.")
        productPhotoPicker(parent,"صورة الباركود (اختيارية)","هذه الصورة تحفظ كمرجع داخلي ولا تظهر للعميل.")
        formPair(parent,{
            field(it,"الكمية بالمخزون *").apply { inputType=InputType.TYPE_CLASS_NUMBER;setText("1") }
        },{
            field(it,"حد التنبيه الأدنى").apply { inputType=InputType.TYPE_CLASS_NUMBER;setText("1") }
        })
        formPair(parent,{
            // The source uses a disabled display-currency control; no invented setting/rate.
            field(it,"عملة الأسعار").apply { setText("—");isEnabled=false }
        },{
            field(it,"سعر البيع *").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        })
        field(parent,"كلفة الشراء للقطعة").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        formSection(parent,"كابلز (اختياري)")
        productHelp(parent,"اربط الرجالي والنسائي فقط إذا كانا يباعان منفردين أو كطقم بسعر خاص. لا يؤثر هذا الربط على مجموعات الألوان.")
        formPair(parent,{
            select(it,"نوع القطعة في الكابلز",listOf("ليست كابلز","رجالي","نسائي"))
        },{ select(it,"القطعة المرتبطة",listOf("بدون ربط")) })
        field(parent,"سعر الكابلز كاملًا").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        productHelp(parent,"مثال: كل قطعة 20$، وسعر الكابلز كاملًا 38$.")
        productPhotoPicker(parent,"صورة الكابلز معًا (اختيارية)","صورة الرجالي والنسائي معًا، وتُحفظ على القطعتين المرتبطتين.")
        // External partners intentionally excluded by the user's existing project instruction.
        formSection(parent,"تفاصيل الساعة للعميل")
        productHelp(parent,"هذه المعلومات تظهر في صفحة QR العامة للساعة.")
        formPair(parent,{ originalDetailChoice(it,"نوع الساعة","watchType") },{ originalDetailChoice(it,"الجنس / الفئة","gender") })
        formPair(parent,{ originalDetailChoice(it,"اللون العام للمنتج الأساسي","color") },{ originalDetailChoice(it,"لون الهيكل","color") })
        formPair(parent,{ originalDetailChoice(it,"لون المينا","dialColor") },{ originalDetailChoice(it,"نوع الحركة","movement") })
        formPair(parent,{ originalDetailChoice(it,"شكل عرض الوقت","displayType") },{ originalDetailChoice(it,"المقاس / أبعاد الهيكل","diameter") })
        formPair(parent,{ originalDetailChoice(it,"مادة الهيكل","caseMaterial") },{ originalDetailChoice(it,"مادة السوار","strapMaterial") })
        formPair(parent,{ originalDetailChoice(it,"لون السوار","strapColor") },{ originalDetailChoice(it,"نوع الزجاج","glassType") })
        formPair(parent,{ originalDetailChoice(it,"مقاومة الماء","waterResistance") },{ originalDetailChoice(it,"مدة الضمان بالأشهر","warrantyMonths") })
        formSection(parent,"منتجات/ألوان إضافية لنفس الموديل")
        productHelp(parent,"الحقول الرئيسية في النموذج تخص المنتج الأساسي. أضف هنا فقط المنتج الإضافي، ولكل إضافي كمية وباركود وصورتان وألوان مستقلة.")
        val variants=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(variants)
        parent.addView(text("＋ إضافة منتج/لون آخر",13f,Color.rgb(122,81,24)).apply {
            background=panel(Color.rgb(247,237,221),12,Color.rgb(223,201,164))
            isClickable=true;setOnClickListener { productVariantPreview(variants) }
        },LinearLayout.LayoutParams(-1,dp(44)))
        productHelp(parent,"الأساسي وكل إضافي يُحفظان كسجلات مخزون مستقلة، لكنهم يتبعون مجموعة موديل واحدة ولا يُعتبرون أسماء مكررة.")
        multilineField(parent,"وصف الساعة","وصف قصير وجميل يظهر للعميل")
        multilineField(parent,"مواصفات إضافية","مثال: تاريخ، منبه، إضاءة، كرونوغراف...")
        formSection(parent,"تعريف الساعة للمستشار")
        productHelp(parent,"حدّد ما ينطبق على الساعة. يمكن اختيار أكثر من فئة أو استخدام؛ هذه البيانات هي التي تجعل ترشيحات المستشار دقيقة.")
        formPair(parent,{
            select(it,"الجنس المناسب",listOf("تلقائي من بيانات المنتج","ذكر","أنثى","مناسب للجميع"))
        },{ select(it,"الحجم البصري",listOf("تلقائي","ناعم وصغير","متوسط ومتوازن","كبير وملفت")) })
        productProfileChoices(parent,"الفئات العمرية المناسبة",listOf("طفل","شاب","رجل","طفلة","فتاة","امرأة","أكبر سنًا"))
        productProfileChoices(parent,"الاستخدام المناسب",listOf("يومي","عمل","اجتماعات ورسمية","مناسبات","رياضة","جامعة","مدرسة","خروج وسهر","سفر وخروج","لعب ونشاط","زيارات وخروج","متعدد الاستخدامات"))
        productProfileChoices(parent,"الأسلوب",listOf("كلاسيكي","عصري","شبابي","فاخر","رياضي","رسمي","ناعم وأنيق","مرح وملون","لطيف وملون","بسيط وهادئ","يشبه ساعات الكبار"))
        formPair(parent,{
            select(it,"درجة اللفت",listOf("غير محدد","هادئ","متوازن","ملفت"))
        },{ select(it,"مناسبة كهدية",listOf("غير محدد","نعم","لا")) })
        multilineField(parent,"ملاحظات داخلية")
        val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
        listOf("إلغاء","⚡ حفظ + إضافة التالي","حفظ المنتج").forEachIndexed { index,label ->
            actions.addView(text(label,13f,if(index==2) Color.WHITE else Color.rgb(122,81,24)).apply {
                background=if(index==2) GradientDrawable(Orientation.TL_BR,intArrayOf(Color.rgb(179,131,50),Color.rgb(139,96,31))).apply { cornerRadius=dp(12).toFloat() }
                    else panel(Color.rgb(247,237,221),12,Color.rgb(223,201,164))
                isClickable=true
                setOnClickListener { if(index==0) returnToPrevious() else previewOnly() }
            },LinearLayout.LayoutParams(0,-2,1f).apply { height=dp(48);if(index<2) marginEnd=dp(9) })
        }
        parent.addView(actions,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(18) })
    }
    private fun maintenancePartsPreview(parent:LinearLayout) {
        parent.addView(text("قطع الصيانة",26f,gold))
        parent.addView(text("بطاريات، أحزمة، زجاج وقطع غيار مع متابعة المخزون.",14f))
        disabledAction(parent,"＋ إضافة قطعة")
        field(parent,"ابحث باسم قطعة الصيانة أو الباركود...")
        parent.addView(text("لم يتم تحميل قطع الصيانة بعد.",15f))
    }
    private fun returnsPreview(parent:LinearLayout) {
        formHeader(parent,"فواتير المرتجع والاستبدال","كل مرتجع يبقى مرتبطاً بالفاتورة الأصلية، دون حذف سجلاتها.","رجوع","preview:المبيعات والفواتير")
        val saleId=field(parent,"رقم الفاتورة الأصلية","أدخل رقم الفاتورة")
        val productId=field(parent,"معرّف المنتج","اختر الصنف المباع")
        val qty=field(parent,"الكمية المرتجعة","1").apply {
            setText("1")
            inputType=InputType.TYPE_CLASS_NUMBER
        }
        val reason=field(parent,"سبب المرتجع","سبب المرتجع مطلوب")
        val account=select(parent,"الخزنة",listOf("درج المحل","الصندوق الخارجي","شام كاش","البنك"))
        val matches=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(matches)
        fun showLines() {
            matches.removeAllViews()
            val id=saleId.text.toString().trim()
            if(id.isBlank()) return
            try {
                val db=legacyBackupStore.readableDatabase
                NativeSalesLedger.create(db)
                db.rawQuery("SELECT l.product_id,COALESCE(p.name,l.product_id),SUM(l.quantity) FROM native_sale_lines l LEFT JOIN domain_products p ON p.id=l.product_id WHERE l.sale_id=? GROUP BY l.product_id",arrayOf(id)).use { cursor ->
                    while(cursor.moveToNext()) {
                        val pid=cursor.getString(0)
                        matches.addView(text(cursor.getString(1)+" · "+cursor.getInt(2),13f,gold).apply {
                            isClickable=true
                            setOnClickListener { productId.setText(pid) }
                        })
                    }
                }
            } catch(e:Exception) {
                matches.addView(text("تعذر تحميل أصناف الفاتورة: "+(e.message ?: ""),12f))
            }
        }
        saleId.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun afterTextChanged(s:android.text.Editable?) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { showLines() }
        })
        parent.addView(Button(this).apply {
            text="حفظ المرتجع"
            setOnClickListener {
                try {
                    NativeSalesLedger.returnLine(legacyBackupStore.writableDatabase,
                        saleId.text.toString().trim(),productId.text.toString().trim(),
                        qty.text.toString().toIntOrNull() ?: 0,reason.text.toString().trim(),
                        account.selectedItem.toString())
                    android.widget.Toast.makeText(this@MainActivity,"تم حفظ المرتجع وعكس حركة المخزون",android.widget.Toast.LENGTH_LONG).show()
                    navigateTo("preview:المبيعات والفواتير")
                } catch(e:Exception) {
                    android.widget.Toast.makeText(this@MainActivity,e.message ?: "تعذر المرتجع",android.widget.Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    private fun vaultsPreview(parent:LinearLayout) {
        parent.addView(text("الخزائن والأرصدة",26f,gold))
        parent.addView(text("ملخص البيع والتحصيل مع الأرصدة الفعلية لكل خزنة.",14f))
        disabledAction(parent,"إقفال اليوم")
        disabledAction(parent,"＋ حركة مالية")
        parent.addView(text("حالة إقفال اليوم غير معروفة حتى ربط البيانات.",13f))
        listOf("درج المحل","الصندوق الخارجي","شام كاش","الحساب البنكي").forEach { name ->
            parent.addView(text(name,19f,gold))
            val allowed=if(name=="شام كاش") listOf("USD","SYP") else listOf("USD","SYP","TRY")
            parent.addView(text(allowed.joinToString("    ") { "$it —" },14f))
        }
        parent.addView(text("سجل الحركات",20f,gold))
        parent.addView(text("لم يتم تحميل سجل الحركات بعد.",14f))
    }
    private fun debtsPreview(parent:LinearLayout) {
        parent.addView(text("الديون والتحصيل",26f,gold))
        parent.addView(text("القيمة الإجمالية التقريبية: —",16f))
        parent.addView(text("سجلات الديون غير متصلة بالبيانات بعد.",14f))
    }
    private fun partnersPreview(parent:LinearLayout) {
        parent.addView(text("الشركاء والسحوبات",26f,gold))
        parent.addView(text("أرباح الفترة وسحوباتها مع المستحق المتراكم لكل شريك.",14f))
        field(parent,"شهر التقرير","YYYY-MM")
        field(parent,"من تاريخ","YYYY-MM-DD")
        field(parent,"إلى تاريخ","YYYY-MM-DD")
        parent.addView(text("الحصص والمستحقات",21f,gold))
        parent.addView(text("لم تُستورد أسماء الشركاء وحصصهم من النسخة الأصلية بعد.",14f))
        listOf("حصة الفترة","سحوبات الفترة","الفرق خلال الفترة","الرصيد المتراكم حتى اليوم").forEach { parent.addView(text("$it    —",15f)) }
        disabledAction(parent,"تسجيل سحب")
        parent.addView(text("لا يُحسب أي ربح أو سحب دون البيانات الفعلية وتواريخ العمليات.",12f))
    }
    private fun reportsPreview(parent:LinearLayout) {
        parent.addView(text("التقارير",26f,gold))
        field(parent,"من تاريخ","YYYY-MM-DD")
        field(parent,"إلى تاريخ","YYYY-MM-DD")
        listOf("المبيعات","الصيانة","المصاريف","المرتجعات","الربح الصافي").forEach {
            parent.addView(text("$it    —",17f))
        }
        parent.addView(text("لا توجد حسابات فعلية في معاينة الواجهة.",12f))
    }
    private fun settingsPreview(parent:LinearLayout) {
        parent.addView(text("استعادة نسخة الصفا القديمة",17f,gold).apply {
            isClickable = true
            setOnClickListener { openLegacyRestore() }
        })
        parent.addView(text("استيراد أولي آمن إلى SQLite، دون استبدال بيانات مستعادة أو تفعيل العمليات المحاسبية.",12f))

        parent.addView(text("الإعدادات",26f,gold))
        parent.addView(text("تخصيص المتجر والعملات والنسخة الاحتياطية.",14f))
        parent.addView(text("بيانات المتجر",20f,gold))
        field(parent,"اسم المتجر")
        select(parent,"عملة التطبيق (الوضع الكامل)",listOf("USD","TRY","SYP"))
        field(parent,"رقم واتساب المتجر (اختياري)","لإظهاره في الإيصال")
        disabledAction(parent,"حفظ الإعدادات")
        parent.addView(text("نسب الشركاء",20f,gold))
        parent.addView(text("يجب أن يكون مجموع النسب 100%.",12f))
        repeat(3) { i ->
            field(parent,"اسم الشريك ${i+1}")
            field(parent,"النسبة %")
        }
        disabledAction(parent,"حفظ نسب الشركاء")
        parent.addView(text("الإنترنت وأسعار العملات",20f,gold))
        parent.addView(text("النسخة الجديدة تعمل دون مزامنة أو اتصال بالإنترنت.",12f))
        listOf("سعر 1 دولار بالليرة السورية","سعر 1 دولار بالليرة التركية","سعر 1 ليرة تركية بالليرة السورية").forEach {
            field(parent,it)
        }
        disabledAction(parent,"حفظ أسعار السوق")
        parent.addView(text("أزرار الصيانة السريعة — الوضع المبسط",20f,gold))
        parent.addView(text("لكل زر يمكنك اختيار سعر ثابت أو مبلغ يدوي وقت الاستخدام.",12f))
        disabledAction(parent,"＋ زر جديد")
        parent.addView(text("قوالب رسائل العملاء",20f,gold))
        listOf("رسالة المبيع","استلام الصيانة","نتيجة الفحص","تحديث حالة الصيانة","جاهزة للاستلام","التسليم والدفع").forEach { field(parent,it) }
        disabledAction(parent,"حفظ القوالب")
        disabledAction(parent,"استعادة القوالب الجاهزة")
        parent.addView(text("النسخ الاحتياطي الآمن",20f,gold))
        disabledAction(parent,"⬇ تصدير نسخة كاملة كملف")
        disabledAction(parent,"⬆ استعادة نسخة من ملف")
        parent.addView(text("لا يمكن استعادة أو تصدير بيانات قبل تنفيذ قاعدة البيانات والتحقق من سلامتها.",12f))
    }
    private fun dailyClosingPreview(parent:LinearLayout) {
        parent.addView(text("إقفال اليوم",26f,gold))
        parent.addView(text("مرتبط مباشرة بدرج المحل والصندوق الخارجي وشام كاش والحساب البنكي، ويعرض فروقات المرتجع والاستبدال.",14f))
        parent.addView(text("الخزائن والأرصدة",20f,gold))
        listOf("USD","SYP","TRY").forEach { parent.addView(text("$it    —",16f)) }
        parent.addView(text("مقبوض نقدي اليوم",20f,gold))
        listOf("USD","SYP","TRY").forEach { parent.addView(text("$it    —",16f)) }
        parent.addView(text("عدّ درج المحل وحدد المدوّر بنفسك",20f,gold))
        parent.addView(text("لا يوجد أي اقتراح تلقائي. اكتب ما عددته فعلياً ثم اكتب المبلغ الذي تريد تركه للغد؛ مبلغ النقل يُحسب فقط من اختيارك.",12f))
        listOf("USD","SYP","TRY").forEach { c ->
            parent.addView(text(c,18f,gold))
            val counted=field(parent,"المبلغ المعدود فعلياً").apply {
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            val carry=field(parent,"المبلغ الذي سيبقى للغد").apply {
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            val transfer=text("المبلغ المنقول: —",15f,gold)
            parent.addView(transfer)
            val refresh= {
                val actual=counted.text.toString().toBigDecimalOrNull()
                val left=carry.text.toString().toBigDecimalOrNull()
                transfer.text=when {
                    actual==null || left==null -> "المبلغ المنقول: —"
                    actual.signum()<0 || left.signum()<0 -> "تحقق من المبالغ المدخلة"
                    left>actual -> "المبلغ المدوّر يتجاوز المبلغ المعدود"
                    else -> "المبلغ المنقول: "+actual.subtract(left).stripTrailingZeros().toPlainString()+" "+c
                }
            }
            listOf(counted,carry).forEach { edit ->
                edit.addTextChangedListener(object:android.text.TextWatcher {
                    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { refresh() }
                    override fun afterTextChanged(s:android.text.Editable?) {}
                })
            }
        }
        parent.addView(text("مطابقة شام كاش",20f,gold))
        field(parent,"رصيد شام كاش USD الفعلي").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        field(parent,"رصيد شام كاش SYP الفعلي").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        field(parent,"ملاحظة الإقفال")
        parent.addView(text("اكتب ما عددته فعلياً، ثم حدد ما تنقله للصندوق الخارجي. المتبقي يصبح رصيد بداية الغد.",12f))
        disabledAction(parent,"إقفال اليوم وترحيل الأرصدة")
        parent.addView(text("لا تُجرى أي حركة نقدية في معاينة Native 7.",12f))
    }
    private fun customerOrdersPreview(parent:LinearLayout) {
        parent.addView(text("طلبات العملاء",26f,gold))
        field(parent,"بحث باسم العميل أو الهاتف أو الساعة")
        parent.addView(text("الطلبات غير متصلة بالبيانات بعد.",13f))
    }
    private fun warrantyPreview(parent:LinearLayout) {
        parent.addView(text("الضمانات",26f,gold))
        field(parent,"بحث برقم الفاتورة أو العميل أو الهاتف")
        parent.addView(text("الضمانات لا تُحسب قبل استعادة بيانات الفواتير.",13f))
    }
    private fun lowStockPreview(parent:LinearLayout) {
        parent.addView(text("نقص المخزون",26f,gold))
        field(parent,"بحث باسم المنتج أو القطعة")
        parent.addView(text("لم تُحمّل بيانات المخزون بعد.",13f))
    }
    private fun stockMovementPreview(parent:LinearLayout) {
        parent.addView(text("حركة المخزون",26f,gold))
        field(parent,"من تاريخ","YYYY-MM-DD")
        field(parent,"إلى تاريخ","YYYY-MM-DD")
        field(parent,"بحث باسم المنتج أو الباركود")
        parent.addView(text("حركات المخزون غير متصلة بالبيانات بعد.",13f))
    }
    private fun auditPreview(parent:LinearLayout) {
        parent.addView(text("سجل التدقيق",26f,gold))
        field(parent,"من تاريخ","YYYY-MM-DD")
        field(parent,"إلى تاريخ","YYYY-MM-DD")
        parent.addView(text("كل عملية حفظ مع الوقت والجهاز",14f))
        parent.addView(text("سجل التدقيق غير متصل بالبيانات بعد.",13f))
    }
    private fun customersPreview(parent:LinearLayout) {
        parent.addView(text("ملفات العملاء",26f,gold))
        field(parent,"بحث باسم العميل أو رقم الهاتف")
        parent.addView(text("المبيعات والصيانة والضمانات والطلبات",14f))
        parent.addView(text("ملفات العملاء غير متصلة بالبيانات بعد.",13f))
    }
    private fun openingBalancesPreview(parent:LinearLayout) {
        parent.addView(text("الأرصدة الافتتاحية",26f,gold))
        parent.addView(text("البضاعة والقطع والنقود قبل بدء العمل",14f))
        listOf("درج المحل","الصندوق الخارجي","شام كاش","الحساب البنكي").forEach { name ->
            parent.addView(text(name,18f,gold))
            val currencies=if(name=="شام كاش") listOf("USD","SYP") else listOf("USD","TRY","SYP")
            currencies.forEach { currency ->
                field(parent,"رصيد $currency","0").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }
        parent.addView(text("المخزون الافتتاحي",20f,gold))
        parent.addView(text("تُدخل كميات المنتجات وقطع الصيانة من شاشاتها، ولا يُضاف مخزون مكرر عند استعادة النسخة القديمة.",13f))
        disabledAction(parent,"حفظ الأرصدة الافتتاحية")
        parent.addView(text("هذه معاينة فقط؛ لا تُحفظ أرصدة ولا تُنشأ حركات مالية.",12f))
    }
    private fun usedWatchesPreview(parent:LinearLayout) {
        parent.addView(text("الساعات المستعملة",26f,gold))
        parent.addView(text("شراء مستعمل أو استبداله بساعة جديدة مع الربط المحاسبي",14f))
        field(parent,"بحث باسم الساعة أو رقم الفاتورة")
        parent.addView(text("لا توجد بيانات مستوردة بعد.",13f))
    }
    private fun thermalPrinterPreview(parent:LinearLayout) {
        parent.addView(text("الطابعة الحرارية",26f,gold))
        parent.addView(text("اختيار عرض الرول والطابعة",14f))
        val sizes=listOf(38,40,44,48,50,57,58,60,76,80,100,110)
        val prefs=getSharedPreferences("alsafa_printer",MODE_PRIVATE)
        val selected=Spinner(this)
        selected.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
            sizes.map { it.toString()+" ملم" })
        selected.setSelection(sizes.indexOf(prefs.getInt("roll_width_mm",80)).coerceAtLeast(0))
        parent.addView(selected)
        parent.addView(Button(this).apply {
            text="حفظ عرض الرول"
            setOnClickListener {
                prefs.edit().putInt("roll_width_mm",sizes[selected.selectedItemPosition]).apply()
                android.widget.Toast.makeText(this@MainActivity,"تم حفظ عرض الرول",android.widget.Toast.LENGTH_SHORT).show()
            }
        })
        parent.addView(Button(this).apply {
            text="اختيار طابعة بلوتوث مقترنة"
            setOnClickListener {
                try {
                    if(android.os.Build.VERSION.SDK_INT>=31 &&
                        checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),7180)
                    } else {
                        val devices=ThermalBluetoothPrinter.paired(this@MainActivity)
                        if(devices.isEmpty()) error("لا توجد طابعة مقترنة؛ اقترن بالطابعة من إعدادات البلوتوث")
                        android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("اختيار الطابعة")
                            .setSingleChoiceItems(devices.map { it.first+" · "+it.second }.toTypedArray(),-1) { dialog,index ->
                                prefs.edit().putString("printer_mac",devices[index].second).apply()
                                dialog.dismiss()
                                android.widget.Toast.makeText(this@MainActivity,"تم اختيار الطابعة",android.widget.Toast.LENGTH_SHORT).show()
                            }.setNegativeButton("إلغاء",null).show()
                    }
                } catch(e:Exception) {
                    android.widget.Toast.makeText(this@MainActivity,e.message,android.widget.Toast.LENGTH_LONG).show()
                }
            }
        })
        parent.addView(Button(this).apply {
            text="إعدادات البلوتوث والطابعة"
            setOnClickListener {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        })
        parent.addView(text("تتم الطباعة عبر خدمة طباعة Android المتوافقة مع الطابعة. الاقتران وحده لا يضمن دعم الطباعة؛ يحتاج اختباراً على الجهاز.",12f))
    }
    private fun inventoryAnalysisPreview(parent:LinearLayout) {
        parent.addView(text("تحليل المخزون",26f,gold))
        parent.addView(text("القيمة والراكد والأكثر مبيعاً والمنخفض",14f))
        parent.addView(text("تحتاج هذه المؤشرات إلى بيانات المخزون والفواتير الفعلية.",13f))
    }
    private fun securityPreview(parent:LinearLayout) {
        parent.addView(text("الأمان والأجهزة",26f,gold))
        parent.addView(text("PIN واسم الجهاز وسجل النشاط",14f))
        parent.addView(text("Native 7 مخصص للتابلت ويعمل دون مزامنة بين الأجهزة.",13f))
        parent.addView(text("لا يوجد PIN مفعّل في معاينة الواجهة.",13f))
    }
    private fun modernCenterPreview(parent:LinearLayout) {
        parent.addView(text("مركز الصفا الحديث",26f,gold))
        parent.addView(text("القرارات، العرض، الولاء، الاستلام الذكي والمزيد",14f))
        parent.addView(text("هذه الأقسام تحتاج إلى نقل قواعدها من المصدر الأصلي قبل تفعيلها.",13f))
    }
    /** Labels and placement copied from renderSearch() in original v3.9.359. */
    private fun globalSearchPreview(parent:LinearLayout) {
        parent.addView(text("بحث سريع",20f,ink).apply { gravity=Gravity.RIGHT })
        parent.addView(text("ابحث في المبيعات والصيانة والمخزون من مكان واحد.",12f,Color.rgb(138,123,108)).apply { gravity=Gravity.RIGHT })
        val searchBox=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            background=panel(paper,12)
            setPadding(dp(8),dp(4),dp(8),dp(4))
        }
        searchBox.addView(text("🔎",19f,gold))
        val query=EditText(this).apply {
            hint="رقم الفاتورة، اسم العميل، الهاتف أو الباركود"
            textSize=14f
            setSingleLine(true)
            background=null
        }
        searchBox.addView(query,LinearLayout.LayoutParams(0,dp(48),1f))
        parent.addView(searchBox,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14);bottomMargin=dp(12) })
        val results=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            background=panel(paper,17)
            setPadding(dp(12),dp(12),dp(12),dp(12))
        }
        parent.addView(results)
        fun updateSearch() {
            results.removeAllViews()
            val term=query.text.toString().trim()
            if(term.isBlank()) {
                results.addView(text("اكتب اسم المنتج أو الباركود أو الموديل للبحث فوراً.",13f))
                return
            }
            try {
                val matches=LegacyProductRepository.list(legacyBackupStore.readableDatabase,term,0,30)
                results.addView(text("نتائج المنتجات والمخزون: ${matches.size}"+if(matches.size==30) " (أول 30 نتيجة)" else "",13f,gold))
                matches.forEach { product ->
                    results.addView(text("${product.name} — ${product.barcode} — المخزون: ${product.stock}",14f).apply {
                        gravity=Gravity.RIGHT
                        setPadding(dp(8),dp(9),dp(8),dp(9))
                        isClickable=true
                        setOnClickListener { navigateTo("preview:المنتجات والمخزون") }
                    })
                }
                if(matches.isEmpty())results.addView(text("لا توجد منتجات مطابقة.",13f))
            } catch(e:Exception) {
                results.addView(text("تعذر البحث في المنتجات: "+(e.message ?: "خطأ غير معروف"),13f))
            }
        }
        query.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { updateSearch() }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        updateSearch()
    }
    private fun cashWalletPreview(parent:LinearLayout) {
        parent.addView(text("الحافظة",26f,gold))
        parent.addView(text("النقدي وشام كاش والتحصيلات",14f))
        parent.addView(text("لم تُحمّل الأرصدة والتحصيلات بعد.",13f))
    }
    /** Native equivalent of the original .topbar, .brand-mark and .brand CSS. */
    private fun originalTopbar(parent:LinearLayout) {
        val bar=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            if(resources.configuration.screenWidthDp>=720) setPadding(dp(25),dp(19),dp(25),dp(19))
            else setPadding(dp(18),dp(18),dp(18),dp(16))
            background=GradientDrawable(Orientation.TL_BR,
                intArrayOf(Color.rgb(249,243,232),Color.rgb(234,217,185))).apply {
                setStroke(dp(1),Color.rgb(229,208,170))
            }
        }
        val brand=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
        }
        val mark=android.widget.ImageView(this).apply {
            assets.open("invoice-logo.png").use { setImageBitmap(android.graphics.BitmapFactory.decodeStream(it)) }
            scaleType=android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription="شعار الصفا للساعات"
        }
        brand.addView(mark,LinearLayout.LayoutParams(dp(if(resources.configuration.screenWidthDp>=720) 100 else 56),dp(48)))
        val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        labels.addView(text("الصفا للساعات",if(resources.configuration.screenWidthDp>=720) 21f else 19f,Color.rgb(122,81,24)).apply {
            gravity=Gravity.RIGHT
            typeface=Typeface.create(typeface,Typeface.BOLD)
            setPadding(0,0,0,0)
        })
        labels.addView(text("مبيعات · مخزون · صيانة · حسابات",11f,Color.rgb(138,123,108)).apply {
            gravity=Gravity.RIGHT
            setPadding(0,dp(3),0,0)
        })
        brand.addView(labels,LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(11) })
        bar.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        val sourceRoute=OriginalRoutes.source(screen)
        val simplified=screen in listOf("chooser","simple") || sourceRoute?.startsWith("father-")==true
        if(!simplified) {
            listOf("↻","☰").forEach { symbol ->
                bar.addView(text(symbol,19f,gold).apply {
                    contentDescription=if(symbol=="↻") "تحديث" else "الإدارة"
                    background=panel(paper,10);isClickable=true
                    setOnClickListener { if(symbol=="☰") navigateTo("management") else previewOnly() }
                },LinearLayout.LayoutParams(dp(40),dp(40)).apply { marginStart=dp(8) })
            }
        }
        parent.addView(bar,LinearLayout.LayoutParams(-1,-2))
    }
    private fun render() {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pearl)
        }
        // Apply system/keyboard insets once so modern edge-to-edge does not cover the UI.
        root.setOnApplyWindowInsetsListener { view,insets ->
            if(android.os.Build.VERSION.SDK_INT>=30) {
                val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                val keyboard=insets.getInsets(android.view.WindowInsets.Type.ime())
                view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,keyboard.bottom))
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)
        if(android.os.Build.VERSION.SDK_INT>=30) {
            // PhoneWindow may have no decor before setContentView. Ask the attached
            // content view for its controller, after attachment, instead of the window.
            root.post {
                val appearance=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                root.windowInsetsController?.setSystemBarsAppearance(appearance,appearance)
            }
        }
        root.requestApplyInsets()
        activeRoot=root
        originalTopbar(root)
        val width=resources.displayMetrics.widthPixels
        val simpleMode=screen=="chooser" || screen=="simple" || screen=="preview:مبيع" ||
            screen=="preview:صيانة سريعة" || screen=="preview:تسليم الساعات"
        val showNavigation=!simpleMode && screen!="advisor"
        val sidebar=showNavigation && resources.configuration.screenWidthDp>=720
        val workArea=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
        }
        root.addView(workArea,LinearLayout.LayoutParams(-1,0,1f))
        if(sidebar) {
            val nav=LegacyNavigation(this)
            nav.show(true,screen) { navigateTo(it) }
            workArea.addView(nav,LinearLayout.LayoutParams(dp(if(resources.configuration.screenWidthDp>=1200 && resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE) 230 else 200),-1))
        }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        workArea.addView(scroll,LinearLayout.LayoutParams(0,-1,1f))
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(15),dp(17),dp(15),dp(24))
        }
        // Match original v3.9.359 Honor Pad X8b tablet breakpoints.
        val tablet=resources.configuration.screenWidthDp>=720
        val landscape=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val wideLandscape=resources.configuration.screenWidthDp>=1200 && landscape
        // Legacy v3.9.359 Honor Pad X8b: portrait 720-1199, landscape >=1200.
        val maxWidth=dp(if(screen=="chooser") 920 else if(wideLandscape) 1440 else if(tablet) 1180 else 680)
        val sidebarWidth=if(sidebar) dp(if(wideLandscape) 230 else 200) else 0
        val available=width-sidebarWidth
        val horizontalPadding=if(wideLandscape) 28 else if(tablet) 20 else 15
        val verticalPadding=if(wideLandscape) 24 else if(tablet) 20 else 17
        body.setPadding(dp(horizontalPadding),dp(verticalPadding),
            dp(horizontalPadding),dp(10))
        scroll.addView(body,android.widget.FrameLayout.LayoutParams(
            if(available>maxWidth) maxWidth else -1,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))
        if(showNavigation && !sidebar) {
            val nav=LegacyNavigation(this)
            nav.show(false,screen) { navigateTo(it) }
            root.addView(nav,LinearLayout.LayoutParams(-1,dp(68)))
        }
        val sourceRoute=OriginalRoutes.source(screen)
        val sourceStructure=sourceRoute!=null && originalPages.contains(sourceRoute)
        if(sourceRoute=="product-form" || screen=="preview:إضافة منتج جديد") {
            legacyFormCard(body) { productCreatePage(it) }
        }
        else if(sourceRoute=="products" || screen=="preview:المنتجات والمخزون") {
            productsPreview(body)
        }
        else if(sourceStructure && screen.startsWith("structure:")) {
            originalPages.show(body,requireNotNull(sourceRoute))
            if(sourceRoute=="settings") {
                val restoreAction=text("استعادة نسخة الصفا القديمة",16f,gold).apply {
                    gravity=Gravity.CENTER
                    background=panel(paper,12)
                    isClickable=true
                    isFocusable=true
                    setOnClickListener { openLegacyRestore() }
                }
                body.addView(restoreAction,LinearLayout.LayoutParams(-1,dp(52)).apply {
                    topMargin=dp(12);bottomMargin=dp(12)
                })
                body.addView(text(
                    if(legacyBackupStore.hasData()) "توجد نسخة محفوظة محلياً؛ لن تُستبدل دون مراجعة."
                    else "استعادة أولية إلى SQLite دون حذف بيانات موجودة. ربط الحسابات لم يكتمل بعد.",
                    12f
                ).apply { gravity=Gravity.RIGHT })
            }
        }
        else when(screen) {
            "chooser" -> {
                body.addView(text("ALSAFA WATCHES",13f,gold))
                body.addView(text("كيف تريد أن تبدأ؟",28f))
                body.addView(text("اختر بيئة العمل المناسبة، أو افتح مستشار اختيار الساعة أمام العميل.",15f))
                val modes=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
                body.addView(modes,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(20);bottomMargin=dp(14) })
                chooserCard(modes,"الوضع المبسط","بيع وصيانة وتسليم سريع","⚡","simple",false)
                modes.addView(View(this),LinearLayout.LayoutParams(dp(14),dp(1)))
                chooserCard(modes,"الوضع الكامل","الإدارة والمخزون والحسابات","◆","full",true)
                card(body,"ساعدني أختار ساعتي","ALSAFA ADVISOR — اختيارات ذكية من الساعات المتوفرة فعلياً الآن","advisor",true)
            }
            "simple" -> {
                body.addView(text("العمل السريع",25f,gold))
                body.addView(text("اختر العملية فقط.",15f))
                body.addView(text("⚙️ الوضع الكامل",14f,gold).apply { isClickable=true;setOnClickListener { navigateTo("full") } })
                currencySummary(body)
                actionGrid(body,listOf(
                    Triple("مبيع","بيع سريع","🛒"),
                    Triple("صيانة سريعة","استلام أو تسليم مباشر","🔧"),
                    Triple("تسليم الساعات","الساعات التي بقيت لدينا","⌚"),
                    Triple("تسليم الوردية","النقود والمحجوزات والمنتظر","🤝")
                ),true)
            }
            "full" -> {
                body.addView(text("الوضع الكامل",25f,gold))
                body.addView(text("كل عمليات المتجر والإدارة.",15f))
                body.addView(text("👨‍🔧 الوضع المبسط",14f,gold).apply { isClickable=true;setOnClickListener { navigateTo("simple") } })
                currencySummary(body)
                actionGrid(body,listOf(
                    Triple("اليوم","ملخص وتنبيهات العمل الآن","☀️"),
                    Triple("مبيع","شاشة المبيع الكاملة","🛒"),
                    Triple("استلام صيانة","طلب صيانة كامل","🔧"),
                    Triple("تسليم صيانة","طلبات قيد المتابعة","✅"),
                    Triple("قبض دين","المبالغ المستحقة","💵"),
                    Triple("مصروف","تسجيل مصروف","📤"),
                    Triple("بحث","عميل، هاتف، طلب أو منتج","🔎")
                ),false)
                val recent=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    background=panel(paper,17)
                }
                val heading=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                    gravity=Gravity.CENTER_VERTICAL
                    setPadding(dp(15),dp(10),dp(15),dp(10))
                }
                heading.addView(text("آخر العمليات",15f).apply { gravity=Gravity.RIGHT },
                    LinearLayout.LayoutParams(0,-2,1f))
                heading.addView(text("الإدارة",12f,gold).apply {
                    isClickable=true
                    setOnClickListener { navigateTo("management") }
                })
                recent.addView(heading)
                recent.addView(text("لا توجد عمليات مسجلة بعد.",13f,Color.rgb(138,123,108)))
                body.addView(recent,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14) })
            }
            "management" -> {
                body.addView(text("الإدارة",25f,gold))
                body.addView(text("الخيارات التي تحتاجها أنت، بعيداً عن شاشة الاستخدام اليومي.",14f))
                actionGrid(body,listOf(
                    Triple("الأرصدة الافتتاحية","البضاعة والقطع والنقود قبل بدء العمل","🏁"),
                    Triple("المنتجات والمخزون","إضافة وتعديل الأسعار والكميات","⌚"),
                    Triple("قطع الصيانة","بطاريات وأحزمة وزجاج وقطع الغيار","🔋"),
                    Triple("المشتريات والموردون","فاتورة شراء تزيد المخزون دون تكرار المصروف","📦"),
                    Triple("الساعات المستعملة","شراء مستعمل أو استبداله بساعة جديدة مع الربط المحاسبي","♻️"),
                    Triple("الفواتير السابقة","عرض PDF وإعادة الإرسال","🧾"),
                    Triple("فواتير المرتجع","الاسترجاع والاستبدال وربطها بالفاتورة الأصلية","↩️"),
                    Triple("الطابعة الحرارية","Bluetooth · اختيار الطابعة وعرض الورق","🖨️"),
                    Triple("كل الصيانة","الحالات والتسليم والمتابعة","🔧"),
                    Triple("الحافظة","النقدي وشام كاش والتحصيلات","💼"),
                    Triple("الخزائن والأرصدة","الدرج والصندوق الخارجي وشام كاش والبنك","🏦"),
                    Triple("إقفال اليوم","عدّ الدرج ونقل الفائض وتجهيز الغد","🌙"),
                    Triple("الديون","المبالغ المستحقة والتحصيل","💵"),
                    Triple("المصاريف","مراجعة المصاريف المسجلة","📤"),
                    Triple("ملفات العملاء","المبيعات والصيانة والضمانات والطلبات","👤"),
                    Triple("طلبات العملاء","حجز ساعة ومتابعة وصولها وتسليمها","📋"),
                    Triple("الضمانات","الضمان الساري والمنتهي حسب الفاتورة","🛡️"),
                    Triple("تحليل المخزون","القيمة والراكد والأكثر مبيعاً والمنخفض","📈"),
                    Triple("سجل التدقيق","كل عملية حفظ مع الوقت والجهاز","🧾"),
                    Triple("التقارير","المبيعات والأرباح والفترات","📊"),
                    Triple("الشركاء والسحوبات","الحصص الشهرية والسحوبات والأرصدة","👥"),
                    Triple("نقص المخزون","الساعات والقطع التي تحتاج تعبئة","⚠️"),
                    Triple("حركة المخزون","كل زيادة ونقص مع التاريخ والجهاز","↕️"),
                    Triple("الأمان والأجهزة","PIN واسم الجهاز وسجل النشاط","🛡️"),
                    Triple("مركز الصفا الحديث","القرارات، العرض، الولاء، الاستلام الذكي والمزيد","✦"),
                    Triple("الإعدادات","العملات والنسخ الاحتياطي والمتجر","⚙️")
                ),false,true)
            }
            "preview:بحث" -> globalSearchPreview(body)
            "preview:مبيع" -> salePreview(body)
            "preview:مبيع كامل" -> legacyFormCard(body) { fullSalePreview(it) }
            "preview:استلام صيانة" -> legacyFormCard(body) { repairFormPreview(it) }
            "preview:طلب صيانة" -> legacyFormCard(body) { repairFormPreview(it) }
            "preview:تسليم صيانة" -> invoiceArchivePreview(body,true)
            "preview:كل الصيانة" -> invoiceArchivePreview(body,true)
            "preview:الفواتير السابقة" -> invoiceArchivePreview(body,false)
            "preview:المبيعات والفواتير" -> invoiceArchivePreview(body,false)
            "preview:مصروف" -> expenseFormPreview(body)
            "preview:المصاريف" -> expenseFormPreview(body)
            "preview:المشتريات والموردون" -> supplierPurchasesPreview(body)
            "preview:فاتورة شراء" -> legacyFormCard(body) { purchaseInvoicePreview(it) }
            "preview:المنتجات والمخزون" -> productsPreview(body)
            "native:product-edit" -> inventoryEditPage(body)
            "native:group-edit" -> productGroupEditPage(body)
            "native:stock-adjustment" -> inventoryAdjustmentPage(body)
            "preview:إضافة منتج جديد" -> legacyFormCard(body) { productCreatePage(it) }
            "preview:قطع الصيانة" -> maintenancePartsPreview(body)
            "preview:فواتير المرتجع" -> returnsPreview(body)
            "preview:الخزائن والأرصدة" -> vaultsPreview(body)
            "preview:الديون" -> debtsPreview(body)
            "preview:قبض دين" -> debtsPreview(body)
            "preview:الشركاء والسحوبات" -> partnersPreview(body)
            "preview:التقارير" -> reportsPreview(body)
            "preview:الإعدادات" -> settingsPreview(body)
            "preview:إقفال اليوم" -> dailyClosingPreview(body)
            "preview:طلبات العملاء" -> customerOrdersPreview(body)
            "preview:الضمانات" -> warrantyPreview(body)
            "preview:نقص المخزون" -> lowStockPreview(body)
            "preview:حركة المخزون" -> stockMovementPreview(body)
            "preview:سجل التدقيق" -> auditPreview(body)
            "preview:ملفات العملاء" -> customersPreview(body)
            "preview:الأرصدة الافتتاحية" -> openingBalancesPreview(body)
            "preview:الساعات المستعملة" -> usedWatchesPreview(body)
            "preview:الطابعة الحرارية" -> thermalPrinterPreview(body)
            "preview:تحليل المخزون" -> inventoryAnalysisPreview(body)
            "preview:الأمان والأجهزة" -> securityPreview(body)
            "preview:مركز الصفا الحديث" -> modernCenterPreview(body)
            "preview:الحافظة" -> cashWalletPreview(body)
            "preview:تسليم الوردية" -> handoverPreview(body)
            "preview:صيانة سريعة" -> quickRepairPreview(body)
            "preview:تسليم الساعات" -> deliveryPreview(body)
            else -> {
                body.addView(text(screen.removePrefix("preview:"),23f,gold))
                body.addView(text("واجهة قيد المطابقة مع التطبيق القديم — لا توجد عمليات حفظ في هذه النسخة.",15f))
            }
        }
        if(screen in listOf("preview:مبيع كامل","preview:طلب صيانة","preview:استلام صيانة","preview:فاتورة شراء"))
            arrangeOriginalFormGrid(body)
        if(screen in listOf("preview:مبيع كامل","preview:طلب صيانة","preview:استلام صيانة"))
            arrangeOriginalFormActions(body)
        if(!screen.startsWith("structure:") && screen!="chooser") body.addView(text("العودة",17f,gold).apply {
            isClickable=true;setOnClickListener {
                returnToPrevious()
            }
        })
    }

}

