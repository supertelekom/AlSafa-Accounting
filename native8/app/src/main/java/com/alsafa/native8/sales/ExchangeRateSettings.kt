package com.alsafa.native8.sales

import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode

/** User-approved editable direct exchange quotes. Rates are NOT historical payment records. */
class ExchangeRateSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("alsafa_exchange_rates", Context.MODE_PRIVATE)
    data class PairRate(val from: String, val to: String, val initial: String)
    companion object {
        val PAIRS = listOf(
            PairRate("TRY", "SYP", "3"),
            PairRate("USD", "TRY", "50"),
            PairRate("USD", "SYP", "140")
        )
    }
    fun rate(fromRaw: String, toRaw: String): BigDecimal {
        val from=SaleCurrency.normalize(fromRaw);val to=SaleCurrency.normalize(toRaw)
        require(from in setOf("TRY","SYP","USD") && to in setOf("TRY","SYP","USD"))
        if (from == to) return BigDecimal.ONE
        val pair = PAIRS.firstOrNull { it.from == from && it.to == to }
        if (pair != null) return read(pair)
        val inverse = PAIRS.firstOrNull { it.from == to && it.to == from }
            ?: error("زوج العملات غير مدعوم")
        return BigDecimal.ONE.divide(read(inverse), 12, RoundingMode.HALF_UP)
    }
    fun read(pair: PairRate): BigDecimal {
        val value = prefs.getString("${pair.from}_${pair.to}", null) ?: pair.initial
        return value.toBigDecimal().also { require(it > BigDecimal.ZERO) { "سعر الصرف يجب أن يكون موجباً" } }
    }
    fun save(pair: PairRate, value: String) {
        require(pair in PAIRS)
        val number = value.trim().toBigDecimal()
        require(number > BigDecimal.ZERO) { "سعر الصرف يجب أن يكون موجباً" }
        check(prefs.edit().putString("${pair.from}_${pair.to}", number.toPlainString()).commit()) {
            "تعذر حفظ سعر الصرف"
        }
    }
}
