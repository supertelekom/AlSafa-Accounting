package com.alsafa.native6

import android.app.Activity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class BackupMediaPathsTest {
    @Test fun photoReferencesMoveToCurrentDevice() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val name="${UUID.randomUUID()}.jpg"
        val record=JSONObject().put("details",JSONObject().put("imagePath","/data/user/0/old.app/files/images/$name").toString())
        val data=JSONObject().put("products",JSONArray().put(record)).put("repairs",JSONArray())
        BackupMediaPaths.rebase(activity,data)
        assertEquals(File(activity.filesDir,"images/$name").canonicalPath,
            JSONObject(record.getString("details")).getString("imagePath"))
    }

    @Test fun unsafeReferenceIsRejected() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val record=JSONObject().put("details",JSONObject().put("imagePath","/tmp/other.txt").toString())
        val data=JSONObject().put("products",JSONArray().put(record)).put("repairs",JSONArray())
        assertThrows(IllegalArgumentException::class.java) {
            BackupMediaPaths.rebase(activity,data)
        }
    }
}
