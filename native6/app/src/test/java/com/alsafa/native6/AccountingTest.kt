package com.alsafa.native6

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class AccountingTest {
    private fun fresh()=Store(StoreDb(RuntimeEnvironment.getApplication(),"test-${Store.id()}.db"))
    private fun fails(action:()->Unit){try{action();fail("Expected rejection")}catch(e:Exception){}}
    @Test fun saleAndFailedMultiLineSaleAreAtomic(){
        val s=fresh();val p=s.addProduct("Casio","A",Currency.USD,2000,1200,2);val q=s.addProduct("Second","B",Currency.USD,3000,1000,0)
        fails{s.sale(listOf(SaleItem(p,1,2000),SaleItem(q,1,3000)),"", "",Currency.USD,5000,"drawer")}
        assertEquals(2,s.product(p).stock);assertEquals(0,s.invoices().size);assertEquals(0,s.balance("drawer",Currency.USD))
        s.sale(listOf(SaleItem(p,1,2000)),"", "",Currency.USD,2000,"drawer");assertEquals(1,s.product(p).stock);assertEquals(2000,s.balance("drawer",Currency.USD))
    }
    @Test fun duplicateCommandDoesNotDoublePost(){val s=fresh();val p=s.addProduct("X","",Currency.USD,1000,500,3);val command=Store.id();s.sale(listOf(SaleItem(p,1,1000)),"","",Currency.USD,1000,"drawer",command=command);fails{s.sale(listOf(SaleItem(p,1,1000)),"","",Currency.USD,1000,"drawer",command=command)};assertEquals(2,s.product(p).stock);assertEquals(1000,s.balance("drawer",Currency.USD))}
    @Test fun returnReducesDebtBeforeCreatingRefundLiability(){
        val s=fresh();val p=s.addProduct("X","",Currency.USD,1000,500,3);val inv=s.sale(listOf(SaleItem(p,3,1000)),"Client","",Currency.USD,1500,"drawer")
        s.returnSale(inv,mapOf(0 to 1L),"إرجاع");assertEquals(500,s.due(inv));assertEquals(0,s.refundable(inv));assertEquals(1500,s.balance("drawer",Currency.USD))
        s.cancelSale(inv,"إلغاء الباقي");assertEquals(3,s.product(p).stock);assertEquals(1500,s.refundable(inv));assertEquals(1500,s.balance("drawer",Currency.USD))
        s.refund(inv,1000,"drawer");assertEquals(500,s.refundable(inv));assertEquals(500,s.balance("drawer",Currency.USD));fails{s.refund(inv,501,"drawer")};fails{s.cancelSale(inv,"تكرار")}
    }
    @Test fun proportionalDiscountHasNoReturnRoundingLeak(){val s=fresh();val p=s.addProduct("X","",Currency.USD,100,50,3);val inv=s.sale(listOf(SaleItem(p,3,100)),"","",Currency.USD,299,"drawer",1);repeat(3){s.returnSale(inv,mapOf(0 to 1L),"جزئي")};assertEquals(299,s.credit(inv));assertEquals(299,s.refundable(inv))}
    @Test fun currenciesNeverMixAndOverdraftRollsBack(){val s=fresh();s.cash("opening","drawer",Currency.TRY,10000,"افتتاح");fails{s.cash("expense","drawer",Currency.USD,100,"مصاريف")};assertEquals(10000,s.balance("drawer",Currency.TRY));assertEquals(0,s.balance("drawer",Currency.USD))}
    @Test fun supplierPaymentUsesPayableAndPurchaseAddsStock(){val s=fresh();val p=s.addProduct("X","",Currency.USD,1500,0,0);val supplier=s.addSupplier("Supplier","");s.cash("opening","drawer",Currency.USD,5000,"افتتاح");s.purchase(supplier,listOf(SaleItem(p,2,1000)),Currency.USD,500,"drawer");assertEquals(2,s.product(p).stock);assertEquals(1500,s.supplierDue(supplier,Currency.USD));s.paySupplier(supplier,Currency.USD,1500,"drawer");assertEquals(0,s.supplierDue(supplier,Currency.USD));assertEquals(3000,s.balance("drawer",Currency.USD))}
    @Test fun withdrawalBelongsToActualMonth(){val s=fresh();s.cash("opening","drawer",Currency.USD,10000,"افتتاح","2026-09-01");s.cash("withdrawal","drawer",Currency.USD,2000,"سحب","2026-10-02","father");assertTrue(s.monthReport("2026-09").none{it["account"]=="equity:withdrawal:father"});assertEquals("2000",s.monthReport("2026-10").single{it["account"]=="equity:withdrawal:father"}["amount"])}
    @Test fun backupRoundTripAndExistingDataProtection(){val s=fresh();s.addProduct("X","",Currency.SYP,50000,20000,4);s.cash("opening","drawer",Currency.SYP,10000,"افتتاح");val out=ByteArrayOutputStream();val ctx=RuntimeEnvironment.getApplication();Backup(ctx,s).export(out);val target=fresh();Backup(ctx,target).restore(ByteArrayInputStream(out.toByteArray()));assertEquals(4,target.products().single().stock);assertEquals(10000,target.balance("drawer",Currency.SYP));fails{Backup(ctx,target).restore(ByteArrayInputStream(out.toByteArray()))};assertEquals(4,target.products().single().stock)}
    @Test fun closingDoesNotOverwriteBalance(){val s=fresh();s.cash("opening","drawer",Currency.USD,10000,"افتتاح");s.closeDay("drawer",mapOf(Currency.USD to 9900L,Currency.TRY to 0L,Currency.SYP to 0L));assertEquals(10000,s.balance("drawer",Currency.USD));assertEquals(9900,s.scalar("SELECT counted FROM closings WHERE currency='USD'"))}
    @Test fun exactMoneyRejectsFractionalCents(){assertEquals(12345,Money.parse("١٢٣٫٤٥",Currency.USD).minor);fails{Money.parse("1.001",Currency.USD)};fails{validatePosting(listOf(Posting("a",Currency.USD,1),Posting("b",Currency.TRY,-1)))}}
    @Test fun exactInventoryValueSurvivesRoundedAverage(){val s=fresh();val p=s.addProduct("X","",Currency.USD,200,100,1);val supplier=s.addSupplier("S","");s.purchase(supplier,listOf(SaleItem(p,2,101)),Currency.USD,0,"drawer");assertEquals(302,s.product(p).value);repeat(3){s.sale(listOf(SaleItem(p,1,200)),"","",Currency.USD,200,"drawer")};assertEquals(0,s.product(p).value);assertEquals(302,s.scalar("SELECT sum(amount) FROM journal WHERE account='expense:cogs'"))}
    @Test fun nativeHomeStarts(){val controller=org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup();assertNotNull(controller.get().findViewById<android.view.View>(android.R.id.content));controller.pause().stop().destroy()}

}
