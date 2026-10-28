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
    private val repairStore by lazy { RepairStore(this) }

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

    private fun dp(value:Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun text(value:String, size:Float, color:Int = ink):TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        setPadding(dp(9),dp(8),dp(9),dp(8))
        typeface = try { Typeface.createFromAsset(assets,"Tajawal-Medium.ttf") } catch (_:Exception) { Typeface.DEFAULT }
    }
    private fun panel(color:Int, radius:Int=20, border:Int=Color.rgb(234,223,206)) =
        GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat(); setStroke(dp(1),border) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
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
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
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
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if(screen!="chooser") returnToPrevious() else super.onBackPressed() }
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
        val columns=if(!simple && !management && resources.configuration.screenWidthDp>=720) 4 else 2
        items.chunked(columns).forEach { pair ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
            parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(if(simple) 7 else if(management) 10 else 9) })
            pair.forEachIndexed { index,item ->
                val box=LinearLayout(this).apply {
                    orientation=if(simple || management) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity=if(management) Gravity.CENTER_VERTICAL else Gravity.CENTER
                    layoutDirection=View.LAYOUT_DIRECTION_RTL
                    background=panel(if(simple) Color.rgb(255,250,242) else paper,
                        if(simple) 24 else if(management) 16 else 14,
                        if(simple) Color.rgb(216,189,136) else Color.rgb(234,223,206))
                    setPadding(dp(if(simple) 9 else if(management) 15 else 13),
                        dp(if(simple) 9 else if(management) 15 else 13),
                        dp(if(simple) 9 else if(management) 15 else 13),
                        dp(if(simple) 9 else if(management) 15 else 13))
                    isClickable=true
                    setOnClickListener { navigateTo(if(screen=="full" && item.first=="مبيع") "preview:مبيع كامل" else "preview:" + item.first) }
                }
                when {
                    simple -> {
                        box.addView(text(item.third,27f,gold))
                        box.addView(text(item.first,16f))
                        box.addView(text(item.second,9f,Color.rgb(138,123,108)))
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
                        box.addView(text(item.third,20f,gold),LinearLayout.LayoutParams(dp(32),dp(32)))
                        val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL }
                        labels.addView(text(item.first,13f).apply { gravity=Gravity.RIGHT;setPadding(dp(8),0,dp(8),0) })
                        labels.addView(text(item.second,10f,Color.rgb(138,123,108)).apply { gravity=Gravity.RIGHT;setPadding(dp(8),dp(4),dp(8),0) })
                        box.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
                    }
                }
                row.addView(box,LinearLayout.LayoutParams(0,dp(if(simple) 93 else if(management) 115 else 72),1f).apply {
                    if(index==0) marginEnd=dp(if(simple) 7 else if(management) 10 else 9)
                })
            }
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
            cancel.background=panel(paper,11)
            cancel.gravity=Gravity.CENTER
            save.gravity=Gravity.CENTER
            actions.addView(cancel,LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd=dp(10) })
            actions.addView(save,LinearLayout.LayoutParams(0,dp(48),1f))
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
            val formPadding=if(resources.configuration.screenWidthDp>=720) 22 else 15
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
            topMargin=dp(20)
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
    private fun field(parent:LinearLayout,label:String,hint:String=""):EditText {
        parent.addView(text(label,11f,Color.rgb(138,123,108)).apply {
            gravity=Gravity.RIGHT
            setPadding(dp(2),dp(5),dp(2),dp(6))
        })
        return EditText(this).apply {
            this.hint=hint
            textSize=14f
            setTextColor(ink)
            setHintTextColor(Color.rgb(138,123,108))
            background=panel(Color.rgb(255,252,247),10,Color.rgb(234,223,206))
            setPadding(dp(10),dp(9),dp(10),dp(9))
            parent.addView(this,LinearLayout.LayoutParams(-1,dp(42)).apply { bottomMargin=dp(11) })
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
            setPadding(dp(2),dp(5),dp(2),dp(6))
        })
        val spinner=Spinner(this).apply {
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            background=panel(Color.rgb(255,252,247),10,Color.rgb(234,223,206))
            setPadding(dp(10),dp(9),dp(10),dp(9))
        }
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,options)
        parent.addView(spinner,LinearLayout.LayoutParams(-1,dp(42)).apply { bottomMargin=dp(11) })
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
        val steps=listOf("اختر الساعة","سعر البيع النهائي","طريقة الدفع")
        quickStep(parent,1,steps[0])
        field(parent,"بحث بالاسم أو REF أو الرقم أو الباركود","مثال: CASIO أو MTP أو 002")
        val barcodeActions=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
        }
        parent.addView(barcodeActions,LinearLayout.LayoutParams(-1,-2))
        val barcodeInput=EditText(this).apply {
            hint="امسح أو اكتب الباركود"
            textSize=14f
            background=panel(Color.rgb(255,252,247),10,Color.rgb(234,223,206))
            setPadding(dp(10),dp(8),dp(10),dp(8))
            inputType=InputType.TYPE_CLASS_NUMBER
        }
        barcodeActions.addView(barcodeInput,LinearLayout.LayoutParams(0,dp(58),1f))
        listOf("📷 قراءة الباركود","إضافة").forEach { label ->
            val action=text(label,13f,gold).apply {
                background=panel(Color.rgb(247,237,221),12,Color.rgb(223,201,164))
                isClickable=true
                setOnClickListener {
                    android.widget.Toast.makeText(this@MainActivity,"قارئ الباركود وربط المخزون لم يُنفذا بعد",android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            barcodeActions.addView(action,LinearLayout.LayoutParams(-2,dp(58)).apply { marginStart=dp(8) })
        }
        parent.addView(text("لم يتم اختيار ساعة بعد",15f,Color.rgb(138,123,108)).apply {
            background=panel(Color.rgb(255,250,242),15,Color.rgb(212,189,146))
        },LinearLayout.LayoutParams(-1,dp(64)).apply { topMargin=dp(12);bottomMargin=dp(21) })
        quickStep(parent,2,steps[1])
        finalAmountCurrency(parent,"USD")
        quickStep(parent,3,steps[2])
        paymentChoices(parent)
        parent.addView(text("✓ إتمام الفاتورة",13f,Color.WHITE).apply {
            background=panel(Color.rgb(139,96,31),12)
        },LinearLayout.LayoutParams(-1,dp(44)))
        parent.addView(text("معاينة شكلية فقط — لا يتم حفظ أي فاتورة.",12f))
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
            background=panel(paper,16)
            setPadding(dp(12),dp(10),dp(12),dp(10))
        }
        item.addView(text("الصنف $index",18f,gold))
        field(item,"اسم الساعة / الصنف *","مثال: ساعة كاسيو")
        field(item,"الموديل","اختياري")
        val qty=field(item,"الكمية *","1").apply {
            inputType=InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        val price=field(item,"سعر البيع *","0").apply {
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val total=text("إجمالي الصنف: —",14f)
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
        if(removable) item.addView(text("حذف الصنف",14f,gold).apply {
            isClickable=true
            setOnClickListener { parent.removeView(item);onChange() }
        })
        parent.addView(item,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    private fun fullSalePreview(parent:LinearLayout) {
        formHeader(parent,"فاتورة مبيعات","اختر من المنتجات أو امسح الباركود بالكاميرا لإضافة الساعة فوراً.","قائمة الفواتير","preview:المبيعات والفواتير")
        formSection(parent,"بيانات الفاتورة")
        select(parent,"نوع البيع *",listOf("بيع من المحل","بيع أونلاين"))
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
        formPair(parent,{ column ->
            select(column,"عملة البيع / القبض *",listOf("USD","TRY","SYP"))
            column.addView(text("المبلغ يدخل صندوق هذه العملة نفسها.",11f,Color.rgb(138,123,108)))
        },{ column ->
            select(column,"طريقة الدفع *",listOf("نقدي","شام كاش"))
            column.addView(text("اختر نقدي أو شام كاش قبل حفظ الفاتورة.",11f,Color.rgb(138,123,108)))
        })
        formPair(parent,{ field(it,"اسم العميل","مهم للضمان والفاتورة") },{ field(it,"رقم واتساب العميل","مثال: 09xxxxxxxx").inputType=InputType.TYPE_CLASS_PHONE })
        select(parent,"مدة الضمان",listOf("بدون ضمان","3 أشهر","6 أشهر","سنة","سنتان")).setSelection(3)
        parent.addView(text("يرتبط الضمان تلقائياً بالعميل والفاتورة.",12f))
        formSection(parent,"إضافة من المخزون")
        field(parent,"بحث سريع عن المنتج","اكتب جزءاً من الاسم أو REF أو الرقم أو الباركود")
        select(parent,"اختر المنتج",listOf("لا توجد منتجات محمّلة بعد"))
        disabledAction(parent,"＋ إضافة المنتج")
        field(parent,"","امسح أو اكتب باركود الساعة")
        disabledAction(parent,"⌁ قراءة بالكاميرا")
        disabledAction(parent,"إضافة بالباركود")
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
        manualSaleItemPreview(lines,1,false,updateSubtotal)
        var nextLine=2
        parent.addView(text("＋ إضافة صنف يدوي",16f,gold).apply {
            isClickable=true
            setOnClickListener { manualSaleItemPreview(lines,nextLine++,true,updateSubtotal);updateSubtotal() }
        })
        parent.addView(liveSubtotal)
        formSection(parent,"الدفع والإرسال")
        select(parent,"حالة الدفع",listOf("مدفوع بالكامل","مدفوع جزئياً","عند الاستلام"))
        select(parent,"قاعدة الخصم",listOf("بدون قاعدة خصم"))
        parent.addView(text("قاعدة الخصم = خصم محفوظ مسبقاً لتستخدمه بسرعة، مثل «عميل مميز 10%».",12f))
        disabledAction(parent,"＋ إنشاء قاعدة خصم")
        field(parent,"خصم الفاتورة اليدوي","0").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        field(parent,"نقاط الولاء المستخدمة","0").apply {
            isEnabled=false
            inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        parent.addView(text("برنامج الولاء متوقف حالياً.",12f))
        field(parent,"نسبة الضريبة %","0").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        parent.addView(text("اختياري ومجهز للمستقبل. اتركه 0 حالياً.",12f))
        val collectionMode=select(parent,"نوع القبض",listOf("عملة واحدة","دفع مختلط (أكثر من عملة)"))
        parent.addView(text("للدفع مثل 55$ + 100₺ اختر دفع مختلط.",12f))
        val singlePayment=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(singlePayment)
        field(singlePayment,"المبلغ المقبوض الآن","0").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val mixedPayment=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        parent.addView(mixedPayment)
        mixedPayment.addView(text("المبالغ المقبوضة فعلياً",16f,gold))
        listOf("USD","TRY","SYP").forEach {
            field(mixedPayment,it,"0").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
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
            android.widget.Toast.makeText(this,"حفظ الفاتورة غير مفعّل حتى اكتمال دورة المبيع",android.widget.Toast.LENGTH_LONG).show()
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
        select(parent,"طريقة الدفع الأولى *",listOf("نقدي","شام كاش"))
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
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AlSafa")
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
        })
        
    }        formPair(parent,{ right ->
        },{ left ->
        })
        select(parent,"عملة الصيانة / القبض *",listOf("USD","TRY","SYP"))
        parent.addView(text("المبلغ يدخل صندوق هذه العملة نفسها.",12f))
        select(parent,"طريقة الدفع الأولى *",listOf("نقدي","شام كاش"))
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
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AlSafa")
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
        parent.addView(text("إلغاء",16f,gold).apply { isClickable=true;setOnClickListener { returnToPrevious() } })
        parent.addView(text("حفظ طلب الصيانة",18f,gold).apply {
            background=panel(Color.rgb(239,228,208),12)
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
        parent.addView(text("المنتجات والمخزون",26f,gold))
        parent.addView(text("أضف ساعاتك وأسعارها وكمياتها وباركودها، ثم اخترها مباشرة في الفاتورة.",14f))
        disabledAction(parent,"↻ تحديث")
        disabledAction(parent,"⬆ استيراد آمن")
        parent.addView(text("＋ منتج",18f,gold).apply {
            isClickable=true
            setOnClickListener { navigateTo("preview:إضافة منتج جديد") }
        })
        field(parent,"ابحث باسم المنتج أو الباركود أو REF / Model...")
        parent.addView(text("لم يتم تحميل المنتجات بعد.",15f))
    }
    private fun productFormPreview(parent:LinearLayout) {
        parent.addView(text("إضافة منتج جديد",26f,gold))
        field(parent,"اسم الساعة / المنتج *")
        field(parent,"الماركة")
        field(parent,"REF / الموديل (اختياري عند استخدام الرابط)","يُستخرج تلقائياً من صفحة المنتج عند توفره")
        select(parent,"فئة ساعة كاسيو",listOf("تلقائي من REF","كاسيو","Edifice","G-Shock","Baby-G","Pro Trek","Sheen","Oceanus","Vintage"))
        field(parent,"رابط صفحة المنتج (اختياري)","الصق رابط صفحة الساعة من أي ماركة").inputType=InputType.TYPE_TEXT_VARIATION_URI
        disabledAction(parent,"لصق الرابط")
        parent.addView(text("عند وضع الرابط يجلب من الصفحة نفسها فقط، دون تغيير محرك بحث الماركة الأساسي.",12f))
        disabledAction(parent,"✦ جلب معلومات الساعة تلقائيًا")
        parent.addView(text("أدخل الماركة وREF ثم اضغط مرة واحدة. يجلب المعلومات والمستشار والصور المتاحة في عملية واحدة، مع الحفاظ على أي بيانات أدخلتها يدويًا.",12f))
        field(parent,"كود المنتج")
        field(parent,"باركود المنتج","امسحه بالكاميرا أو اكتبه")
        disabledAction(parent,"⌁ قراءة بالكاميرا")
        parent.addView(text("يحفظ مرة واحدة ويستخدم لاستدعاء المنتج والسعر عند البيع.",12f))
        listOf("الصورة الرئيسية للساعة","صورة إضافية 1 (اختيارية)","صورة إضافية 2 (اختيارية)","صورة الباركود (اختيارية)").forEach { label ->
            parent.addView(text(label,16f,gold))
            disabledAction(parent,"📷 الكاميرا")
            disabledAction(parent,"🖼️ اختيار من الاستوديو")
            disabledAction(parent,"حذف الصورة")
        }
        field(parent,"الكمية بالمخزون *","1").inputType=InputType.TYPE_CLASS_NUMBER
        field(parent,"حد التنبيه الأدنى","1").inputType=InputType.TYPE_CLASS_NUMBER
        select(parent,"عملة الأسعار",listOf("USD","TRY","SYP"))
        field(parent,"سعر البيع *").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        field(parent,"كلفة الشراء للقطعة").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        formSection(parent,"كابلز (اختياري)")
        parent.addView(text("اربط الرجالي والنسائي فقط إذا كانا يباعان منفردين أو كطقم بسعر خاص. لا يؤثر هذا الربط على مجموعات الألوان.",12f))
        select(parent,"نوع القطعة في الكابلز",listOf("ليست كابلز","رجالي","نسائي"))
        select(parent,"القطعة المرتبطة",listOf("بدون ربط"))
        field(parent,"سعر الكابلز كاملًا").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        parent.addView(text("صورة الكابلز معًا (اختيارية)",16f,gold))
        disabledAction(parent,"📷 الكاميرا")
        disabledAction(parent,"🖼️ اختيار من الاستوديو")
        formSection(parent,"الشراكة الخارجية")
        parent.addView(text("استخدمها فقط للبضاعة التي رأس مالها من أحد الشريكين الخارجيين.",12f))
        select(parent,"مصدر رأس المال",listOf("بضاعة المحل — بدون شريك خارجي"))
        field(parent,"نسبة الشريك من الربح/الخسارة %").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        formSection(parent,"تفاصيل الساعة للعميل")
        parent.addView(text("هذه المعلومات تظهر في صفحة QR العامة للساعة.",12f))
        field(parent,"وصف الساعة","وصف قصير وجميل يظهر للعميل")
        field(parent,"مواصفات إضافية","مثال: تاريخ، منبه، إضاءة، كرونوغراف...")
        parent.addView(text("المجموعات والألوان تُنقل وفق بنية المنتج الأصلية، ولا تُحذف ألوان محفوظة عند التعديل.",12f))
        select(parent,"نوع القطعة في الكابلز",listOf("ليست كابلز","رجالي","نسائي"))
        select(parent,"القطعة المرتبطة",listOf("بدون قطعة مرتبطة"))
        field(parent,"سعر الكابلز كاملًا").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        parent.addView(text("الشراكات الخارجية",20f,gold))
        select(parent,"مصدر رأس المال",listOf("بضاعة المحل — بدون شريك خارجي"))
        field(parent,"نسبة الشريك من الربح/الخسارة %").inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        field(parent,"وصف الساعة","وصف قصير وجميل يظهر للعميل")
        field(parent,"مواصفات إضافية","مثال: تاريخ، منبه، إضاءة، كرونوغراف...")
        parent.addView(text("تعريف الساعة للمستشار",20f,gold))
        select(parent,"الجنس المناسب",listOf("تلقائي من بيانات المنتج","ذكر","أنثى","مناسب للجميع"))
        select(parent,"الحجم البصري",listOf("تلقائي","ناعم وصغير","متوسط ومتوازن","كبير وملفت"))
        listOf("الفئات العمرية المناسبة","الاستخدام المناسب","الأسلوب").forEach { parent.addView(text(it,15f)) }
        select(parent,"درجة اللفت",listOf("غير محدد","هادئ","متوازن","ملفت"))
        select(parent,"مناسبة كهدية",listOf("غير محدد","نعم","لا"))
        field(parent,"ملاحظات داخلية")
        parent.addView(text("إلغاء",16f,gold).apply { isClickable=true;setOnClickListener { returnToPrevious() } })
        disabledAction(parent,"⚡ حفظ + إضافة التالي")
        disabledAction(parent,"حفظ المنتج")
        parent.addView(text("معاينة شكلية فقط — لا تتم إضافة المنتج إلى المخزون.",12f))
    }
    private fun maintenancePartsPreview(parent:LinearLayout) {
        parent.addView(text("قطع الصيانة",26f,gold))
        parent.addView(text("بطاريات، أحزمة، زجاج وقطع غيار مع متابعة المخزون.",14f))
        disabledAction(parent,"＋ إضافة قطعة")
        field(parent,"ابحث باسم قطعة الصيانة أو الباركود...")
        parent.addView(text("لم يتم تحميل قطع الصيانة بعد.",15f))
    }
    private fun returnsPreview(parent:LinearLayout) {
        parent.addView(text("فواتير المرتجع والاستبدال",26f,gold))
        parent.addView(text("كل مرتجع محفوظ كمستند مستقل ومرتبط بفاتورته الأصلية.",14f))
        parent.addView(text("الأرشيف غير متصل بالبيانات بعد.",15f))
        parent.addView(text("لا تُنفذ أي إعادة للمخزون أو الخزنة من هذه المعاينة.",12f))
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
        parent.addView(text("Bluetooth · اختيار الطابعة وعرض الورق",14f))
        parent.addView(text("إعدادات الطابعة والورق غير مفعلة في معاينة الواجهة.",13f))
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
        val hint=text("لا توجد نتائج.",13f,Color.rgb(138,123,108))
        results.addView(hint)
        parent.addView(results)
        query.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                hint.text=if(s.isNullOrBlank()) "لا توجد نتائج." else "البحث في المبيعات والمخزون غير متصل بالبيانات بعد."
            }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
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
        val mark=text("◷",18f,Color.rgb(122,81,24)).apply {
            gravity=Gravity.CENTER
            background=GradientDrawable().apply {
                setColor(Color.rgb(249,243,232))
                cornerRadius=dp(22).toFloat()
                setStroke(dp(2),gold)
            }
        }
        brand.addView(mark,LinearLayout.LayoutParams(dp(43),dp(43)))
        val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        labels.addView(text("الصفا للساعات",19f,Color.rgb(122,81,24)).apply {
            gravity=Gravity.RIGHT
            typeface=Typeface.create(typeface,Typeface.BOLD)
            setPadding(0,0,0,0)
        })
        labels.addView(text("Alsafa Watches",11f,Color.rgb(138,123,108)).apply {
            gravity=Gravity.RIGHT
            setPadding(0,dp(3),0,0)
        })
        brand.addView(labels,LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(11) })
        bar.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        if(screen!="chooser") {
            bar.addView(text("رجوع",12f,gold).apply {
                background=panel(Color.rgb(255,253,249),10)
                isClickable=true
                setOnClickListener { returnToPrevious() }
            })
        }
        parent.addView(bar,LinearLayout.LayoutParams(-1,-2))
    }
    private fun render() {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pearl)
        }
        setContentView(root)
        activeRoot=root
        originalTopbar(root)
        val width=resources.displayMetrics.widthPixels
        val simpleMode=screen=="simple" || screen=="preview:مبيع" ||
            screen=="preview:صيانة سريعة" || screen=="preview:تسليم الساعات" ||
            screen=="preview:تسليم الوردية"
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
            workArea.addView(nav,LinearLayout.LayoutParams(dp(220),-1))
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
        val maxWidth=dp(if(wideLandscape) 1440 else if(tablet) 1280 else 680)
        val sidebarWidth=if(sidebar) dp(220) else 0
        val available=width-sidebarWidth
        body.setPadding(dp(if(tablet) { if(resources.configuration.screenWidthDp>=1200) 28 else 20 } else 15),dp(if(tablet) { if(resources.configuration.screenWidthDp>=1200) 24 else 20 } else 17),
            dp(if(tablet) { if(resources.configuration.screenWidthDp>=1200) 28 else 20 } else 15),dp(10))
        scroll.addView(body,android.widget.FrameLayout.LayoutParams(
            if(available>maxWidth) maxWidth else -1,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))
        if(showNavigation && !sidebar) {
            val nav=LegacyNavigation(this)
            nav.show(false,screen) { navigateTo(it) }
            root.addView(nav,LinearLayout.LayoutParams(-1,dp(68)))
        }
        when(screen) {
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
            "preview:إضافة منتج جديد" -> productFormPreview(body)
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
        if(screen!="chooser") body.addView(text("العودة",17f,gold).apply {
            isClickable=true;setOnClickListener {
                returnToPrevious()
            }
        })
    }

}
