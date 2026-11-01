package com.alsafa.native8

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alsafa.native8.data.operational.OperationalDatabase
import com.alsafa.native8.data.operational.ProductStockRepository

/** Explicit manual inventory adjustment; imported base stock remains immutable. */
class ProductStockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra("source").orEmpty()
        val parent = intent.getStringExtra("parent").orEmpty()
        val variant = intent.getStringExtra("variant")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply { text = "تسوية كمية المنتج"; textSize = 23f })
        val amount = EditText(this).apply { hint = "فرق الكمية (+ زيادة، - نقصان)"; setSingleLine() }
        val note = EditText(this).apply { hint = "سبب التسوية"; setSingleLine() }
        root.addView(amount)
        root.addView(note)
        val status = TextView(this)
        root.addView(status)
        val history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(history)
        fun refresh() {
            Thread {
                val db = OperationalDatabase(applicationContext)
                val rows = try { ProductStockRepository(db).history(source, parent, variant) } finally { db.close() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    history.removeAllViews()
                    for (row in rows) history.addView(TextView(this).apply {
                        text = row.delta + " | " + row.before + " ← " + row.after + " | " + row.note
                        setPadding(8, 12, 8, 12)
                    })
                }
            }.start()
        }
        root.addView(Button(this).apply {
            text = "حفظ التسوية"
            setOnClickListener {
                isEnabled = false
                val button = this
                val quantity = amount.text.toString().trim()
                val reason = note.text.toString().trim()
                Thread {
                    val result = try {
                        val db = OperationalDatabase(applicationContext)
                        try { ProductStockRepository(db).adjust(source, parent, variant, quantity, reason) }
                        finally { db.close() }
                        null
                    } catch (e: Exception) { e.message ?: "فشل حفظ التسوية" }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        button.isEnabled = true
                        if (result == null) {
                            status.text = "تم حفظ حركة المخزون"
                            amount.setText("")
                            note.setText("")
                            refresh()
                        } else status.text = result
                    }
                }.start()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }
}
