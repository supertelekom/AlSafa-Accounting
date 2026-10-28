package com.alsafa.native6.accounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineCollectionAllocationTest {
    @Test fun partialCollectionRecognizesOnlyProportionalCostAndProfit() {
        val first=OnlineCollectionAllocation.allocate(16200,10000,0,0,15100)
        assertEquals(9320,first.capital)
        assertEquals(5780,first.profit)
        val final=OnlineCollectionAllocation.allocate(16200,10000,15100,9320,1100)
        assertEquals(680,final.capital)
        assertEquals(420,final.profit)
        assertEquals(10000,first.capital+final.capital)
        assertEquals(6200,first.profit+final.profit)
    }

    @Test fun invoice231UsesRealArchivedCost54Dollars() {
        // 9 watches * $6, sale $162, historical receipt $151, due $11.
        val historical=OnlineCollectionAllocation.allocate(16200,5400,0,0,15100)
        assertEquals(5033,historical.capital)
        assertEquals(10067,historical.profit)
        val remaining=OnlineCollectionAllocation.allocate(
            16200,5400,15100,historical.capital,1100)
        assertEquals(367,remaining.capital)
        assertEquals(733,remaining.profit)
        assertEquals(5400,historical.capital+remaining.capital)
    }

    @Test fun pennyRemainderIsAllocatedToFinalReceipt() {
        val first=OnlineCollectionAllocation.allocate(300,100,0,0,100)
        val second=OnlineCollectionAllocation.allocate(300,100,100,first.capital,100)
        val last=OnlineCollectionAllocation.allocate(300,100,200,
            first.capital+second.capital,100)
        assertEquals(100,first.capital+second.capital+last.capital)
    }

    @Test fun overcollectionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            OnlineCollectionAllocation.allocate(16200,10000,15100,9320,1101)
        }
    }

    @Test fun costCanExceedSellingPriceWithoutLosingCapital() {
        val a=OnlineCollectionAllocation.allocate(100,150,0,0,100)
        assertEquals(150,a.capital)
        assertEquals(-50,a.profit)
    }
}
