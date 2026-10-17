package com.alsafa.native5.accounting;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Daily/monthly closing input: opening, inflow, outflow and closing balance
 * per vault and currency. Includes every posted operation, not just FX.
 * The posting event timestamp determines the period, never upload time.
 */
public final class VaultPeriodReport {
    private final SQLiteDatabase db;
    public VaultPeriodReport(SQLiteDatabase db) {this.db=Objects.requireNonNull(db,"db");}

    public static final class Totals {
        public final Money opening;
        public final Money inflow;
        public final Money outflow;
        public final Money closing;
        Totals(Money.Currency currency,long opening,long inflow,long outflow) {
            this.opening=new Money(currency,opening);
            this.inflow=new Money(currency,inflow);
            this.outflow=new Money(currency,outflow);
            this.closing=new Money(currency,Math.addExact(opening,Math.subtractExact(inflow,outflow)));
        }
    }

    public EnumMap<Money.Currency,Totals> forMonth(InvoiceSettlement.Channel vault,
            YearMonth month,ZoneId storeZone) {
        Objects.requireNonNull(month,"month");
        Objects.requireNonNull(storeZone,"storeZone");
        long start=month.atDay(1).atStartOfDay(storeZone).toInstant().toEpochMilli();
        long end=month.plusMonths(1).atDay(1).atStartOfDay(storeZone).toInstant().toEpochMilli();
        return forPeriod(vault,start,end);
    }

    public EnumMap<Money.Currency,Totals> forPeriod(InvoiceSettlement.Channel vault,
            long startInclusiveMs,long endExclusiveMs) {
        Objects.requireNonNull(vault,"vault");
        if(startInclusiveMs>=endExclusiveMs) throw new IllegalArgumentException("Invalid period");
        String account=VaultRouting.account(vault,Money.Currency.USD);
        EnumMap<Money.Currency,Totals> result=new EnumMap<>(Money.Currency.class);
        String sql="SELECT j.currency,"+
            "COALESCE(SUM(CASE WHEN e.created_at_ms<? THEN j.signed_minor_units ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN e.created_at_ms>=? AND e.created_at_ms<? AND j.signed_minor_units>0 THEN j.signed_minor_units ELSE 0 END),0),"+
            "COALESCE(SUM(CASE WHEN e.created_at_ms>=? AND e.created_at_ms<? AND j.signed_minor_units<0 THEN -j.signed_minor_units ELSE 0 END),0)"+
            " FROM journal_lines j JOIN accounting_events e ON e.event_id=j.event_id"+
            " WHERE j.account_id=? AND e.created_at_ms<? GROUP BY j.currency";
        try(Cursor c=db.rawQuery(sql,new String[]{Long.toString(startInclusiveMs),
            Long.toString(startInclusiveMs),Long.toString(endExclusiveMs),
            Long.toString(startInclusiveMs),Long.toString(endExclusiveMs),
            account,Long.toString(endExclusiveMs)})) {
            while(c.moveToNext()) {
                Money.Currency currency=Money.Currency.valueOf(c.getString(0));
                VaultRouting.validate(vault,currency);
                result.put(currency,new Totals(currency,c.getLong(1),c.getLong(2),c.getLong(3)));
            }
        }
        for(Money.Currency currency:Money.Currency.values()) {
            if(vault==InvoiceSettlement.Channel.SHAM_CASH && currency==Money.Currency.TRY) continue;
            if(!result.containsKey(currency)) result.put(currency,new Totals(currency,0,0,0));
        }
        return result;
    }
}
