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
    @Test fun proportionalAllocationPreservesExactFullValue() {
        assertEquals(101L, proportional(101L, 3L, 3L))
        assertEquals(34L, proportional(101L, 1L, 3L))
        assertEquals(0L, proportional(101L, 0L, 3L))
    }

    @Test fun rejectsInvalidProportionalAllocation() {
        assertThrows(IllegalArgumentException::class.java) { proportional(100L, 4L, 3L) }
        assertThrows(IllegalArgumentException::class.java) { proportional(100L, 1L, 0L) }
        assertThrows(IllegalArgumentException::class.java) { proportional(-1L, 1L, 2L) }
        assertThrows(IllegalArgumentException::class.java) { proportional(100L, -1L, 2L) }
    }

    @Test fun allocatesInvoiceDiscountWithoutLosingMinorUnits() {
        val lines = allocateInvoiceNet(listOf(101L, 101L, 101L), 1L)
        assertEquals(302L, lines.sum())
        assertEquals(3, lines.size)
        assertEquals(listOf(0L, 200L, 0L), allocateInvoiceNet(listOf(0L, 250L, 0L), 50L))
        assertEquals(listOf(5L, 5L), allocateInvoiceNet(listOf(5L, 5L), 0L))
    }

    @Test fun rejectsInvalidInvoiceDiscounts() {
        assertThrows(IllegalArgumentException::class.java) {
            allocateInvoiceNet(listOf(0L, 0L), 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            allocateInvoiceNet(listOf(5L), 5L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            allocateInvoiceNet(listOf(-1L, 5L), 0L)
        }
        assertThrows(ArithmeticException::class.java) {
            allocateInvoiceNet(listOf(Long.MAX_VALUE, 1L), 0L)
        }
    }

    @Test fun partialReturnsReconcileExactlyToOriginalLineValue() {
        for (quantity in 1L..30L) {
            for (net in 0L..200L) {
                var returned = 0L
                var credited = 0L
                repeat(quantity.toInt()) {
                    val next = returned + 1L
                    val cumulative = proportional(net, next, quantity)
                    val credit = Math.subtractExact(cumulative, credited)
                    org.junit.Assert.assertTrue("Negative return credit", credit >= 0L)
                    credited = Math.addExact(credited, credit)
                    returned = next
                }
                assertEquals(net, credited)
            }
        }
    }

}
