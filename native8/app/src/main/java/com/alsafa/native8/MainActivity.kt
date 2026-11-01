package com.alsafa.native8

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Temporary navigation shell only.
 * Final home screen must match original v3.9.359 labels, order and layout.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
            setPadding(24, 24, 24, 24)
        }
        layout.addView(TextView(this).apply {
            text = "الصفا Native 8"
            textSize = 24f
            gravity = Gravity.CENTER
        })
        layout.addView(Button(this).apply {
            text = "المنتجات"
            setOnClickListener { startActivity(Intent(this@MainActivity, ProductsActivity::class.java)) }
        })
        layout.addView(Button(this).apply {
            text = "استعادة البيانات"
            setOnClickListener { startActivity(Intent(this@MainActivity, RestoreActivity::class.java)) }
        })
        setContentView(layout)
    }
}
