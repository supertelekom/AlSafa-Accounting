package com.alsafa.native6.accounting

import org.junit.Assert.*
import org.junit.Test

class OnlineCutoverCostBasisTest {
    @Test fun confirmedElevenDollarDueRequiresActualCostBasis() {
        // Hypothetical $100 cost, not the real cost of invoice 231.
        val result=OnlineCutoverCostBasis.inspect(
            originalTotal=16200,originalCost=10000,
            alreadyCollected=15100,alreadyRecognizedCost=9320)
        assertEquals(1100,result.uncollected)
        assertEquals(680,result.unrecognizedCost)
        assertEquals(420,result.potentialProfit)
    }
    @Test fun wrongHistoricalCostMustBlockCutover() {
        assertThrows(IllegalArgumentException::class.java) {
            OnlineCutoverCostBasis.inspect(16200,10000,15100,0)
        }
    }
    @Test fun completelyCollectedInvoiceHasNoPendingBasis() {
        val result=OnlineCutoverCostBasis.inspect(16200,10000,16200,10000)
        assertEquals(0,result.uncollected)
        assertEquals(0,result.unrecognizedCost)
    }
    @Test fun returnedInventoryCostMustNotBeRecognizedAgain() {
        val result=OnlineCutoverCostBasis.inspect(
            20000,12000,10000,6000,returnedCredit=5000,returnedCost=3000)
        assertEquals(5000,result.uncollected)
        assertEquals(3000,result.unrecognizedCost)
    }
}
