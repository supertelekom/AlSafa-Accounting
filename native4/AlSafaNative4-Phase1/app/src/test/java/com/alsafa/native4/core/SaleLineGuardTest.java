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
}
