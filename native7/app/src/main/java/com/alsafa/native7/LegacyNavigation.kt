package com.alsafa.native7

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Native port of the v3.9.359 tablet navigation.
 * Original ordering: الرئيسية، مبيع، صيانة، بحث، المزيد.
 * This component deliberately does not create any new navigation labels.
 */
internal class LegacyNavigation(context: Context) : LinearLayout(context) {
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    fun show(sidebar: Boolean, current: String, navigate: (String) -> Unit) {
        removeAllViews()
        orientation = if (sidebar) VERTICAL else HORIZONTAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setBackgroundColor(Color.rgb(255, 250, 242))
        val entries = listOf(
            Triple("⌂", "الرئيسية", "chooser"),
            Triple("🛒", "مبيع", "preview:مبيع كامل"),
            Triple("🔧", "صيانة", "preview:طلب صيانة"),
            Triple("🔎", "بحث", "preview:بحث"),
            Triple("☰", "المزيد", "management")
        )
        entries.forEach { (icon, title, destination) ->
            val active = current == destination
            val button = LinearLayout(context).apply {
                orientation = if (sidebar) HORIZONTAL else VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER
                setPadding(dp(if (sidebar) 12 else 3), dp(if (sidebar) 10 else 5), dp(if (sidebar) 12 else 3), dp(if (sidebar) 10 else 5))
                background = GradientDrawable().apply {
                    setColor(if (active) Color.rgb(244, 232, 211) else Color.rgb(255, 250, 242))
                    cornerRadius = dp(10).toFloat()
                }
                isClickable = true
                isFocusable = true
                contentDescription = title
                setOnClickListener { navigate(destination) }
            }
            val symbol = TextView(context).apply {
                text = icon
                textSize = 19f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(169, 121, 41))
            }
            val label = TextView(context).apply {
                text = title
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(62, 52, 41))
            }
            button.addView(symbol)
            button.addView(label)
            addView(button, if (sidebar) LayoutParams(-1, dp(49)) else LayoutParams(0, dp(65), 1f))
        }
    }
}
