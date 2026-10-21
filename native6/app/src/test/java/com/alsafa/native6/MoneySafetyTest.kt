package com.alsafa.native6

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneySafetyTest {
    @Test fun parsesArabicAndPersianDigitsExactly() {
        assertEquals(12345L, Money.parse("١٢٣٫٤٥", Currency.USD).minor)
        assertEquals(12345L, Money.parse("۱۲۳٫۴۵", Currency.USD).minor)
    }

    @Test fun rejectsExcessFractionalPrecision() {
        assertThrows(ArithmeticException::class.java) {
            Money.parse("1.001", Currency.USD)
        }
    }

    @Test fun rejectsLongOverflow() {
        assertThrows(ArithmeticException::class.java) {
            Money.parse("999999999999999999999", Currency.USD)
        }
    }

    @Test fun preventsMixedCurrencyArithmetic() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(100, Currency.USD) + Money(100, Currency.TRY)
        }
    }

    @Test fun validatesBalancedPostingsPerCurrency() {
        validatePosting(listOf(Posting("cash:drawer", Currency.USD, 500), Posting("revenue:sales", Currency.USD, -500)))
        assertThrows(IllegalArgumentException::class.java) {
            validatePosting(listOf(Posting("cash:drawer", Currency.USD, 500), Posting("revenue:sales", Currency.TRY, -500)))
        }
    }

    @Test fun catchesOverflowInAdditionAndMultiplication() {
        assertThrows(ArithmeticException::class.java) { add(Long.MAX_VALUE, 1) }
        assertThrows(ArithmeticException::class.java) { mul(Long.MAX_VALUE, 2) }
    }
}
