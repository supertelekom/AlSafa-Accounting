package com.alsafa.native5.accounting;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
        public final Money recoveredCapital;
        public final Money realizedProfit;
        Totals(Money collected,Money capital,Money profit) {
            this.collected=collected;
            this.recoveredCapital=capital;
            this.realizedProfit=profit;
        }
    }
    public EnumMap<Money.Currency,Totals> forMonth(YearMonth month,ZoneId storeZone) {
        return query(month,storeZone,null);
    }

    /** Filter by the actual payment destination without mixing cash drawers. */
    public EnumMap<Money.Currency,Totals> forMonthAndDestination(YearMonth month,ZoneId storeZone,
            InvoiceSettlement.Channel destination) {
        return query(month,storeZone,Objects.requireNonNull(destination,"destination"));
    }

    private EnumMap<Money.Currency,Totals> query(YearMonth month,ZoneId storeZone,
            InvoiceSettlement.Channel destination) {
        Objects.requireNonNull(month,"month");
        Objects.requireNonNull(storeZone,"storeZone");
        long start=month.atDay(1).atStartOfDay(storeZone).toInstant().toEpochMilli();
        long end=month.plusMonths(1).atDay(1).atStartOfDay(storeZone).toInstant().toEpochMilli();
        EnumMap<Money.Currency,long[]> sums=new EnumMap<>(Money.Currency.class);
        String sql="SELECT currency,received_minor,released_cost_minor FROM online_collections WHERE collected_at_ms>=? AND collected_at_ms<?";
        String[] args={Long.toString(start),Long.toString(end)};
        if(destination!=null) {
            sql+=" AND destination=?";
            args=new String[]{Long.toString(start),Long.toString(end),destination.name()};
        }
        try(Cursor c=db.rawQuery(sql,args)) {
            while(c.moveToNext()) {
                Money.Currency unit=Money.Currency.valueOf(c.getString(0));
                long[] values=sums.get(unit);
                if(values==null) {values=new long[3];sums.put(unit,values);}
                values[0]=Math.addExact(values[0],c.getLong(1));
                values[1]=Math.addExact(values[1],c.getLong(2));
                values[2]=Math.addExact(values[2],
                    Math.subtractExact(c.getLong(1),c.getLong(2)));
            }
        }
        EnumMap<Money.Currency,Totals> result=new EnumMap<>(Money.Currency.class);
        for(Money.Currency unit:sums.keySet()) {
            long[] values=sums.get(unit);
            result.put(unit,new Totals(new Money(unit,values[0]),new Money(unit,values[1]),new Money(unit,values[2])));
        }
        return result;
    }
}
