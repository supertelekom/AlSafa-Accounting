package com.alsafa.native8

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.alsafa.native8.data.operational.OperationalDatabase
import com.alsafa.native8.data.operational.ProductVariantRepository
import org.json.JSONObject

class ProductColorEditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra("source").orEmpty()
        val parent = intent.getStringExtra("parent").orEmpty()
        val variantId = intent.getStringExtra("variant")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = if (variantId == null) "إضافة لون للمنتج" else "تعديل لون المنتج"
            textSize = 23f
        })
        val inputs = linkedMapOf<String, EditText>()
        for ((key, title) in listOf("color" to "اسم اللون", "stock" to "الكمية",
            "salePrice" to "سعر البيع", "cost" to "رأس المال",
            "barcode" to "الباركود", "photo" to "صورة اللون")) {
            root.addView(TextView(this).apply { text = title })
            val input = EditText(this).apply { hint = title; setSingleLine(true) }
            inputs[key] = input
            root.addView(input)
        }
        val status = TextView(this)
        root.addView(status)
        val save = Button(this).apply {
            text = "حفظ اللون"
            isEnabled = variantId == null
            setOnClickListener {
                isEnabled = false
                val data = JSONObject()
                inputs.forEach { (key, field) -> data.put(key, field.text.toString().trim()) }
                Thread {
                    val error = try {
                        val db = OperationalDatabase(applicationContext)
                        try { ProductVariantRepository(db).save(source, parent, data, variantId) }
                        finally { db.close() }
                        null
                    } catch (e: Exception) { e.message ?: "تعذر حفظ اللون" }
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        isEnabled = true
                        if (error == null) finish() else status.text = error
                    }
                }.start()
            }
        }
        root.addView(save)
        if (variantId != null) {
            root.addView(Button(this).apply {
                text = "حذف اللون"
                setOnClickListener {
                    AlertDialog.Builder(this@ProductColorEditorActivity)
                        .setMessage("حذف هذا اللون؟")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("حذف") { _, _ ->
                            Thread {
                                val db = OperationalDatabase(applicationContext)
                                try { ProductVariantRepository(db).delete(source, parent, variantId) }
                                finally { db.close() }
                                runOnUiThread { finish() }
                            }.start()
                        }.show()
                }
            })
            Thread {
                val db = OperationalDatabase(applicationContext)
                val color = try { ProductVariantRepository(db).get(variantId) } finally { db.close() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (color == null) status.text = "تعذر تحميل اللون"
                    else {
                        inputs.forEach { (key, field) -> field.setText(color.optString(key)) }
                        save.isEnabled = true
                    }
                }
            }.start()
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
