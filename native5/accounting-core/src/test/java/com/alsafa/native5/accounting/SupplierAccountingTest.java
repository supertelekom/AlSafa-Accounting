package com.alsafa.native5.accounting;

import org.junit.Test;
import static org.junit.Assert.*;

public class SupplierAccountingTest {
    @Test public void supplierInvoiceCreatesPayableNotCashMovement() {
        PostingBatch invoice=SupplierAccounting.recordInvoice("purchase-1","supplier-inv-1",
            new Money(Money.Currency.USD,10000),new Money(Money.Currency.USD,2000));
        assertEquals("SUPPLIER_INVOICE",invoice.operationKind());
        assertEquals(3,invoice.journal().lines().size());
        assertEquals("liability:accounts_payable",invoice.journal().lines().get(2).accountId);
        assertEquals(-12000,invoice.journal().lines().get(2).amount.minorUnits());
    }
    @Test public void supplierPaymentReducesPayableWithoutNewExpense() {
        PostingBatch payment=SupplierAccounting.settle("pay-1","supplier-inv-1",
            new Money(Money.Currency.USD,5000),InvoiceSettlement.Channel.SHAM_CASH);
        assertEquals("SUPPLIER_PAYMENT",payment.operationKind());
        assertEquals("liability:accounts_payable",payment.journal().lines().get(0).accountId);
        assertEquals("asset:payment:SHAM_CASH",payment.journal().lines().get(1).accountId);
        assertEquals(-5000,payment.journal().lines().get(1).amount.minorUnits());
    }
}
