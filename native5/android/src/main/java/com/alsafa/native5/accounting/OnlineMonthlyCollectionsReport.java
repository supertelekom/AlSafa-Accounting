package com.alsafa.native5.accounting;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Cash-basis management report for online collections only.
 * Dispatches do not appear; each immutable receipt is counted in its
 * actual collection month using the shop's configured timezone.
 */
public final class OnlineMonthlyCollectionsReport {
    private final SQLiteDatabase db;
    public OnlineMonthlyCollectionsReport(SQLiteDatabase db) {
        this.db=Objects.requireNonNull(db,"db");
    }
    public static final class Totals {
        public final Money collected;
        public final Money realizedProfit;
        Totals(Money collected,Money profit) {
            this.collected=collected;
            this.realizedProfit=profit;
        }
    }
    public EnumMap<Money.Currency,Totals> forMonth(YearMonth month,ZoneId storeZone) {
        Objects.requireNonNull(month,"month");
        Objects.requireNonNull(storeZone,"storeZone");
        long start=month.atDay(1).atStartOfDay(storeZone).toInstant().toEpochMilli();
        long end=month.plusMonths(1).atDay(1).atStartOfDay(storeZone).toInstant().toEpochMilli();
        EnumMap<Money.Currency,long[]> sums=new EnumMap<>(Money.Currency.class);
        try(Cursor c=db.rawQuery(
            "SELECT currency,received_minor,released_cost_minor FROM online_collections WHERE collected_at_ms>=? AND collected_at_ms<?",
            new String[]{Long.toString(start),Long.toString(end)})) {
            while(c.moveToNext()) {
                Money.Currency unit=Money.Currency.valueOf(c.getString(0));
                long[] values=sums.get(unit);
                if(values==null) {values=new long[2];sums.put(unit,values);}
                values[0]=Math.addExact(values[0],c.getLong(1));
                values[1]=Math.addExact(values[1],
                    Math.subtractExact(c.getLong(1),c.getLong(2)));
            }
        }
        EnumMap<Money.Currency,Totals> result=new EnumMap<>(Money.Currency.class);
        for(Money.Currency unit:sums.keySet()) {
            long[] values=sums.get(unit);
            result.put(unit,new Totals(new Money(unit,values[0]),new Money(unit,values[1])));
        }
        return result;
    }
}
