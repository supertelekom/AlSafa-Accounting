package com.alsafa.native8

import android.app.Activity
import android.content.Intent
import com.alsafa.native8.sales.SalesArchiveActivity
import com.alsafa.native8.sales.FinancialReviewActivity
import com.alsafa.native8.sales.ExpenseActivity
import com.alsafa.native8.sales.PartnersActivity
import com.alsafa.native8.maintenance.MaintenancePartsActivity
import com.alsafa.native8.maintenance.MaintenanceActivity
import com.alsafa.native8.sales.SuppliersActivity
import com.alsafa.native8.sales.PurchasesActivity
import com.alsafa.native8.sales.CashboxMovementsActivity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Temporary, high-contrast test launcher. Each module remains a separate Activity. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(19, 37, 57)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(245, 248, 252))
        }
        root.addView(TextView(this).apply {
            text = "الصفا للساعات — Native 8"
            textSize = 26f
            setTextColor(Color.rgb(19, 37, 57))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 })
        root.addView(TextView(this).apply {
            text = "نسخة فحص SQLite | BUILD 8.0.3-DB"
            textSize = 16f
            setTextColor(Color.rgb(60, 70, 85))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 })
        root.addView(Button(this).apply {
            text = "اختيار النسخة الاحتياطية واستيرادها"
            textSize = 19f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(18, 83, 142)))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, RestoreActivity::class.java).apply {
                    putExtra(RestoreActivity.EXTRA_PICK_ON_OPEN, true)
                })
            }
        }, LinearLayout.LayoutParams(-1, 76).apply { bottomMargin = 20 })
        root.addView(Button(this).apply {
            text = "استعادة البيانات"
            textSize = 19f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(28, 95, 104)))
            setOnClickListener { startActivity(Intent(this@MainActivity, RestoreActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76).apply { bottomMargin = 20 })
        root.addView(Button(this).apply {
            text = "المنتجات المستوردة"
            textSize = 19f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(64, 75, 96)))
            setOnClickListener { startActivity(Intent(this@MainActivity, ProductsActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76))
        root.addView(Button(this).apply {
            text = "فواتير المبيع والقبض"
            setOnClickListener { startActivity(Intent(this@MainActivity, SalesArchiveActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76))
        root.addView(Button(this).apply {
            text = "حركات الصناديق / شام كاش"
            setOnClickListener { startActivity(Intent(this@MainActivity, CashboxMovementsActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76))
        root.addView(Button(this).apply {
            text = "أسعار الصرف"
            setOnClickListener { startActivity(Intent(this@MainActivity, ExchangeRatesActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76))
        root.addView(Button(this).apply {
            text = "الموردون"
            setOnClickListener { startActivity(Intent(this@MainActivity, SuppliersActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76))
        root.addView(Button(this).apply {
            text = "الصيانة"
            setOnClickListener { startActivity(Intent(this@MainActivity, MaintenanceActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76))
        root.addView(Button(this).apply {
            text = "قطع الصيانة"
            setOnClickListener { startActivity(Intent(this@MainActivity, MaintenancePartsActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76))
        root.addView(Button(this).apply {
            text = "الأرباح — مراجعة الحسابات"
            setOnClickListener { startActivity(Intent(this@MainActivity, FinancialReviewActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76))
        root.addView(Button(this).apply {
            text = "المصاريف"
            setOnClickListener { startActivity(Intent(this@MainActivity, ExpenseActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1,76))
        root.addView(Button(this).apply {
            text = "الشركاء والسحوبات"
            setOnClickListener { startActivity(Intent(this@MainActivity, PartnersActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1,76))
        root.addView(Button(this).apply {
            text = "المشتريات"
            setOnClickListener { startActivity(Intent(this@MainActivity, PurchasesActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, 76))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }
}
