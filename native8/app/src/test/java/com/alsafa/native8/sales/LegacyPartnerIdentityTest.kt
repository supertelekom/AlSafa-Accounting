package com.alsafa.native8.sales

import org.junit.Test
import org.junit.Assert.*

class LegacyPartnerIdentityTest {
 @Test fun sourcePartnerIdsAndShares() {
  assertEquals(listOf("father","me","brother"),LegacyFinancialRules.partners.map { it.id })
  assertEquals(listOf(40,35,25),LegacyFinancialRules.partners.map { it.share })
  assertEquals(100,LegacyFinancialRules.partners.sumOf { it.share })
 }
}
