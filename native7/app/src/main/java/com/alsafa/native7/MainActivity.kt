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

/** UI-only prototype. No accounting transactions, persistence or network calls. */
class MainActivity : Activity() {
    private val ink = Color.rgb(62,52,41)
    private val gold = Color.rgb(169,121,41)
    private val pearl = Color.rgb(248,243,235)
    private val paper = Color.rgb(255,253,249)
    private var screen = "chooser"
    private var previousScreen = "chooser"
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
        render()
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("screen",screen)
        outState.putString("previousScreen",previousScreen)
        super.onSaveInstanceState(outState)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if(screen!="chooser"){screen=if(screen.startsWith("preview:")) previousScreen else "chooser";render()}else super.onBackPressed() }
    private fun card(parent:LinearLayout, title:String, description:String, target:String, dark:Boolean=false) {
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            background=panel(if(dark) Color.rgb(46,40,34) else paper,26)
            setPadding(dp(14),dp(18),dp(14),dp(18))
            isClickable=true
            setOnClickListener { screen=target;render() }
        }
        card.addView(text(title,23f,if(dark) Color.WHITE else ink))
        card.addView(text(description,13f,if(dark) Color.rgb(231,216,197) else ink))
        parent.addView(card,LinearLayout.LayoutParams(-1,dp(138)).apply { bottomMargin=dp(13) })
    }
    private fun chooserCard(parent:LinearLayout,title:String,description:String,symbol:String,target:String,dark:Boolean) {
        val background=if(dark) GradientDrawable(Orientation.TL_BR,intArrayOf(Color.rgb(37,33,30),Color.rgb(64,55,48)))
            else GradientDrawable().apply { setColor(paper) }
        background.cornerRadius=dp(26).toFloat()
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL; setPadding(dp(20),dp(12),dp(20),dp(12))
            this.background=background; isClickable=true
            setOnClickListener { screen=target;render() }
        }
        val badge=text(symbol,22f,if(dark) Color.rgb(234,217,192) else gold).apply {
            background=panel(if(dark) Color.rgb(75,68,60) else Color.rgb(245,230,203),18)
        }
        card.addView(badge,LinearLayout.LayoutParams(dp(54),dp(54)))
        card.addView(text(title,20f,if(dark) Color.WHITE else ink).apply { gravity=Gravity.RIGHT })
        card.addView(text(description,11f,if(dark) Color.rgb(213,201,187) else ink).apply { gravity=Gravity.RIGHT })
        card.addView(text("دخول",10f,if(dark) Color.rgb(213,185,143) else gold).apply { gravity=Gravity.LEFT })
        parent.addView(card,LinearLayout.LayoutParams(0,dp(165),1f))
    }
    private fun actionGrid(parent:LinearLayout,items:List<Triple<String,String,String>>,simple:Boolean) {
        items.chunked(2).forEach { pair ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
            parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(13) })
            pair.forEachIndexed { index,item ->
                val box=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
                    background=panel(paper,if(simple) 24 else 18)
                    setPadding(dp(10),dp(12),dp(10),dp(12))
                    isClickable=true;setOnClickListener { previousScreen=screen;screen="preview:" + item.first;render() }
                }
                box.addView(text(item.third,if(simple) 42f else 28f,gold))
                box.addView(text(item.first,if(simple) 20f else 18f))
                box.addView(text(item.second,11f))
                row.addView(box,LinearLayout.LayoutParams(0,dp(if(simple) 210 else 130),1f).apply {
                    if(index==0) marginEnd=dp(13)
                })
            }
        }
    }
    private fun currencySummary(parent:LinearLayout) {
        val titles=listOf("↪" to "المدوّر من الإقفال السابق", "🛒" to "مبيعات اليوم", "🔧" to "صيانة اليوم")
        titles.forEach { (symbol,label) ->
            val section=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                layoutDirection=View.LAYOUT_DIRECTION_RTL
                background=panel(paper,17)
                setPadding(dp(10),dp(9),dp(10),dp(9))
            }
            section.addView(text("$symbol  $label",16f,gold).apply { gravity=Gravity.RIGHT })
            val values=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL }
            listOf("USD","SYP","TRY").forEach { currency ->
                val cell=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER }
                cell.addView(text(currency,12f,ink))
                cell.addView(text("—",17f,gold))
                values.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
            }
            section.addView(values)
            parent.addView(section,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        }
    }
    private fun render() {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pearl)
        }
        setContentView(root)
        val scroll=ScrollView(this).apply { isFillViewport=true }
        root.addView(scroll,LinearLayout.LayoutParams(-1,-1))
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20),dp(22),dp(20),dp(24))
        }
        scroll.addView(body)
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
                body.addView(text("⚙️ الوضع الكامل",14f,gold).apply { isClickable=true;setOnClickListener { screen="full";render() } })
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
                body.addView(text("👨‍🔧 الوضع المبسط",14f,gold).apply { isClickable=true;setOnClickListener { screen="simple";render() } })
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
                body.addView(text("آخر العمليات",20f,gold))
                body.addView(text("لا توجد عمليات مسجلة بعد.",14f))
                body.addView(text("الإدارة",17f,gold).apply { isClickable=true;setOnClickListener { screen="management";render() } })
            }
            "management" -> {
                body.addView(text("الإدارة",25f,gold))
                actionGrid(body,listOf(
                    Triple("الأرصدة الافتتاحية","البضاعة والقطع والنقود","📋"),
                    Triple("المنتجات والمخزون","الساعات والكميات","⌚"),
                    Triple("قطع الصيانة","القطع والمخزون","🔧"),
                    Triple("المشتريات والموردون","المشتريات والذمم","📦"),
                    Triple("الفواتير السابقة","الفواتير والأرشيف","🧾"),
                    Triple("فواتير المرتجع","المرتجع والاستبدال","↩️"),
                    Triple("كل الصيانة","الحالات والمتابعة","🔧"),
                    Triple("الخزائن والأرصدة","الدرج والخزائن","💵"),
                    Triple("إقفال اليوم","الدرج والمدور","📅"),
                    Triple("الديون","التحصيل","💳"),
                    Triple("المصاريف","المصاريف المسجلة","📤"),
                    Triple("ملفات العملاء","العملاء والضمانات","👤"),
                    Triple("التقارير","الأرباح والفترات","📊"),
                    Triple("الشركاء والسحوبات","الحصص والسحوبات","🤝")
                ),false)
            }
            else -> {
                body.addView(text(screen.removePrefix("preview:"),23f,gold))
                body.addView(text("واجهة قيد المطابقة مع التطبيق القديم — لا توجد عمليات حفظ في هذه النسخة.",15f))
            }
        }
        if(screen!="chooser") body.addView(text("العودة",17f,gold).apply {
            isClickable=true;setOnClickListener {
                screen=if(screen.startsWith("preview:")) previousScreen else "chooser"
                render()
            }
        })
    }

}
