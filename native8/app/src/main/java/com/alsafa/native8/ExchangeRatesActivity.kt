package com.alsafa.native8

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import com.alsafa.native8.sales.ExchangeRateSettings

class ExchangeRatesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rates = ExchangeRateSettings(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(24,24,24,24)
        }
        root.addView(TextView(this).apply {
            text = "أسعار الصرف"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "تُستخدم الأسعار المباشرة المحددة لكل زوج، ولا يؤدي تعديلها إلى تغيير أسعار الدفعات السابقة."
        })
        for (pair in ExchangeRateSettings.PAIRS) {
            root.addView(TextView(this).apply {
                text = "1 ${pair.from} = كم ${pair.to}؟"
                textSize = 18f
            })
            val entry = EditText(this).apply {
                setText(rates.read(pair).stripTrailingZeros().toPlainString())
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
                textDirection = View.TEXT_DIRECTION_LTR
            }
            root.addView(entry)
            root.addView(Button(this).apply {
                text = "حفظ ${pair.from} / ${pair.to}"
                setOnClickListener {
                    try {
                        rates.save(pair, entry.text.toString())
                        Toast.makeText(this@ExchangeRatesActivity,"تم حفظ السعر",Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@ExchangeRatesActivity,e.message ?: "سعر غير صالح",Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
