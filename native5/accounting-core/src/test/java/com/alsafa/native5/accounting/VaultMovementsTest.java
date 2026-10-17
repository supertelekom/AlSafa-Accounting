package com.alsafa.native5.accounting;

import org.junit.Test;
import static org.junit.Assert.*;

public class VaultMovementsTest {
    private static Money amount(Money.Currency currency) {return new Money(currency,1250);}
    @Test public void transfersAreBalancedAndPreserveCurrency() {
        for(Money.Currency currency:Money.Currency.values()) {
            PostingBatch move=VaultMovements.transfer("move-"+currency,amount(currency),
                InvoiceSettlement.Channel.CASH_DRAWER,InvoiceSettlement.Channel.EXTERNAL_CASHBOX);
            assertEquals("VAULT_TRANSFER",move.operationKind());
            assertEquals(currency,move.journal().lines().get(0).amount.currency());
            assertEquals(1250,move.journal().lines().get(0).amount.minorUnits());
            assertEquals(-1250,move.journal().lines().get(1).amount.minorUnits());
        }
    }
    @Test public void shamCashRejectsTurkishLiraForAllMovements() {
        Money tryAmount=amount(Money.Currency.TRY);
        assertRejected(() -> VaultMovements.transfer("transfer-1",tryAmount,
            InvoiceSettlement.Channel.CASH_DRAWER,InvoiceSettlement.Channel.SHAM_CASH));
        assertRejected(() -> VaultMovements.expense("expense-1",tryAmount,
            InvoiceSettlement.Channel.SHAM_CASH));
        assertRejected(() -> VaultMovements.ownerContribution("contribution-1",tryAmount,
            InvoiceSettlement.Channel.SHAM_CASH));
        assertRejected(() -> VaultMovements.ownerWithdrawal("withdrawal-1",tryAmount,
            InvoiceSettlement.Channel.SHAM_CASH));
    }
    @Test public void contributionIsEquityNotSalesAndDrawingsAreNotExpense() {
        PostingBatch contribution=VaultMovements.ownerContribution("capital-1",
            amount(Money.Currency.USD),InvoiceSettlement.Channel.SHAM_CASH);
        assertEquals("equity:owner_contribution",contribution.journal().lines().get(1).accountId);
        PostingBatch withdrawal=VaultMovements.ownerWithdrawal("drawing-1",
            amount(Money.Currency.SYP),InvoiceSettlement.Channel.CASH_DRAWER);
        assertEquals("equity:owner_drawings",withdrawal.journal().lines().get(0).accountId);
    }
    @Test public void rejectsZeroAndSameVaultTransfers() {
        assertRejected(() -> VaultMovements.transfer("same-1",amount(Money.Currency.USD),
            InvoiceSettlement.Channel.CASH_DRAWER,InvoiceSettlement.Channel.CASH_DRAWER));
        assertRejected(() -> VaultMovements.expense("zero-1",new Money(Money.Currency.USD,0),
            InvoiceSettlement.Channel.CASH_DRAWER));
    }
    private static void assertRejected(Runnable task) {
        try {task.run();fail("Expected rejection");}
        catch(IllegalArgumentException expected) {}
    }
}
