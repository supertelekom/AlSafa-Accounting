package com.alsafa.native7

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.IdentityHashMap

/**
 * Android Views built from a source-derived layout specification. No HTML renderer,
 * WebView, JavaScript, network, database, or accounting writes are used here.
 * Values are layout previews. Submission actions deliberately remain inactive.
 */
internal class OriginalPageStructure(
    private val activity: Activity,
    private val navigate: (String) -> Unit,
    private val back: () -> Unit
) {
    private val pages by lazy { loadPages() }
    private fun loadPages():JSONObject {
        // Android packaging can strip .gz and expose the decompressed asset.
        // Support both packaged names and inspect bytes rather than the extension.
        val asset=try {
            activity.assets.open("original-structure.json.gz")
        } catch (_:java.io.FileNotFoundException) {
            activity.assets.open("original-structure.json")
        }
        return asset.buffered().use { input ->
            input.mark(2)
            val first=input.read()
            val second=input.read()
            input.reset()
            val decoded=if(first==0x1f && second==0x8b) java.util.zip.GZIPInputStream(input) else input
            decoded.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()).getJSONObject("pages") }
        }
    }
    private val font by lazy { Typeface.createFromAsset(activity.assets, "Tajawal-Medium.ttf") }
    private val ink = Color.rgb(62,52,41)
    private val muted = Color.rgb(138,123,108)
    private val gold = Color.rgb(122,81,24)
    private val goldAccent = Color.rgb(169,121,41)
    private val line = Color.rgb(234,223,206)
    private val metadata = IdentityHashMap<View,JSONObject>()
    private val controls = mutableMapOf<String,View>()
    private val radioNames = mutableMapOf<String,MutableList<RadioButton>>()
    private var root: View? = null
    private var currentRoute = ""
    private var unique = 0
    private fun dp(n:Int) = (n*activity.resources.displayMetrics.density+.5f).toInt()
    private fun items(array:JSONArray?) = (0 until (array?.length() ?: 0)).map { requireNotNull(array).getJSONObject(it) }
    private fun classes(n:JSONObject) = (0 until (n.optJSONArray("sourceClasses")?.length() ?: 0)).map { n.getJSONArray("sourceClasses").getString(it) }
    private fun bg(color:Int=Color.rgb(255,253,249),radius:Int=17,border:Int=line) = GradientDrawable().apply { setColor(color);cornerRadius=dp(radius).toFloat();setStroke(dp(1),border) }
    private fun column() = LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
    private fun childParams(node:JSONObject,parentKind:String):LinearLayout.LayoutParams {
        val kind=node.optString("kind")
        val margin=when {
            parentKind=="field" -> 6
            kind in listOf("section","form","summaryGrid","reportGrid") -> 14
            else -> 2
        }
        val css=classes(node)
        return LinearLayout.LayoutParams(-1,-2).apply {
            bottomMargin=dp(when {
                css.contains("form-section") -> 14
                css.contains("section-card") -> 14
                css.contains("section-title") -> 0
                css.contains("transaction") -> 0
                else -> margin
            })
            if(css.contains("form-section")) topMargin=dp(20)
            if(css.contains("section-card")) topMargin=dp(14)
        }
    }
     private fun tintAccentMetric(view:View,node:JSONObject) {
        val cs=classes(node)
        if(view is TextView) {
            view.setTextColor(if(cs.contains("metric-label") || cs.contains("metric-note"))
                Color.rgb(252,235,208) else Color.rgb(255,249,236))
        }
        if(view is ViewGroup) {
            // Native layout wrappers may contain extra views. Use the source
            // metadata attached during build instead of matching child indices.
            for(i in 0 until view.childCount) {
                val child=view.getChildAt(i)
                tintAccentMetric(child,metadata[child] ?: node)
            }
        }
    }
    private fun label(value:String,size:Float=13f,color:Int=ink,bold:Boolean=false) = TextView(activity).apply {
        text=value;textSize=size;setTextColor(color);typeface=if(bold) Typeface.create(font,Typeface.BOLD) else font
        gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;includeFontPadding=false
        layoutDirection=View.LAYOUT_DIRECTION_RTL;textDirection=View.TEXT_DIRECTION_FIRST_STRONG
        breakStrategy=android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
        hyphenationFrequency=android.text.Layout.HYPHENATION_FREQUENCY_NONE
    }
    fun contains(route:String) = pages.has(route)
    fun show(parent:LinearLayout,route:String) {
        metadata.clear();controls.clear();radioNames.clear();currentRoute=route
        val view=build(pages.getJSONObject(route))
        root=view
        parent.addView(view,LinearLayout.LayoutParams(-1,-2))
        view.post {
            // A posted callback from an older route must not update controls
            // belonging to a newly displayed route.
            if(root===view && currentRoute==route && view.parent!=null) conditions()
        }
    }
    private fun register(view:View,n:JSONObject):View {
        metadata[view]=n
        if(n.has("sourceId")) controls[n.getString("sourceId")]=view
        view.tag=n.optString("key")
        if(n.optBoolean("hidden")) view.visibility=View.GONE
        return view
    }
    private fun build(n:JSONObject):View {
        val kind=n.getString("kind")
        val cs=classes(n)
        val children=items(n.optJSONArray("children"))
        val view:View=when(kind) {
            "input" -> input(n)
            "select" -> selection(n)
            "toggle" -> toggle(n)
            "button" -> button(n)
            "title","text" -> label(n.optString("text"),if(kind=="text" && n.optString("tag")=="label") 11f else n.optDouble("size",13.0).toFloat(),if(n.optBoolean("muted") || (kind=="text" && n.optString("tag")=="label")) muted else ink,n.optString("weight")=="bold").apply {
                setPadding(0,dp(if(kind=="title") 3 else 2),0,dp(if(kind=="title") 7 else 5))
                if(cs.any { it in listOf("barcode-help","help") }) { textSize=11f;setTextColor(muted) }
                if(cs.contains("metric-label")) { textSize=11f;setTextColor(muted) }
                if(cs.contains("metric-value")) { textSize=18f;typeface=Typeface.create(font,Typeface.BOLD);setPadding(0,dp(8),0,0) }
                if(cs.contains("metric-note")) { textSize=10f;setTextColor(muted);setPadding(0,dp(5),0,0) }
                if(cs.contains("form-section")) { textSize=13f;typeface=Typeface.create(font,Typeface.BOLD);setTextColor(gold) }
                if(cs.contains("report-tile-value")) { textSize=14f;typeface=Typeface.create(font,Typeface.BOLD);setTextColor(gold);setPadding(0,dp(7),0,0) }
                if(cs.contains("report-tile-label")) { textSize=10f;setTextColor(muted) }
                if(cs.contains("transaction-icon")) {
                    textSize=18f;gravity=Gravity.CENTER
                    minWidth=dp(34);minHeight=dp(34)
                    background=GradientDrawable().apply {
                        setColor(Color.rgb(247,237,221));cornerRadius=dp(11).toFloat()
                    }
                    setTextColor(goldAccent)
                }
                if(cs.contains("transaction-amount")) {
                    textSize=12f;typeface=Typeface.create(font,Typeface.BOLD)
                    setSingleLine(true)
                    if(cs.contains("negative")) setTextColor(Color.rgb(173,54,54))
                }
                if(cs.contains("transaction-info")) { textSize=12f;maxLines=2 }
                if(cs.contains("empty")) {
                    textSize=13f
                    setTextColor(muted)
                    gravity=Gravity.CENTER
                    setLineSpacing(0f,1.7f)
                    setPadding(dp(18),dp(27),dp(18),dp(27))
                }
                if(n.optString("tag")=="strong" && cs.contains("transaction-title")) {
                    textSize=12f;typeface=Typeface.create(font,Typeface.BOLD)
                    setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END
                }
                if(cs.contains("transaction-subtitle")) {
                    textSize=10f;setTextColor(muted);setPadding(0,dp(3),0,0)
                }
                if(cs.contains("section-title") && kind=="title") { textSize=15f;typeface=Typeface.create(font,Typeface.BOLD) }
                if(cs.contains("link-button")) {
                    textSize=12f
                    setTextColor(Color.rgb(122,81,24))
                    setPadding(0,0,0,0)
                }
                if(cs.contains("form-section")) {
                    // The original source uses --gold-deep for section headings.
                    textSize=13f
                    typeface=Typeface.create(font,Typeface.BOLD)
                    setTextColor(Color.rgb(122,81,24))
                }
                if(kind=="text" && n.optString("tag")=="label") setPadding(0,0,0,dp(6))
                if(cs.contains("simple-icon")) {
                    val compact=activity.resources.configuration.screenWidthDp<=420
                    textSize=if(compact) 22f else 25f
                    gravity=Gravity.CENTER
                    minWidth=dp(if(compact) 40 else 46)
                    minHeight=dp(if(compact) 40 else 46)
                    background=GradientDrawable().apply {
                        setColor(Color.rgb(248,237,219))
                        cornerRadius=dp(14).toFloat()
                    }
                }
            }
            "picture" -> label(n.optString("text","لا توجد صورة"),10f,muted).apply {
                gravity=Gravity.CENTER;minHeight=dp(84);background=bg(Color.rgb(255,250,242),12,Color.rgb(223,201,164))
            }
            "line" -> View(activity).apply {
                setBackgroundColor(line)
                minimumHeight=dp(1)
                layoutParams=ViewGroup.LayoutParams(-1,dp(1))
            }
            "formGrid","summaryGrid","reportGrid","manageGrid","simpleGrid","actionGrid","repairGrid","radioGrid","modeGrid","choiceGrid","modernGrid" -> {
                val tablet=activity.resources.configuration.screenWidthDp>=720
                val wide=activity.resources.configuration.screenWidthDp>=1200 && activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val count=when(kind) {
                    "formGrid" -> if(wide) 3 else if(tablet) 2 else if(activity.resources.configuration.screenWidthDp>380) 2 else 1
                    "manageGrid" -> if(tablet) 4 else 2
                    "modernGrid" -> if(tablet) 3 else 2
                    "summaryGrid","reportGrid" -> if(cs.contains("currency-home-values")) 3 else if(cs.contains("home-currency-ledger")) { if(activity.resources.configuration.screenWidthDp>760) 3 else 1 } else if(kind=="reportGrid" && tablet) 3 else 2
                    "choiceGrid" -> if(tablet) 4 else 2
                    "simpleGrid" -> if(tablet) 3 else 2
                    "actionGrid" -> if(tablet) 3 else 2
                    "repairGrid" -> if(activity.resources.configuration.screenWidthDp>=1024 ||
                        (activity.resources.configuration.screenWidthDp>=900 &&
                         activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)) 4 else if(tablet) 3 else 2
                    else -> 2
                }
                SourceGrid(activity,count,dp(when(kind) { "reportGrid" -> 9; "repairGrid" -> 7; "manageGrid","summaryGrid" -> 10; else -> 11 })).apply {
                    children.forEach { child ->
                        val childClasses=classes(child)
                        val fullSpan=child.optBoolean("full") || childClasses.contains("full") ||
                            child.optString("sourceStyle").contains("grid-column: 1 / -1")
                        addCell(build(child),fullSpan)
                    }
                }
            }
            "heading","inputRow","photoRow","searchRow" -> LinearLayout(activity).apply {
                orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;gravity=Gravity.CENTER_VERTICAL
                children.forEachIndexed { index,child ->
                    val content=build(child)
                    addView(content,LinearLayout.LayoutParams(if(index==0 && kind!="photoRow") 0 else if(index==0 && kind=="photoRow") dp(116) else if(kind=="photoRow") 0 else -2,-2,
                        if(index==0 && kind!="photoRow" || index>0 && kind=="photoRow") 1f else 0f).apply { if(index<children.lastIndex) marginEnd=dp(if(kind=="photoRow") 12 else 9) })
                }
                if(kind=="heading") setPadding(0,dp(2),0,dp(14))
                if(kind=="searchRow") minimumHeight=dp(42)
            }
            // The legacy report filter is a CSS grid, not a vertical stack:
            // two flexible fields and one auto-width action on tablets.
            "reportFilter" -> reportFilter(children)
            "actions" -> SourceGrid(activity,children.size.coerceIn(1,3),dp(9)).apply {
                setPadding(0,dp(18),0,dp(9))
                children.forEach { addCell(build(it),false) }
            }
            "flow" -> SourceFlow(activity,dp(9)).apply {
                children.forEach { addView(build(it),ViewGroup.LayoutParams(-2,-2)) }
                if(kind=="actions") setPadding(0,dp(18),0,dp(9))
            }
            "fold" -> column().apply {
                val initiallyOpen=n.optBoolean("open") || n.optJSONObject("data")?.optString("open")=="true"
                val body=column().apply { visibility=if(initiallyOpen) View.VISIBLE else View.GONE }
                val summary=children.firstOrNull()
                if(summary!=null) addView(build(summary).apply {
                    isClickable=true
                    isFocusable=true
                    contentDescription=summary.optString("text").ifEmpty { "تفاصيل" }
                    setOnClickListener {
                        val expand=body.visibility!=View.VISIBLE
                        // Native details/summary semantics: show or hide immediately.
                        // Do not invent a fade animation absent from the source.
                        body.animate().cancel()
                        body.alpha=1f
                        body.visibility=if(expand) View.VISIBLE else View.GONE
                        isSelected=expand
                    }
                    isSelected=initiallyOpen
                    importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
                })
                children.drop(1).forEach { body.addView(build(it)) }
                addView(body)
            }
            else -> if(cs.contains("report-filter")) reportFilter(children) else column().apply {
                children.forEachIndexed { index,child ->
                    val params=childParams(child,kind)
                    // CSS .form-section:first-child removes the default top margin.
                    if(index==0 && classes(child).contains("form-section")) params.topMargin=0
                    addView(build(child),params)
                }
                // Original .transaction:last-child has no bottom border.
                // Do not remove borders from non-transaction siblings.
                val lastVisible=(childCount-1 downTo 0)
                    .map { getChildAt(it) }.firstOrNull { it.visibility!=View.GONE }
                if(lastVisible!=null && metadata[lastVisible]?.let { classes(it).contains("transaction") }==true)
                    lastVisible.background=null
                when(kind) {
                    "section","form" -> {
                        background=bg()
                        // .section-card is a border-only wrapper; its heading and
                        // body own their padding. A .form-card is padded instead.
                        val inset=when {
                            cs.contains("section-card") -> 0
                            kind=="form" && activity.resources.configuration.screenWidthDp>=720 -> 22
                            else -> 15
                        }
                        setPadding(dp(inset),dp(inset),dp(inset),dp(inset))
                    }
                    "stat" -> {
                        background=bg(Color.rgb(255,253,249),16)
                        minimumHeight=dp(101)
                        setPadding(dp(14),dp(14),dp(14),dp(14))
                        if(cs.contains("accent")) {
                            background=GradientDrawable(
                                GradientDrawable.Orientation.TL_BR,
                                intArrayOf(Color.rgb(178,130,50),Color.rgb(139,96,31))
                            ).apply { cornerRadius=dp(16).toFloat() }
                            for(i in 0 until childCount) tintAccentMetric(getChildAt(i),metadata[getChildAt(i)] ?: n)
                        }
                    }
                    "sectionHeading" -> setPadding(0,dp(20),0,dp(14))
                    "empty" -> { setPadding(dp(18),dp(27),dp(18),dp(27));gravity=Gravity.CENTER }
                    "field" -> setPadding(0,0,0,0)
                    "help" -> setPadding(0,dp(4),0,dp(10))
                    else -> Unit
                }
                // Source classes are orthogonal to the structural kind. Apply these
                // after the kind switch so a 'section' or 'form' also gets its CSS.
                run {
                        if(cs.contains("transaction")) {
                            // Source .transaction is a horizontal flex row, not
                            // a vertical generic container.
                            orientation=LinearLayout.HORIZONTAL
                            gravity=Gravity.CENTER_VERTICAL
                            setPadding(dp(15),dp(12),dp(15),dp(12))
                            for(i in 0 until childCount) {
                                val child=getChildAt(i)
                                val source=metadata[child]
                                val childClasses=source?.let { classes(it) } ?: emptySet<String>()
                                child.layoutParams=LinearLayout.LayoutParams(
                                    if(childClasses.contains("transaction-info")) 0 else -2,
                                    -2,
                                    if(childClasses.contains("transaction-info")) 1f else 0f
                                ).apply { if(i>0) marginStart=dp(10) }
                            }
                            // CSS: 1px bottom divider #f4ede3.
                            background=object:android.graphics.drawable.Drawable() {
                                private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color=Color.rgb(244,237,227)
                                    strokeWidth=dp(1).toFloat()
                                }
                                override fun draw(canvas:android.graphics.Canvas) {
                                    val y=bounds.bottom-dp(1)/2f
                                    canvas.drawLine(bounds.left.toFloat(),y,bounds.right.toFloat(),y,paint)
                                }
                                override fun setAlpha(alpha:Int) { paint.alpha=alpha;invalidateSelf() }
                                override fun setColorFilter(filter:android.graphics.ColorFilter?) { paint.colorFilter=filter;invalidateSelf() }
                                @Deprecated("Deprecated in Java")
                                override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
                            }
                        }
                        if(cs.contains("transaction-info")) {
                            // The original CSS targets nested strong/span tags, not
                            // fabricated transaction-title/subtitle classes.
                            for(i in 0 until childCount) {
                                val child=getChildAt(i)
                                val source=metadata[child]
                                if(child is TextView && source!=null) {
                                    when(source.optString("tag")) {
                                        "strong" -> {
                                            child.textSize=12f
                                            child.typeface=Typeface.create(font,Typeface.BOLD)
                                            child.setSingleLine(true)
                                            child.ellipsize=android.text.TextUtils.TruncateAt.END
                                        }
                                        "span" -> {
                                            child.textSize=10f
                                            child.setTextColor(muted)
                                            child.setPadding(child.paddingLeft,dp(3),
                                                child.paddingRight,child.paddingBottom)
                                        }
                                    }
                                }
                            }
                        }
                        if(cs.contains("report-tile")) {
                            background=bg(Color.rgb(255,253,249),13)
                            minimumHeight=dp(78)
                            setPadding(dp(12),dp(12),dp(12),dp(12))
                        }
                        if(cs.contains("metric-card")) {
                            background=bg(Color.rgb(255,253,249),16)
                            minimumHeight=dp(101)
                            setPadding(dp(14),dp(14),dp(14),dp(14))
                            if(cs.contains("accent")) {
                                background=GradientDrawable(
                                    GradientDrawable.Orientation.TL_BR,
                                    intArrayOf(Color.rgb(178,130,50),Color.rgb(139,96,31))
                                ).apply { cornerRadius=dp(16).toFloat() }
                                for(i in 0 until childCount) tintAccentMetric(getChildAt(i),metadata[getChildAt(i)] ?: n)
                            }
                        }
                        if(cs.contains("section-card")) {
                            background=bg(Color.rgb(255,253,249),17)
                            setPadding(0,0,0,0)
                            // CSS overflow:hidden keeps section headers and rows
                            // inside the rounded card boundary.
                            clipToOutline=true
                            clipChildren=true
                        }
                        if(cs.contains("section-title")) {
                            orientation=LinearLayout.HORIZONTAL
                            gravity=Gravity.CENTER_VERTICAL
                            // CSS .section-title h3 and .link-button are
                            // descendant selectors: match source tags/classes.
                            for(i in 0 until childCount) {
                                val child=getChildAt(i)
                                val source=metadata[child]
                                if(child is TextView && source!=null) {
                                    if(source.optString("tag")=="h3") {
                                        child.textSize=15f
                                        child.typeface=Typeface.create(font,Typeface.BOLD)
                                        child.setPadding(0,0,0,0)
                                    }
                                    if(classes(source).contains("link-button")) {
                                        child.textSize=12f
                                        child.setTextColor(Color.rgb(122,81,24))
                                        child.setPadding(0,0,0,0)
                                    }
                                }
                            }
                            for(i in 0 until childCount) {
                                val child=getChildAt(i)
                                val existing=child.layoutParams as? LinearLayout.LayoutParams
                                child.layoutParams=LinearLayout.LayoutParams(
                                    if(i==0) 0 else -2,-2,if(i==0) 1f else 0f
                                ).apply {
                                    if(i>0) marginStart=dp(9)
                                    bottomMargin=0
                                }
                            }
                            setPadding(dp(15),dp(14),dp(15),dp(14))
                            // Legacy .section-title has a bottom rule, not a full box border.
                            background=object:android.graphics.drawable.Drawable() {
                                private val rule=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color=line
                                    strokeWidth=dp(1).toFloat()
                                }
                                override fun draw(canvas:android.graphics.Canvas) {
                                    val y=bounds.bottom-dp(1).toFloat()/2f
                                    canvas.drawLine(bounds.left.toFloat(),y,bounds.right.toFloat(),y,rule)
                                }
                                override fun setAlpha(alpha:Int) { rule.alpha=alpha;invalidateSelf() }
                                override fun setColorFilter(filter:android.graphics.ColorFilter?) { rule.colorFilter=filter;invalidateSelf() }
                                @Deprecated("Deprecated in Java")
                                override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
                            }
                        }
                        if(cs.contains("report-filter")) {
                            background=bg(Color.rgb(255,253,249),15)
                            setPadding(dp(13),dp(13),dp(13),dp(13))
                        }
                        if(cs.contains("form-card")) {
                            background=bg(Color.rgb(255,253,249),17)
                            val inset=if(activity.resources.configuration.screenWidthDp>=720) 22 else 15
                            setPadding(dp(inset),dp(inset),dp(inset),dp(inset))
                        }
                }
            }
        }
        return register(view,n)
    }
    /**
     * Source .report-filter: two flexible columns and an intrinsic-width action.
     * At <=380dp the action occupies a separate full-width row.
     * Keep child ordering unchanged for keyboard navigation and accessibility.
     */
    private fun reportFilter(nodes:List<JSONObject>):View {
        val compact=activity.resources.configuration.screenWidthDp<=380
        val outer=column().apply {
            background=bg(Color.rgb(255,253,249),15)
            setPadding(dp(13),dp(13),dp(13),dp(13))
        }
        val row=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.BOTTOM
        }
        var fields=0
        val compactActions=mutableListOf<View>()
        for(node in nodes) {
            val child=build(node)
            val action=node.optString("kind")=="button"
            if(action) {
                child.minimumHeight=dp(42)
                child.setPadding(dp(12),0,dp(12),0)
            } else {
                // Original CSS: .report-filter .field label { font-size:10px }
                fun adjustLabels(view:View) {
                    if(view is TextView && metadata[view]?.optString("tag")=="label")
                        view.textSize=10f
                    if(view is ViewGroup) for(i in 0 until view.childCount)
                        adjustLabels(view.getChildAt(i))
                }
                adjustLabels(child)
            }
            if(action) {
                if(compact) compactActions.add(child)
                else row.addView(child,LinearLayout.LayoutParams(-2,-2).apply {
                    if(fields>0) marginStart=dp(8)
                })
            } else {
                row.addView(child,LinearLayout.LayoutParams(0,-2,1f).apply {
                    if(fields>0) marginStart=dp(8)
                })
                fields++
            }
        }
        outer.addView(row,LinearLayout.LayoutParams(-1,-2))
        compactActions.forEach { action ->
            outer.addView(action,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        }
        return outer
    }
    private fun input(n:JSONObject):View {
        val type=n.optString("type")
        val date=type in listOf("date","month","time")
        val edit=EditText(activity).apply {
            typeface=font;textSize=14f;setTextColor(ink);setHintTextColor(muted)
            gravity=Gravity.RIGHT or (if(type=="multiline") Gravity.TOP else Gravity.CENTER_VERTICAL)
            layoutDirection=View.LAYOUT_DIRECTION_RTL;textDirection=View.TEXT_DIRECTION_FIRST_STRONG
            setPadding(dp(10),dp(9),dp(10),dp(9));background=bg(Color.rgb(255,252,247),10)
            minHeight=dp(42);hint=n.optString("placeholder")
            inputType=when {
                type=="multiline" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                type=="password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                type=="email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                type=="tel" || n.optString("inputmode")=="tel" -> InputType.TYPE_CLASS_PHONE
                type=="number" || n.optString("inputmode")=="decimal" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                n.optString("inputmode")=="numeric" -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT
            }
            if(type=="multiline") { minHeight=dp(78);minLines=3;maxLines=8 } else setSingleLine(true)
            val value=when(n.optString("value")) {
                "@today" -> java.time.LocalDate.now().toString()
                "@monthStart" -> java.time.LocalDate.now().withDayOfMonth(1).toString()
                "@time" -> java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                "@month" -> java.time.YearMonth.now().toString()
                else -> n.optString("value")
            }
            setText(value)
            if(n.optJSONObject("data")?.let { data ->
                listOf("data-purchase-qty","data-purchase-unit-cost","data-variant-qty","data-variant-cost").any { data.has(it) }
            }==true || n.optString("sourceId")=="purchase-paid") {
                addTextChangedListener(object:TextWatcher {
                    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { updatePurchaseTotals() }
                    override fun afterTextChanged(s:Editable?) {}
                })
            }
            isEnabled=!n.optBoolean("disabled")
            if(n.optBoolean("readonly")) {
                isFocusable=false
                isCursorVisible=false
                if(!date) isClickable=false
            }
            if(date) {
                isFocusable=false;isClickable=true
                setOnClickListener {
                    if(type=="time") {
                        val now=runCatching { java.time.LocalTime.parse(text.toString()) }.getOrDefault(java.time.LocalTime.now())
                        android.app.TimePickerDialog(activity,{ _,h,m -> setText(String.format(java.util.Locale.US,"%02d:%02d",h,m)) },now.hour,now.minute,true).show()
                    } else {
                        val now=runCatching { java.time.LocalDate.parse(if(type=="month") text.toString()+"-01" else text.toString()) }.getOrDefault(java.time.LocalDate.now())
                        android.app.DatePickerDialog(activity,{ _,y,m,d ->
                            setText(if(type=="month") java.time.YearMonth.of(y,m+1).toString() else java.time.LocalDate.of(y,m+1,d).toString())
                        },now.year,now.monthValue-1,now.dayOfMonth).show()
                    }
                }
            }
        }
        return edit
    }
    private fun selection(n:JSONObject):View {
        val options=items(n.optJSONArray("options"))
        return Spinner(activity).apply {
            background=bg(Color.rgb(255,252,247),10);setPadding(dp(6),dp(4),dp(6),dp(4));minimumHeight=dp(42)
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            adapter=object:ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,options.map { it.optString("label") }) {
                private fun style(view:View)=view.apply { (this as? TextView)?.apply { typeface=font;textSize=13f;setTextColor(ink);gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
                    layoutDirection=View.LAYOUT_DIRECTION_RTL;textDirection=View.TEXT_DIRECTION_FIRST_STRONG } }
                override fun getView(position:Int,convertView:View?,parent:ViewGroup)=style(super.getView(position,convertView,parent))
                override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup)=style(super.getDropDownView(position,convertView,parent))
                override fun isEnabled(position:Int)=!options[position].optBoolean("disabled")
                override fun areAllItemsEnabled()=options.none { it.optBoolean("disabled") }
            }
            if(options.isNotEmpty()) {
                val selected=n.optInt("selected").coerceIn(options.indices)
                val valid=if(!options[selected].optBoolean("disabled")) selected
                    else options.indices.firstOrNull { !options[it].optBoolean("disabled") } ?: selected
                setSelection(valid)
            }
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent:AdapterView<*>?) {}
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { conditions() }
            }
        }
    }
    private fun toggle(n:JSONObject):View {
        val radio=n.optString("inputType")=="radio"
        val control=if(radio) RadioButton(activity) else CheckBox(activity)
        control.apply {
            text=n.optString("text");typeface=font;textSize=12f;setTextColor(ink);layoutDirection=View.LAYOUT_DIRECTION_RTL
            isChecked=n.optBoolean("checked");minHeight=dp(42)
            isEnabled=!n.optBoolean("disabled")
            if(radio) {
                val group=radioNames.getOrPut(n.optString("name")) { mutableListOf() }
                group.add(this as RadioButton)
                setOnClickListener { group.forEach { it.isChecked=it===this };conditions() }
            } else {
                setOnCheckedChangeListener { _,_ -> conditions() }
            }
        }
        return control
    }
    private fun button(n:JSONObject):View {
        val cs=classes(n)
        val rich=n.optJSONArray("children")
        val view=if(rich!=null) column().apply {
            val horizontal=cs.any { it in listOf("simple-action","modern-feature-card","mode-card","mode-advisor-card") } && !cs.contains("father-main-card")
            orientation=if(horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity=if(cs.contains("father-main-card") || cs.contains("simple-action")) Gravity.CENTER else Gravity.CENTER_VERTICAL
            items(rich).forEachIndexed { index,child ->
                val content=build(child).apply { if(this is TextView && index==0) {
                    textSize=if(cs.contains("father-main-card")) { if(activity.resources.configuration.screenWidthDp<=420) 42f else 51f } else if(cs.contains("mode-card")) 31f else if(cs.contains("modern-feature-card")) 28f else if(cs.contains("manage-card")) 25f else if(cs.contains("quick-service")) 23f else if(cs.contains("simple-action")) 25f else 25f
                    if(!horizontal || cs.contains("simple-action")) gravity=Gravity.CENTER
                } else if(this is TextView && index>0) {
                    textSize=when { cs.contains("father-main-card") && index==1 -> if(activity.resources.configuration.screenWidthDp<=420) 20f else 24f;cs.contains("mode-card") && index==1 -> 20f;cs.contains("quick-service") && index==1 -> 16f;cs.contains("quick-service") && index==2 -> 15f;cs.contains("simple-action") && index==1 -> 17f;cs.contains("modern-feature-card") && index==1 -> 15f;cs.contains("manage-card") && index==1 -> 14f;index==1 -> 14f;cs.contains("father-main-card") -> 11f;cs.contains("mode-card") -> 11f;cs.contains("quick-service") -> 11f;cs.contains("simple-action") -> 11f;else -> 10f }
                } }
                addView(content,if(horizontal) LinearLayout.LayoutParams(if(index==1) 0 else if(index==0) dp(if(cs.contains("mode-card")) 58 else if(cs.contains("modern-feature-card")) 42 else if(cs.contains("simple-action")) (if(activity.resources.configuration.screenWidthDp<=420) 40 else 46) else 46) else -2,-2,if(index==1) 1f else 0f).apply { if(index==0 && horizontal) marginEnd=dp(if(cs.contains("mode-card")) 15 else if(cs.contains("modern-feature-card")) 13 else if(cs.contains("simple-action")) (if(activity.resources.configuration.screenWidthDp<=420) 9 else 12) else 12)
                    if(cs.contains("father-main-card") && index==1) topMargin=dp(13)
                    if(cs.contains("father-main-card") && index==2) topMargin=dp(8)
                    if(cs.contains("manage-card") && index==1) topMargin=dp(8)
                    if(cs.contains("manage-card") && index==2) topMargin=dp(5)
                    if(cs.contains("simple-action") && index==2) topMargin=dp(5)
                    if(cs.contains("quick-service") && index==1) topMargin=dp(3)
                    if(cs.contains("quick-service") && index==2) topMargin=dp(3) }
                    else LinearLayout.LayoutParams(-1,-2))
            }
            minimumHeight=dp(when { cs.contains("father-main-card") -> if(activity.resources.configuration.screenWidthDp<=420) 155 else 185;cs.contains("mode-card") -> 122;cs.contains("manage-card") -> 115;cs.contains("modern-feature-card") -> 86;cs.contains("quick-service") -> when {
                activity.resources.configuration.screenWidthDp>=900 &&
                    activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE -> 72
                activity.resources.configuration.screenWidthDp<=420 -> 74
                else -> 78
            };cs.contains("simple-action") -> 52;else -> 104 })
            setPadding(dp(if(cs.contains("mode-card")) 18 else if(cs.contains("modern-feature-card")) 16 else if(cs.contains("simple-action")) 14 else if(cs.contains("father-main-card")) (if(activity.resources.configuration.screenWidthDp<=420) 8 else 18) else 15),dp(if(cs.contains("mode-card")) 18 else if(cs.contains("modern-feature-card")) 16 else if(cs.contains("simple-action")) 8 else if(cs.contains("father-main-card")) (if(activity.resources.configuration.screenWidthDp<=420) 13 else 18) else 15),dp(if(cs.contains("mode-card")) 18 else if(cs.contains("modern-feature-card")) 16 else if(cs.contains("simple-action")) 7 else if(cs.contains("father-main-card")) (if(activity.resources.configuration.screenWidthDp<=420) 8 else 18) else 15),dp(if(cs.contains("mode-card")) 18 else if(cs.contains("modern-feature-card")) 16 else if(cs.contains("simple-action")) 6 else if(cs.contains("father-main-card")) (if(activity.resources.configuration.screenWidthDp<=420) 13 else 18) else 15))
            if(cs.contains("quick-service")) {
                val compact=activity.resources.configuration.screenWidthDp<=420 ||
                    (activity.resources.configuration.screenWidthDp>=900 &&
                    activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                setPadding(dp(if(compact) 4 else 5),dp(if(compact) 5 else 6),
                    dp(if(compact) 4 else 5),dp(if(compact) 5 else 6))
                gravity=Gravity.CENTER
            }
            background=when {
                cs.contains("quick-service") -> bg(Color.rgb(255,250,242),14,line).apply { setStroke(dp(2),line) }
                cs.contains("father-main-card") -> GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(255,253,249),Color.rgb(247,234,211))).apply { cornerRadius=dp(24).toFloat();setStroke(dp(2),Color.rgb(216,189,136)) }
                cs.contains("advanced") || cs.contains("mode-advisor-card") -> GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(46,40,34),Color.rgb(62,51,39))).apply { cornerRadius=dp(26).toFloat() }
                cs.contains("father") -> GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(255,250,242),Color.rgb(248,237,217))).apply { cornerRadius=dp(26).toFloat();setStroke(dp(2),Color.rgb(216,189,136)) }
                cs.contains("simple-action") -> bg(
                    radius=18,
                    border=if(cs.contains("sale")) Color.rgb(218,195,152) else line
                )
                else -> bg(radius=if(cs.contains("manage-card")) 16 else 18)
            }
            if(cs.contains("simple-action")) {
                val iconColor=when {
                    cs.contains("deliver") -> Color.rgb(234,244,236)
                    cs.contains("debt") -> Color.rgb(255,245,223)
                    cs.contains("expense") -> Color.rgb(255,240,237)
                    else -> Color.rgb(248,237,219)
                }
                for(i in 0 until childCount) {
                    val child=getChildAt(i)
                    if(metadata[child]?.let { classes(it).contains("simple-icon") }==true)
                        child.background=GradientDrawable().apply {
                            setColor(iconColor)
                            cornerRadius=dp(14).toFloat()
                        }
                }
            }
        } else label(n.optString("text"),13f,if(n.optBoolean("primary")) Color.WHITE else gold,true).apply {
            gravity=Gravity.CENTER;setPadding(dp(12),dp(10),dp(12),dp(10));minHeight=dp(if(cs.contains("close")) 32 else if(cs.contains("tiny-button")) 25 else if(cs.contains("father-complete")) 64 else 44)
            background=if(n.optBoolean("primary")) GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(179,131,50),Color.rgb(139,96,31))).apply { cornerRadius=dp(12).toFloat() }
                else bg(Color.rgb(247,237,221),12,Color.rgb(223,201,164))
        }
        if(cs.contains("advanced") || cs.contains("mode-advisor-card")) {
            fun light(child:View) {
                if(child is TextView) child.setTextColor(Color.rgb(239,226,206))
                if(child is ViewGroup) for(i in 0 until child.childCount) light(child.getChildAt(i))
            }
            light(view)
        }
        // Legacy CSS: .simple-action:active { transform:scale(.985) }.
        if(cs.contains("simple-action")) {
            view.setOnTouchListener { target,event ->
                when(event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        target.animate().cancel()
                        target.scaleX=0.985f;target.scaleY=0.985f
                    }
                    android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL -> {
                        target.animate().cancel()
                        target.scaleX=1f;target.scaleY=1f
                    }
                }
                false // Preserve the normal click and accessibility behavior.
            }
        }
        view.isClickable=true;view.isFocusable=true;view.contentDescription=n.optString("text")
        view.isEnabled=!n.optBoolean("disabled")
        view.setOnClickListener {
            val route=n.optString("route").ifEmpty { n.optString("targetRoute") }
            val action=n.optString("action")
            when {
                route.isNotEmpty() -> navigate(route)
                action=="close-modal" -> back()
                action in actionPages -> navigate(actionPages.getValue(action))
                action in operationalActions -> navigate(operationalActions.getValue(action))
                action=="purchase-add-line" -> duplicatePurchaseLine(false)
                action=="purchase-quick-add" -> duplicatePurchaseLine(true)
                action=="purchase-remove-line" -> removePurchaseLine(view)
                action=="add-manual-item" -> addTemplate("sale-items","sale-line-template")
                action=="add-color-variant" -> addVariant(view)
                action=="remove-color-variant" -> removeMatching(view,"color-variant-row")
                action=="select-quick-repair" -> {
                    val manual=n.optJSONObject("data")?.optString("data-manual-price")=="1"
                    val data=n.optJSONObject("data")
                    val service=data?.optString("data-service").orEmpty()
                    val price=data?.optString("data-price").orEmpty()
                    val quickId=data?.optString("data-quick-id").orEmpty()
                    fun fill(id:String,value:String) {
                        (controls[id] as? EditText)?.setText(value)
                    }
                    fill("father-repair-service",service)
                    fill("father-repair-quick-id",quickId)
                    fill("father-repair-price",if(manual) "" else price)
                    fill("father-repair-base-amount",if(manual) "" else price)
                    fill("father-custom-repair-amount",if(manual) "" else price)
                    fill("father-custom-repair-work","")
                    controls["father-custom-repair"]?.visibility=View.VISIBLE
                    if(manual) (controls["father-custom-repair-amount"] as? EditText)?.post {
                        (controls["father-custom-repair-amount"] as? EditText)?.requestFocus()
                    }
                    // Only one quick-repair choice should remain visually selected.
                    metadata.forEach { (other,node) ->
                        if(node.optString("action")=="select-quick-repair") {
                            other.background=if(other===view)
                                bg(Color.rgb(243,229,204),14,Color.rgb(183,138,62))
                            else bg(Color.rgb(247,237,221),12,Color.rgb(223,201,164))
                        }
                    }
                }
                action=="remove-item" -> removeMatching(view,"line-item")
                else -> Toast.makeText(activity,"معاينة الواجهة فقط — الوظيفة غير مفعلة بعد",Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }
    private fun ancestors(view:View):Sequence<View> = generateSequence(view) { it.parent as? View }
    private fun purchaseRow(view:View)=ancestors(view).firstOrNull { metadata[it]?.let { n -> classes(n).contains("purchase-line") }==true }
    private fun removePurchaseLine(view:View) {
        purchaseRow(view)?.let { row ->
            (row.parent as? ViewGroup)?.removeView(row)
            unregisterDetached(row)
            conditions()
        }
    }
    /** Dynamic rows must never reuse DOM-derived IDs from their source template. */
    private fun uniqueDynamicIds(node:JSONObject) {
        val suffix="-copy-${++unique}"
        fun visit(part:JSONObject) {
            if(part.has("sourceId")) part.put("sourceId",part.getString("sourceId")+suffix)
            if(part.has("key")) part.put("key",part.getString("key")+suffix)
            if(part.optString("kind")=="toggle" && part.optString("inputType")=="radio" && part.has("name"))
                part.put("name",part.getString("name")+suffix)
            items(part.optJSONArray("children")).forEach { visit(it) }
        }
        visit(node)
    }
    private fun duplicatePurchaseLine(quick:Boolean) {
        val host=controls["purchase-lines"] as? ViewGroup ?: return
        val template=metadata.entries.firstOrNull { (view,node) ->
            classes(node).contains("purchase-line") && ancestors(view).any { it===host }
        }?.value ?: return
        val copy=JSONObject(template.toString()).also { uniqueDynamicIds(it) }
        fun prepare(node:JSONObject) {
            if(quick && node.optJSONObject("data")?.has("data-purchase-item")==true) node.put("selected",1)
            if(node.optString("text").matches(Regex("الصنف [0-9٠-٩]+"))) node.put("text","الصنف "+java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("ar-EG")).format(host.childCount+1))
            items(node.optJSONArray("children")).forEach { prepare(it) }
        }
        prepare(copy)
        val row=build(copy)
        if(host is SourceGrid) host.addCell(row,false) else host.addView(row)
        conditions()
        if(quick) {
            val name=metadata.entries.firstOrNull { (field,node) ->
                field is EditText && node.optJSONObject("data")?.has("data-purchase-new-name")==true &&
                    ancestors(field).any { it===row }
            }?.key as? EditText
            name?.post { name.requestFocus() }
            row.post { row.parent?.let { _ -> row.requestRectangleOnScreen(android.graphics.Rect(0,0,row.width,row.height),true) } }
        }
    }
    private fun purchaseNumber(view:View?):Double =
        (view as? EditText)?.text?.toString()?.trim()?.replace("٫",".")?.replace(",","")?.toDoubleOrNull() ?: 0.0

    private fun purchaseField(scope:View,key:String):View? =
        metadata.entries.firstOrNull { (view,node) ->
            node.optJSONObject("data")?.has(key)==true && ancestors(view).any { it===scope }
        }?.key

    private fun updatePurchaseTotals() {
        // Port of main.js updatePurchaseTotals/readColorVariantRows: UI-only, no ledger writes.
        val rows=metadata.entries.filter { (view,node) ->
            classes(node).contains("purchase-line") && view.parent!=null
        }.map { it.key }
        if(rows.isEmpty()) return
        var total=0.0
        rows.forEach { row ->
            val qty=kotlin.math.max(1.0,kotlin.math.floor(purchaseNumber(purchaseField(row,"data-purchase-qty"))))
            val cost=kotlin.math.max(0.0,purchaseNumber(purchaseField(row,"data-purchase-unit-cost")))
            val variants=metadata.entries.filter { (view,node) ->
                node.optJSONObject("data")?.has("data-color-variant-row")==true &&
                    ancestors(view).any { it===row }
            }.map { it.key }
            val variantsTotal=variants.sumOf { variant ->
                val vqty=kotlin.math.max(1.0,kotlin.math.floor(purchaseNumber(purchaseField(variant,"data-variant-qty"))))
                val costField=purchaseField(variant,"data-variant-cost") as? EditText
                val raw=costField?.text?.toString()?.trim().orEmpty()
                vqty*(if(raw.isEmpty()) cost else kotlin.math.max(0.0,purchaseNumber(costField)))
            }
            val amount=qty*cost+variantsTotal
            total+=amount
            (purchaseField(row,"data-purchase-line-total") as? EditText)?.setText(
                String.format(java.util.Locale.US,"%.2f",amount)
            )
        }
        val paid=kotlin.math.max(0.0,purchaseNumber(controls["purchase-paid"]))
        (controls["purchase-grand-total"] as? EditText)?.setText(String.format(java.util.Locale.US,"%.2f",total))
        (controls["purchase-balance"] as? EditText)?.setText(String.format(java.util.Locale.US,"%.2f",kotlin.math.max(0.0,total-paid)))
    }
    private fun conditions() {
        // Visibility only, mirroring the original form groups. No monetary calculation or saving.
        val rows=metadata.keys.filter { metadata[it]?.let { n -> classes(n).contains("purchase-line") }==true }
        rows.forEach { row ->
            val descendants=metadata.keys.filter { view -> ancestors(view).any { it===row } }
            val selects=descendants.filterIsInstance<Spinner>()
            val kind=selects.firstOrNull { metadata[it]?.optJSONObject("data")?.has("data-purchase-kind")==true }
            val item=selects.firstOrNull { metadata[it]?.optJSONObject("data")?.has("data-purchase-item")==true }
            val isNew=item?.selectedItemPosition==1
            val isPart=kind?.selectedItemPosition==1
            descendants.forEach { child ->
                val cs=metadata[child]?.let { classes(it) } ?: emptyList()
                if(cs.contains("purchase-new-field")) child.visibility=if(isNew && (!cs.contains("purchase-watch-field") || !isPart) && (!cs.contains("purchase-part-field") || isPart)) View.VISIBLE else View.GONE
            }
        }
        metadata.keys.filterIsInstance<Spinner>().forEach { brand ->
            val node=metadata[brand] ?: return@forEach
            if(node.optJSONObject("data")?.has("data-watch-brand-select")==true) {
                val scope=ancestors(brand).firstOrNull { metadata[it]?.optJSONObject("data")?.has("data-watch-lookup-scope")==true } ?: root
                val field=ancestors(brand).firstOrNull { metadata[it]?.let { n -> classes(n).contains("field") }==true }
                metadata.forEach { (view,meta) ->
                    if(meta.optJSONObject("data")?.has("data-casio-only")==true && ancestors(view).any { it===scope })
                        view.visibility=if(brand.selectedItem?.toString()=="كاسيو") View.VISIBLE else View.GONE
                    if(meta.optJSONObject("data")?.has("data-watch-brand-custom-box")==true && ancestors(view).any { it===field })
                        view.visibility=if(brand.selectedItemPosition==brand.count-1) View.VISIBLE else View.GONE
                }
            }
        }
        (controls["quick-repair-price-mode"] as? Spinner)?.let {
            controls["quick-repair-fixed-price"]?.visibility=if(it.selectedItemPosition==1) View.GONE else View.VISIBLE
        }
        (controls["sale-type"] as? Spinner)?.let {
            controls["online-sale-hint"]?.visibility=if(it.selectedItemPosition==1) View.VISIBLE else View.GONE
        }
        val receipt=controls["sale-receipt-mode"] as? Spinner
        if(receipt!=null) {
            controls["sale-mixed-payment-box"]?.visibility=if(receipt.selectedItemPosition==1) View.VISIBLE else View.GONE
            metadata.forEach { (view,node) -> if(node.optJSONObject("data")?.has("data-single-payment")==true) view.visibility=if(receipt.selectedItemPosition==0) View.VISIBLE else View.GONE }
        }
        radioNames["deliverNow"]?.firstOrNull { it.isChecked }?.let { radio ->
            controls["father-repair-customer-fields"]?.visibility=if(metadata[radio]?.optString("value")=="0") View.VISIBLE else View.GONE
        }
        updatePurchaseTotals()
    }
    private fun addTemplate(hostId:String,template:String) {
        val host=controls[hostId] as? ViewGroup ?: return
        if(pages.has(template)) {
            val copy=JSONObject(pages.getJSONObject(template).toString()).also { uniqueDynamicIds(it) }
            fun number(node:JSONObject) {
                if(node.optString("text").matches(Regex("الصنف [0-9٠-٩]+"))) node.put("text","الصنف "+java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("ar-EG")).format(host.childCount+1))
                items(node.optJSONArray("children")).forEach { number(it) }
            }
            number(copy)
            val view=build(copy)
            if(host is SourceGrid) host.addCell(view,false) else host.addView(view)
            conditions()
        }
    }
    private fun addVariant(button:View) {
        val candidates=metadata.entries.filter { (_,node) ->
            node.optJSONObject("data")?.has("data-color-variants")==true
        }.mapNotNull { it.key as? ViewGroup }
        fun lineage(view:View):List<View> = listOf(view)+ancestors(view)
        val buttonLineage=lineage(button)
        val target=candidates.maxByOrNull { candidate ->
            lineage(candidate).count { it in buttonLineage }
        } ?: return
        val copy=JSONObject(pages.getJSONObject("variant-row-template").toString()).also { uniqueDynamicIds(it) }
        val row=build(copy)
        if(target is SourceGrid) target.addCell(row,false) else target.addView(row)
        conditions()
    }
    private fun unregisterDetached(view:View) {
        val sourceId=metadata[view]?.optString("sourceId").orEmpty()
        metadata.remove(view)
        controls.entries.removeAll { it.value === view }
        if(sourceId.isNotEmpty() && sourceId !in controls) {
            metadata.entries.firstOrNull { (other,node) ->
                other !== view && node.optString("sourceId")==sourceId
            }?.let { (other,_) -> controls[sourceId]=other }
        }
        radioNames.values.forEach { group -> group.removeAll { it === view } }
        if(view is ViewGroup) for(i in 0 until view.childCount) unregisterDetached(view.getChildAt(i))
    }
    private fun removeMatching(button:View,sourceClass:String) {
        ancestors(button).firstOrNull { metadata[it]?.let { n -> classes(n).contains(sourceClass) }==true }?.let { removed ->
            if(sourceClass=="color-variant-row") {
                val savedId=metadata[removed]?.optJSONObject("data")?.optString("data-variant-id").orEmpty()
                if(savedId.isNotBlank()) {
                    Toast.makeText(activity,"لا يمكن حذف لون محفوظ من معاينة الواجهة؛ يجب التحقق من سجله وعملياته أولاً.",Toast.LENGTH_LONG).show()
                    return
                }
            }
            (removed.parent as? ViewGroup)?.removeView(removed)
            unregisterDetached(removed)
            conditions()
        }
    }
    private val operationalActions=mapOf(
        "open-products" to "products",
        "open-sales" to "sales",
        "open-repairs" to "repairs",
        "open-purchases" to "purchases",
        "open-vaults" to "vaults",
        "open-reports" to "reports",
        "open-settings" to "settings",
        "open-expenses" to "expenses",
        "open-debts" to "debts",
        "open-customers" to "customers",
        "open-maintenance-parts" to "maintenance-parts"
    )
    private val actionPages=mapOf(
        "new-supplier" to "supplier-form", "new-maintenance-part" to "maintenance-part-form",
        "new-product" to "product-form", "opening-new-product" to "product-form",
        "opening-new-part" to "maintenance-part-form", "partner-withdrawal-new" to "partner-withdrawal-form",
        "quick-repair-button-new" to "quick-repair-button-form", "new-customer-order" to "customer-order-form",
        "customer-order-new" to "customer-order-form", "open-maintenance-parts-from-quick" to "maintenance-parts",
        "modern-new-discount" to "discount-rule-form", "quick-repair-button-edit" to "quick-repair-button-form",
        "modern-quick-shortcuts" to "shortcut-settings", "cash-movement-new" to "cash-movement-form",
        "new-cash-movement" to "cash-movement-form", "open-cash-movement" to "cash-movement-form", "opening-quick-product" to "product-quick-form"
    )
}

/** A real native grid with full-width fields; hidden fields reflow without leaving holes. */
private class SourceGrid(context:android.content.Context,private val columns:Int,private val gap:Int):ViewGroup(context) {
    private val full=IdentityHashMap<View,Boolean>()
    private val frames=IdentityHashMap<View,android.graphics.Rect>()
    fun addCell(view:View,span:Boolean) { full[view]=span;addView(view,LayoutParams(-1,-2)) }
    override fun onViewRemoved(child:View) {
        super.onViewRemoved(child)
        full.remove(child)
        frames.remove(child)
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec);val usable=(width-paddingLeft-paddingRight).coerceAtLeast(0)
        val cell=((usable-gap*(columns-1))/columns).coerceAtLeast(0)
        var column=0;var top=paddingTop;var rowHeight=0
        val rowViews=mutableListOf<View>()
        frames.clear()
        fun nextRow() {
            if(column>0) {
                // CSS grid items stretch to the tallest cell in their row.
                rowViews.forEach { child ->
                    frames[child]?.let { rect ->
                        if(child.measuredHeight!=rowHeight) {
                            child.measure(
                                MeasureSpec.makeMeasureSpec(rect.width(),MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(rowHeight,MeasureSpec.EXACTLY)
                            )
                        }
                        rect.bottom=rect.top+rowHeight
                    }
                }
                rowViews.clear()
                top+=rowHeight+gap;column=0;rowHeight=0
            }
        }
        for(i in 0 until childCount) {
            val child=getChildAt(i);if(child.visibility==GONE) continue
            val span=if(full[child]==true) columns else 1
            if(span==columns) nextRow()
            val w=if(span==columns) usable else cell
            child.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
            val right=width-paddingRight-column*(cell+gap)
            frames[child]=android.graphics.Rect(right-w,top,right,top+child.measuredHeight)
            rowViews.add(child)
            rowHeight=maxOf(rowHeight,child.measuredHeight);column+=span
            if(column>=columns) nextRow()
        }
        val pendingRowHeight=if(column>0) rowHeight else 0
        if(column>0) rowViews.forEach { child ->
            frames[child]?.let { rect ->
                if(child.measuredHeight!=rowHeight) {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(rect.width(),MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rowHeight,MeasureSpec.EXACTLY)
                    )
                }
                rect.bottom=rect.top+rowHeight
            }
        }
        val height=if(column>0) top+pendingRowHeight else if(frames.isEmpty()) paddingTop else (top-gap).coerceAtLeast(paddingTop)
        setMeasuredDimension(resolveSize(width,widthMeasureSpec),resolveSize(height+paddingBottom,heightMeasureSpec))
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        for(i in 0 until childCount) {
            val child=getChildAt(i)
            frames[child]?.let { rect -> child.layout(rect.left,rect.top,rect.right,rect.bottom) }
        }
    }
}

/** Native wrapping action row; RTL order is retained at every width. */
private class SourceFlow(context:android.content.Context,private val gap:Int):ViewGroup(context) {
    private val frames=IdentityHashMap<View,android.graphics.Rect>()
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec);val usable=(width-paddingLeft-paddingRight).coerceAtLeast(0)
        var used=0;var top=paddingTop;var height=0;var widest=0;frames.clear()
        for(i in 0 until childCount) {
            val child=getChildAt(i);if(child.visibility==GONE) continue
            child.measure(MeasureSpec.makeMeasureSpec(usable,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
            if(used>0 && used+child.measuredWidth>usable) { used=0;top+=height+gap;height=0 }
            val right=width-paddingRight-used
            frames[child]=android.graphics.Rect(right-child.measuredWidth,top,right,top+child.measuredHeight)
            used+=child.measuredWidth+gap;widest=maxOf(widest,used-gap);height=maxOf(height,child.measuredHeight)
        }
        val measured=if(MeasureSpec.getMode(widthMeasureSpec)==MeasureSpec.EXACTLY) width else (widest+paddingLeft+paddingRight).coerceAtMost(width)
        if(measured!=width) frames.values.forEach { it.offset(measured-width,0) }
        setMeasuredDimension(measured,resolveSize(top+height+paddingBottom,heightMeasureSpec))
    }
    override fun onViewRemoved(child:View) {
        super.onViewRemoved(child)
        frames.remove(child)
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int) {
        for(i in 0 until childCount) {
            val child=getChildAt(i)
            frames[child]?.let { rect -> child.layout(rect.left,rect.top,rect.right,rect.bottom) }
        }
    }
}
