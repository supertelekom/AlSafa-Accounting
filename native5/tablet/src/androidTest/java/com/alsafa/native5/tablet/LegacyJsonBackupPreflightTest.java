package com.alsafa.native5.tablet;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LegacyJsonBackupPreflightTest {
    private LegacyJsonBackupPreflight.Inspection inspect(String json) throws Exception {
        return LegacyJsonBackupPreflight.inspect(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void acceptsExportedEnvelopeAndCounts() throws Exception {
        LegacyJsonBackupPreflight.Inspection result=inspect(
            "{\"format\":\"alsafa-accounting-backup\",\"formatVersion\":2,"+
            "\"data\":{\"settings\":{},\"products\":[{}],\"sales\":[],\"suppliers\":[{},{}]}}");
        assertEquals(2,result.version);
        assertEquals(Integer.valueOf(1),result.counts.get("products"));
        assertEquals(Integer.valueOf(2),result.counts.get("suppliers"));
        assertEquals(Integer.valueOf(-1),result.counts.get("repairs"));
        assertEquals(64,result.sha256.length());
    }
    @Test public void acceptsLegacyBareDataObject() throws Exception {
        assertEquals("legacy-data-object",
            inspect("{\"settings\":{},\"products\":[],\"sales\":[]}").format);
    }
    @Test public void rejectsUnsupportedVersion() throws Exception {
        try {
            inspect("{\"format\":\"alsafa-accounting-backup\",\"formatVersion\":3,"+
                "\"data\":{\"settings\":{},\"products\":[],\"sales\":[]}}");
            fail("Unsupported version accepted");
        } catch(org.json.JSONException expected) {}
    }
    @Test public void rejectsWrongCollectionTypes() throws Exception {
        try {
            inspect("{\"settings\":{},\"products\":[],\"sales\":[],\"suppliers\":{}}");
            fail("Malformed collection accepted");
        } catch(org.json.JSONException expected) {}
    }
    @Test public void rejectsDuplicateIds() throws Exception {
        try {
            inspect("{\\"settings\\":{},\\"products\\":[{\\"id\\":\\"same\\"},{\\"id\\":\\"same\\"}],\\"sales\\":[]}");
            fail("Duplicate IDs accepted");
        } catch(org.json.JSONException expected) {}
    }
    @Test public void rejectsMissingIds() throws Exception {
        try {
            inspect("{\\"settings\\":{},\\"products\\":[{}],\\"sales\\":[]}");
            fail("Missing IDs accepted");
        } catch(org.json.JSONException expected) {}
    }
    @Test public void rejectsNonObjectRows() throws Exception {
        try {
            inspect("{\\"settings\\":{},\\"products\\":[1],\\"sales\\":[]}");
            fail("Non-object record accepted");
        } catch(org.json.JSONException expected) {}
    }
}
