package com.alsafa.native5.accounting;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/**
 * Explicit, separately approved cash-count correction. Never changes a closing
 * snapshot. Variance is posted to a suspense account, not sales or profit.
 */
public final class VaultVarianceAdjustment {
    private VaultVarianceAdjustment() {}

    public static PostingBatch post(String eventId,String closingId,
            InvoiceSettlement.Channel vault,Money variance,String reason) {
        if(eventId==null || !eventId.matches("[A-Za-z0-9_-]{1,128}") ||
           closingId==null || !closingId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("Invalid event or closing ID");
        Objects.requireNonNull(variance,"variance");
        Objects.requireNonNull(vault,"vault");
        VaultRouting.validate(vault,variance.currency());
        if(variance.minorUnits()==0)
            throw new IllegalArgumentException("No difference to correct");
        if(reason==null || reason.trim().length()<3 || reason.length()>500)
            throw new IllegalArgumentException("Adjustment requires an explanation");
        String account=VaultRouting.account(vault,variance.currency());
        JournalEntry journal=new JournalEntry(eventId,Arrays.asList(
            new JournalEntry.Line(account,variance),
            new JournalEntry.Line("liability:cash_count_suspense",variance.negate())
        ));
        String safeReason=reason.trim().replace("\\","\\\\").replace("\"","\\\"")
            .replace("\n","\\n").replace("\r","\\r");
        String payload="{\"closingId\":\""+closingId+"\",\"vault\":\""+vault+
            "\",\"currency\":\""+variance.currency()+"\",\"varianceMinor\":"+
            variance.minorUnits()+",\"reason\":\""+safeReason+"\"}";
        return new PostingBatch(journal,"VAULT_VARIANCE_ADJUSTMENT",payload,
            Collections.emptyList());
    }
}
