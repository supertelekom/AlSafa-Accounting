package com.alsafa.native5.accounting;

import org.json.JSONObject;
import org.json.JSONException;

/** Stable JSON for review-only synchronization; never an accounting posting. */
public final class ReviewSyncPayload {
    private ReviewSyncPayload() {}

    public static String request(String id,String invoiceId,String number,String reason,long at) {
        try {
            JSONObject data=new JSONObject();
            data.put("schemaVersion",1);
            data.put("requestId",id);
            data.put("invoiceId",invoiceId);
            data.put("invoiceNumber",number);
            data.put("reason",reason);
            data.put("requestedAtMs",at);
            return data.toString();
        } catch(JSONException invalid) {
            throw new IllegalStateException("Cannot encode review request",invalid);
        }
    }

    public static String decision(String requestId,String decision,String reason,long at) {
        try {
            JSONObject data=new JSONObject();
            data.put("schemaVersion",1);
            data.put("requestId",requestId);
            data.put("decision",decision);
            data.put("reason",reason);
            data.put("decidedAtMs",at);
            return data.toString();
        } catch(JSONException invalid) {
            throw new IllegalStateException("Cannot encode review decision",invalid);
        }
    }
}
