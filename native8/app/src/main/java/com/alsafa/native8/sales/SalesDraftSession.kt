package com.alsafa.native8.sales

import androidx.compose.runtime.*
import java.math.BigDecimal

/** Temporary in-process sale draft shared between invoice and payment screens.
 * Finalized invoices are persisted by SalesEngine; drafts are not yet crash durable.
 */
object SalesDraftSession {
    var customer by mutableStateOf("")
    var phone by mutableStateOf("")
    var discount by mutableStateOf("0")
    var channel by mutableStateOf(SalesChannelClassification.STORE)
    var currency by mutableStateOf("USD")
    var cashbox by mutableStateOf("الدرج")
    var amount by mutableStateOf("")
    var message by mutableStateOf("")
    val lines = mutableStateListOf<SaleLine>()
    val payments = mutableStateListOf<SalePayment>()

    fun add(choice: SaleChoice, quantity: BigDecimal): String {
        require(quantity > BigDecimal.ZERO) { "الكمية غير صالحة" }
        val reserved = lines.filter {
            it.productSource == choice.source && it.productId == choice.productId && it.variantId == choice.variantId
        }.fold(BigDecimal.ZERO) { a, line -> a + line.quantity }
        require(quantity + reserved <= choice.available.toBigDecimal()) { "الكمية غير كافية" }
        lines.add(SaleLine(choice.source, choice.productId, choice.variantId, choice.title,
            quantity, choice.price.toBigDecimal(), choice.cost.toBigDecimal()))
        return "تمت إضافة ${choice.title}"
    }

    fun draft(): SaleDraft = SaleDraft(customer.trim(), phone.trim(), lines.toList(),
        payments.toList(), discount.ifBlank { "0" }.toBigDecimal(),channel)

    fun clear() {
        customer = ""; phone = ""; discount = "0"; amount = ""; channel = SalesChannelClassification.STORE
        lines.clear(); payments.clear(); message = ""
    }
}
