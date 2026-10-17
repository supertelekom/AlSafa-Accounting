package com.alsafa.native5.accounting;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Live vault balances derived from immutable journal lines.
 * Never sum different currencies into one number or infer a conversion rate.
 */
public final class VaultBalanceReader {
    private final SQLiteDatabase db;
    public VaultBalanceReader(SQLiteDatabase db) {this.db=Objects.requireNonNull(db,"db");}

    public Money balance(InvoiceSettlement.Channel vault,Money.Currency currency) {
        String account=VaultRouting.account(vault,currency);
        long minor=DatabaseUtils.longForQuery(db,
            "SELECT COALESCE(SUM(signed_minor_units),0) FROM journal_lines WHERE account_id=? AND currency=?",
            new String[]{account,currency.name()});
        return new Money(currency,minor);
    }

    public EnumMap<Money.Currency,Money> balances(InvoiceSettlement.Channel vault) {
        Objects.requireNonNull(vault,"vault");
        EnumMap<Money.Currency,Money> result=new EnumMap<>(Money.Currency.class);
        for(Money.Currency currency:Money.Currency.values()) {
            if(vault==InvoiceSettlement.Channel.SHAM_CASH && currency==Money.Currency.TRY)
                continue;
            result.put(currency,balance(vault,currency));
        }
        return result;
    }
}
