package com.alsafa.native4.core;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class SaleLineGuardTest {
 @Test public void allowsDistinctProductsAndManualLines(){SaleLineGuard.requireUniqueProducts(Arrays.asList("A","B","",""));}
 @Test(expected=IllegalArgumentException.class) public void rejectsRepeatedProduct(){SaleLineGuard.requireUniqueProducts(Arrays.asList("A","B","A"));}
 @Test(expected=IllegalArgumentException.class) public void rejectsRepeatedProductWithWhitespace(){SaleLineGuard.requireUniqueProducts(Arrays.asList("A"," A "));}
 @Test(expected=IllegalArgumentException.class) public void rejectsEmptySale(){SaleLineGuard.requireUniqueProducts(Arrays.<String>asList());}
 @Test public void calculatesRemainingStock(){assertEquals(3,SaleLineGuard.remaining(5,2));}
 @Test(expected=IllegalArgumentException.class) public void rejectsOversell(){SaleLineGuard.remaining(1,2);}
 @Test(expected=IllegalArgumentException.class) public void rejectsZeroQuantity(){SaleLineGuard.remaining(2,0);}
 @Test public void allowsFullDiscount(){assertEquals(0.0,SaleLineGuard.payable(50.0,50.0),0.00001);}
 @Test public void computesPartialDiscount(){assertEquals(45.0,SaleLineGuard.payable(50.0,5.0),0.00001);}
 @Test(expected=IllegalArgumentException.class) public void rejectsOverDiscount(){SaleLineGuard.payable(50.0,51.0);}
 @Test(expected=IllegalArgumentException.class) public void rejectsInfiniteSubtotal(){SaleLineGuard.payable(Double.POSITIVE_INFINITY,1.0);}
 @Test(expected=IllegalArgumentException.class) public void rejectsNaNDiscount(){SaleLineGuard.payable(50.0,Double.NaN);}
 @Test public void multipliesSaleLine(){assertEquals(75.0,SaleLineGuard.lineAmount(25.0,3),0.00001);}
 @Test public void sumsSaleLines(){assertEquals(125.0,SaleLineGuard.addAmount(75.0,50.0),0.00001);}
 @Test(expected=IllegalArgumentException.class) public void rejectsInfiniteLine(){SaleLineGuard.lineAmount(Double.MAX_VALUE,2);}
 @Test(expected=IllegalArgumentException.class) public void rejectsInfiniteSum(){SaleLineGuard.addAmount(Double.MAX_VALUE,Double.MAX_VALUE);}
 @Test(expected=IllegalArgumentException.class) public void rejectsNegativeLinePrice(){SaleLineGuard.lineAmount(-1.0,2);}
 @Test public void computesUnpaidUsd(){assertEquals(15.0,SaleLineGuard.balanceUsd(60.0,45.0),0.00001);}
 @Test public void acceptsExactPayment(){assertEquals(0.0,SaleLineGuard.balanceUsd(60.0,60.0),0.00001);}
 @Test public void toleratesSubCentRounding(){assertEquals(0.0,SaleLineGuard.balanceUsd(60.0,60.005),0.00001);}
 @Test(expected=IllegalArgumentException.class) public void rejectsExcessPayment(){SaleLineGuard.balanceUsd(60.0,61.0);}
 @Test(expected=IllegalArgumentException.class) public void rejectsInvalidPayment(){SaleLineGuard.balanceUsd(60.0,Double.NaN);}
 @Test public void marksFullyPaid(){assertEquals("PAID",SaleLineGuard.paymentStatus(60,60,true));}
 @Test public void marksPartialPayment(){assertEquals("PARTIAL",SaleLineGuard.paymentStatus(60,40,true));}
 @Test public void marksUnpaidInvoiceDue(){assertEquals("DUE",SaleLineGuard.paymentStatus(60,0,false));}
 @Test public void marksZeroInvoicePaid(){assertEquals("PAID",SaleLineGuard.paymentStatus(0,0,false));}
}
