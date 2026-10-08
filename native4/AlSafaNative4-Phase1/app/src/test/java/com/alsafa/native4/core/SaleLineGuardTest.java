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
}
