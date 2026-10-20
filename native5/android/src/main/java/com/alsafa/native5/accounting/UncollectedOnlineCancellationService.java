package com.alsafa.native5.accounting;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * Narrow, safe cancellation: ONLY never-collected online dispatches whose exact
 * shipped stock is physically returned. Historical unlinked dispatches are blocked.
 * No cash or refund is posted. All writes commit together or not at all.
 */
public final class UncollectedOnlineCancellationService {
    private final SQLiteDatabase db;
    public UncollectedOnlineCancellationService(SQLiteDatabase db) { this.db=Objects.requireNonNull(db); }

    public void cancel(String eventId,String invoiceId,String actorId,boolean physicallyReturned) {
        if(!valid(eventId) || !valid(invoiceId) || !valid(actorId))
            throw new IllegalArgumentException("Invalid cancellation identifiers");
        if(!physicallyReturned) throw new IllegalArgumentException("Physical return must be confirmed");
        if(db.inTransaction()) throw new IllegalStateException("Nested transaction");
        db.beginTransactionNonExclusive();
        try {
            long cost,received,released;
            String currency,requestId,reason,dispatchId;
            try(Cursor c=db.rawQuery(
                "SELECT i.cost_minor,i.collected_minor,i.released_cost_minor,i.currency,r.request_id,r.reason,l.dispatch_event_id "+
                "FROM online_invoices i JOIN invoice_cancellation_requests r ON r.invoice_id=i.invoice_id "+
                "JOIN online_dispatch_links l ON l.invoice_id=i.invoice_id "+
                "LEFT JOIN invoice_cancellation_decisions d ON d.request_id=r.request_id "+
                "WHERE i.invoice_id=? AND d.request_id IS NULL",
                new String[]{invoiceId})) {
                if(!c.moveToFirst()) throw new IllegalStateException("Missing pending review or dispatch linkage");
                cost=c.getLong(0); received=c.getLong(1); released=c.getLong(2);
                currency=c.getString(3); requestId=c.getString(4);
                reason=c.getString(5); dispatchId=c.getString(6);
            }
            try(Cursor original=db.rawQuery(
                "SELECT operation_kind FROM accounting_events WHERE event_id=?",
                new String[]{dispatchId})) {
                if(!original.moveToFirst() || !"ONLINE_DISPATCH".equals(original.getString(0)))
                    throw new IllegalStateException("Linked event is not an online dispatch");
            }
            if(received!=0 || released!=0 || cost<=0)
                throw new IllegalStateException("Collected invoices require a refund-specific workflow");
            try(Cursor c=db.rawQuery("SELECT 1 FROM online_collections WHERE invoice_id=? LIMIT 1",
                new String[]{invoiceId})) {
                if(c.moveToFirst()) throw new IllegalStateException("Collection history exists");
            }
            try(Cursor previous=db.rawQuery(
                "SELECT 1 FROM online_cancellations WHERE invoice_id=? LIMIT 1",
                new String[]{invoiceId})) {
                if(previous.moveToFirst()) throw new IllegalStateException("Invoice already cancelled");
            }
            List<InventoryDelta> returned=new ArrayList<>();
            try(Cursor c=db.rawQuery(
                "SELECT product_id,quantity_delta FROM inventory_movements WHERE event_id=? ORDER BY line_no",
                new String[]{dispatchId})) {
                while(c.moveToNext()) {
                    long dispatched=c.getLong(1);
                    if(dispatched>=0 || dispatched==Long.MIN_VALUE)
                        throw new IllegalStateException("Invalid dispatch stock history");
                    returned.add(new InventoryDelta(c.getString(0),-dispatched));
                }
            }
            if(returned.isEmpty()) throw new IllegalStateException("Missing dispatch stock history");
            try(Cursor c=db.rawQuery(
                "SELECT account_id,signed_minor_units,currency FROM journal_lines WHERE event_id=? ORDER BY line_no",
                new String[]{dispatchId})) {
                if(!c.moveToFirst() || !"asset:goods_in_transit".equals(c.getString(0)) ||
                    c.getLong(1)!=cost || !currency.equals(c.getString(2)))
                    throw new IllegalStateException("Dispatch journal does not reconcile");
                if(!c.moveToNext() || !"asset:inventory".equals(c.getString(0)) ||
                    c.getLong(1)!=-cost || !currency.equals(c.getString(2)) || c.moveToNext())
                    throw new IllegalStateException("Dispatch journal does not reconcile");
            }
            long now=System.currentTimeMillis();
            ContentValues event=new ContentValues();
            event.put("event_id",eventId); event.put("operation_kind","ONLINE_DISPATCH_CANCELLATION");
            event.put("created_at_ms",now);
            db.insertOrThrow("accounting_events",null,event);
            insertLine(eventId,0,"asset:inventory",currency,cost);
            insertLine(eventId,1,"asset:goods_in_transit",currency,-cost);
            int line=0;
            for(InventoryDelta delta:returned) {
                long quantity=delta.quantityChange();
                db.execSQL("UPDATE inventory_balances SET quantity=quantity+? WHERE product_id=? AND quantity<=?",
                    new Object[]{quantity,delta.productId(),Long.MAX_VALUE-quantity});
                if(android.database.DatabaseUtils.longForQuery(db,"SELECT changes()",null)!=1)
                    throw new IllegalStateException("Missing stock or quantity overflow");
                ContentValues movement=new ContentValues();
                movement.put("event_id",eventId);movement.put("line_no",line++);
                movement.put("product_id",delta.productId());movement.put("quantity_delta",quantity);
                db.insertOrThrow("inventory_movements",null,movement);
            }
            ContentValues cancellation=new ContentValues();
            cancellation.put("invoice_id",invoiceId);
            cancellation.put("cancellation_event_id",eventId);
            cancellation.put("request_id",requestId);
            cancellation.put("actor_id",actorId);
            cancellation.put("reason",reason);
            cancellation.put("physical_return_confirmed",1);
            cancellation.put("cancelled_at_ms",now);
            db.insertOrThrow("online_cancellations",null,cancellation);
            ContentValues outbox=new ContentValues();
            outbox.put("operation_id",eventId);
            outbox.put("operation_kind","ONLINE_DISPATCH_CANCELLATION");
            JSONObject payload=new JSONObject();
            JSONArray movements=new JSONArray();
            try {
                payload.put("invoiceId",invoiceId);
                payload.put("dispatchEventId",dispatchId);
                payload.put("requestId",requestId);
                payload.put("actorId",actorId);
                payload.put("costMinor",cost);
                payload.put("currency",currency);
                payload.put("physicalReturnConfirmed",true);
                for(InventoryDelta delta:returned) {
                    JSONObject movement=new JSONObject();
                    movement.put("productId",delta.productId());
                    movement.put("quantityDelta",delta.quantityChange());
                    movements.put(movement);
                }
                payload.put("inventoryMovements",movements);
            } catch(JSONException invalid) {
                throw new IllegalStateException("Cannot encode cancellation payload",invalid);
            }
            outbox.put("payload",payload.toString());
            outbox.put("created_at_ms",now);
            db.insertOrThrow("outbox",null,outbox);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private void insertLine(String eventId,int number,String account,String currency,long amount) {
        ContentValues row=new ContentValues();
        row.put("event_id",eventId);row.put("line_no",number);row.put("account_id",account);
        row.put("currency",currency);row.put("signed_minor_units",amount);
        db.insertOrThrow("journal_lines",null,row);
    }
    private static boolean valid(String value) {
        return value!=null && value.matches("[A-Za-z0-9_-]{1,128}");
    }
}
