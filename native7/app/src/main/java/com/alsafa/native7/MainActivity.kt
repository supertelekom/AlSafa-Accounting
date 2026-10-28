package com.alsafa.native7

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
        render()
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("screen",screen)
        super.onSaveInstanceState(outState)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if(screen!="chooser"){screen="chooser";render()}else super.onBackPressed() }
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
    private fun render() {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; layoutDirection=View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pearl)
        }
        setContentView(root)
        root.addView(text("الصفا للساعات",27f,gold))
        val scroll=ScrollView(this)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(22),dp(12),dp(22),dp(20))
        }
        scroll.addView(body)
        when(screen) {
            "chooser" -> {
                body.addView(text("كيف تريد أن تبدأ؟",25f))
                body.addView(text("اختر بيئة العمل المناسبة، أو افتح مستشار اختيار الساعة أمام العميل.",15f))
                card(body,"الوضع المبسط","بيع وصيانة وتسليم سريع","simple")
                card(body,"الوضع الكامل","الإدارة والمخزون والحسابات","full",true)
                card(body,"ساعدني أختار ساعتي","اختيارات من الساعات المتوفرة فعلياً الآن","advisor",true)
            }
            "simple" -> {
                body.addView(text("العمل السريع",25f,gold))
                for(name in listOf("مبيع","صيانة سريعة","تسليم الساعات","تسليم الوردية"))
                    card(body,name,"واجهة قيد المطابقة مع الأصل","preview")
            }
            "full" -> {
                body.addView(text("الوضع الكامل",25f,gold))
                for(name in listOf("اليوم","مبيع","استلام صيانة","تسليم صيانة","قبض دين","مصروف","بحث","الإدارة"))
                    card(body,name,"واجهة قيد المطابقة مع الأصل","preview")
            }
            else -> {
                body.addView(text("هذه الشاشة قيد النقل من التطبيق الأصلي",20f))
                body.addView(text("لا توجد عمليات مالية أو بيانات تجريبية قابلة للحفظ في هذه النسخة.",15f))
            }
        }
        val back=text("العودة",17f,gold).apply { isClickable=true;setOnClickListener { screen="chooser";render() } }
        if(screen!="chooser")root.addView(back)
    }
}
