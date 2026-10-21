import com.alsafa.native6.*

fun main() {
    check(Money.parse("١٢٣٫٤٥", Currency.USD).minor == 12345L)
    check(Money.parse("۱۲۳٫۴۵", Currency.USD).minor == 12345L)
    check(proportional(101, 1, 3) == 34L)
    check(proportional(101, 3, 3) == 101L)
    validatePosting(listOf(Posting("cash", Currency.USD, 500), Posting("revenue", Currency.USD, -500)))

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
    check(expectedFailures == 8)
    println("PASS: 5 successful assertions and 8 expected failures")
}
