import com.alsafa.native6.*

fun main() {
    check(Money.parse("١٢٣٫٤٥", Currency.USD).minor == 12345L)
    check(Money.parse("۱۲۳٫۴۵", Currency.USD).minor == 12345L)
    check(proportional(101, 1, 3) == 34L)
    check(proportional(101, 3, 3) == 101L)
    validatePosting(listOf(Posting("cash", Currency.USD, 500), Posting("revenue", Currency.USD, -500)))

    check(allocateInvoiceNet(listOf(101L, 101L, 101L), 1L).sum() == 302L)
    check(allocateInvoiceNet(listOf(1L, 1L, 1L), 2L).sum() == 1L)
    check(allocateInvoiceNet(listOf(0L, 250L, 0L), 50L) == listOf(0L, 200L, 0L))
    check(allocateInvoiceNet(listOf(5L, 5L), 0L) == listOf(5L, 5L))
    var expectedFailures = 0
    fun rejects(block: () -> Unit) {
        try {
            block()
        } catch (e: ArithmeticException) {
            expectedFailures++
            return
        } catch (e: IllegalArgumentException) {
            expectedFailures++
            return
        }
        error("Invalid accounting input was accepted")
    }
    rejects { Money.parse("1.001", Currency.USD) }
    rejects { Money.parse("999999999999999999999", Currency.USD) }
    rejects { Money(100, Currency.USD) + Money(100, Currency.TRY) }
    rejects { validatePosting(listOf(Posting("cash", Currency.USD, 500), Posting("revenue", Currency.TRY, -500))) }
    rejects { add(Long.MAX_VALUE, 1) }
    rejects { mul(Long.MAX_VALUE, 2) }
    rejects { proportional(100, 4, 3) }
    rejects { proportional(100, 1, 0) }
    rejects { allocateInvoiceNet(listOf(0L, 0L), 0L) }
    rejects { allocateInvoiceNet(listOf(5L), 5L) }
    rejects { allocateInvoiceNet(listOf(Long.MAX_VALUE, 1L), 0L) }
    check(expectedFailures == 11)
    println("PASS: 9 successful assertions and 11 expected failures")
}
