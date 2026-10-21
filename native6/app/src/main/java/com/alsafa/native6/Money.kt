package com.alsafa.native6

import java.math.BigDecimal
import java.math.RoundingMode

enum class Currency { USD, TRY, SYP }
/** Native5 Money/JournalEntry rules ported to Kotlin: exact integers; balanced per currency. */
data class Money(val minor: Long, val currency: Currency) {
    operator fun plus(other: Money): Money { require(currency == other.currency); return copy(minor = Math.addExact(minor, other.minor)) }
    fun display() = "${BigDecimal.valueOf(minor, 2).toPlainString()} ${currency.name}"
    companion object {
        fun parse(text: String, currency: Currency): Money {
            val normalized = text.trim().map { c -> when(c) { in '٠'..'٩' -> '0' + (c-'٠'); in '۰'..'۹' -> '0' + (c-'۰'); '٫' -> '.'; else -> c } }.joinToString("")
            return Money(BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact(), currency)
        }
    }
}
data class Posting(val account: String, val currency: Currency, val amount: Long)
fun validatePosting(lines: List<Posting>) {
    require(lines.size >= 2 && lines.all { it.account.isNotBlank() && it.amount != 0L }) { "قيد غير صالح" }
    lines.groupBy { it.currency }.values.forEach { group -> require(group.fold(0L) { a, p -> Math.addExact(a,p.amount) } == 0L) { "قيد غير متوازن" } }
}
fun add(a: Long,b: Long) = Math.addExact(a,b)
fun mul(a: Long,b: Long) = Math.multiplyExact(a,b)
fun proportional(value: Long, quantity: Long, total: Long): Long {
    require(value>=0L && quantity>=0L && total>0L && quantity<=total) { "نسبة محاسبية غير صالحة" }
    return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(quantity)).divide(BigDecimal.valueOf(total),0,RoundingMode.HALF_UP).longValueExact()
}

/** Allocate an invoice total across nonnegative gross lines without losing a minor unit. */
fun allocateInvoiceNet(grossLines: List<Long>, discount: Long): List<Long> {
    require(grossLines.isNotEmpty() && grossLines.all { it >= 0L }) { "قيم الأصناف غير صالحة" }
    val gross = grossLines.fold(0L, ::add)
    require(gross > 0L && discount >= 0L && discount < gross) { "إجمالي الفاتورة أو الخصم غير صالح" }
    val net = Math.subtractExact(gross, discount)
    var grossRunning = 0L
    var netRunning = 0L
    val result = grossLines.map { line ->
        grossRunning = add(grossRunning, line)
        val target = proportional(net, grossRunning, gross)
        val allocated = Math.subtractExact(target, netRunning)
        netRunning = target
        require(allocated >= 0L) { "توزيع الخصم غير صالح" }
        allocated
    }
    check(netRunning == net)
    return result
}

/** Incremental credit for a partial return; the final return recovers the exact line value. */
fun allocateReturnIncrement(originalValue: Long, originalQty: Long, previouslyReturnedQty: Long, previouslyCredited: Long, returnQty: Long): Long {
    require(originalValue >= 0L && originalQty > 0L && previouslyReturnedQty in 0L..originalQty &&
        previouslyCredited in 0L..originalValue && returnQty > 0L &&
        returnQty <= Math.subtractExact(originalQty, previouslyReturnedQty)) { "كمية أو قيمة المرتجع غير صالحة" }
    require(previouslyCredited == proportional(originalValue, previouslyReturnedQty, originalQty)) { "القيمة المرتجعة سابقاً لا تطابق الكمية المسجلة" }
    val newCumulative = proportional(originalValue, Math.addExact(previouslyReturnedQty, returnQty), originalQty)
    require(previouslyCredited <= newCumulative) { "رصيد المرتجع السابق يتجاوز الاستحقاق" }
    return Math.subtractExact(newCumulative, previouslyCredited)
}
