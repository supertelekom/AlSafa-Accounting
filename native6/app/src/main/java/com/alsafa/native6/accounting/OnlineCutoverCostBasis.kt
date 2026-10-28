package com.alsafa.native6.accounting

import java.math.BigInteger

/**
 * Preview-only allocation of the remaining online invoice cost basis at cutover.
 * Original sale cost and settled/returned cost must be independently reconciled.
 */
object OnlineCutoverCostBasis {
    data class Remaining(val uncollected:Long,val unrecognizedCost:Long,
                         val potentialProfit:Long)

    fun inspect(originalTotal:Long, originalCost:Long,
                alreadyCollected:Long, alreadyRecognizedCost:Long,
                returnedCredit:Long=0L, returnedCost:Long=0L):Remaining {
        require(originalTotal>0L && originalCost>=0L)
        require(alreadyCollected in 0..originalTotal)
        require(returnedCredit>=0L && returnedCost>=0L && returnedCost<=originalCost)
        require(alreadyCollected<=originalTotal-returnedCredit)
        val effectiveTotal=originalTotal-returnedCredit
        val effectiveCost=originalCost-returnedCost
        require(effectiveTotal>0L || alreadyCollected==0L)
        require(alreadyRecognizedCost in 0..effectiveCost)
        val uncollected=effectiveTotal-alreadyCollected
        val targetRecognized=if(effectiveTotal==0L) 0L else
            BigInteger.valueOf(effectiveCost)
                .multiply(BigInteger.valueOf(alreadyCollected))
                .divide(BigInteger.valueOf(effectiveTotal)).longValueExact()
        require(alreadyRecognizedCost==targetRecognized) {
            "Historical cost recognition does not match collected amount; manual audit required"
        }
        val pendingCost=effectiveCost-alreadyRecognizedCost
        return Remaining(uncollected,pendingCost,uncollected-pendingCost)
    }
}
