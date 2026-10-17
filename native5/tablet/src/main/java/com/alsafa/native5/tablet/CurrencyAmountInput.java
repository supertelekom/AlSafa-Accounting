package com.alsafa.native5.tablet;

import com.alsafa.native5.accounting.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tablet display/input adapter for the current Native5 two-decimal minor-unit
 * convention. Rejects rounding, grouping separators and scientific notation.
 */
public final class CurrencyAmountInput {
    private CurrencyAmountInput() {}

    public static Money parse(Money.Currency currency,String input) {
        if(currency==null || input==null)
            throw new IllegalArgumentException("Missing currency amount");
        String text=input.trim();
        if(!text.matches("[0-9]+(?:\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("أدخل مبلغًا موجبًا بصيغة 10 أو 10.50");
        try {
            long minor=new BigDecimal(text).movePointRight(2)
                .setScale(0,RoundingMode.UNNECESSARY).longValueExact();
            return new Money(currency,minor);
        } catch(ArithmeticException error) {
            throw new IllegalArgumentException("المبلغ أكبر من الحد المسموح",error);
        }
    }

    public static String display(Money money) {
        if(money==null) throw new IllegalArgumentException("Missing amount");
        return BigDecimal.valueOf(money.minorUnits(),2).toPlainString();
    }
}
