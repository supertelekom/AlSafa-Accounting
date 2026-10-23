package com.alsafa.native6

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SupplierChronologyTest {
    private lateinit var helper:StoreDb
    private lateinit var store:Store

    @Before fun setUp() {
        helper=StoreDb(RuntimeEnvironment.getApplication() as Context,
            "supplier-test-"+System.nanoTime()+".db")
        store=Store(helper)
    }
    @After fun tearDown() { helper.close() }

    @Test fun supplierPaymentCannotPrecedePurchase() {
        val supplier=store.addSupplier("Test supplier","")
        val product=store.addProduct("Watch","",Currency.USD,15000L,10000L,0L)
        val purchaseDay=LocalDate.now().minusDays(2).toString()
        val beforePurchase=LocalDate.now().minusDays(3).toString()
        store.cash("opening","drawer",Currency.USD,30000L,"Opening cash",date=beforePurchase)
        store.purchase(supplier,listOf(SaleItem(product,1L,10000L)),
            Currency.USD,0L,"drawer",date=purchaseDay)
        assertEquals(10000L,store.supplierDue(supplier,Currency.USD))
        try {
            store.paySupplier(supplier,Currency.USD,1000L,"drawer",date=beforePurchase)
            fail("Cannot pay supplier before payable exists")
        } catch (_:IllegalArgumentException) { }
        assertEquals(10000L,store.supplierDue(supplier,Currency.USD))
        assertTrue(store.negativeSupplierDebtDays().isEmpty())
    }

    @Test fun supplierPaymentsNeverOverpayHistoricalDebt() {
        val supplier=store.addSupplier("Test supplier","")
        val product=store.addProduct("Watch","",Currency.USD,15000L,10000L,0L)
        val purchaseDay=LocalDate.now().minusDays(4).toString()
        val laterDay=LocalDate.now().minusDays(2).toString()
        store.cash("opening","drawer",Currency.USD,30000L,"Opening cash",date=LocalDate.now().minusDays(5).toString())
        store.purchase(supplier,listOf(SaleItem(product,1L,10000L)),
            Currency.USD,0L,"drawer",date=purchaseDay)
        store.paySupplier(supplier,Currency.USD,7000L,"drawer",date=laterDay)
        assertEquals(3000L,store.supplierAvailableOn(supplier,Currency.USD,purchaseDay))
        val cashBefore=store.balance("drawer",Currency.USD)
        try {
            store.paySupplier(supplier,Currency.USD,4000L,"drawer",date=purchaseDay)
            fail("Backdated payment must not overpay later debt")
        } catch (_:IllegalArgumentException) { }
        assertEquals(cashBefore,store.balance("drawer",Currency.USD))
        assertEquals(3000L,store.supplierDue(supplier,Currency.USD))
        assertTrue(store.negativeSupplierDebtDays().isEmpty())
    }
    @Test fun historicalSupplierDebtCarriesForwardWithoutBecomingCurrentMonthPurchase() {
        val supplier=store.addSupplier("Previous-month supplier","")
        val previous=java.time.YearMonth.now().minusMonths(1)
        val current=java.time.YearMonth.now()
        val debtDay=previous.atDay(10).toString()
        val paymentDay=current.atDay(1).toString()
        store.supplierOpening(supplier,Currency.USD,12000L,debtDay)
        store.cash("opening","drawer",Currency.USD,20000L,"Cash before payment",date=debtDay)
        store.paySupplier(supplier,Currency.USD,5000L,"drawer",date=paymentDay)
        val oldStatement=store.supplierStatement(supplier,Currency.USD,previous.toString())
        val currentStatement=store.supplierStatement(supplier,Currency.USD,current.toString())
        assertEquals(12000L,oldStatement.getValue("new_opening_debt"))
        assertEquals(0L,oldStatement.getValue("payments"))
        assertEquals(12000L,oldStatement.getValue("closing"))
        assertEquals(12000L,currentStatement.getValue("opening"))
        assertEquals(0L,currentStatement.getValue("purchases"))
        assertEquals(0L,currentStatement.getValue("new_opening_debt"))
        assertEquals(5000L,currentStatement.getValue("payments"))
        assertEquals(7000L,currentStatement.getValue("closing"))
        assertEquals(7000L,store.supplierDue(supplier,Currency.USD))
        assertTrue(store.supplierMonthReconciles(supplier,Currency.USD,current.toString()))
    }

}
