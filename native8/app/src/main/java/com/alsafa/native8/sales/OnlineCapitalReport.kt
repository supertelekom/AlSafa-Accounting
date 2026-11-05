package com.alsafa.native8.sales

import java.math.BigDecimal
import java.math.MathContext

/**
 * Two distinct online cost measures:
 * - stockCostOnSaleDate: cost of stock shipped/sold, counted once per invoice
 * - recognizedCostOnCollectionDate: proportion of cost matched to actual collections
 * These are report projections only and NEVER post a stock movement or expense.
 */
object OnlineCapitalReport {
 private val mc=MathContext.DECIMAL128
 private val zero=BigDecimal.ZERO
 fun stockCost(lines:List<RoomSaleLine>):BigDecimal =
  lines.fold(zero) { sum,line ->
   val quantity=line.quantity.toBigDecimal()
   val unitCost=line.unit_cost.toBigDecimal()
   require(quantity>=zero && unitCost>=zero)
   sum+quantity.multiply(unitCost,mc)
  }
 fun recognizedCost(events:List<SaleProfitEvent>):BigDecimal =
  events.fold(zero) { sum,event ->
   val net=event.invoice_net.toBigDecimal()
   val credited=event.credited_base.toBigDecimal()
   val cost=event.cost_basis.toBigDecimal()
   require(net>=zero && credited>=zero && cost>=zero)
   if(net==zero) {
    require(credited==zero)
    sum
   } else sum+cost.multiply(credited,mc).divide(net,mc)
  }
}
