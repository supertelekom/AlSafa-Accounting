package com.alsafa.native6

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReceivableAccountingTest {
    private lateinit var helper: StoreDb
    private lateinit var store: Store

    @Before fun setUp() {
        val context=RuntimeEnvironment.getApplication() as Context
        helper=StoreDb(context, "receivable-test-" + System.nanoTime() + ".db")
        store=Store(helper)
    }
    @After fun tearDown() { helper.close() }

    private fun invoice(paid:Long):String {
        val product=store.addProduct("Test watch","",Currency.USD,10000L,5000L,4L)
        return store.sale(listOf(SaleItem(product,1L,10000L)),"Customer","",Currency.USD,paid,"drawer")
    }

    @Test fun paidSaleReturnCreatesRefundLiabilityNotCustomerDebt() {
        val id=invoice(10000L)
        assertEquals(0L,store.due(id))
        store.returnSale(id,mapOf(0 to 1L),"Returned")
        assertEquals(0L,store.due(id))
        assertEquals(0L,store.invoiceDueOn(id,Store.today()))
        assertEquals(10000L,store.refundLiability(id))
        assertEquals(10000L,store.refundable(id))
        store.refund(id,10000L,"drawer")
        assertEquals(0L,store.refundLiability(id))
        assertEquals(0L,store.due(id))
        assertTrue(store.customerReceivableIssues().isEmpty())
        assertTrue(store.refundLiabilityIssues().isEmpty())
    }

    @Test fun creditSaleReturnCancelsReceivableWithoutCashRefund() {
        val id=invoice(0L)
        assertEquals(10000L,store.due(id))
        store.returnSale(id,mapOf(0 to 1L),"Returned")
        assertEquals(0L,store.due(id))
        assertEquals(0L,store.refundLiability(id))
        assertEquals(0L,store.refundable(id))
        assertTrue(store.customerReceivableIssues().isEmpty())
    }

    @Test fun refundCannotExceedPostedLiability() {
        val id=invoice(10000L)
        store.returnSale(id,mapOf(0 to 1L),"Returned")
        try {
            store.refund(id,10001L,"drawer")
            fail("Excess refund must be rejected")
        } catch (_:IllegalArgumentException) { }
        assertEquals(10000L,store.refundLiability(id))
    }
    @Test fun partialRefundKeepsOnlyRemainingLiability() {
        val id=invoice(10000L)
        store.returnSale(id,mapOf(0 to 1L),"Returned")
        store.refund(id,4000L,"drawer")
        assertEquals(6000L,store.refundLiability(id))
        assertEquals(6000L,store.refundable(id))
        assertEquals(0L,store.due(id))
        assertTrue(store.refundLiabilityIssues().isEmpty())
    }

    @Test fun historicalRefundCannotOverdrawLaterRefundLiability() {
        val product=store.addProduct("Backdated watch","",Currency.USD,10000L,5000L,2L)
        val saleDay=java.time.LocalDate.now().minusDays(4).toString()
        val returnDay=java.time.LocalDate.now().minusDays(3).toString()
        val laterDay=java.time.LocalDate.now().minusDays(2).toString()
        val id=store.sale(listOf(SaleItem(product,1L,10000L)),"Customer","",
            Currency.USD,10000L,"drawer",date=saleDay,receiptDate=saleDay)
        store.returnSale(id,mapOf(0 to 1L),"Returned",date=returnDay)
        store.refund(id,4000L,"drawer",date=laterDay)
        assertEquals(6000L,store.refundableOn(id,returnDay))
        try {
            store.refund(id,6001L,"drawer",date=returnDay)
            fail("Backdated refund must respect the later refund")
        } catch (_:IllegalArgumentException) { }
        assertEquals(6000L,store.refundLiability(id))
        assertTrue(store.negativeRefundLiabilityDays().isEmpty())
    }

    @Test fun refundBeforeReturnDateIsRejectedWithoutChangingLedger() {
        val product=store.addProduct("Chronology watch","",Currency.USD,10000L,5000L,2L)
        val saleDay=java.time.LocalDate.now().minusDays(5).toString()
        val beforeReturn=java.time.LocalDate.now().minusDays(4).toString()
        val returnDay=java.time.LocalDate.now().minusDays(3).toString()
        val id=store.sale(listOf(SaleItem(product,1L,10000L)),"Customer","",
            Currency.USD,10000L,"drawer",date=saleDay,receiptDate=saleDay)
        store.returnSale(id,mapOf(0 to 1L),"Returned",date=returnDay)
        assertEquals(0L,store.refundableOn(id,beforeReturn))
        try {
            store.refund(id,1000L,"drawer",date=beforeReturn)
            fail("Refund must not predate its liability")
        } catch (_:IllegalArgumentException) { }
        assertEquals(10000L,store.refundLiability(id))
        assertTrue(store.negativeRefundLiabilityDays().isEmpty())
    }

    @Test fun rejectedRefundDoesNotCreateLedgerOrCashMovement() {
        val id=invoice(10000L)
        store.returnSale(id,mapOf(0 to 1L),"Returned")
        val cashBefore=store.balance("drawer",Currency.USD)
        val refundsBefore=store.scalar("SELECT count(*) FROM refunds WHERE invoice_id=?",id)
        try {
            store.refund(id,10001L,"drawer")
            fail("Invalid refund must be rejected")
        } catch (_:IllegalArgumentException) { }
        assertEquals(cashBefore,store.balance("drawer",Currency.USD))
        assertEquals(refundsBefore,store.scalar("SELECT count(*) FROM refunds WHERE invoice_id=?",id))
        assertEquals(10000L,store.refundLiability(id))
    }

    @Test fun rejectedBackdatedCollectionDoesNotChangeReceivable() {
        val product=store.addProduct("Credit watch","",Currency.USD,10000L,5000L,2L)
        val invoiceDay=java.time.LocalDate.now().minusDays(3).toString()
        val receiptDay=java.time.LocalDate.now().minusDays(1).toString()
        val id=store.sale(listOf(SaleItem(product,1L,10000L)),"Customer","",
            Currency.USD,0L,"drawer",date=invoiceDay)
        store.collect(id,7000L,"drawer",date=receiptDay)
        val cashBefore=store.balance("drawer",Currency.USD)
        try {
            store.collect(id,4000L,"drawer",date=invoiceDay)
            fail("Collection must not overdraw future receivable")
        } catch (_:IllegalArgumentException) { }
        assertEquals(3000L,store.due(id))
        assertEquals(cashBefore,store.balance("drawer",Currency.USD))
        assertTrue(store.negativeReceivableDays().isEmpty())
    }

}
